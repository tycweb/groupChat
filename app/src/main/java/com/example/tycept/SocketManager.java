package com.example.tycept;

import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import java.net.URISyntaxException;
import org.json.JSONArray;
import org.json.JSONObject;

public class SocketManager {

    public interface JoinCallback {
        void onReady();
        void onError(String message);
    }

    // Change this if your server URL ever changes.
    public static final String SERVER_URL = "https://chatting-htgk.onrender.com/";

    private static SocketManager instance;
    private Socket socket;

    // Session state kept here so it survives moving between activities.
    public String myName;
    public JSONArray conversations;

    // The server only knows who a socket belongs to after a "join" on
    // THAT specific connection. If the transport drops and reconnects
    // (app backgrounded, network blip, doze mode) it's a brand new
    // connection server-side even though it's the same Socket object
    // here, so we keep what we need to silently redo "join".
    private String myPassword;
    private boolean joined = false;

    private SocketManager() {
    }

    public static synchronized SocketManager getInstance() {
        if (instance == null) {
            instance = new SocketManager();
        }
        return instance;
    }

    public Socket getSocket() {
        if (socket == null) {
            try {
                IO.Options opts = new IO.Options();
                opts.reconnection = true;
                opts.forceNew = false;
                socket = IO.socket(SERVER_URL, opts);
            } catch (URISyntaxException e) {
                throw new RuntimeException("Bad server URL", e);
            }

            socket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    joined = false;
                }
            });
        }
        if (!socket.connected()) {
            socket.connect();
        }
        return socket;
    }

    /** Call this once, right after a successful "join" response from LoginActivity. */
    public void onJoined(String name, String password, JSONArray convos) {
        myName = name;
        myPassword = password;
        conversations = convos;
        joined = true;
    }

    /**
     * Makes sure this socket is connected AND has completed "join" on the
     * server before running callback.onReady(). Safe to call every time a
     * screen needs the socket — if we're already joined on the current
     * connection it calls back immediately; otherwise it waits for connect
     * (if needed) and silently redoes "join" using the last credentials.
     */
    public void ensureJoined(final JoinCallback callback) {
        final Socket s = getSocket();

        if (joined && s.connected()) {
            callback.onReady();
            return;
        }

        if (myName == null || myPassword == null) {
            // Never logged in this process (shouldn't normally happen since
            // every screen after LoginActivity requires a completed join).
            callback.onError("Not logged in");
            return;
        }

        if (s.connected()) {
            rejoin(s, callback);
            return;
        }

        s.once(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                rejoin(s, callback);
            }
        });
    }

    private void rejoin(Socket s, final JoinCallback callback) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("name", myName);
            payload.put("password", myPassword);
        } catch (Exception e) {
            callback.onError("Rejoin failed");
            return;
        }

        s.emit("join", payload, new Ack() {
            @Override
            public void call(Object... args) {
                if (args.length > 0 && args[0] instanceof JSONObject) {
                    JSONObject result = (JSONObject) args[0];
                    if (!result.has("error")) {
                        conversations = result.optJSONArray("conversations");
                        joined = true;
                        callback.onReady();
                        return;
                    }
                }
                joined = false;
                callback.onError("Rejoin failed");
            }
        });
    }
}
