package com.song.Song;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.VH> {

    public interface Callback {
        void onDelete(int index);
    }

    private JSONArray playlists = new JSONArray();
    private final Callback callback;

    public PlaylistAdapter(Callback callback) {
        this.callback = callback;
    }

    public void submit(JSONArray newPlaylists) {
        playlists = newPlaylists != null ? newPlaylists : new JSONArray();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() { return playlists.length(); }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, final int position) {
        JSONObject p = playlists.optJSONObject(position);
        if (p == null) return;
        int songCount = p.optJSONArray("tracks") != null ? p.optJSONArray("tracks").length() : 0;
        holder.name.setText(p.optString("name"));
        holder.count.setText(songCount + " songs");
        holder.deleteBtn.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					if (callback != null) callback.onDelete(position);
				}
			});
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, count, deleteBtn;
        VH(View itemView) {
            super(itemView);
            name = (TextView) itemView.findViewById(R.id.playlistName);
            count = (TextView) itemView.findViewById(R.id.playlistCount);
            deleteBtn = (TextView) itemView.findViewById(R.id.playlistDeleteBtn);
        }
    }
}
