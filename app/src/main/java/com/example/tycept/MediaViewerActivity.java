package com.example.tycept;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.VideoView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

// Full-screen in-app viewer for chat photos/videos, launched from
// ChatMessageAdapter instead of handing the URL off to an external app.
// Both photos and videos are pinch-zoomable via PinchZoomLayout (see that
// class for why it owns touch input directly rather than passing taps
// through to the VideoView).
public class MediaViewerActivity extends Activity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_IS_VIDEO = "isVideo";

    private VideoView videoView;
    private MediaController mediaController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_viewer);

        String url = getIntent().getStringExtra(EXTRA_URL);
        boolean isVideo = getIntent().getBooleanExtra(EXTRA_IS_VIDEO, false);

        ImageView imageView = findViewById(R.id.viewerImage);
        videoView = findViewById(R.id.viewerVideo);
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

            mediaController = new MediaController(this);
            mediaController.setAnchorView(videoView);
            videoView.setMediaController(mediaController);
            videoView.setVideoURI(Uri.parse(url));

            // The VideoView never receives raw touches directly (PinchZoomLayout
            // owns them so pinch/pan work), so a tap has to re-show the
            // controller ourselves instead of relying on VideoView's own
            // touch-to-reveal behavior.
            videoZoomLayout.setOnSingleTapListener(new PinchZoomLayout.OnSingleTapListener() {
                @Override
                public void onSingleTap() {
                    if (mediaController.isShowing()) {
                        mediaController.hide();
                    } else {
                        mediaController.show();
                    }
                }
            });

            videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    progressBar.setVisibility(View.GONE);
                    videoView.start();
                }
            });
            videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(MediaViewerActivity.this, "Couldn't play this video", Toast.LENGTH_SHORT).show();
                    return true;
                }
            });
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
        if (videoView != null && videoView.isPlaying()) {
            videoView.stopPlayback();
        }
    }
}
