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

public class RecentPlayAdapter extends RecyclerView.Adapter<RecentPlayAdapter.VH> {

    public interface Callback {
        void onClick(Track track, List<Track> queue);
    }

    private List<Track> tracks = new ArrayList<Track>();
    private final Callback callback;

    public RecentPlayAdapter(Callback callback) {
        this.callback = callback;
    }

    public void submit(List<Track> newTracks) {
        tracks = newTracks != null ? newTracks : new ArrayList<Track>();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() { return tracks.size(); }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recent_play_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final Track track = tracks.get(position);
        holder.title.setText(track.title + " - " + track.artist);
        ImageLoader.load(holder.image, track.imageUrl);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					if (callback != null) callback.onClick(track, tracks);
				}
			});
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView image;
        TextView title;
        VH(View itemView) {
            super(itemView);
            image = (ImageView) itemView.findViewById(R.id.recentImage);
            title = (TextView) itemView.findViewById(R.id.recentTitle);
        }
    }
}
