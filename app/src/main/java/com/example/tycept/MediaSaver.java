package com.example.tycept;

import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

// Downloads a message's photo/video URL and saves it into the device's
// Pictures/Tycept or Movies/Tycept gallery folder, tapped from the small
// save button overlaid on each media bubble.
class MediaSaver {

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final int REQUEST_WRITE_STORAGE = 2001;

    static void save(final Activity activity, final String url, final boolean isVideo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && activity.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(
                    new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE);
            Toast.makeText(activity, "Tap save again once storage access is granted", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(activity, "Saving…", Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = downloadAndInsert(activity, url, isVideo);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(activity, ok ? "Saved to gallery" : "Couldn't save that", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private static boolean downloadAndInsert(Activity activity, String url, boolean isVideo) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.connect();
            InputStream in = conn.getInputStream();

            String fileName = "tycept_" + System.currentTimeMillis() + (isVideo ? ".mp4" : ".jpg");
            String mimeType = isVideo ? "video/mp4" : "image/jpeg";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        (isVideo ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES) + "/Tycept");

                Uri collection = isVideo
                        ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                Uri itemUri = activity.getContentResolver().insert(collection, values);
                if (itemUri == null) return false;

                OutputStream out = activity.getContentResolver().openOutputStream(itemUri);
                if (out == null) return false;
                copy(in, out);
                out.close();
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        isVideo ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES), "Tycept");
                if (!dir.exists()) dir.mkdirs();
                File outFile = new File(dir, fileName);
                FileOutputStream out = new FileOutputStream(outFile);
                copy(in, out);
                out.close();

                // Make it show up in the gallery app immediately.
                android.media.MediaScannerConnection.scanFile(
                        activity, new String[]{outFile.getAbsolutePath()}, new String[]{mimeType}, null);
            }

            in.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}
