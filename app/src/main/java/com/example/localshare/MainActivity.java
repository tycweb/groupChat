package com.example.localshare;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View logo = findViewById(R.id.logoContainer);
        View title = findViewById(R.id.titleText);
        View subtitle = findViewById(R.id.subtitleText);
        View sendButton = findViewById(R.id.sendButton);
        View receiveButton = findViewById(R.id.receiveButton);

        AnimUtils.fadeSlideIn(logo, 0);
        AnimUtils.fadeSlideIn(title, 80);
        AnimUtils.fadeSlideIn(subtitle, 140);
        AnimUtils.fadeSlideIn(sendButton, 220);
        AnimUtils.fadeSlideIn(receiveButton, 300);

        AnimUtils.attachPressFeedback(sendButton);
        AnimUtils.attachPressFeedback(receiveButton);

        sendButton.setOnClickListener(v -> startActivity(new Intent(this, SendActivity.class)));
        receiveButton.setOnClickListener(v -> startActivity(new Intent(this, ReceiveActivity.class)));
    }
}
