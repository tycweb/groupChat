package com.example.localshare;

import android.app.Activity;
import android.app.DownloadManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;

public class ReceiveActivity extends Activity {

    private EditText addressInput;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive);

        addressInput = findViewById(R.id.addressInput);
        webView = findViewById(R.id.webView);
        View connectButton = findViewById(R.id.connectButton);

        AnimUtils.attachPressFeedback(connectButton);

        webView.getSettings().setJavaScriptEnabled(false);
        webView.setWebViewClient(new WebViewClient());
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            String fileName = Uri.parse(url).getLastPathSegment();
            if (fileName == null || fileName.isEmpty()) fileName = "downloaded_file";
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(request);
        });

        connectButton.setOnClickListener(v -> {
            String address = addressInput.getText().toString().trim();
            if (!address.isEmpty()) {
                String url = address.startsWith("http") ? address : "http://" + address + "/";
                webView.loadUrl(url);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        ConnectivityHelper.checkAndPrompt(this);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
