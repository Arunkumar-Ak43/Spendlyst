package com.spendlyst.app;

import android.app.DownloadManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
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
import java.io.OutputStream;
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
                        Toast.makeText(getApplicationContext(), "PDF saved to Downloads folder.", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Log.e("DownloadError", "Failed to save data URL file", e);
                        Toast.makeText(getApplicationContext(), "Failed to save PDF. Please check app permissions.", Toast.LENGTH_LONG).show();
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
        final String[] parts = url.split(",");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid data URL.");
        }

        final byte[] data = Base64.decode(parts[1], Base64.DEFAULT);
        final String fileExtension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        final String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        final String fileName = "Spendlyst_Report_" + timeStamp + "." + (fileExtension != null ? fileExtension : "pdf");

        final ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 1);
        }

        final ContentResolver resolver = getContentResolver();
        final Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        final Uri itemUri = resolver.insert(collection, contentValues);

        if (itemUri == null) {
            throw new IOException("Failed to create new MediaStore record.");
        }

        try (final OutputStream os = resolver.openOutputStream(itemUri)) {
            if (os == null) {
                throw new IOException("Failed to get output stream.");
            }
            os.write(data);
        } catch (IOException e) {
            // If something fails, delete the pending entry
            resolver.delete(itemUri, null, null);
            throw e;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear();
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0); // Mark as complete
            resolver.update(itemUri, contentValues, null, null);
        }
    }
}
