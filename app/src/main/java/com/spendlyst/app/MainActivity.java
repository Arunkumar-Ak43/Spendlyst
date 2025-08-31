package com.spendlyst.app;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private WebView myWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // This removes the top title bar for a full-screen experience
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_main);

        myWebView = (WebView) findViewById(R.id.webview);
        WebSettings webSettings = myWebView.getSettings();

        // Enable JavaScript - VERY IMPORTANT for your app to work
        webSettings.setJavaScriptEnabled(true);
        // Enable DOM Storage - VERY IMPORTANT for localStorage to work
        webSettings.setDomStorageEnabled(true);

        myWebView.setWebViewClient(new WebViewClient());
        myWebView.loadUrl("file:///android_asset/Index.html");
    }

    @Override
    public void onBackPressed() {
        if (myWebView.canGoBack()) {
            myWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
