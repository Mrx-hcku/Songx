package com.song.Song;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LoginActivity extends AppCompatActivity {

    private static final String BACKEND_URL = "https://servingportal.onrender.com/api/auth/google-webview";
    private static final String CLIENT_ID = "399583110899-ct2srih9k8m2g85davn0a0jffompvoph.apps.googleusercontent.com";
    private static final String USER_PREFS = "song_user_data";
    private static final int RC_SIGN_IN = 9001;

    private View loginPromptScreen;
    private ProgressBar loginProgress;
    private SharedPreferences userPrefs;
    private GoogleSignInClient googleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        userPrefs = getSharedPreferences(USER_PREFS, MODE_PRIVATE);
        loginPromptScreen = findViewById(R.id.loginPromptScreen);
        loginProgress = (ProgressBar) findViewById(R.id.loginProgress);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestServerAuthCode(CLIENT_ID)
            .requestEmail()
            .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        View googleSignInButton = findViewById(R.id.googleSignInButton);
        googleSignInButton.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { startGoogleLogin(); }
			});

        runEntranceAnimations();
    }

    private void runEntranceAnimations() {
        final View logoMark = findViewById(R.id.logoMark);
        final View wordmark = findViewById(R.id.wordmarkText);
        final View tagline = findViewById(R.id.taglineText);
        final View button = findViewById(R.id.googleSignInButton);
        final View footer = findViewById(R.id.loginFooterText);

        logoMark.startAnimation(AnimationUtils.loadAnimation(this, R.anim.logo_pop_in));

        wordmark.postDelayed(new Runnable() {
				@Override public void run() {
					wordmark.startAnimation(AnimationUtils.loadAnimation(LoginActivity.this, R.anim.fade_in));
				}
			}, 250);

        tagline.postDelayed(new Runnable() {
				@Override public void run() {
					tagline.startAnimation(AnimationUtils.loadAnimation(LoginActivity.this, R.anim.fade_in));
				}
			}, 400);

        button.postDelayed(new Runnable() {
				@Override public void run() {
					button.startAnimation(AnimationUtils.loadAnimation(LoginActivity.this, R.anim.slide_up_fade));
				}
			}, 550);

        footer.postDelayed(new Runnable() {
				@Override public void run() {
					footer.startAnimation(AnimationUtils.loadAnimation(LoginActivity.this, R.anim.fade_in));
				}
			}, 700);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (userPrefs.getString("googleId", null) != null) {
            goToMain();
        }
    }

    private void startGoogleLogin() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                String authCode = account.getServerAuthCode();
                if (authCode != null) {
                    verifyWithBackend(authCode);
                } else {
                    Toast.makeText(this, "Login failed: no auth code", Toast.LENGTH_SHORT).show();
                }
            } catch (ApiException e) {
                Toast.makeText(this, "Login failed: " + e.getStatusCode(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void verifyWithBackend(String code) {
        loginProgress.setVisibility(View.VISIBLE);
        new VerifyTask().execute(code);
    }

    private class VerifyTask extends SimpleAsyncTask<String, VerifyResult> {
        @Override
        protected VerifyResult doInBackground(String... params) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(BACKEND_URL).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(45000);
                conn.setReadTimeout(45000);

                JSONObject body = new JSONObject();
                body.put("code", params[0]);

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();

                int status = conn.getResponseCode();
                BufferedReader reader = new BufferedReader(new InputStreamReader(
																status < 400 ? conn.getInputStream() : conn.getErrorStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                VerifyResult result = new VerifyResult();
                result.statusCode = status;
                result.rawBody = sb.toString();
                try {
                    result.json = new JSONObject(result.rawBody);
                } catch (Exception parseErr) {
                    result.json = null;
                }
                return result;
            } catch (Exception e) {
                VerifyResult result = new VerifyResult();
                result.error = e.getClass().getSimpleName() + ": " + e.getMessage();
                return result;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        @Override
        protected void onPostExecute(VerifyResult result) {
            loginProgress.setVisibility(View.GONE);

            if (result.error != null) {
                Toast.makeText(LoginActivity.this, "Network error: " + result.error, Toast.LENGTH_LONG).show();
                return;
            }

            if (result.statusCode == 200 && result.json != null) {
                SharedPreferences.Editor editor = userPrefs.edit();
                editor.putString("googleId", result.json.optString("googleId"));
                editor.putString("name", result.json.optString("name"));
                editor.putString("email", result.json.optString("email"));
                editor.putString("photoUrl", result.json.optString("photoUrl"));
                editor.putBoolean("subscribed", result.json.optBoolean("subscribed", false));
                editor.apply();
                goToMain();
            } else if (result.statusCode == 403) {
                Toast.makeText(LoginActivity.this, "Your Account is blocked.", Toast.LENGTH_LONG).show();
            } else {
                String detail = result.rawBody != null && result.rawBody.length() > 0
                    ? result.rawBody
                    : ("HTTP " + result.statusCode);
                Toast.makeText(LoginActivity.this, "Login fail: " + detail, Toast.LENGTH_LONG).show();
            }
        }
    }

    private static class VerifyResult {
        int statusCode;
        JSONObject json;
        String rawBody;
        String error;
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
