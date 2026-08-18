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

public class RecommendationAdapter extends RecyclerView.Adapter<RecommendationAdapter.VH> {

    public interface Callback {
        void onClick(Track track, List<Track> queue);
        boolean isFavorite(String trackId);
        boolean toggleFavorite(Track track); // returns new favorite state
    }

    private List<Track> tracks = new ArrayList<Track>();
    private final Callback callback;

    public RecommendationAdapter(Callback callback) {
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
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recommendation_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final Track track = tracks.get(position);
        holder.title.setText(track.title);
        holder.artist.setText(track.artist);
        ImageLoader.load(holder.image, track.imageUrl);

        boolean fav = callback != null && callback.isFavorite(track.id);
        holder.heart.setImageResource(fav ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);

        holder.heart.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					if (callback == null) return;
					boolean nowFav = callback.toggleFavorite(track);
					holder.heart.setImageResource(nowFav ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
				}
			});

        holder.itemView.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					if (callback != null) callback.onClick(track, tracks);
				}
			});
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView image, heart;
        TextView title, artist;
        VH(View itemView) {
            super(itemView);
            image = (ImageView) itemView.findViewById(R.id.recRowImage);
            title = (TextView) itemView.findViewById(R.id.recRowTitle);
            artist = (TextView) itemView.findViewById(R.id.recRowArtist);
            heart = (ImageView) itemView.findViewById(R.id.recRowHeart);
        }
    }
}
