package com.example.tycept;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

// Full-screen in-app viewer for chat photos/videos, launched from
// ChatMessageAdapter instead of handing the URL off to an external app.
// Both photos and videos are pinch-zoomable via PinchZoomLayout (see that
// class for why it owns touch input directly rather than passing taps
// through to the PlayerView).
//
// Video playback is backed by Media3 ExoPlayer rather than the old
// android.widget.VideoView: VideoView's default MediaController is the
// unstyled black/orange Holo widget, and VideoView doesn't reliably keep a
// video's native aspect ratio during playback. PlayerView's built-in
// AspectRatioFrameLayout (resize_mode="fit") fixes both — a 16:9 video stays
// 16:9, a 9:16 video stays 9:16, and the controls use our own modern style
// (see custom_video_controls.xml).
public class MediaViewerActivity extends Activity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_IS_VIDEO = "isVideo";

    private PlayerView playerView;
    private ExoPlayer exoPlayer;
    private boolean controllerVisible = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_viewer);

        String url = getIntent().getStringExtra(EXTRA_URL);
        boolean isVideo = getIntent().getBooleanExtra(EXTRA_IS_VIDEO, false);

        ImageView imageView = findViewById(R.id.viewerImage);
        playerView = findViewById(R.id.viewerVideo);
        PinchZoomLayout imageZoomLayout = findViewById(R.id.imageZoomLayout);
        PinchZoomLayout videoZoomLayout = findViewById(R.id.videoZoomLayout);
        final ProgressBar progressBar = findViewById(R.id.viewerProgress);
        View closeButton = findViewById(R.id.closeButton);

        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        if (url == null || url.length() == 0) {
            finish();
            return;
        }

        if (isVideo) {
            imageZoomLayout.setVisibility(View.GONE);
            videoZoomLayout.setVisibility(View.VISIBLE);
            videoZoomLayout.resetZoom();

            exoPlayer = new ExoPlayer.Builder(this).build();
            playerView.setPlayer(exoPlayer);

            // The PlayerView never receives raw touches directly
            // (PinchZoomLayout owns them so pinch/pan work), so a tap has to
            // toggle the controller ourselves instead of relying on
            // PlayerView's own touch-to-reveal behavior.
            playerView.setControllerVisibilityListener(new PlayerView.ControllerVisibilityListener() {
                @Override
                public void onVisibilityChanged(int visibility) {
                    controllerVisible = visibility == View.VISIBLE;
                }
            });
            videoZoomLayout.setOnSingleTapListener(new PinchZoomLayout.OnSingleTapListener() {
                @Override
                public void onSingleTap() {
                    if (controllerVisible) {
                        playerView.hideController();
                    } else {
                        playerView.showController();
                    }
                }
            });

            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY) {
                        progressBar.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(MediaViewerActivity.this, "Couldn't play this video", Toast.LENGTH_SHORT).show();
                }
            });

            exoPlayer.setMediaItem(MediaItem.fromUri(url));
            exoPlayer.setPlayWhenReady(true);
            exoPlayer.prepare();
        } else {
            videoZoomLayout.setVisibility(View.GONE);
            imageZoomLayout.setVisibility(View.VISIBLE);
            imageZoomLayout.resetZoom();

            Glide.with(this).load(url).listener(new RequestListener<Drawable>() {
                @Override
                public boolean onLoadFailed(GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(MediaViewerActivity.this, "Couldn't load this photo", Toast.LENGTH_SHORT).show();
                    return false;
                }

                @Override
                public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                    progressBar.setVisibility(View.GONE);
                    return false;
                }
            }).into(imageView);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (exoPlayer != null) {
            exoPlayer.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }
}
