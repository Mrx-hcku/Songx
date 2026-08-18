package com.song.Song;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public class ApiClient {

    private static final String BASE_URL = "https://song1-beta.vercel.app/";
    private static ApiService apiService;

    public static ApiService getApiService() {
        if (apiService == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();

            Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

            apiService = retrofit.create(ApiService.class);
        }
        return apiService;
    }

    public interface ApiService {
        @GET("api/search/songs")
        Call<JsonObjectWrapper> searchSongs(@Query("query") String query);

        @GET("api/search/artists")
        Call<JsonObjectWrapper> searchArtists(@Query("query") String query, @Query("limit") int limit);

        @GET("api/songs/{id}/lyrics")
        Call<JsonObjectWrapper> getLyrics(@Path("id") String songId);
    }

    public static class JsonObjectWrapper {
        @SerializedName("success")
        private boolean success;

        @SerializedName("data")
        private JsonObject data;

        public boolean isSuccess() { return success; }
        public JsonObject getData() { return data; }
    }
}
