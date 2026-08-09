package com.example.tycept;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import io.socket.client.Ack;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import org.json.JSONObject;

public class LoginActivity extends Activity {

    private static final String PREFS = "tycept_prefs";
    private static final String KEY_REMEMBER = "remember";
    private static final String KEY_NAME = "saved_name";
    private static final String KEY_PASSWORD = "saved_password";

    private EditText nameInput;
    private EditText passwordInput;
    private CheckBox rememberMeCheckbox;
    private Button joinButton;
    private TextView errorText;
    private View formFields;
    private View skeletonContainer;
    private TextView skeletonLabel;

    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;
    private boolean joinResolved;

    private Emitter.Listener onConnectError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        nameInput = findViewById(R.id.nameInput);
        passwordInput = findViewById(R.id.passwordInput);
        rememberMeCheckbox = findViewById(R.id.rememberMeCheckbox);
        joinButton = findViewById(R.id.joinButton);
        errorText = findViewById(R.id.errorText);
        formFields = findViewById(R.id.formFields);
        skeletonContainer = findViewById(R.id.skeletonContainer);
        skeletonLabel = findViewById(R.id.skeletonLabel);

        joinButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptJoin();
            }
        });

        maybeAutoLogin();
    }

    private void maybeAutoLogin() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean remember = prefs.getBoolean(KEY_REMEMBER, false);
        String savedName = prefs.getString(KEY_NAME, null);
        String savedPassword = prefs.getString(KEY_PASSWORD, null);

        if (remember && !TextUtils.isEmpty(savedName) && !TextUtils.isEmpty(savedPassword)) {
            nameInput.setText(savedName);
            passwordInput.setText(savedPassword);
            skeletonLabel.setText("Signing you back in…");
            attemptJoin();
        }
    }

    private void attemptJoin() {
        final String name = nameInput.getText().toString().trim();
        final String password = passwordInput.getText().toString();

        if (TextUtils.isEmpty(name)) {
            showError("Enter a name");
            return;
        }
        if (password.length() < 4) {
            showError("Password must be at least 4 characters");
            return;
        }

        errorText.setVisibility(View.GONE);
        showSkeleton(true);
        joinResolved = false;

        final Socket socket = SocketManager.getInstance().getSocket();

        onConnectError = new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (joinResolved) return;
                        String detail = args.length > 0 ? String.valueOf(args[0]) : "unknown error";
                        showError("Can't reach server: " + detail);
                        finishAttempt();
                    }
                });
            }
        };
        socket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);

        timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (joinResolved) return;
                showError("Still waiting on the server. Free hosting can take up to a minute to wake up — try again in a bit.");
                finishAttempt();
            }
        };
        mainHandler.postDelayed(timeoutRunnable, 45000);

        JSONObject payload = new JSONObject();
        try {
            payload.put("name", name);
            payload.put("password", password);
        } catch (Exception e) {
            return;
        }

        socket.emit("join", payload, new Ack() {
            @Override
            public void call(final Object... args) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        joinResolved = true;
                        mainHandler.removeCallbacks(timeoutRunnable);
                        handleJoinResponse(args, name, password);
                    }
                });
            }
        });
    }

    private void showSkeleton(boolean show) {
        if (show) {
            formFields.setVisibility(View.GONE);
            skeletonContainer.setVisibility(View.VISIBLE);
            Animation pulse = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pulse);
            skeletonContainer.startAnimation(pulse);
        } else {
            skeletonContainer.clearAnimation();
            skeletonContainer.setVisibility(View.GONE);
            formFields.setVisibility(View.VISIBLE);
        }
    }

    private void finishAttempt() {
        joinResolved = true;
        showSkeleton(false);
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    private void handleJoinResponse(Object[] args, String name, String password) {
        finishAttempt();

        if (args.length == 0 || !(args[0] instanceof JSONObject)) {
            showError("Something went wrong. Try again.");
            return;
        }

        JSONObject result = (JSONObject) args[0];

        if (result.has("error")) {
            String error = result.optString("error");
            if ("wrong-password".equals(error)) {
                showError("Wrong password for that name");
            } else if ("password-required".equals(error)) {
                showError("Password too short");
            } else {
                showError("Couldn't join: " + error);
            }
            // A saved login stopped working (e.g. password changed) — forget it
            // rather than looping on a failing auto-login every launch.
            clearSavedCredentials();
            return;
        }

        if (rememberMeCheckbox.isChecked()) {
            saveCredentials(name, password);
        } else {
            clearSavedCredentials();
        }

        SocketManager.getInstance().onJoined(
                result.optString("name"),
                password,
                result.optJSONArray("conversations")
        );

        Intent intent = new Intent(LoginActivity.this, ConversationsActivity.class);
        startActivity(intent);
        finish();
    }

    private void saveCredentials(String name, String password) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.putBoolean(KEY_REMEMBER, true);
        editor.putString(KEY_NAME, name);
        editor.putString(KEY_PASSWORD, password);
        editor.apply();
    }

    private void clearSavedCredentials() {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.clear();
        editor.apply();
    }
}
