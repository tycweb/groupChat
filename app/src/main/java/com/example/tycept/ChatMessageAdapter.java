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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.util.List;

// RecyclerView.Adapter replaces the old ListView/ArrayAdapter. ListView's row
// recycling doesn't reliably re-measure a recycled row when its content type
// changes drastically between binds (e.g. a tall video row recycled into a
// short text-only row), which produced visible overlap between messages.
// RecyclerView's ViewHolder pattern + per-item measurement doesn't have that
// failure mode.
public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder> {

    private final Context context;
    private final List<ChatMessage> messages;
    private final LayoutInflater inflater;

    // Only one video plays inline at a time, like Messenger — tapping a
    // different one (or scrolling it off) reverts the previous back to its
    // thumbnail. This is UI-only state, not persisted with the message.
    private ChatMessage currentlyPlaying;

    public ChatMessageAdapter(Context context, List<ChatMessage> messages) {
        this.context = context;
        this.messages = messages;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).type;
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == ChatMessage.TYPE_SENT
                ? R.layout.item_message_sent
                : R.layout.item_message_received;
        View view = inflater.inflate(layout, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        final ChatMessage message = messages.get(position);

        if (holder.senderView != null) {
            if (message.type == ChatMessage.TYPE_RECEIVED) {
                holder.senderView.setText(message.senderName);
                holder.senderView.setVisibility(View.VISIBLE);
            } else {
                holder.senderView.setVisibility(View.GONE);
            }
        }

        if (TextUtils.isEmpty(message.text)) {
            // Pure photo/video message — the timestamp lives on the media
            // overlay instead, so this whole row would just be dead space.
            holder.textWrap.setVisibility(View.GONE);
        } else {
            holder.textWrap.setVisibility(View.VISIBLE);
            holder.textView.setText(message.text);
        }

        bindImage(holder, message);
        bindVideo(holder, message);

        holder.timeView.setText(DateFormat.format("hh:mm a", message.time));
    }

    @Override
    public void onViewRecycled(@NonNull MessageViewHolder holder) {
        super.onViewRecycled(holder);
        // A recycled row might still be holding a live player from whatever
        // message it displayed before — make sure it's stopped so audio/video
        // doesn't keep running behind a different, now-visible row.
        if (holder.player != null && holder.player.isPlaying()) {
            holder.player.stopPlayback();
        }
    }

    private void bindImage(final MessageViewHolder holder, final ChatMessage message) {
        if (TextUtils.isEmpty(message.imageUrl)) {
            holder.imageWrap.setVisibility(View.GONE);
            return;
        }

        holder.imageWrap.setVisibility(View.VISIBLE);
        holder.imageTimeOverlay.setText(DateFormat.format("hh:mm a", message.time));
        Glide.with(context)
                .load(message.imageUrl)
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .into(holder.imageView);

        holder.imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openInApp(message.imageUrl, false);
            }
        });
        holder.imageSaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveMedia(message.imageUrl, false);
            }
        });
    }

    private void bindVideo(final MessageViewHolder holder, final ChatMessage message) {
        if (TextUtils.isEmpty(message.videoUrl)) {
            holder.videoContainer.setVisibility(View.GONE);
            return;
        }
        holder.videoContainer.setVisibility(View.VISIBLE);

        final ImageView thumb = holder.videoThumb;
        final View playOverlay = holder.playButtonOverlay;
        final VideoView player = holder.player;
        holder.videoTimeOverlay.setText(DateFormat.format("hh:mm a", message.time));

        holder.videoSaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveMedia(message.videoUrl, true);
            }
        });
        holder.videoExpandButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openInApp(message.videoUrl, true);
            }
        });

        if (message == currentlyPlaying) {
            thumb.setVisibility(View.GONE);
            playOverlay.setVisibility(View.GONE);
            holder.videoSaveButton.setVisibility(View.GONE);
            holder.videoExpandButton.setVisibility(View.GONE);
            holder.videoTimeOverlay.setVisibility(View.GONE);

            // The thumbnail (adjustViewBounds) is what actually sizes the
            // bubble to the video's real aspect ratio. Once it's hidden the
            // VideoView needs an explicit height or the bubble would collapse
            // — so we lock it to whatever height the thumbnail last measured.
            int lockedHeight = thumb.getHeight() > 0 ? thumb.getHeight() : player.getLayoutParams().height;
            ViewGroup.LayoutParams lp = player.getLayoutParams();
            if (lp.height != lockedHeight) {
                lp.height = lockedHeight;
                player.setLayoutParams(lp);
            }
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
                            notifyItemChanged(holder.getAdapterPosition());
                        }
                    }
                });
                player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mp, int what, int extra) {
                        if (currentlyPlaying == message) {
                            currentlyPlaying = null;
                            notifyItemChanged(holder.getAdapterPosition());
                        }
                        return true;
                    }
                });
            }

            // Tap the playing video again to stop and go back to the thumbnail.
            holder.videoContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    player.stopPlayback();
                    currentlyPlaying = null;
                    notifyItemChanged(holder.getAdapterPosition());
                }
            });
        } else {
            if (player.isPlaying()) {
                player.stopPlayback();
            }
            player.setVisibility(View.GONE);
            thumb.setVisibility(View.VISIBLE);
            playOverlay.setVisibility(View.VISIBLE);
            holder.videoSaveButton.setVisibility(View.VISIBLE);
            holder.videoExpandButton.setVisibility(View.VISIBLE);
            holder.videoTimeOverlay.setVisibility(View.VISIBLE);
            VideoThumbnailLoader.load(message.videoUrl, thumb);

            holder.videoContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Quick press-bounce on the play button for a bit of life
                    // before the video takes over the bubble.
                    playOverlay.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80)
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    currentlyPlaying = message;
                                    notifyItemChanged(holder.getAdapterPosition());
                                }
                            }).start();
                }
            });
        }
    }

    private void saveMedia(String url, boolean isVideo) {
        if (context instanceof Activity) {
            MediaSaver.save((Activity) context, url, isVideo);
        }
    }

    private void openInApp(String url, boolean isVideo) {
        Intent intent = new Intent(context, MediaViewerActivity.class);
        intent.putExtra(MediaViewerActivity.EXTRA_URL, url);
        intent.putExtra(MediaViewerActivity.EXTRA_IS_VIDEO, isVideo);
        context.startActivity(intent);
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView senderView;
        TextView textView;
        TextView timeView;
        View textWrap;

        View imageWrap;
        ImageView imageView;
        View imageSaveButton;
        TextView imageTimeOverlay;

        View videoContainer;
        ImageView videoThumb;
        View playButtonOverlay;
        VideoView player;
        View videoSaveButton;
        View videoExpandButton;
        TextView videoTimeOverlay;

        MessageViewHolder(View row) {
            super(row);
            senderView = row.findViewById(R.id.messageSender);
            textView = row.findViewById(R.id.messageText);
            timeView = row.findViewById(R.id.messageTime);
            textWrap = row.findViewById(R.id.messageTextWrap);

            imageWrap = row.findViewById(R.id.messageImageWrap);
            imageView = row.findViewById(R.id.messageImage);
            imageSaveButton = row.findViewById(R.id.imageSaveButton);
            imageTimeOverlay = row.findViewById(R.id.imageTimeOverlay);

            videoContainer = row.findViewById(R.id.messageVideoContainer);
            videoThumb = row.findViewById(R.id.messageVideoThumb);
            playButtonOverlay = row.findViewById(R.id.playButtonOverlay);
            player = row.findViewById(R.id.messageVideoPlayer);
            videoSaveButton = row.findViewById(R.id.videoSaveButton);
            videoExpandButton = row.findViewById(R.id.videoExpandButton);
            videoTimeOverlay = row.findViewById(R.id.videoTimeOverlay);
        }
    }
}
