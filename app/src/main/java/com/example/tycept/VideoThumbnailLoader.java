package com.example.tycept;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

// Pulls a single frame out of a remote video URL (MediaMetadataRetriever supports
// network URIs directly) to use as a thumbnail in the chat bubble, since the
// server only gives us the video URL itself, not a separate poster image.
class VideoThumbnailLoader {

    private static final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(24) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return 1;
        }
    };
    private static final Set<String> loading = new HashSet<>();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Exposes the cached frame so callers can read its width/height (i.e. the
    // video's real aspect ratio) without re-decoding anything. Used to size
    // the inline player identically to the thumbnail it replaces, so the
    // chat bubble never visibly resizes when playback starts.
    static Bitmap getCached(String videoUrl) {
        return cache.get(videoUrl);
    }

    static void load(final String videoUrl, final ImageView target) {
        target.setTag(videoUrl);

        Bitmap cached = cache.get(videoUrl);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }

        target.setImageBitmap(null);

        synchronized (loading) {
            if (loading.contains(videoUrl)) return;
            loading.add(videoUrl);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap frame = null;
                try {
                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    retriever.setDataSource(videoUrl, new HashMap<String, String>());
                    frame = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if (frame == null) {
                        frame = retriever.getFrameAtTime(0);
                    }
                    retriever.release();
                } catch (Exception ignored) {
                    // No preview available for this video — the play button alone still works.
                }

                final Bitmap result = frame;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (loading) {
                            loading.remove(videoUrl);
                        }
                        if (result != null) {
                            cache.put(videoUrl, result);
                        }
                        if (result != null && videoUrl.equals(target.getTag())) {
                            target.setAlpha(0f);
                            target.setImageBitmap(result);
                            target.animate().alpha(1f).setDuration(200).start();
                        }
                    }
                });
            }
        }).start();
    }
}
