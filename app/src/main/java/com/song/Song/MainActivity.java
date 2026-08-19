package com.song.Song;

import android.Manifest;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import android.app.NotificationManager;
import android.widget.FrameLayout;

import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.IUnityAdsShowListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.services.banners.BannerErrorInfo;
import com.unity3d.services.banners.BannerView;
import com.unity3d.services.banners.UnityBannerSize;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final String CACHE_PREFS = "song_cache";
    private static final String APP_PREFS = "song_app_data";
    private static final int MAX_RECENT = 10;

    private static final String[][] CATEGORIES = {
        {"Hindi", "Latest Hindi Songs"},
        {"English", "Hollywood Top Hits"},
        {"Bhojpuri", "Bhojpuri Song"}
    };

    private static final String RECOMMENDATION_QUERY = "Top Hits 2026";

    private LinearLayout categoriesContainer;
    private RecyclerView artistsRow;
    private ArtistRowAdapter artistRowAdapter;
    private ProgressBar topProgress;
    private SharedPreferences cachePrefs;
    private SharedPreferences appPrefs;

    // Page views
    private View homeHeader;
    private ScrollView homePage, searchPage, myMusicPage, profilePage;
    private LinearLayout navHome, navSearch, navMyMusic, navSettings;

    // Profile page views
    private ImageView profilePhoto;
    private TextView profileNameText, profileEmailText, profileSubscriptionText;
    private View profileEditNameButton, profileEditNameRow, profileLogoutButton;
    private View profileChangePhotoButton, profileChangePhotoRow;
    private View profileUpgradeRow;
    private TextView profileUpgradeText;
    private ProgressBar photoUploadProgress;
    private SharedPreferences userPrefs;

    // Search page views
    private EditText searchInput;
    private RecyclerView searchResultsContainer;
    private LinearLayout searchBrowseContainer;
    private TrackRowAdapter searchResultsAdapter;
    private RecyclerView recentPlayRow;
    private RecentPlayAdapter recentPlayAdapter;
    private TextView recentPlayEmpty;
    private RecyclerView recommendationContainer;
    private RecommendationAdapter recommendationAdapter;

    // My Music page views
    private RecyclerView playlistsContainer, downloadsContainer;
    private PlaylistAdapter playlistAdapter;
    private TrackRowAdapter downloadsAdapter;
    private TextView downloadsEmpty;

    // Player views
    private View miniPlayer;
    private ImageView miniAlbumArt, miniPlayPause;
    private ProgressBar miniBufferingSpinner, bufferingSpinner;
    private TextView miniTitle, miniArtist;
    private View playerFullScreen;
    private ImageView playerAlbumArt, playerHeart, playerShare, playerPlayPauseBtn;
    private ImageView playerPrevBtn, playerNextBtn, playerShuffleBtn, playerRepeatBtn;
    private TextView playerTitle, playerArtist, playerTimeCurrent, playerTimeTotal;
    private SeekBar playerSeekBar;
    private CircularProgressView circularProgress;

    // Ads
    private FrameLayout bannerAdView;
    private BannerView unityBannerView;
    private static final String GAME_ID = "800356656";
    private static final boolean TEST_MODE = false;
    private static final String BANNER_PLACEMENT = "Banner_Android";
    private static final String INTERSTITIAL_PLACEMENT = "Interstitial_Android";
    private boolean interstitialReady = false;

    // Music service connection
    private MusicService musicService;
    private boolean serviceBound = false;
    private List<Track> pendingQueue = new ArrayList<Track>();

    private MainViewModel viewModel;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            musicService = ((MusicService.MusicBinder) binder).getService();
            musicService.setListener(playbackListener);
            serviceBound = true;
            Track current = musicService.getCurrentTrack();
            if (current != null) {
                updatePlayerUi(current);
                miniPlayer.setVisibility(View.VISIBLE);
                updatePlayPauseIcons(musicService.isPlaying());
            }
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            musicService = null;
        }
    };

    private final MusicService.PlaybackListener playbackListener = new MusicService.PlaybackListener() {
        @Override public void onTrackChanged(Track track) {
            runOnUiThread(new Runnable() {
					@Override public void run() {
						Track t = musicService.getCurrentTrack();
						if (t == null) return;
						updatePlayerUi(t);
						miniPlayer.setVisibility(View.VISIBLE);
						addToRecentPlay(t);
						checkSongIntervalAd();
					}
				});
        }
        @Override public void onPlayStateChanged(final boolean playing) {
            runOnUiThread(new Runnable() {
					@Override public void run() { updatePlayPauseIcons(playing); }
				});
        }
        @Override public void onProgress(final int positionMs, final int durationMs) {
            runOnUiThread(new Runnable() {
					@Override public void run() {
						if (durationMs > 0) {
							float percent = (positionMs / (float) durationMs) * 100f;
							playerSeekBar.setProgress((int) (percent * 10));
							circularProgress.setProgress(percent);
						}
						playerTimeCurrent.setText(formatTime(positionMs));
					}
				});
        }
        @Override public void onBuffering(final boolean buffering) {
            runOnUiThread(new Runnable() {
					@Override public void run() { showBuffering(buffering); }
				});
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        SharedPreferences userPrefsCheck = getSharedPreferences("song_user_data", MODE_PRIVATE);
        if (userPrefsCheck.getString("googleId", null) == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 501);
            }
        }

        setContentView(R.layout.main);

        cachePrefs = getSharedPreferences(CACHE_PREFS, MODE_PRIVATE);
        appPrefs = getSharedPreferences(APP_PREFS, MODE_PRIVATE);

        UnityAds.initialize(this, GAME_ID, TEST_MODE, new IUnityAdsInitializationListener() {
            @Override public void onInitializationComplete() { loadInterstitialAd(); }
            @Override public void onInitializationFailed(UnityAds.UnityAdsInitializationError error, String message) {}
        });

        bindViews();
        setGreeting();
        setupBottomNav();
        setupSearch();
        setupMyMusic();
        setupProfile();
        setupPlayer();

        loadAllCategories();
        loadArtistsRow();
        renderRecentPlay();
        renderPlaylists();
        renderDownloads();
        loadRecommendations();

        startService(new Intent(this, MusicService.class));

        boolean isSubscribed = userPrefs.getBoolean("subscribed", false);

        if (!isSubscribed) {
            loadBannerAd();
        } else {
            bannerAdView.setVisibility(View.GONE);
        }

        int openCount = appPrefs.getInt("open_count", 0) + 1;
        appPrefs.edit().putInt("open_count", openCount).apply();
        if (!isSubscribed && openCount % 5 == 0) {
            loadAndShowInterstitial();
        }
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
            musicService.setListener(null);
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    private void bindViews() {
        categoriesContainer = (LinearLayout) findViewById(R.id.categoriesContainer);
        artistsRow = (RecyclerView) findViewById(R.id.artistsRow);
        artistsRow.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        artistRowAdapter = new ArtistRowAdapter(new ArtistRowAdapter.OnArtistClick() {
				@Override public void onClick(ArtistRowAdapter.ArtistItem artist) {
					Intent intent = new Intent(MainActivity.this, SeeAllActivity.class);
					intent.putExtra("mode", SeeAllActivity.MODE_ARTIST);
					intent.putExtra("value", artist.id);
					intent.putExtra("title", artist.name);
					startActivity(intent);
				}
			});
        artistsRow.setAdapter(artistRowAdapter);
        topProgress = (ProgressBar) findViewById(R.id.topProgress);

        homeHeader = findViewById(R.id.homeHeader);
        homePage = (ScrollView) findViewById(R.id.homePage);
        searchPage = (ScrollView) findViewById(R.id.searchPage);
        myMusicPage = (ScrollView) findViewById(R.id.myMusicPage);
        profilePage = (ScrollView) findViewById(R.id.profilePage);

        navHome = (LinearLayout) findViewById(R.id.navHome);
        navSearch = (LinearLayout) findViewById(R.id.navSearch);
        navMyMusic = (LinearLayout) findViewById(R.id.navMyMusic);
        navSettings = (LinearLayout) findViewById(R.id.navSettings);

        profilePhoto = (ImageView) findViewById(R.id.profilePhoto);
        profileNameText = (TextView) findViewById(R.id.profileNameText);
        profileEmailText = (TextView) findViewById(R.id.profileEmailText);
        profileSubscriptionText = (TextView) findViewById(R.id.profileSubscriptionText);
        profileEditNameButton = findViewById(R.id.profileEditNameButton);
        profileEditNameRow = findViewById(R.id.profileEditNameRow);
        profileChangePhotoButton = findViewById(R.id.profileChangePhotoButton);
        profileChangePhotoRow = findViewById(R.id.profileChangePhotoRow);
        profileUpgradeRow = findViewById(R.id.profileUpgradeRow);
        profileUpgradeText = (TextView) findViewById(R.id.profileUpgradeText);
        photoUploadProgress = (ProgressBar) findViewById(R.id.photoUploadProgress);
        profileLogoutButton = findViewById(R.id.profileLogoutButton);
        userPrefs = getSharedPreferences("song_user_data", MODE_PRIVATE);

        searchInput = (EditText) findViewById(R.id.searchInput);
        searchResultsContainer = (RecyclerView) findViewById(R.id.searchResultsContainer);
        searchResultsContainer.setLayoutManager(new LinearLayoutManager(this));
        searchResultsAdapter = new TrackRowAdapter(false, new TrackRowAdapter.Callback() {
				@Override public void onClick(Track track, List<Track> queue) { playTrack(track, queue); }
				@Override public void onLongClick(Track track) { addToDownloads(track); }
				@Override public void onRemove(Track track) {}
			});
        searchResultsContainer.setAdapter(searchResultsAdapter);

        searchBrowseContainer = (LinearLayout) findViewById(R.id.searchBrowseContainer);

        recentPlayRow = (RecyclerView) findViewById(R.id.recentPlayRow);
        recentPlayRow.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recentPlayAdapter = new RecentPlayAdapter(new RecentPlayAdapter.Callback() {
				@Override public void onClick(Track track, List<Track> queue) { playTrack(track, queue); }
			});
        recentPlayRow.setAdapter(recentPlayAdapter);

        recentPlayEmpty = (TextView) findViewById(R.id.recentPlayEmpty);

        recommendationContainer = (RecyclerView) findViewById(R.id.recommendationContainer);
        recommendationContainer.setLayoutManager(new LinearLayoutManager(this));
        recommendationAdapter = new RecommendationAdapter(new RecommendationAdapter.Callback() {
				@Override public void onClick(Track track, List<Track> queue) { playTrack(track, queue); }
				@Override public boolean isFavorite(String trackId) { return MainActivity.this.isFavorite(trackId); }
				@Override public boolean toggleFavorite(Track track) { return MainActivity.this.toggleFavorite(track); }
			});
        recommendationContainer.setAdapter(recommendationAdapter);

        playlistsContainer = (RecyclerView) findViewById(R.id.playlistsContainer);
        playlistsContainer.setLayoutManager(new LinearLayoutManager(this));
        playlistAdapter = new PlaylistAdapter(new PlaylistAdapter.Callback() {
				@Override public void onDelete(int index) { deletePlaylist(index); }
			});
        playlistsContainer.setAdapter(playlistAdapter);

        downloadsContainer = (RecyclerView) findViewById(R.id.downloadsContainer);
        downloadsContainer.setLayoutManager(new LinearLayoutManager(this));
        downloadsAdapter = new TrackRowAdapter(true, new TrackRowAdapter.Callback() {
				@Override public void onClick(Track track, List<Track> queue) { playTrack(track, queue); }
				@Override public void onLongClick(Track track) { addToDownloads(track); }
				@Override public void onRemove(Track track) { removeFromDownloads(track.id); }
			});
        downloadsContainer.setAdapter(downloadsAdapter);
        downloadsEmpty = (TextView) findViewById(R.id.downloadsEmpty);

        miniPlayer = findViewById(R.id.miniPlayer);
        miniAlbumArt = (ImageView) findViewById(R.id.miniAlbumArt);
        miniPlayPause = (ImageView) findViewById(R.id.miniPlayPause);
        miniBufferingSpinner = (ProgressBar) findViewById(R.id.miniBufferingSpinner);
        miniTitle = (TextView) findViewById(R.id.miniTitle);
        miniArtist = (TextView) findViewById(R.id.miniArtist);

        playerFullScreen = findViewById(R.id.playerFullScreen);
        playerAlbumArt = (ImageView) findViewById(R.id.playerAlbumArt);
        playerHeart = (ImageView) findViewById(R.id.playerHeart);
        playerShare = (ImageView) findViewById(R.id.playerShare);
        playerPlayPauseBtn = (ImageView) findViewById(R.id.playerPlayPauseBtn);
        playerPrevBtn = (ImageView) findViewById(R.id.playerPrevBtn);
        playerNextBtn = (ImageView) findViewById(R.id.playerNextBtn);
        playerShuffleBtn = (ImageView) findViewById(R.id.playerShuffleBtn);
        playerRepeatBtn = (ImageView) findViewById(R.id.playerRepeatBtn);
        playerTitle = (TextView) findViewById(R.id.playerTitle);
        playerArtist = (TextView) findViewById(R.id.playerArtist);
        playerTimeCurrent = (TextView) findViewById(R.id.playerTimeCurrent);
        playerTimeTotal = (TextView) findViewById(R.id.playerTimeTotal);
        playerSeekBar = (SeekBar) findViewById(R.id.playerSeekBar);
        circularProgress = (CircularProgressView) findViewById(R.id.circularProgress);
        bufferingSpinner = (ProgressBar) findViewById(R.id.bufferingSpinner);

        bannerAdView = (FrameLayout) findViewById(R.id.bannerAdView);

        View searchIconBtn = findViewById(R.id.searchIcon);
        searchIconBtn.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { switchTab(1); }
			});
    }

    private void setGreeting() {
        TextView greetingText = (TextView) findViewById(R.id.greetingText);
        if (greetingText == null) return;
        int hour = Integer.parseInt(DateFormat.format("HH", System.currentTimeMillis()).toString());
        String greeting;
        if (hour < 12) greeting = "Good Morning";
        else if (hour < 17) greeting = "Good Afternoon";
        else greeting = "Good Evening";
        greetingText.setText(greeting);
    }

    private void setupBottomNav() {
        navHome.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { switchTab(0); } });
        navSearch.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { switchTab(1); } });
        navMyMusic.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { switchTab(2); } });
        navSettings.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { switchTab(3); }
			});
    }

    private void switchTab(int index) {
        homePage.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        searchPage.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        myMusicPage.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        profilePage.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
        homeHeader.setVisibility(index == 0 ? View.VISIBLE : View.GONE);

        setNavTint(navHome, index == 0);
        setNavTint(navSearch, index == 1);
        setNavTint(navMyMusic, index == 2);
        setNavTint(navSettings, index == 3);

        if (index == 2) { renderPlaylists(); renderDownloads(); }
        if (index == 1) renderRecentPlay();
        if (index == 3) renderProfile();
    }

    private void setNavTint(LinearLayout navItem, boolean active) {
        int color = active ? getColorCompat(R.color.primary_accent) : getColorCompat(R.color.text_low);
        for (int i = 0; i < navItem.getChildCount(); i++) {
            View child = navItem.getChildAt(i);
            if (child instanceof ImageView) ((ImageView) child).setColorFilter(color);
            else if (child instanceof TextView) ((TextView) child).setTextColor(color);
        }
    }

    private int getColorCompat(int colorRes) {
        return getResources().getColor(colorRes);
    }

    private void loadAllCategories() {
        for (String[] category : CATEGORIES) addCategorySection(category[0], category[1]);
    }

    private void addCategorySection(final String title, final String query) {
        View section = LayoutInflater.from(this).inflate(R.layout.section_category, categoriesContainer, false);
        TextView sectionTitle = (TextView) section.findViewById(R.id.sectionTitle);
        TextView seeAllText = (TextView) section.findViewById(R.id.seeAllText);
        final RecyclerView cardRow = (RecyclerView) section.findViewById(R.id.cardRow);
        cardRow.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        final TrackCardAdapter adapter = new TrackCardAdapter(new TrackCardAdapter.OnCardClick() {
				@Override public void onClick(Track track, List<Track> queue) { playTrack(track, queue); }
				@Override public void onLongClick(Track track) { addToDownloads(track); }
			});
        cardRow.setAdapter(adapter);
        sectionTitle.setText(title);
        categoriesContainer.addView(section);

        seeAllText.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					Intent intent = new Intent(MainActivity.this, SeeAllActivity.class);
					intent.putExtra("mode", SeeAllActivity.MODE_QUERY);
					intent.putExtra("value", query);
					intent.putExtra("title", title);
					startActivity(intent);
				}
			});

        String cacheKey = "cat_" + title;
        List<Track> cached = loadCachedTracks(cacheKey);
        if (!cached.isEmpty()) adapter.submitTracks(cached);
        else adapter.showLoading();

        final boolean hadCache = !cached.isEmpty();
        if (!hadCache) topProgress.setVisibility(View.VISIBLE);
        viewModel.fetchTracks(query).observe(this, new androidx.lifecycle.Observer<List<Track>>() {
				@Override public void onChanged(List<Track> tracks) {
					topProgress.setVisibility(View.GONE);
					if (tracks.isEmpty()) {
						if (!hadCache) adapter.showError();
						return;
					}
					adapter.submitTracks(tracks);
					saveTracksToCache(cacheKey, tracks);
				}
			});
    }

    private static final String[] POPULAR_ARTIST_NAMES = {
        "Arijit Singh", "Shreya Ghoshal", "Neha Kakkar", "Diljit Dosanjh",
        "Jubin Nautiyal", "Armaan Malik", "Sonu Nigam", "Badshah"
    };
    private static final String ARTIST_CACHE_KEY = "artists_row_cache";

    private void loadArtistsRow() {
        JSONArray cached = loadCachedArtists();
        if (cached != null && cached.length() > 0) {
            List<ArtistRowAdapter.ArtistItem> items = new ArrayList<ArtistRowAdapter.ArtistItem>();
            for (int i = 0; i < cached.length(); i++) {
                JSONObject artist = cached.optJSONObject(i);
                if (artist != null) items.add(toArtistItem(artist));
            }
            artistRowAdapter.submitAll(items);
        } else {
            artistRowAdapter.setSkeletonCount(POPULAR_ARTIST_NAMES.length);
        }

        final JSONArray freshResults = new JSONArray();
        for (final String artistName : POPULAR_ARTIST_NAMES) {
            viewModel.fetchArtist(artistName).observe(this, new androidx.lifecycle.Observer<JSONObject>() {
					@Override public void onChanged(JSONObject artist) {
						if (artist != null) {
							freshResults.put(artist);
							saveCachedArtists(freshResults);
							artistRowAdapter.addArtist(toArtistItem(artist));
						}
					}
				});
        }
    }

    private ArtistRowAdapter.ArtistItem toArtistItem(JSONObject artist) {
        String artistId = artist.optString("id", "");
        String artistName = artist.optString("name", "Artist");
        String imageUrl = "";
        try {
            JSONArray images = artist.optJSONArray("image");
            if (images != null && images.length() > 0) {
                imageUrl = images.getJSONObject(images.length() - 1).optString("url", "");
            }
        } catch (Exception ignored) {}
        return new ArtistRowAdapter.ArtistItem(artistId, artistName, imageUrl);
    }

    private JSONArray loadCachedArtists() {
        try {
            String json = cachePrefs.getString(ARTIST_CACHE_KEY, null);
            if (json == null) return null;
            return new JSONArray(json);
        } catch (Exception e) {
            return null;
        }
    }

    private void saveCachedArtists(JSONArray artists) {
        cachePrefs.edit().putString(ARTIST_CACHE_KEY, artists.toString()).apply();
    }

    private List<Track> loadCachedTracks(String cacheKey) {
        List<Track> tracks = new ArrayList<Track>();
        String json = cachePrefs.getString(cacheKey, null);
        if (json == null) return tracks;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) tracks.add(Track.fromJson(arr.getJSONObject(i)));
        } catch (Exception ignored) {}
        return tracks;
    }

    private void saveTracksToCache(String cacheKey, List<Track> tracks) {
        JSONArray arr = new JSONArray();
        for (Track t : tracks) arr.put(t.toJson());
        cachePrefs.edit().putString(cacheKey, arr.toString()).apply();
    }

    private void setupSearch() {
        searchInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
				@Override
				public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
					if (actionId == EditorInfo.IME_ACTION_SEARCH) {
						performSearch(searchInput.getText().toString().trim());
						return true;
					}
					return false;
				}
			});
    }

    private void loadRecommendations() {
        viewModel.fetchTracks(RECOMMENDATION_QUERY).observe(this, new androidx.lifecycle.Observer<List<Track>>() {
				@Override public void onChanged(List<Track> tracks) {
					recommendationAdapter.submit(tracks);
				}
			});
    }

    private boolean isFavorite(String trackId) {
        for (Track t : loadTrackListPref("favorites")) if (t.id.equals(trackId)) return true;
        return false;
    }

    private boolean toggleFavorite(Track track) {
        List<Track> favorites = loadTrackListPref("favorites");
        for (int i = favorites.size() - 1; i >= 0; i--) {
            if (favorites.get(i).id.equals(track.id)) {
                favorites.remove(i);
                saveTrackListPref("favorites", favorites);
                return false;
            }
        }
        favorites.add(0, track);
        saveTrackListPref("favorites", favorites);
        return true;
    }

    private void performSearch(String query) {
        if (query.length() == 0) {
            searchResultsContainer.setVisibility(View.GONE);
            searchBrowseContainer.setVisibility(View.VISIBLE);
            return;
        }
        searchBrowseContainer.setVisibility(View.GONE);
        searchResultsContainer.setVisibility(View.VISIBLE);
        searchResultsAdapter.showMessage("Searching...");

        viewModel.fetchTracks(query).observe(this, new androidx.lifecycle.Observer<List<Track>>() {
				@Override public void onChanged(List<Track> tracks) {
					if (tracks.isEmpty()) {
						searchResultsAdapter.showMessage("Kuch nahi mila.");
						return;
					}
					searchResultsAdapter.submit(tracks);
				}
			});
    }

    private void renderRecentPlay() {
        final List<Track> recent = loadTrackListPref("recent_plays");
        recentPlayEmpty.setVisibility(recent.isEmpty() ? View.VISIBLE : View.GONE);
        recentPlayAdapter.submit(recent);
    }

    private void addToRecentPlay(Track track) {
        List<Track> recent = loadTrackListPref("recent_plays");
        for (int i = recent.size() - 1; i >= 0; i--) {
            if (recent.get(i).id.equals(track.id)) recent.remove(i);
        }
        recent.add(0, track);
        while (recent.size() > MAX_RECENT) recent.remove(recent.size() - 1);
        saveTrackListPref("recent_plays", recent);
    }

    private void setupProfile() {
        profileEditNameButton.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { showEditNameDialog(); }
			});
        profileEditNameRow.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { showEditNameDialog(); }
			});
        profileChangePhotoButton.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { requestPhotoPick(); }
			});
        profileChangePhotoRow.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { requestPhotoPick(); }
			});
        profileLogoutButton.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { showLogoutConfirmDialog(); }
			});
        profileUpgradeRow.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					if (!userPrefs.getBoolean("subscribed", false)) {
						startActivity(new Intent(MainActivity.this, SubscriptionActivity.class));
					}
				}
			});
    }

    private void renderProfile() {
        String name = userPrefs.getString("name", "");
        String email = userPrefs.getString("email", "");
        String photoUrl = userPrefs.getString("photoUrl", "");
        boolean subscribed = userPrefs.getBoolean("subscribed", false);

        profileNameText.setText(name.length() > 0 ? name : "Unknown");
        profileEmailText.setText(email);
        profileSubscriptionText.setText(subscribed ? "Subscribed" : "Free Plan");
        if (photoUrl != null && photoUrl.length() > 0) {
            ImageLoader.load(profilePhoto, photoUrl);
        }

        if (subscribed) {
            profileUpgradeText.setText("You're on Premium — ads-free, best quality");
            profileUpgradeText.setTextColor(getColorCompat(R.color.text_low));
        } else {
            profileUpgradeText.setText("Upgrade to Premium — ₹30/month");
            profileUpgradeText.setTextColor(getColorCompat(R.color.primary_accent));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderProfile();
    }

    private void showEditNameDialog() {
        final EditText input = new EditText(this);
        input.setText(userPrefs.getString("name", ""));
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        new AlertDialog.Builder(this)
            .setTitle("Naam badlo")
            .setView(input)
            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    String newName = input.getText().toString().trim();
                    if (newName.length() > 0) updateNameOnServer(newName);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void updateNameOnServer(final String newName) {
        final String googleId = userPrefs.getString("googleId", null);
        if (googleId == null) return;

        new SimpleAsyncTask<Void, Boolean>() {
            @Override protected Boolean doInBackground(Void... params) {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL("https://servingportal.onrender.com/api/user/" + googleId + "/name");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("PUT");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    OutputStream os = conn.getOutputStream();
                    JSONObject body = new JSONObject();
                    body.put("name", newName);
                    os.write(body.toString().getBytes("UTF-8"));
                    os.close();
                    return conn.getResponseCode() == 200;
                } catch (Exception e) {
                    return false;
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }

            @Override protected void onPostExecute(Boolean success) {
                if (success) {
                    userPrefs.edit().putString("name", newName).apply();
                    renderProfile();
                    Toast.makeText(MainActivity.this, "Naam update ho gaya", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Naam update nahi ho paya", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private void requestPhotoPick() {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            int permission = checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
            if (permission != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 502);
                return;
            }
        }
        launchImagePicker();
    }

    private void launchImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, 501);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 501 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            uploadPhotoToCloudinary(data.getData());
        }
    }

    private void uploadPhotoToCloudinary(final Uri imageUri) {
        photoUploadProgress.setVisibility(View.VISIBLE);
        new SimpleAsyncTask<Void, String>() {
            @Override protected String doInBackground(Void... params) {
                HttpURLConnection conn = null;
                try {
                    ContentResolver resolver = getContentResolver();
                    InputStream is = resolver.openInputStream(imageUri);
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] chunk = new byte[4096];
                    int len;
                    while ((len = is.read(chunk)) != -1) buffer.write(chunk, 0, len);
                    is.close();
                    byte[] imageBytes = buffer.toByteArray();

                    String boundary = "----SongUploadBoundary" + System.currentTimeMillis();
                    URL url = new URL("https://api.cloudinary.com/v1_1/dwvzjfjbb/image/upload");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                    DataOutputStream out = new DataOutputStream(conn.getOutputStream());
                    out.writeBytes("--" + boundary + "\r\n");
                    out.writeBytes("Content-Disposition: form-data; name=\"upload_preset\"\r\n\r\nMusicli\r\n");
                    out.writeBytes("--" + boundary + "\r\n");
                    out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"profile.jpg\"\r\n");
                    out.writeBytes("Content-Type: image/jpeg\r\n\r\n");
                    out.write(imageBytes);
                    out.writeBytes("\r\n--" + boundary + "--\r\n");
                    out.flush();
                    out.close();

                    if (conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        reader.close();
                        return new JSONObject(sb.toString()).optString("secure_url", null);
                    }
                } catch (Exception ignored) {}
                finally { if (conn != null) conn.disconnect(); }
                return null;
            }

            @Override protected void onPostExecute(String secureUrl) {
                if (secureUrl != null) updatePhotoOnServer(secureUrl);
                else {
                    photoUploadProgress.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Upload fail ho gaya", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private void updatePhotoOnServer(final String photoUrl) {
        final String googleId = userPrefs.getString("googleId", null);
        if (googleId == null) { photoUploadProgress.setVisibility(View.GONE); return; }

        new SimpleAsyncTask<Void, Boolean>() {
            @Override protected Boolean doInBackground(Void... params) {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL("https://servingportal.onrender.com/api/user/" + googleId + "/photo");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("PUT");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    OutputStream os = conn.getOutputStream();
                    JSONObject body = new JSONObject();
                    body.put("photoUrl", photoUrl);
                    os.write(body.toString().getBytes("UTF-8"));
                    os.close();
                    return conn.getResponseCode() == 200;
                } catch (Exception e) {
                    return false;
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }

            @Override protected void onPostExecute(Boolean success) {
                photoUploadProgress.setVisibility(View.GONE);
                if (success) {
                    userPrefs.edit().putString("photoUrl", photoUrl).apply();
                    renderProfile();
                    Toast.makeText(MainActivity.this, "Photo update ho gayi", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private void showLogoutConfirmDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Kya tum logout karna chahte ho?")
            .setPositiveButton("Logout", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    userPrefs.edit().clear().apply();
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
                    finish();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void setupMyMusic() {
        findViewById(R.id.createPlaylistButton).setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { showCreatePlaylistDialog(); }
			});
    }

    private void showCreatePlaylistDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Playlist name");
        new AlertDialog.Builder(this)
            .setTitle("Naya Playlist")
            .setView(input)
            .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    String name = input.getText().toString().trim();
                    if (name.length() > 0) createPlaylist(name);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void createPlaylist(String name) {
        try {
            JSONArray playlists = new JSONArray(appPrefs.getString("playlists", "[]"));
            JSONObject p = new JSONObject();
            p.put("name", name);
            p.put("tracks", new JSONArray());
            playlists.put(p);
            appPrefs.edit().putString("playlists", playlists.toString()).apply();
            renderPlaylists();
        } catch (Exception ignored) {}
    }

    private void renderPlaylists() {
        try {
            JSONArray playlists = new JSONArray(appPrefs.getString("playlists", "[]"));
            playlistAdapter.submit(playlists);
        } catch (Exception ignored) {}
    }

    private void deletePlaylist(int index) {
        try {
            JSONArray playlists = new JSONArray(appPrefs.getString("playlists", "[]"));
            JSONArray updated = new JSONArray();
            for (int i = 0; i < playlists.length(); i++) if (i != index) updated.put(playlists.getJSONObject(i));
            appPrefs.edit().putString("playlists", updated.toString()).apply();
            renderPlaylists();
        } catch (Exception ignored) {}
    }

    private void renderDownloads() {
        final List<Track> downloads = loadTrackListPref("downloads");
        downloadsEmpty.setVisibility(downloads.isEmpty() ? View.VISIBLE : View.GONE);
        downloadsAdapter.submit(downloads);
    }

    private void addToDownloads(Track track) {
        List<Track> downloads = loadTrackListPref("downloads");
        for (Track t : downloads) if (t.id.equals(track.id)) {
				Toast.makeText(this, "Pehle se downloads mein hai", Toast.LENGTH_SHORT).show();
				return;
			}

        int downloadLimit = userPrefs.getBoolean("subscribed", false) ? 60 : 7;
        if (downloads.size() >= downloadLimit) {
            Toast.makeText(this, "Download limit (" + downloadLimit + ") poori ho gayi. Premium lekar limit badhao.", Toast.LENGTH_LONG).show();
            return;
        }

        downloads.add(0, track);
        saveTrackListPref("downloads", downloads);
        Toast.makeText(this, "Downloads mein add ho gaya", Toast.LENGTH_SHORT).show();
    }

    private void removeFromDownloads(String trackId) {
        List<Track> downloads = loadTrackListPref("downloads");
        for (int i = downloads.size() - 1; i >= 0; i--) if (downloads.get(i).id.equals(trackId)) downloads.remove(i);
        saveTrackListPref("downloads", downloads);
        renderDownloads();
    }

    private void attachTrackActions(View view, final Track track, final List<Track> queue) {
        view.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { playTrack(track, queue); }
			});
        view.setOnLongClickListener(new View.OnLongClickListener() {
				@Override public boolean onLongClick(View v) { addToDownloads(track); return true; }
			});
    }

    private void setupPlayer() {
        miniPlayer.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { showFullPlayer(); }
			});
        miniPlayPause.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { if (serviceBound) musicService.togglePlayPause(); }
			});
        findViewById(R.id.playerCollapseBtn).setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { hideFullPlayer(); }
			});
        findViewById(R.id.playerMenuBtn).setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { showPlayerMenu(); }
			});
        findViewById(R.id.playerQueueBtn).setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { showQueueDialog(); }
			});
        playerPlayPauseBtn.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { if (serviceBound) musicService.togglePlayPause(); }
			});
        playerPrevBtn.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { if (serviceBound) musicService.prev(); }
			});
        playerNextBtn.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					if (serviceBound) musicService.skipToNext();
				}
			});
        playerHeart.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					if (!serviceBound) return;
					Track current = musicService.getCurrentTrack();
					if (current == null) return;
					boolean nowFav = toggleFavorite(current);
					playerHeart.setImageResource(nowFav ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
				}
			});
        playerShare.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { shareCurrentTrack(); }
			});
        findViewById(R.id.playerLyricsToggle).setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { showLyrics(); }
			});

        playerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
				@Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}
				@Override public void onStartTrackingTouch(SeekBar seekBar) {}
				@Override public void onStopTrackingTouch(SeekBar seekBar) {
					if (serviceBound) musicService.seekToProgress1000(seekBar.getProgress());
				}
			});
    }

    private void playTrack(Track track, List<Track> queue) {
        pendingQueue = queue;
        if (serviceBound) {
            musicService.playQueue(queue, track);
        } else {
            Toast.makeText(this, "Player tayyar ho raha hai...", Toast.LENGTH_SHORT).show();
        }
    }

    private void showBuffering(boolean show) {
        bufferingSpinner.setVisibility(show ? View.VISIBLE : View.GONE);
        miniBufferingSpinner.setVisibility(show ? View.VISIBLE : View.GONE);
        miniPlayPause.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void updatePlayerUi(Track track) {
        miniTitle.setText(track.title);
        miniArtist.setText(track.artist);
        ImageLoader.load(miniAlbumArt, track.imageUrl);

        playerTitle.setText(track.title);
        playerArtist.setText(track.artist);
        ImageLoader.loadCircular(playerAlbumArt, track.imageUrl);
        playerHeart.setImageResource(isFavorite(track.id) ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
        playerSeekBar.setProgress(0);
        circularProgress.setProgress(0);
        playerTimeCurrent.setText("0:00");
        if (serviceBound) playerTimeTotal.setText(formatTime(musicService.getDurationMs()));
    }

    private void updatePlayPauseIcons(boolean playing) {
        int icon = playing ? R.drawable.ic_pause : R.drawable.ic_play;
        miniPlayPause.setImageResource(icon);
        playerPlayPauseBtn.setImageResource(icon);
    }

    private void showFullPlayer() { playerFullScreen.setVisibility(View.VISIBLE); }
    private void hideFullPlayer() { playerFullScreen.setVisibility(View.GONE); }

    private void showPlayerMenu() {
        if (!serviceBound) return;
        final Track track = musicService.getCurrentTrack();
        if (track == null) return;
        boolean isDownloaded = isInDownloads(track.id);

        final String[] options = {
            "Add to Playlist",
            isDownloaded ? "Remove from Downloads" : "Add to Downloads",
            "Share",
            "Song Info"
        };

        new AlertDialog.Builder(this)
            .setTitle(track.title)
            .setItems(options, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    switch (which) {
                        case 0: showAddToPlaylistDialog(track); break;
                        case 1:
                            if (isInDownloads(track.id)) removeFromDownloads(track.id);
                            else addToDownloads(track);
                            break;
                        case 2: shareCurrentTrack(); break;
                        case 3: showSongInfo(track); break;
                    }
                }
            })
            .show();
    }

    private void showQueueDialog() {
        if (!serviceBound) return;
        final List<Track> queue = musicService.getQueue();
        final int currentIndex = musicService.getCurrentQueueIndex();
        if (queue.isEmpty()) return;

        final android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar);
        dialog.setContentView(R.layout.dialog_queue);
        LinearLayout container = (LinearLayout) dialog.findViewById(R.id.queueContainer);

        for (int i = 0; i < queue.size(); i++) {
            final int position = i;
            final Track track = queue.get(i);
            View row = LayoutInflater.from(this).inflate(R.layout.item_recommendation_row, container, false);
            ImageView img = (ImageView) row.findViewById(R.id.recRowImage);
            TextView titleView = (TextView) row.findViewById(R.id.recRowTitle);
            ImageLoader.load(img, track.imageUrl);
            titleView.setText(i == currentIndex ? "▶  " + track.title : track.title);
            ((TextView) row.findViewById(R.id.recRowArtist)).setText(track.artist);

            row.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) {
						musicService.playAtIndex(position);
						dialog.dismiss();
					}
				});
            container.addView(row);
        }
        dialog.show();
    }

    private boolean isInDownloads(String trackId) {
        for (Track t : loadTrackListPref("downloads")) if (t.id.equals(trackId)) return true;
        return false;
    }

    private void showSongInfo(Track track) {
        new AlertDialog.Builder(this)
            .setTitle("Song Info")
            .setMessage("Title: " + track.title + "\nArtist: " + track.artist)
            .setPositiveButton("OK", null)
            .show();
    }

    private void shareCurrentTrack() {
        if (!serviceBound) return;
        Track track = musicService.getCurrentTrack();
        if (track == null) return;
        String shareText = "Sun rahe ho: " + track.title + " - " + track.artist;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(intent, "Share via"));
    }

    private void showAddToPlaylistDialog(final Track track) {
        try {
            final JSONArray playlists = new JSONArray(appPrefs.getString("playlists", "[]"));
            final List<String> names = new ArrayList<String>();
            for (int i = 0; i < playlists.length(); i++) names.add(playlists.getJSONObject(i).optString("name"));
            names.add("+ Create New Playlist");

            new AlertDialog.Builder(this)
                .setTitle("Add to Playlist")
                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (which == names.size() - 1) showCreatePlaylistDialogThenAdd(track);
                        else addTrackToPlaylist(which, track);
                    }
                })
                .show();
        } catch (Exception ignored) {}
    }

    private void showCreatePlaylistDialogThenAdd(final Track track) {
        final EditText input = new EditText(this);
        input.setHint("Playlist name");
        new AlertDialog.Builder(this)
            .setTitle("Naya Playlist")
            .setView(input)
            .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    String name = input.getText().toString().trim();
                    if (name.length() > 0) {
                        try {
                            JSONArray playlists = new JSONArray(appPrefs.getString("playlists", "[]"));
                            JSONObject p = new JSONObject();
                            p.put("name", name);
                            JSONArray tracks = new JSONArray();
                            tracks.put(track.toJson());
                            p.put("tracks", tracks);
                            playlists.put(p);
                            appPrefs.edit().putString("playlists", playlists.toString()).apply();
                            renderPlaylists();
                        } catch (Exception ignored) {}
                    }
                }
            }).show();
    }

    private void addTrackToPlaylist(int index, Track track) {
        try {
            JSONArray playlists = new JSONArray(appPrefs.getString("playlists", "[]"));
            JSONObject playlist = playlists.getJSONObject(index);
            JSONArray tracks = playlist.optJSONArray("tracks");
            if (tracks == null) tracks = new JSONArray();
            tracks.put(track.toJson());
            playlist.put("tracks", tracks);
            appPrefs.edit().putString("playlists", playlists.toString()).apply();
            renderPlaylists();
        } catch (Exception ignored) {}
    }

    @Override
    public void onBackPressed() {
        if (playerFullScreen.getVisibility() == View.VISIBLE) hideFullPlayer();
        else super.onBackPressed();
    }

    private String formatTime(int ms) {
        int totalSeconds = ms / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return minutes + ":" + (seconds < 10 ? "0" + seconds : "" + seconds);
    }

    private List<Track> loadTrackListPref(String key) {
        List<Track> tracks = new ArrayList<Track>();
        try {
            JSONArray arr = new JSONArray(appPrefs.getString(key, "[]"));
            for (int i = 0; i < arr.length(); i++) tracks.add(Track.fromJson(arr.getJSONObject(i)));
        } catch (Exception ignored) {}
        return tracks;
    }

    private void saveTrackListPref(String key, List<Track> tracks) {
        JSONArray arr = new JSONArray();
        for (Track t : tracks) arr.put(t.toJson());
        appPrefs.edit().putString(key, arr.toString()).apply();
    }

    private void loadBannerAd() {
        unityBannerView = new BannerView(this, BANNER_PLACEMENT, new UnityBannerSize(320, 50));
        unityBannerView.setListener(new BannerView.IListener() {
            @Override public void onBannerLoaded(BannerView bannerAdView) {}
            @Override public void onBannerShown(BannerView bannerAdView) {}
            @Override public void onBannerClick(BannerView bannerAdView) {}
            @Override public void onBannerFailedToLoad(BannerView bannerAdView, BannerErrorInfo errorInfo) {}
            @Override public void onBannerLeftApplication(BannerView bannerAdView) {}
        });
        bannerAdView.addView(unityBannerView);
        unityBannerView.load();
    }

    private void loadInterstitialAd() {
        UnityAds.load(INTERSTITIAL_PLACEMENT, new IUnityAdsLoadListener() {
            @Override public void onUnityAdsAdLoaded(String placementId) { interstitialReady = true; }
            @Override public void onUnityAdsFailedToLoad(String placementId, UnityAds.UnityAdsLoadError error, String message) { interstitialReady = false; }
        });
    }

    private void loadAndShowInterstitial() {
        if (!interstitialReady) return;
        UnityAds.show(this, INTERSTITIAL_PLACEMENT, new IUnityAdsShowListener() {
            @Override public void onUnityAdsShowFailure(String placementId, UnityAds.UnityAdsShowError error, String message) {}
            @Override public void onUnityAdsShowStart(String placementId) {}
            @Override public void onUnityAdsShowClick(String placementId) {}
            @Override public void onUnityAdsShowComplete(String placementId, UnityAds.UnityAdsShowCompletionState state) {
                interstitialReady = false;
                loadInterstitialAd();
            }
        });
    }

    private void checkSongIntervalAd() {
        if (userPrefs.getBoolean("subscribed", false)) return;
        int songCount = appPrefs.getInt("song_count", 0) + 1;
        appPrefs.edit().putInt("song_count", songCount).apply();
        if (songCount % 4 == 0) loadAndShowInterstitial();
    }

    private void showLyrics() {
        if (!serviceBound) return;
        final Track track = musicService.getCurrentTrack();
        if (track == null) return;

        if (!userPrefs.getBoolean("subscribed", false)) {
            new AlertDialog.Builder(this)
                .setTitle("Premium Feature")
                .setMessage("Lyrics sirf Premium members ke liye available hai. Upgrade karo lyrics dekhne ke liye.")
                .setPositiveButton("Upgrade", new DialogInterface.OnClickListener() {
						@Override public void onClick(DialogInterface dialog, int which) {
							startActivity(new Intent(MainActivity.this, SubscriptionActivity.class));
						}
					})
                .setNegativeButton("Cancel", null)
                .show();
            return;
        }

        final TextView lyricsView = new TextView(this);
        lyricsView.setText("Lyrics load ho rahe hai...");
        lyricsView.setTextColor(getColorCompat(R.color.text_high));
        lyricsView.setTextSize(15);
        lyricsView.setPadding(40, 30, 40, 30);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(lyricsView);

        new AlertDialog.Builder(this)
            .setTitle(track.title)
            .setView(scrollView)
            .setPositiveButton("Close", null)
            .show();

        new SimpleAsyncTask<Track, String>() {
            @Override protected String doInBackground(Track... params) { return fetchLyrics(params[0]); }
            @Override protected void onPostExecute(String lyrics) {
                lyricsView.setText(lyrics != null && lyrics.length() > 0 ? lyrics : "Lyrics nahi mile.");
            }
        }.execute(track);
    }

    private String fetchLyrics(Track track) {
        String primary = fetchLyricsFromOwnApi(track.id);
        if (primary != null && primary.length() > 0) return primary;

        String firstArtist = track.artist != null && track.artist.length() > 0
			? track.artist.split(",")[0].trim() : "";
        return fetchLyricsFromLrcLib(firstArtist, track.title);
    }

    // --- RETROFIT FOR LYRICS API ---
    private String fetchLyricsFromOwnApi(String songId) {
        try {
            Response<ApiClient.JsonObjectWrapper> response = ApiClient.getApiService().getLyrics(songId).execute();
            if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                com.google.gson.JsonObject data = response.body().getData();
                if (data != null && data.has("lyrics")) {
                    String lyrics = data.get("lyrics").getAsString();
                    return lyrics.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n");
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String fetchLyricsFromLrcLib(String artist, String title) {
        HttpURLConnection conn = null;
        try {
            String url = "https://lrclib.net/api/search?track_name=" + URLEncoder.encode(title, "UTF-8")
				+ "&artist_name=" + URLEncoder.encode(artist, "UTF-8");
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(10000);
            conn.setRequestProperty("User-Agent", "SongApp/1.0");

            BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            JSONArray results = new JSONArray(sb.toString());
            if (results.length() > 0) {
                return results.getJSONObject(0).optString("plainLyrics", "");
            }
        } catch (Exception ignored) {}
        finally { if (conn != null) conn.disconnect(); }
        return null;
    }
}

