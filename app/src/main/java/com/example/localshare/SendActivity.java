package com.example.localshare;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.format.Formatter;
import android.widget.Button;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class SendActivity extends Activity {

    private static final int PICK_FILES_REQUEST = 100;
    private static final int SERVER_PORT = 8080;

    private final List<Uri> fileUris = new ArrayList<>();
    private final List<String> fileNames = new ArrayList<>();

    private TextView addressText;
    private TextView filesText;
    private Button startButton;
    private FileServer server;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send);

        addressText = findViewById(R.id.addressText);
        filesText = findViewById(R.id.filesText);
        Button chooseButton = findViewById(R.id.chooseButton);
        startButton = findViewById(R.id.startButton);

        chooseButton.setOnClickListener(v -> pickFiles());
        startButton.setOnClickListener(v -> toggleServer());
    }

    private void pickFiles() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, PICK_FILES_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILES_REQUEST && resultCode == RESULT_OK && data != null) {
            fileUris.clear();
            fileNames.clear();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    addFile(data.getClipData().getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                addFile(data.getData());
            }
            updateFilesText();
        }
    }

    private void addFile(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
        fileUris.add(uri);
        fileNames.add(queryName(uri));
    }

    private String queryName(Uri uri) {
        String name = "file";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = cursor.getString(idx);
            }
        } catch (Exception ignored) {
        }
        return name;
    }

    private void updateFilesText() {
        StringBuilder sb = new StringBuilder();
        for (String n : fileNames) sb.append(n).append("\n");
        filesText.setText(sb.length() == 0 ? "No files chosen" : sb.toString());
    }

    private void toggleServer() {
        if (server == null) {
            if (fileUris.isEmpty()) {
                filesText.setText("Choose files first");
                return;
            }
            server = new FileServer(SERVER_PORT, getContentResolver(), fileUris, fileNames);
            server.start();
            String ip = getLocalIpAddress();
            addressText.setText("Sharing at:\nhttp://" + ip + ":" + SERVER_PORT + "/\n\nEnter this address on the receiving device.");
            startButton.setText("Stop Sharing");
        } else {
            server.stopServer();
            server = null;
            addressText.setText("Sharing stopped");
            startButton.setText("Start Sharing");
        }
    }

    private String getLocalIpAddress() {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        int ip = wm.getConnectionInfo().getIpAddress();
        return Formatter.formatIpAddress(ip);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (server != null) server.stopServer();
    }
}
