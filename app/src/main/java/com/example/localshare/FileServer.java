package com.example.localshare;

import android.content.ContentResolver;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.List;

/**
 * A minimal single-purpose HTTP server that serves an index page listing
 * picked files, and streams each file's bytes on request. Not meant to be
 * a general-purpose web server, only a local file share endpoint.
 */
public class FileServer extends Thread {

    public interface Listener {
        void onError(Exception e);
    }

    private final int port;
    private final ContentResolver resolver;
    private final List<Uri> fileUris;
    private final List<String> fileNames;
    private ServerSocket serverSocket;
    private volatile boolean running = true;
    private Listener listener;

    public FileServer(int port, ContentResolver resolver, List<Uri> fileUris, List<String> fileNames) {
        this.port = port;
        this.resolver = resolver;
        this.fileUris = fileUris;
        this.fileNames = fileNames;
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void stopServer() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            while (running) {
                Socket client = serverSocket.accept();
                new Thread(() -> handleClient(client)).start();
            }
        } catch (IOException e) {
            if (running && listener != null) listener.onError(e);
        }
    }

    private void handleClient(Socket client) {
        try (Socket socket = client) {
            InputStream in = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String requestLine = reader.readLine();
            if (requestLine == null) return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String path = URLDecoder.decode(parts[1], "UTF-8");

            OutputStream out = socket.getOutputStream();

            if (path.equals("/") || path.equals("/index.html")) {
                serveIndex(out);
            } else if (path.startsWith("/download/")) {
                int idx = Integer.parseInt(path.substring("/download/".length()));
                serveFile(out, idx);
            } else {
                writeHeader(out, 404, "text/plain", -1);
                out.write("Not found".getBytes("UTF-8"));
            }
        } catch (Exception e) {
            // Per-connection errors are not fatal to the server.
        }
    }

    private void serveIndex(OutputStream out) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><meta charset='utf-8'>")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1'>")
                .append("<title>LocalShare</title></head><body style='font-family:sans-serif;background:#0a0b10;color:#fff;padding:24px;'>")
                .append("<h2>Shared files</h2><ul style='line-height:2;'>");
        for (int i = 0; i < fileNames.size(); i++) {
            html.append("<li><a style='color:#7c93ff;' href=\"/download/").append(i).append("\">")
                    .append(escapeHtml(fileNames.get(i))).append("</a></li>");
        }
        html.append("</ul></body></html>");
        byte[] body = html.toString().getBytes("UTF-8");
        writeHeader(out, 200, "text/html; charset=utf-8", body.length);
        out.write(body);
    }

    private void serveFile(OutputStream out, int idx) throws IOException {
        if (idx < 0 || idx >= fileUris.size()) {
            writeHeader(out, 404, "text/plain", -1);
            out.write("Not found".getBytes("UTF-8"));
            return;
        }
        Uri uri = fileUris.get(idx);
        String name = fileNames.get(idx);
        String mime = resolver.getType(uri);
        if (mime == null) {
            String ext = MimeTypeMap.getFileExtensionFromUrl(name);
            mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        }
        if (mime == null) mime = "application/octet-stream";

        try (InputStream fis = resolver.openInputStream(uri)) {
            if (fis == null) throw new IOException("cannot open file");
            StringBuilder header = new StringBuilder();
            header.append("HTTP/1.1 200 OK\r\n")
                    .append("Content-Type: ").append(mime).append("\r\n")
                    .append("Content-Disposition: attachment; filename=\"").append(name).append("\"\r\n")
                    .append("Connection: close\r\n\r\n");
            out.write(header.toString().getBytes("UTF-8"));

            byte[] buf = new byte[8192];
            int read;
            while ((read = fis.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
        }
        out.flush();
    }

    private void writeHeader(OutputStream out, int code, String contentType, long length) throws IOException {
        String status = code == 200 ? "OK" : code == 404 ? "Not Found" : "Error";
        StringBuilder header = new StringBuilder();
        header.append("HTTP/1.1 ").append(code).append(" ").append(status).append("\r\n")
                .append("Content-Type: ").append(contentType).append("\r\n");
        if (length >= 0) header.append("Content-Length: ").append(length).append("\r\n");
        header.append("Connection: close\r\n\r\n");
        out.write(header.toString().getBytes("UTF-8"));
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
