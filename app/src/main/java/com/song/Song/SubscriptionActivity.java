package com.song.Song;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class SubscriptionActivity extends AppCompatActivity {

    private static final String CHECKOUT_URL = "https://servingportal.onrender.com/payment/checkout";
    private static final String SUCCESS_URL_PREFIX = "https://servingportal.onrender.com/payment-success";

    private WebView webView;
    private SharedPreferences userPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subscription);

        userPrefs = getSharedPreferences("song_user_data", MODE_PRIVATE);

        findViewById(R.id.subscriptionBackBtn).setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { finish(); }
			});

        webView = (WebView) findViewById(R.id.subscriptionWebView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        webView.setWebViewClient(new WebViewClient() {
				@Override
				public boolean shouldOverrideUrlLoading(WebView view, String url) {
					if (url.startsWith(SUCCESS_URL_PREFIX)) {
						handlePaymentResult(url);
						return true;
					}
					return false;
				}
			});

        String googleId = userPrefs.getString("googleId", "");
        String name = userPrefs.getString("name", "");
        String email = userPrefs.getString("email", "");

        String checkoutUrl = CHECKOUT_URL
			+ "?googleId=" + Uri.encode(googleId)
			+ "&name=" + Uri.encode(name)
			+ "&email=" + Uri.encode(email);

        webView.loadUrl(checkoutUrl);
    }

    private void handlePaymentResult(String url) {
        Uri uri = Uri.parse(url);
        String status = uri.getQueryParameter("status");

        if ("ok".equals(status)) {
            userPrefs.edit().putBoolean("subscribed", true).apply();
            Toast.makeText(this, "Subscription activate ho gaya! 🎉", Toast.LENGTH_LONG).show();
        } else if ("cancelled".equals(status)) {
            Toast.makeText(this, "Payment cancel kar diya", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Payment fail ho gaya, dobara try karo", Toast.LENGTH_SHORT).show();
        }

        setResult(RESULT_OK);
        finish();
    }
}

