package com.song.Song;

import androidx.appcompat.app.AppCompatActivity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class SeeAllActivity extends AppCompatActivity {

    public static final String MODE_QUERY = "query";
    public static final String MODE_ARTIST = "artist";

    private static final String API_BASE_URL = "https://song1-beta.vercel.app/";
    private static final int PAGE_SIZE = 20;

    private String mode;
    private String value;   // search query text OR artist id
    private int currentPage = 0;
    private boolean isLoading = false;
    private boolean hasMore = true;
    private final List<Track> loadedTracks = new ArrayList<Track>();

    private LinearLayout seeAllContainer;
    private ProgressBar seeAllLoadingMore;
    private ScrollView seeAllScrollView;

    private MusicService musicService;
    private boolean serviceBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            musicService = ((MusicService.MusicBinder) binder).getService();
            serviceBound = true;
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            musicService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_see_all);

        mode = getIntent().getStringExtra("mode");
        value = getIntent().getStringExtra("value");
        String title = getIntent().getStringExtra("title");

        TextView titleText = (TextView) findViewById(R.id.seeAllTitleText);
        titleText.setText(title != null ? title : "Songs");

        seeAllContainer = (LinearLayout) findViewById(R.id.seeAllContainer);
        seeAllLoadingMore = (ProgressBar) findViewById(R.id.seeAllLoadingMore);
        seeAllScrollView = (ScrollView) findViewById(R.id.seeAllScrollView);

        findViewById(R.id.seeAllBackBtn).setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { finish(); }
			});

        seeAllScrollView.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
				@Override public void onScrollChanged() {
					View child = seeAllScrollView.getChildAt(0);
					if (child == null) return;
					int scrollY = seeAllScrollView.getScrollY();
					int diff = child.getHeight() - (seeAllScrollView.getHeight() + scrollY);
					if (diff < 600 && !isLoading && hasMore) {
						loadNextPage();
					}
				}
			});

        loadNextPage();
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, MusicService.class), serviceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    private void loadNextPage() {
        isLoading = true;
        seeAllLoadingMore.setVisibility(View.VISIBLE);
        new SimpleAsyncTask<Void, List<Track>>() {
					@Override protected List<Track> doInBackground(Void... params) {
						return MODE_ARTIST.equals(mode)
							? fetchArtistSongsPage(value, currentPage)
							: fetchSearchPage(value, currentPage);
					}

					@Override protected void onPostExecute(List<Track> newTracks) {
						isLoading = false;
						seeAllLoadingMore.setVisibility(View.GONE);

						if (newTracks.isEmpty()) {
							hasMore = false;
							if (currentPage == 0) {
								TextView empty = new TextView(SeeAllActivity.this);
								empty.setText("Kuch nahi mila.");
								empty.setTextColor(0xFF8A968F);
								empty.setPadding(8, 24, 8, 8);
								seeAllContainer.addView(empty);
							}
							return;
						}

						loadedTracks.addAll(newTracks);
						renderNewRows(newTracks);
						currentPage++;
					}
				}.execute();
    }

    private void renderNewRows(List<Track> tracks) {
        for (final Track track : tracks) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_recommendation_row, seeAllContainer, false);
            ImageView img = (ImageView) row.findViewById(R.id.recRowImage);
            ((TextView) row.findViewById(R.id.recRowTitle)).setText(track.title);
            ((TextView) row.findViewById(R.id.recRowArtist)).setText(track.artist);
            ImageLoader.load(img, track.imageUrl);

            View heart = row.findViewById(R.id.recRowHeart);
            if (heart != null) heart.setVisibility(View.GONE);

            row.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) { playTrack(track); }
				});
            seeAllContainer.addView(row);
        }
    }

    private void playTrack(Track track) {
        if (!serviceBound || musicService == null) {
            Toast.makeText(this, "Player ready nahi hai, ek second wait karo", Toast.LENGTH_SHORT).show();
            return;
        }
        musicService.playQueue(loadedTracks, track);
        finish();
    }

    // ============================================================
    //  Network + parsing (mirrors MainActivity's fetch logic)
    // ============================================================

    private List<Track> fetchSearchPage(String query, int page) {
        try {
            String url = API_BASE_URL + "api/search/songs?query=" + URLEncoder.encode(query, "UTF-8")
				+ "&page=" + page + "&limit=" + PAGE_SIZE;
            return fetchAndParse(url, "results");
        } catch (Exception e) {
            return new ArrayList<Track>();
        }
    }

    private List<Track> fetchArtistSongsPage(String artistId, int page) {
        String url = API_BASE_URL + "api/artists/" + artistId + "/songs?page=" + page
			+ "&limit=" + PAGE_SIZE + "&sortBy=popularity&sortOrder=desc";
        List<Track> tracks = fetchAndParse(url, "songs");
        if (tracks.isEmpty()) tracks = fetchAndParse(url, "results");
        return tracks;
    }

    private List<Track> fetchAndParse(String urlString, String arrayKey) {
        List<Track> tracks = new ArrayList<Track>();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlString).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            JSONObject root = new JSONObject(sb.toString());
            JSONObject data = root.optJSONObject("data");
            if (data == null) return tracks;
            JSONArray results = data.optJSONArray(arrayKey);
            if (results == null) return tracks;

            for (int i = 0; i < results.length(); i++) {
                JSONObject t = results.getJSONObject(i);
                String id = t.optString("id", "");
                String name = t.has("name") ? t.optString("name") : t.optString("title", "Unknown Song");
                boolean hasLyrics = t.optBoolean("hasLyrics", false);
                String lyricsId = t.isNull("lyricsId") ? null : t.optString("lyricsId", null);
                tracks.add(new Track(id, name, extractArtist(t), extractImageUrl(t), extractDownloadUrls(t),
									 t.optString("url", ""), hasLyrics, lyricsId));
            }
        } catch (Exception e) {
            // return whatever was parsed so far (likely empty)
        } finally {
            if (conn != null) conn.disconnect();
        }
        return tracks;
    }

    private static String extractArtist(JSONObject track) {
        if (track.has("primaryArtists") && track.optString("primaryArtists").length() > 0) return track.optString("primaryArtists");
        if (track.has("artist") && track.optString("artist").length() > 0) return track.optString("artist");
        try {
            JSONObject artists = track.optJSONObject("artists");
            if (artists != null) {
                JSONArray primary = artists.optJSONArray("primary");
                if (primary != null && primary.length() > 0) {
                    StringBuilder names = new StringBuilder();
                    for (int i = 0; i < primary.length(); i++) {
                        if (i > 0) names.append(", ");
                        names.append(primary.getJSONObject(i).optString("name", ""));
                    }
                    return names.toString();
                }
            }
        } catch (Exception ignored) {}
        return "Unknown Artist";
    }

    private static String extractImageUrl(JSONObject track) {
        try {
            JSONArray images = track.optJSONArray("image");
            if (images != null && images.length() > 0) {
                return images.getJSONObject(images.length() - 1).optString("url", "");
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static List<String> extractDownloadUrls(JSONObject track) {
        List<String> urls = new ArrayList<String>();
        try {
            JSONArray downloadUrls = track.optJSONArray("downloadUrl");
            if (downloadUrls != null) {
                for (int i = 0; i < downloadUrls.length(); i++) {
                    String u = downloadUrls.getJSONObject(i).optString("url", "");
                    if (u.length() > 0) urls.add(u);
                }
            }
        } catch (Exception ignored) {}
        return urls;
    }
}

