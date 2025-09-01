package com.spendlyst.app;

import android.app.DownloadManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.spendlyst.app.databinding.ActivityMainBinding;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // Define a constant for the notepad filename for consistency.
    private static final String NOTEPAD_FILE_NAME = "spendlyst_notepad.html";
    private ActivityMainBinding binding;

    /**
     * WebAppInterface provides a bridge for JavaScript to call native Android code.
     */
    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void saveNotepad(String content) {
            try (FileOutputStream fos = mContext.openFileOutput(NOTEPAD_FILE_NAME, Context.MODE_PRIVATE)) {
                fos.write(content.getBytes());
                Log.d("WebAppInterface", "Notepad content saved to internal storage.");
            } catch (IOException e) {
                Log.e("WebAppInterface", "Failed to save notepad content.", e);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WebSettings webSettings = binding.webview.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);

        // Add the JavaScript interface to bridge JS and native Android code
        binding.webview.addJavascriptInterface(new WebAppInterface(this), "AndroidNotepad");

        binding.webview.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                if (url.startsWith("data:")) {
                    try {
                        saveDataUrlAsFile(url, mimetype);
                        Toast.makeText(getApplicationContext(), "PDF saved to Downloads folder.", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Log.e("DownloadError", "Failed to save data URL file", e);
                        Toast.makeText(getApplicationContext(), "Failed to save PDF. Please check app permissions.", Toast.LENGTH_LONG).show();
                    }
                } else {
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

        binding.webview.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Once the page is loaded, read the notepad file and inject the content.
                loadNotepadContentFromInternalStorage(view);
            }
        });

        binding.webview.loadUrl("file:///android_asset/Index.html");
    }

    private void loadNotepadContentFromInternalStorage(WebView view) {
        File file = new File(getFilesDir(), NOTEPAD_FILE_NAME);
        if (!file.exists()) {
            Log.d("MainActivity", "Notepad file does not exist. Nothing to load.");
            return;
        }

        StringBuilder contentBuilder = new StringBuilder();
        try (FileInputStream fis = openFileInput(NOTEPAD_FILE_NAME);
             InputStreamReader isr = new InputStreamReader(fis);
             BufferedReader br = new BufferedReader(isr)) {
            String line;
            while ((line = br.readLine()) != null) {
                contentBuilder.append(line).append("\n");
            }
            // Use JSONObject.quote to safely escape the string for JavaScript injection
            String script = "document.getElementById('notepadEditor').innerHTML = " + JSONObject.quote(contentBuilder.toString()) + ";";
            view.evaluateJavascript(script, null);
            Log.d("MainActivity", "Successfully loaded and injected notepad content.");
        } catch (IOException e) {
            Log.e("MainActivity", "Failed to read notepad content from internal storage.", e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // When the app is paused (e.g., user switches to another app or closes it),
        // we explicitly call the JavaScript function to save any pending data.
        // This now triggers the reliable native save via the WebAppInterface.
        if (binding != null && binding.webview != null) {
            binding.webview.evaluateJavascript("if(typeof saveNotepadContent === 'function') { saveNotepadContent(); }", null);
            Log.d("MainActivity", "onPause: Called saveNotepadContent() in WebView.");
        }
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
            resolver.delete(itemUri, null, null);
            throw e;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear();
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(itemUri, contentValues, null, null);
        }
    }
}
