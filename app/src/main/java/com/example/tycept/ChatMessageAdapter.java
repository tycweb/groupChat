package com.example.tycept;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.util.ArrayList;
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

    // One ExoPlayer per row that has ever played a video, created lazily so
    // scrolling past a video-less row costs nothing. Tracked here (rather
    // than only on the ViewHolder) so the activity can release every player
    // on teardown even if some are on rows currently scrolled off-screen.
    private final List<ExoPlayer> players = new ArrayList<>();

    // Matches the web app's GROUP_WINDOW_MS: consecutive messages from the
    // same sender within this window are visually grouped — name/avatar and
    // timestamp only show once per run, and the seam between bubbles flattens.
    private static final long GROUP_WINDOW_MS = 5 * 60 * 1000;

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

        boolean groupedPrev = isGrouped(messages.get(Math.max(position - 1, 0)), message, position > 0);
        boolean groupedNext = position < messages.size() - 1
                && isGrouped(message, messages.get(position + 1), true);

        if (holder.senderView != null) {
            if (message.type == ChatMessage.TYPE_RECEIVED && !groupedPrev) {
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
            // Messenger only shows the timestamp on the last bubble of a run.
            holder.timeView.setVisibility(groupedNext ? View.GONE : View.VISIBLE);
        }

        bindImage(holder, message, groupedNext);
        bindVideo(holder, message, groupedNext);

        holder.timeView.setText(DateFormat.format("hh:mm a", message.time));

        // Flatten the seam corner(s) that touch a neighboring bubble from the
        // same sender, and tighten the row's top spacing so a run reads as
        // one continuous shape rather than separate rounded pills.
        boolean isSent = message.type == ChatMessage.TYPE_SENT;
        int bubbleRes;
        if (groupedPrev && groupedNext) {
            bubbleRes = isSent ? R.drawable.bubble_sent_grouped_both : R.drawable.bubble_received_grouped_both;
        } else if (groupedPrev) {
            bubbleRes = isSent ? R.drawable.bubble_sent_grouped_prev : R.drawable.bubble_received_grouped_prev;
        } else if (groupedNext) {
            bubbleRes = isSent ? R.drawable.bubble_sent_grouped_next : R.drawable.bubble_received_grouped_next;
        } else {
            bubbleRes = isSent ? R.drawable.bubble_sent : R.drawable.bubble_received;
        }
        holder.bubbleContainer.setBackgroundResource(bubbleRes);

        int density = (int) (2 * context.getResources().getDisplayMetrics().density);
        int normalTopPadding = (int) (4 * context.getResources().getDisplayMetrics().density);
        holder.messageRow.setPadding(
                holder.messageRow.getPaddingLeft(),
                groupedPrev ? density : normalTopPadding,
                holder.messageRow.getPaddingRight(),
                holder.messageRow.getPaddingBottom());
    }

    // Mirrors the web app's groupedPrev/groupedNext check: same sender name,
    // within the grouping window, neither deleted.
    private boolean isGrouped(ChatMessage earlier, ChatMessage later, boolean hasNeighbor) {
        if (!hasNeighbor || earlier == later) return false;
        return earlier.senderName != null
                && earlier.senderName.equals(later.senderName)
                && Math.abs(later.time - earlier.time) < GROUP_WINDOW_MS;
    }

    @Override
    public void onViewRecycled(@NonNull MessageViewHolder holder) {
        super.onViewRecycled(holder);
        // A recycled row might still be holding a live player from whatever
        // message it displayed before — make sure it's stopped so audio/video
        // doesn't keep running behind a different, now-visible row.
        if (holder.exoPlayer != null) {
            holder.exoPlayer.stop();
        }
    }

    // The bubble's video container is a fixed width (230dp) with the height
    // derived from the video's real aspect ratio, clamped to the same
    // min/max the XML previously enforced via the thumbnail's
    // adjustViewBounds. Computing it from the cached thumbnail frame — the
    // same source both the "showing thumbnail" and "playing" states read —
    // means both states always resolve to the exact same pixel height, so
    // the bubble never visibly resizes when playback starts. A 16:9 video
    // stays the 16:9-derived height; a 9:16 video stays the 9:16-derived one.
    private static final int VIDEO_CONTAINER_WIDTH_DP = 230;
    private static final int VIDEO_MIN_HEIGHT_DP = 150;
    private static final int VIDEO_MAX_HEIGHT_DP = 320;
    private static final int VIDEO_DEFAULT_HEIGHT_DP = 200;

    private int videoContainerHeightPx(Bitmap thumbnailFrame) {
        float density = context.getResources().getDisplayMetrics().density;
        if (thumbnailFrame == null || thumbnailFrame.getWidth() <= 0 || thumbnailFrame.getHeight() <= 0) {
            return Math.round(VIDEO_DEFAULT_HEIGHT_DP * density);
        }
        int containerWidthPx = Math.round(VIDEO_CONTAINER_WIDTH_DP * density);
        int minHeightPx = Math.round(VIDEO_MIN_HEIGHT_DP * density);
        int maxHeightPx = Math.round(VIDEO_MAX_HEIGHT_DP * density);
        int height = Math.round(containerWidthPx * (float) thumbnailFrame.getHeight() / thumbnailFrame.getWidth());
        return Math.max(minHeightPx, Math.min(maxHeightPx, height));
    }

    private void bindImage(final MessageViewHolder holder, final ChatMessage message, boolean groupedNext) {
        if (TextUtils.isEmpty(message.imageUrl)) {
            holder.imageWrap.setVisibility(View.GONE);
            return;
        }

        holder.imageWrap.setVisibility(View.VISIBLE);
        holder.imageTimeOverlay.setVisibility(groupedNext ? View.GONE : View.VISIBLE);
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

    private void bindVideo(final MessageViewHolder holder, final ChatMessage message, final boolean groupedNext) {
        if (TextUtils.isEmpty(message.videoUrl)) {
            holder.videoContainer.setVisibility(View.GONE);
            return;
        }
        holder.videoContainer.setVisibility(View.VISIBLE);

        final ImageView thumb = holder.videoThumb;
        final View playOverlay = holder.playButtonOverlay;
        final PlayerView player = holder.player;
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

            // Same height formula the thumbnail state uses below, read from
            // the same cached frame — guarantees a pixel-identical height so
            // there's no jump when swapping from thumbnail to player.
            int height = videoContainerHeightPx(VideoThumbnailLoader.getCached(message.videoUrl));
            ViewGroup.LayoutParams lp = player.getLayoutParams();
            if (lp.height != height) {
                lp.height = height;
                player.setLayoutParams(lp);
            }
            player.setVisibility(View.VISIBLE);

            if (holder.exoPlayer == null) {
                holder.exoPlayer = new ExoPlayer.Builder(context).build();
                players.add(holder.exoPlayer);
                player.setPlayer(holder.exoPlayer);
            }
            final ExoPlayer exoPlayer = holder.exoPlayer;

            if (exoPlayer.getPlaybackState() == Player.STATE_IDLE || exoPlayer.getCurrentMediaItem() == null) {
                exoPlayer.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int playbackState) {
                        if (playbackState == Player.STATE_ENDED && currentlyPlaying == message) {
                            currentlyPlaying = null;
                            notifyItemChanged(holder.getAdapterPosition());
                        }
                    }

                    @Override
                    public void onPlayerError(PlaybackException error) {
                        if (currentlyPlaying == message) {
                            currentlyPlaying = null;
                            notifyItemChanged(holder.getAdapterPosition());
                        }
                    }
                });
                exoPlayer.setMediaItem(MediaItem.fromUri(message.videoUrl));
                exoPlayer.setRepeatMode(Player.REPEAT_MODE_OFF);
                exoPlayer.prepare();
            }
            exoPlayer.setPlayWhenReady(true);

            // Tap the playing video again to stop and go back to the thumbnail.
            holder.videoContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    exoPlayer.pause();
                    exoPlayer.seekTo(0);
                    currentlyPlaying = null;
                    notifyItemChanged(holder.getAdapterPosition());
                }
            });
        } else {
            if (holder.exoPlayer != null) {
                holder.exoPlayer.stop();
            }
            player.setVisibility(View.GONE);
            thumb.setVisibility(View.VISIBLE);
            playOverlay.setVisibility(View.VISIBLE);
            holder.videoSaveButton.setVisibility(View.VISIBLE);
            holder.videoExpandButton.setVisibility(View.VISIBLE);
            holder.videoTimeOverlay.setVisibility(groupedNext ? View.GONE : View.VISIBLE);
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

    // Every inline chat-bubble ExoPlayer this adapter has created, including
    // ones on rows currently scrolled off-screen. Call from the host
    // Activity's onDestroy() so playback surfaces don't leak.
    public void releaseAllPlayers() {
        for (ExoPlayer p : players) {
            p.release();
        }
        players.clear();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        View messageRow;
        View bubbleContainer;
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
        PlayerView player;
        ExoPlayer exoPlayer;
        View videoSaveButton;
        View videoExpandButton;
        TextView videoTimeOverlay;

        MessageViewHolder(View row) {
            super(row);
            messageRow = row.findViewById(R.id.messageRow);
            bubbleContainer = row.findViewById(R.id.bubbleContainer);
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
