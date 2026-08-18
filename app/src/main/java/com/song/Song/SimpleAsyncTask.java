package com.song.Song;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Drop-in modern replacement for android.os.AsyncTask (deprecated since API 30).
 * Same doInBackground/onPostExecute shape, backed by an ExecutorService + main-thread Handler.
 */
public abstract class SimpleAsyncTask<Params, Result> {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    protected void onPreExecute() {}

    protected abstract Result doInBackground(Params... params);

    protected void onPostExecute(Result result) {}

    @SafeVarargs
    public final void execute(final Params... params) {
        onPreExecute();
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                final Result result = doInBackground(params);
                mainHandler.post(new Runnable() {
                    @Override public void run() { onPostExecute(result); }
                });
            }
        });
    }
}
