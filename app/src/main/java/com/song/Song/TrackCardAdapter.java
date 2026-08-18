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
 * Renders a horizontal row of song cards (RecyclerView, replaces the old
 * manual HorizontalScrollView + LinearLayout.addView() loop).
 * Also doubles as the skeleton/loading-placeholder adapter so callers don't
 * need a separate class for the "loading..." state.
 */
public class TrackCardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SKELETON = 0;
    private static final int TYPE_CARD = 1;
    private static final int TYPE_ERROR = 2;
    private static final int SKELETON_COUNT = 4;

    public interface OnCardClick {
        void onClick(Track track, List<Track> queue);
        void onLongClick(Track track);
    }

    private List<Track> tracks = new ArrayList<Track>();
    private boolean loading;
    private boolean error;
    private final OnCardClick callback;

    public TrackCardAdapter(OnCardClick callback) {
        this.callback = callback;
    }

    public void showLoading() {
        loading = true;
        error = false;
        tracks = new ArrayList<Track>();
        notifyDataSetChanged();
    }

    public void showError() {
        loading = false;
        error = true;
        tracks = new ArrayList<Track>();
        notifyDataSetChanged();
    }

    public void submitTracks(List<Track> newTracks) {
        loading = false;
        error = false;
        tracks = newTracks != null ? newTracks : new ArrayList<Track>();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (loading) return TYPE_SKELETON;
        if (error) return TYPE_ERROR;
        return TYPE_CARD;
    }

    @Override
    public int getItemCount() {
        if (loading) return SKELETON_COUNT;
        if (error) return 1;
        return tracks.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SKELETON) {
            View v = inflater.inflate(R.layout.item_skeleton_card, parent, false);
            return new SkeletonVH(v);
        }
        if (viewType == TYPE_ERROR) {
            TextView empty = new TextView(parent.getContext());
            empty.setText("Failed to load.");
            empty.setTextColor(0xFFFF3D68);
            empty.setPadding(8, 8, 8, 8);
            return new SkeletonVH(empty);
        }
        View v = inflater.inflate(R.layout.item_song_card, parent, false);
        return new CardVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof CardVH) {
            final Track track = tracks.get(position);
            CardVH vh = (CardVH) holder;
            vh.title.setText(track.title);
            vh.artist.setText(track.artist);
            ImageLoader.load(vh.image, track.imageUrl);
            vh.itemView.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) {
						if (callback != null) callback.onClick(track, tracks);
					}
				});
            vh.itemView.setOnLongClickListener(new View.OnLongClickListener() {
					@Override public boolean onLongClick(View v) {
						if (callback != null) callback.onLongClick(track);
						return true;
					}
				});
        }
    }

    static class CardVH extends RecyclerView.ViewHolder {
        ImageView image;
        TextView title, artist;
        CardVH(View itemView) {
            super(itemView);
            image = (ImageView) itemView.findViewById(R.id.cardImage);
            title = (TextView) itemView.findViewById(R.id.cardTitle);
            artist = (TextView) itemView.findViewById(R.id.cardArtist);
        }
    }

    static class SkeletonVH extends RecyclerView.ViewHolder {
        SkeletonVH(View itemView) { super(itemView); }
    }
}
