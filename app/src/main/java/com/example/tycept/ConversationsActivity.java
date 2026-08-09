package com.example.tycept;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import org.json.JSONArray;
import org.json.JSONObject;

public class ConversationsActivity extends Activity {

    private String myName;
    private ListView listView;
    private ConversationAdapter adapter;
    private Socket socket;
    private Emitter.Listener onNewMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversations);

        TextView presenceLine = findViewById(R.id.presenceLine);
        myName = SocketManager.getInstance().myName;
        presenceLine.setText("logged in as " + myName);

        listView = findViewById(R.id.conversationListView);
        adapter = new ConversationAdapter(
                this,
                SocketManager.getInstance().conversations,
                myName,
                new ConversationAdapter.Listener() {
                    @Override
                    public void onConversationClick(JSONObject conv, String title) {
                        // Reading it now — drop the "new" highlight so it
                        // doesn't stay flagged when the user comes back.
                        conv.remove("_unread");
                        adapter.notifyDataSetChanged();

                        String convId = conv.optString("id");
                        Intent intent = new Intent(ConversationsActivity.this, ChatActivity.class);
                        intent.putExtra("conversationId", convId);
                        intent.putExtra("conversationTitle", title);
                        startActivity(intent);
                    }
                }
        );
        listView.setAdapter(adapter);

        socket = SocketManager.getInstance().getSocket();
        setupIncomingMessageListener();
        SocketManager.getInstance().ensureJoined(new SocketManager.JoinCallback() {
            @Override
            public void onReady() {
                // Already have "conversations" from login/rejoin; nothing else to do here.
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ConversationsActivity.this,
                        "Couldn't reconnect — pull to refresh once you're back online",
                        Toast.LENGTH_LONG).show();
            }
        });

        findViewById(R.id.brandLogo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.startAnimation(AnimationUtils.loadAnimation(ConversationsActivity.this, R.anim.press_scale));
                Toast.makeText(ConversationsActivity.this, "Signed in as " + myName, Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.newChatButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.startAnimation(AnimationUtils.loadAnimation(ConversationsActivity.this, R.anim.press_scale));
                Toast.makeText(ConversationsActivity.this, "New chat — coming soon", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.featuresTab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.startAnimation(AnimationUtils.loadAnimation(ConversationsActivity.this, R.anim.press_scale));
                Toast.makeText(ConversationsActivity.this, "Features tab — coming soon", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.menuTab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.startAnimation(AnimationUtils.loadAnimation(ConversationsActivity.this, R.anim.press_scale));
                Toast.makeText(ConversationsActivity.this, "Menu tab — coming soon", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Listens for every "message" event server-wide (same event ChatActivity
     * listens to) so the conversation list can react live: whichever chat the
     * message belongs to gets its preview/time updated, jumps to the top of
     * the list, and is highlighted (border + dot) until the user opens it.
     */
    private void setupIncomingMessageListener() {
        onNewMessage = new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (args.length == 0 || !(args[0] instanceof JSONObject)) return;
                        handleIncomingMessage((JSONObject) args[0]);
                    }
                });
            }
        };
        socket.on("message", onNewMessage);
    }

    private void handleIncomingMessage(JSONObject m) {
        String convId = m.optString("conversationId");
        if (convId.isEmpty()) return;

        JSONArray conversations = SocketManager.getInstance().conversations;
        if (conversations == null) return;

        JSONObject conv = null;
        for (int i = 0; i < conversations.length(); i++) {
            JSONObject candidate = conversations.optJSONObject(i);
            if (candidate != null && convId.equals(candidate.optString("id"))) {
                conv = candidate;
                break;
            }
        }
        // Message belongs to a conversation we don't have locally yet (e.g. a
        // brand new chat someone just started with us) — nothing to bump.
        if (conv == null) return;

        try {
            JSONObject lastMessage = new JSONObject();
            lastMessage.put("name", m.optString("name"));
            lastMessage.put("time", m.optLong("time", System.currentTimeMillis()));
            if (m.has("text")) lastMessage.put("text", m.optString("text"));
            if (m.has("image") && !m.isNull("image")) lastMessage.put("image", true);
            if (m.has("video") && !m.isNull("video")) lastMessage.put("video", true);
            if (m.has("audio") && !m.isNull("audio")) lastMessage.put("audio", true);
            conv.put("lastMessage", lastMessage);
        } catch (Exception e) {
            return;
        }

        String sender = m.optString("name");
        boolean fromSomeoneElse = sender != null && !sender.equals(myName);
        if (fromSomeoneElse) {
            try {
                conv.put("_unread", true);
            } catch (Exception ignored) {
            }
        }

        // Bump it to the top of the visible list (the conv JSONObject is the
        // same instance, so mutating it above already updated everyone who
        // holds a reference — this just reorders the adapter's copy).
        adapter.remove(conv);
        adapter.insert(conv, 0);
        if (fromSomeoneElse) {
            adapter.playBumpAnimationOnceFor(convId);
        }
        adapter.notifyDataSetChanged();
        listView.smoothScrollToPosition(0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (socket != null && onNewMessage != null) {
            socket.off("message", onNewMessage);
        }
    }
}
