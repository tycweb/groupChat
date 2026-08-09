package com.example.tycept;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.util.List;

public class ChatMessageAdapter extends ArrayAdapter<ChatMessage> {

    private LayoutInflater inflater;

    // Only one video plays inline at a time, like Messenger — tapping a
    // different one (or scrolling it off) reverts the previous back to its
    // thumbnail. This is UI-only state, not persisted with the message.
    private ChatMessage currentlyPlaying;

    public ChatMessageAdapter(Context context, List<ChatMessage> messages) {
        super(context, 0, messages);
        inflater = LayoutInflater.from(context);
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).type;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ChatMessage message = getItem(position);
        int layout = message.type == ChatMessage.TYPE_SENT
                ? R.layout.item_message_sent
                : R.layout.item_message_received;

        View view = convertView;
        if (view == null) {
            view = inflater.inflate(layout, parent, false);
        }

        TextView senderView = view.findViewById(R.id.messageSender);
        TextView textView = view.findViewById(R.id.messageText);
        TextView timeView = view.findViewById(R.id.messageTime);

        if (senderView != null) {
            if (message.type == ChatMessage.TYPE_RECEIVED) {
                senderView.setText(message.senderName);
                senderView.setVisibility(View.VISIBLE);
            } else {
                senderView.setVisibility(View.GONE);
            }
        }

        if (TextUtils.isEmpty(message.text)) {
            textView.setVisibility(View.GONE);
        } else {
            textView.setVisibility(View.VISIBLE);
            textView.setText(message.text);
        }

        bindImage(view, message);
        bindVideo(view, message);

        timeView.setText(DateFormat.format("hh:mm a", message.time));

        return view;
    }

    private void bindImage(View row, final ChatMessage message) {
        View wrap = row.findViewById(R.id.messageImageWrap);
        final ImageView imageView = row.findViewById(R.id.messageImage);
        View saveButton = row.findViewById(R.id.imageSaveButton);

        if (TextUtils.isEmpty(message.imageUrl)) {
            wrap.setVisibility(View.GONE);
            return;
        }

        wrap.setVisibility(View.VISIBLE);
        Glide.with(getContext())
                .load(message.imageUrl)
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .into(imageView);

        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openInApp(message.imageUrl, false);
            }
        });
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveMedia(message.imageUrl, false);
            }
        });
    }

    private void bindVideo(View row, final ChatMessage message) {
        View videoContainer = row.findViewById(R.id.messageVideoContainer);

        if (TextUtils.isEmpty(message.videoUrl)) {
            videoContainer.setVisibility(View.GONE);
            return;
        }
        videoContainer.setVisibility(View.VISIBLE);

        final ImageView thumb = row.findViewById(R.id.messageVideoThumb);
        final View playOverlay = row.findViewById(R.id.playButtonOverlay);
        final VideoView player = row.findViewById(R.id.messageVideoPlayer);
        View saveButton = row.findViewById(R.id.videoSaveButton);

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveMedia(message.videoUrl, true);
            }
        });

        if (message == currentlyPlaying) {
            thumb.setVisibility(View.GONE);
            playOverlay.setVisibility(View.GONE);
            saveButton.setVisibility(View.GONE);
            player.setVisibility(View.VISIBLE);

            if (!player.isPlaying()) {
                player.setVideoURI(Uri.parse(message.videoUrl));
                player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        mp.setLooping(false);
                        player.start();
                    }
                });
                player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        if (currentlyPlaying == message) {
                            currentlyPlaying = null;
                            notifyDataSetChanged();
                        }
                    }
                });
                player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mp, int what, int extra) {
                        if (currentlyPlaying == message) {
                            currentlyPlaying = null;
                            notifyDataSetChanged();
                        }
                        return true;
                    }
                });
            }

            // Tap the playing video again to stop and go back to the thumbnail.
            videoContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    player.stopPlayback();
                    currentlyPlaying = null;
                    notifyDataSetChanged();
                }
            });
        } else {
            // Defensive: this recycled row might still be holding a live player
            // from whatever message it displayed before — make sure it's stopped.
            if (player.isPlaying()) {
                player.stopPlayback();
            }
            player.setVisibility(View.GONE);
            thumb.setVisibility(View.VISIBLE);
            playOverlay.setVisibility(View.VISIBLE);
            saveButton.setVisibility(View.VISIBLE);
            VideoThumbnailLoader.load(message.videoUrl, thumb);

            videoContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Quick press-bounce on the play button for a bit of life
                    // before the video takes over the bubble.
                    playOverlay.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80)
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    currentlyPlaying = message;
                                    notifyDataSetChanged();
                                }
                            }).start();
                }
            });
        }
    }

    private void saveMedia(String url, boolean isVideo) {
        Context context = getContext();
        if (context instanceof Activity) {
            MediaSaver.save((Activity) context, url, isVideo);
        }
    }

    private void openInApp(String url, boolean isVideo) {
        Intent intent = new Intent(getContext(), MediaViewerActivity.class);
        intent.putExtra(MediaViewerActivity.EXTRA_URL, url);
        intent.putExtra(MediaViewerActivity.EXTRA_IS_VIDEO, isVideo);
        getContext().startActivity(intent);
    }
}
