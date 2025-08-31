package com.spendlyst.app;

import android.app.DownloadManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.DownloadListener;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import android.util.Base64;

import com.spendlyst.app.databinding.ActivityMainBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // This removes the top title bar for a full-screen experience
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WebSettings webSettings = binding.webview.getSettings();

        // Enable JavaScript - VERY IMPORTANT for your app to work
        webSettings.setJavaScriptEnabled(true);
        // Enable DOM Storage - VERY IMPORTANT for localStorage to work
        webSettings.setDomStorageEnabled(true);

        // Handle file downloads
        binding.webview.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                // Handle data URLs from jsPDF
                if (url.startsWith("data:")) {
                    try {
                        saveDataUrlAsFile(url, mimetype);
                        Toast.makeText(getApplicationContext(), "Saving PDF to Downloads folder...", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), "Failed to save PDF.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // Handle regular file downloads
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setMimeType(mimetype);
                    request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype));
                    request.setDescription("Downloading file...");
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimetype));

                    DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    dm.enqueue(request);
                }
            }
        });

        binding.webview.setWebViewClient(new WebViewClient());
        binding.webview.loadUrl("file:///android_asset/Index.html");
    }

    @Override
    public void onBackPressed() {
        if (binding.webview.canGoBack()) {
            binding.webview.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private void saveDataUrlAsFile(String url, String mimeType) throws IOException {
        String[] parts = url.split(",");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid data URL.");
        }

        String base64Data = parts[1];
        byte[] data = Base64.decode(base64Data, Base64.DEFAULT);

        // Create a unique filename
        String fileExtension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "Spendlyst_Report_" + timeStamp + "." + (fileExtension != null ? fileExtension : "pdf");

        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(downloadsDir, fileName);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }

        // Notify the system that a new file is available and show download complete notification
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        dm.addCompletedDownload(file.getName(), "Spendlyst Report", true, mimeType, file.getAbsolutePath(), file.length(), true);
    }
}
