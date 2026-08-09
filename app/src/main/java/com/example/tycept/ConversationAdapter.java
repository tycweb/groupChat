package com.example.tycept;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

public class ConversationAdapter extends ArrayAdapter<JSONObject> {

    public interface Listener {
        void onConversationClick(JSONObject conversation, String title);
    }

    private LayoutInflater inflater;
    private String myName;
    private Listener listener;

    public ConversationAdapter(Context context, JSONArray conversations, String myName, Listener listener) {
        super(context, 0);
        inflater = LayoutInflater.from(context);
        this.myName = myName;
        this.listener = listener;
        if (conversations != null) {
            for (int i = 0; i < conversations.length(); i++) {
                add(conversations.optJSONObject(i));
            }
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = inflater.inflate(R.layout.item_conversation, parent, false);
        }

        final JSONObject conv = getItem(position);
        View card = view.findViewById(R.id.convCard);
        TextView titleView = view.findViewById(R.id.convTitle);
        TextView previewView = view.findViewById(R.id.convPreview);
        TextView timeView = view.findViewById(R.id.convTime);
        View avatarBg = view.findViewById(R.id.convAvatarBg);
        TextView avatarInitial = view.findViewById(R.id.convAvatarInitial);

        String title = conv.optString("name");
        if (title == null || title.isEmpty()) {
            JSONArray members = conv.optJSONArray("members");
            title = "Chat";
            if (members != null) {
                for (int i = 0; i < members.length(); i++) {
                    String m = members.optString(i);
                    if (!m.equals(myName)) {
                        title = m;
                        break;
                    }
                }
            }
        }
        final String finalTitle = title;
        titleView.setText(title);

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(AvatarUtil.colorForName(title));
        avatarBg.setBackground(circle);
        avatarInitial.setText(AvatarUtil.initialForName(title));

        JSONObject last = conv.optJSONObject("lastMessage");
        if (last != null) {
            String text = last.optString("text");
            if (text == null || text.isEmpty()) {
                if (last.optBoolean("image")) text = "📷 Photo";
                else if (last.optBoolean("video")) text = "🎥 Video";
                else if (last.optBoolean("audio")) text = "🎤 Voice message";
                else text = "";
            }
            previewView.setText(text);
            long time = last.optLong("time", 0);
            if (time > 0) {
                timeView.setText(DateFormat.format("hh:mm a", time));
            } else {
                timeView.setText("");
            }
        } else {
            previewView.setText("No messages yet");
            timeView.setText("");
        }

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.press_scale));
                if (listener != null) {
                    listener.onConversationClick(conv, finalTitle);
                }
            }
        });

        return view;
    }
}
