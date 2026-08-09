package com.example.tycept;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;

import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import io.socket.client.Ack;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import org.json.JSONArray;
import org.json.JSONObject;

public class ChatActivity extends Activity {

    private static final int REQUEST_PICK_MEDIA = 1001;

    // Mirrors the web app's compressImage()/sendVideo() constants (app.js) so
    // media sent from the phone behaves the same as media sent from the browser.
    private static final int MAX_IMAGE_DIMENSION = 1280;
    private static final int IMAGE_QUALITY = 72;
    private static final long MAX_VIDEO_BYTES = 20L * 1024 * 1024; // server hard-caps ~27MB base64 (~20MB raw); no on-device video compression yet, so we just refuse anything bigger up front instead of silently failing server-side

    private String conversationId;
    private String myName;

    private RecyclerView listView;
    private LinearLayoutManager layoutManager;
    private View skeletonContainer;
    private ObjectAnimator skeletonAnimator;
    private EditText messageInput;
    private View sendButton;
    private View attachButton;
    private TextView titleView;
    private TextView subtitleView;
    private TextView avatarInitialView;

    private List<ChatMessage> messages = new ArrayList<>();
    private ChatMessageAdapter adapter;

    private Socket socket;
    private Emitter.Listener onNewMessage;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean sendingMedia = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        conversationId = getIntent().getStringExtra("conversationId");
        String passedTitle = getIntent().getStringExtra("conversationTitle");
        myName = SocketManager.getInstance().myName;

        titleView = findViewById(R.id.chatTitle);
        titleView.setText(passedTitle);

        subtitleView = findViewById(R.id.chatSubtitle);
        avatarInitialView = findViewById(R.id.chatAvatarInitial);
        if (!TextUtils.isEmpty(passedTitle)) {
            avatarInitialView.setText(passedTitle.substring(0, 1).toUpperCase());
        }

        findViewById(R.id.backButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        View.OnClickListener comingSoon = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(ChatActivity.this, "Coming soon", Toast.LENGTH_SHORT).show();
            }
        };
        findViewById(R.id.searchButton).setOnClickListener(comingSoon);
        findViewById(R.id.paletteButton).setOnClickListener(comingSoon);
        findViewById(R.id.bellButton).setOnClickListener(comingSoon);

        listView = findViewById(R.id.messageListView);
        skeletonContainer = findViewById(R.id.skeletonContainer);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        attachButton = findViewById(R.id.attachButton);

        adapter = new ChatMessageAdapter(this, messages);
        layoutManager = new LinearLayoutManager(this);
        listView.setLayoutManager(layoutManager);
        listView.setAdapter(adapter);
        listView.setItemAnimator(null); // avoid the default fade/move animation
        // firing every time a video toggles play state via notifyItemChanged

        skeletonAnimator = ObjectAnimator.ofFloat(skeletonContainer, "alpha", 1f, 0.35f);
        skeletonAnimator.setDuration(700);
        skeletonAnimator.setRepeatMode(ValueAnimator.REVERSE);
        skeletonAnimator.setRepeatCount(ValueAnimator.INFINITE);
        skeletonAnimator.start();

        socket = SocketManager.getInstance().getSocket();

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });

        attachButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickMedia();
            }
        });

        setupIncomingMessageListener();

        SocketManager.getInstance().ensureJoined(new SocketManager.JoinCallback() {
            @Override
            public void onReady() {
                openConversation();
            }

            @Override
            public void onError(String message) {
                hideSkeleton();
                Toast.makeText(ChatActivity.this,
                        "Couldn't load messages — check your connection and reopen the chat",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openConversation() {
        JSONObject payload = new JSONObject();
        try {
            payload.put("id", conversationId);
        } catch (Exception e) {
            return;
        }

        socket.emit("open-conversation", payload, new Ack() {
            @Override
            public void call(final Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        handleOpenResult(args);
                    }
                });
            }
        });
    }

    private void handleOpenResult(Object[] args) {
        if (args.length == 0 || !(args[0] instanceof JSONObject)) return;
        JSONObject result = (JSONObject) args[0];
        if (result.has("error")) return;

        JSONArray history = result.optJSONArray("history");
        messages.clear();
        if (history != null) {
            for (int i = 0; i < history.length(); i++) {
                JSONObject m = history.optJSONObject(i);
                addMessageFromJson(m);
            }
        }
        adapter.notifyDataSetChanged();
        if (!messages.isEmpty()) {
            listView.scrollToPosition(messages.size() - 1);
        }
        hideSkeleton();
    }

    private void hideSkeleton() {
        if (skeletonAnimator != null) {
            skeletonAnimator.cancel();
        }
        if (skeletonContainer != null) {
            skeletonContainer.setVisibility(View.GONE);
        }
        listView.setVisibility(View.VISIBLE);
    }

    private void addMessageFromJson(JSONObject m) {
        if (m == null) return;
        String senderName = m.optString("name");
        String text = m.optString("text");
        if (m.optBoolean("deleted")) {
            text = "(deleted)";
        }
        long time = m.optLong("time", System.currentTimeMillis());
        int type = senderName.equals(myName) ? ChatMessage.TYPE_SENT : ChatMessage.TYPE_RECEIVED;
        ChatMessage message = new ChatMessage(senderName, text, time, type);

        // Server sends these as public Supabase URLs (or JSON null if there's no
        // media on this message) — same "image"/"video" fields the web app uses.
        // NOTE: Android's on-device org.json can stringify a JSON null value into
        // the literal string "null" via optString's fallback — so we check isNull()
        // explicitly rather than trusting the fallback.
        String image = extractMediaUrl(m, "image");
        if (!TextUtils.isEmpty(image)) message.imageUrl = image;
        String video = extractMediaUrl(m, "video");
        if (!TextUtils.isEmpty(video)) message.videoUrl = video;

        messages.add(message);
    }

    private static String extractMediaUrl(JSONObject m, String key) {
        if (!m.has(key) || m.isNull(key)) return null;
        String value = m.optString(key, "");
        if (TextUtils.isEmpty(value) || "null".equals(value)) return null;
        return value;
    }

    private void setupIncomingMessageListener() {
        onNewMessage = new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (args.length == 0 || !(args[0] instanceof JSONObject)) return;
                        JSONObject m = (JSONObject) args[0];
                        String msgConvId = m.optString("conversationId");
                        if (!msgConvId.equals(conversationId)) return;
                        addMessageFromJson(m);
                        adapter.notifyDataSetChanged();
                        listView.scrollToPosition(messages.size() - 1);
                    }
                });
            }
        };
        socket.on("message", onNewMessage);
    }

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        JSONObject payload = new JSONObject();
        try {
            payload.put("conversationId", conversationId);
            payload.put("text", text);
        } catch (Exception e) {
            return;
        }

        socket.emit("message", payload);
        messageInput.setText("");
        // The server will echo this message back via the "message" event
        // (broadcast to the whole room, including us), so we don't add it
        // locally here — that would double it up.
    }

    // --- Photo / video sending -------------------------------------------------
    // Mirrors the web app's flow (app.js: sendImage/sendVideo): read the picked
    // file, base64-encode it into a "data:<mime>;base64,..." string, and emit it
    // on the same "message" event as text — the server (server.js) uploads it to
    // Supabase Storage and broadcasts back a message with a public "image"/"video" URL.

    private void pickMedia() {
        if (sendingMedia) return;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "Send photo or video"), REQUEST_PICK_MEDIA);
        } catch (Exception e) {
            Toast.makeText(this, "No app found to pick media", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_MEDIA || resultCode != RESULT_OK || data == null) return;

        final Uri uri = data.getData();
        if (uri == null) return;

        String mimeType = getContentResolver().getType(uri);
        if (mimeType == null) mimeType = "";

        if (mimeType.startsWith("image/")) {
            sendImageAsync(uri);
        } else if (mimeType.startsWith("video/")) {
            sendVideoAsync(uri, mimeType);
        } else {
            Toast.makeText(this, "Only photos and videos can be sent", Toast.LENGTH_SHORT).show();
        }
    }

    private void setSendingMedia(boolean sending, String toastIfStarting) {
        sendingMedia = sending;
        attachButton.setEnabled(!sending);
        if (sending && toastIfStarting != null) {
            Toast.makeText(this, toastIfStarting, Toast.LENGTH_SHORT).show();
        }
    }

    private void sendImageAsync(final Uri uri) {
        setSendingMedia(true, "Sending photo…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                    bitmap = downscale(bitmap, MAX_IMAGE_DIMENSION);

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, baos);
                    final String dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            emitMediaMessage("image", dataUrl);
                            setSendingMedia(false, null);
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            setSendingMedia(false, null);
                            Toast.makeText(ChatActivity.this, "Couldn't send that photo — try a different one", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void sendVideoAsync(final Uri uri, final String mimeType) {
        setSendingMedia(true, "Sending video…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream in = getContentResolver().openInputStream(uri);
                    if (in == null) throw new Exception("Couldn't open video");

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    long total = 0;
                    while ((read = in.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_VIDEO_BYTES) {
                            in.close();
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    setSendingMedia(false, null);
                                    Toast.makeText(ChatActivity.this, "That video is too large to send. Try a shorter clip.", Toast.LENGTH_LONG).show();
                                }
                            });
                            return;
                        }
                        baos.write(buffer, 0, read);
                    }
                    in.close();

                    final String dataUrl = "data:" + mimeType + ";base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            emitMediaMessage("video", dataUrl);
                            setSendingMedia(false, null);
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            setSendingMedia(false, null);
                            Toast.makeText(ChatActivity.this, "Couldn't send that video — try a different one", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void emitMediaMessage(String field, String dataUrl) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("conversationId", conversationId);
            payload.put(field, dataUrl);
        } catch (Exception e) {
            return;
        }
        socket.emit("message", payload);
        // Same as text: the server broadcasts this back to us via "message", so
        // we don't add it locally here.
    }

    private static Bitmap downscale(Bitmap bitmap, int maxDimension) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= maxDimension && height <= maxDimension) return bitmap;

        float scale = (float) maxDimension / Math.max(width, height);
        int newWidth = Math.round(width * scale);
        int newHeight = Math.round(height * scale);
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (skeletonAnimator != null) {
            skeletonAnimator.cancel();
        }
        if (socket != null && onNewMessage != null) {
            socket.off("message", onNewMessage);
        }
    }
}
