package com.example.localshare;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.sendButton).setOnClickListener(v ->
                startActivity(new Intent(this, SendActivity.class)));

        findViewById(R.id.receiveButton).setOnClickListener(v ->
                startActivity(new Intent(this, ReceiveActivity.class)));
    }
}
