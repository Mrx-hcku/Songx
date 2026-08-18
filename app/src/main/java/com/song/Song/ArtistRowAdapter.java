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
 * Renders the horizontal "Top Artists" row. Preserves the original behavior:
 * a fixed number of skeleton placeholders shown up front, with real artist
 * circles appended as each async fetch completes.
 */
public class ArtistRowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SKELETON = 0;
    private static final int TYPE_ARTIST = 1;

    public static class ArtistItem {
        public final String id, name, imageUrl;
        public ArtistItem(String id, String name, String imageUrl) {
            this.id = id; this.name = name; this.imageUrl = imageUrl;
        }
    }

    public interface OnArtistClick {
        void onClick(ArtistItem artist);
    }

    private int skeletonCount = 0;
    private final List<ArtistItem> loaded = new ArrayList<ArtistItem>();
    private final OnArtistClick callback;

    public ArtistRowAdapter(OnArtistClick callback) {
        this.callback = callback;
    }

    public void setSkeletonCount(int count) {
        skeletonCount = count;
        notifyDataSetChanged();
    }

    public void addArtist(ArtistItem artist) {
        loaded.add(artist);
        notifyItemInserted(skeletonCount + loaded.size() - 1);
    }

    public void submitAll(List<ArtistItem> artists) {
        skeletonCount = 0;
        loaded.clear();
        loaded.addAll(artists);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position < skeletonCount ? TYPE_SKELETON : TYPE_ARTIST;
    }

    @Override
    public int getItemCount() {
        return skeletonCount + loaded.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_artist_circle, parent, false);
        return viewType == TYPE_SKELETON ? new SkeletonVH(v) : new ArtistVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof SkeletonVH) {
            SkeletonVH vh = (SkeletonVH) holder;
            vh.image.setBackgroundColor(0xFF232B26);
            vh.name.setText("");
        } else if (holder instanceof ArtistVH) {
            final ArtistItem artist = loaded.get(position - skeletonCount);
            ArtistVH vh = (ArtistVH) holder;
            vh.name.setText(artist.name);
            if (artist.imageUrl != null && artist.imageUrl.length() > 0) {
                ImageLoader.load(vh.image, artist.imageUrl);
            }
            vh.itemView.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) {
						if (callback != null) callback.onClick(artist);
					}
				});
        }
    }

    static class ArtistVH extends RecyclerView.ViewHolder {
        ImageView image;
        TextView name;
        ArtistVH(View itemView) {
            super(itemView);
            image = (ImageView) itemView.findViewById(R.id.artistCircleImage);
            name = (TextView) itemView.findViewById(R.id.artistCircleName);
        }
    }

    static class SkeletonVH extends RecyclerView.ViewHolder {
        ImageView image;
        TextView name;
        SkeletonVH(View itemView) {
            super(itemView);
            image = (ImageView) itemView.findViewById(R.id.artistCircleImage);
            name = (TextView) itemView.findViewById(R.id.artistCircleName);
        }
    }
}
