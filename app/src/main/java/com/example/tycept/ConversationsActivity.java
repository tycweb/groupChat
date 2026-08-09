package com.example.tycept;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public class ConversationsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversations);

        TextView presenceLine = findViewById(R.id.presenceLine);
        final String myName = SocketManager.getInstance().myName;
        presenceLine.setText("logged in as " + myName);

        ListView listView = findViewById(R.id.conversationListView);
        ConversationAdapter adapter = new ConversationAdapter(
                this,
                SocketManager.getInstance().conversations,
                myName,
                new ConversationAdapter.Listener() {
                    @Override
                    public void onConversationClick(JSONObject conv, String title) {
                        String convId = conv.optString("id");
                        Intent intent = new Intent(ConversationsActivity.this, ChatActivity.class);
                        intent.putExtra("conversationId", convId);
                        intent.putExtra("conversationTitle", title);
                        startActivity(intent);
                    }
                }
        );
        listView.setAdapter(adapter);

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
}
