package com.song.Song;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Vertical list of song rows — reused by Search Results and Downloaded Songs
 * (the only difference between the two is whether the remove ("X") button shows).
 */
public class TrackRowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ROW = 0;
    private static final int TYPE_MESSAGE = 1;

    public interface Callback {
        void onClick(Track track, List<Track> queue);
        void onLongClick(Track track);
        void onRemove(Track track); // only used when showRemove is true
    }

    private List<Track> tracks = new ArrayList<Track>();
    private String message; // non-null shows a single message row instead of tracks (e.g. "Searching...")
    private final boolean showRemove;
    private final Callback callback;

    public TrackRowAdapter(boolean showRemove, Callback callback) {
        this.showRemove = showRemove;
        this.callback = callback;
    }

    public void submit(List<Track> newTracks) {
        message = null;
        tracks = newTracks != null ? newTracks : new ArrayList<Track>();
        notifyDataSetChanged();
    }

    public void showMessage(String text) {
        message = text;
        tracks = new ArrayList<Track>();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return message != null ? TYPE_MESSAGE : TYPE_ROW;
    }

    @Override
    public int getItemCount() { return message != null ? 1 : tracks.size(); }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_MESSAGE) {
            TextView tv = new TextView(parent.getContext());
            tv.setTextColor(parent.getContext().getResources().getColor(R.color.text_low));
            tv.setPadding(8, 16, 8, 16);
            return new MessageVH(tv);
        }
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_track_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder rawHolder, int position) {
        if (rawHolder instanceof MessageVH) {
            ((MessageVH) rawHolder).text.setText(message);
            return;
        }
        VH holder = (VH) rawHolder;
        final Track track = tracks.get(position);
        holder.title.setText(track.title);
        holder.artist.setText(track.artist);
        ImageLoader.load(holder.image, track.imageUrl);

        holder.itemView.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					if (callback != null) callback.onClick(track, tracks);
				}
			});
        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
				@Override public boolean onLongClick(View v) {
					if (callback != null) callback.onLongClick(track);
					return true;
				}
			});

        if (showRemove) {
            holder.removeBtn.setVisibility(View.VISIBLE);
            holder.removeBtn.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) {
						if (callback != null) callback.onRemove(track);
					}
				});
        } else {
            holder.removeBtn.setVisibility(View.GONE);
            holder.removeBtn.setOnClickListener(null);
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView image;
        TextView title, artist, removeBtn;
        VH(View itemView) {
            super(itemView);
            image = (ImageView) itemView.findViewById(R.id.rowImage);
            title = (TextView) itemView.findViewById(R.id.rowTitle);
            artist = (TextView) itemView.findViewById(R.id.rowArtist);
            removeBtn = (TextView) itemView.findViewById(R.id.rowRemoveBtn);
        }
    }

    static class MessageVH extends RecyclerView.ViewHolder {
        TextView text;
        MessageVH(TextView itemView) {
            super(itemView);
            text = itemView;
        }
    }
}
