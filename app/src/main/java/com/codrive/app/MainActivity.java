package com.codrive.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.*;
import android.widget.FrameLayout;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.*;
import com.codrive.BuildConfig;
import com.codrive.R;
import com.codrive.core.speech.SpeechInputController;
import org.json.*;
import java.io.*;

/** Bundled HTML design with Android speech input and persistent screen checkpoints. */
public final class MainActivity extends AppCompatActivity {
    private WebView web;
    private SpeechInputController speech;
    private int speechId = -1;
    private boolean permissionPending, loaded;
    private volatile int viewportWidth;
    private float density;
    private final ActivityResultLauncher<String> microphonePermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (!permissionPending || speechId < 0) return;
                permissionPending = false;
                if (granted) startSpeech(speechId);
                else failSpeech(speechId, "마이크 권한을 허용해주세요.");
            });
    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        speech = new SpeechInputController(this);
        density = getResources().getDisplayMetrics().density;
        viewportWidth = getResources().getConfiguration().screenWidthDp;
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(243, 240, 233));
        web = new WebView(this);
        web.setId(R.id.recommendation_web);
        web.setBackgroundColor(Color.rgb(243, 240, 233));
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setTextZoom(Math.round(getResources().getConfiguration().fontScale * 100));
        web.addJavascriptInterface(new NativeBridge(), "CoDrive");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
            }
            @Override public void onPageFinished(WebView view, String url) {
                loaded = true;
                view.evaluateJavascript("window.CoDriveViewport?.()", null);
            }
        });
        web.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            viewportWidth = Math.max(1, Math.round((right - left) / density));
            if (loaded) web.evaluateJavascript("window.CoDriveViewport?.()", null);
        });
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightNavigationBars(true);
        try {
            String html = asset("route-recommendation.html")
                    .replace("<head>", "<head><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; connect-src 'none'\">")
                    .replace("<script>", "<script>" + asset("native-adapter.js") + "</script><script>");
            web.loadDataWithBaseURL("https://codrive.local/", html, "text/html", "UTF-8", null);
        } catch (IOException error) { throw new IllegalStateException("추천 화면을 불러오지 못했어요.", error); }
    }
    private String asset(String name) throws IOException {
        try (InputStream input = getAssets().open(name); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int size;
            while ((size = input.read(buffer)) != -1) output.write(buffer, 0, size);
            return output.toString("UTF-8");
        }
    }
    private final class NativeBridge {
        @JavascriptInterface public String viewport() {
            return "{\"width\":" + viewportWidth + ",\"density\":" + density + "}";
        }
        @JavascriptInterface public String readState() {
            return getSharedPreferences("recommendation_screen", MODE_PRIVATE).getString("state", "{}");
        }
        @JavascriptInterface public void saveState(String json) {
            if (json == null) return;
            try {
                new JSONObject(json);
                getSharedPreferences("recommendation_screen", MODE_PRIVATE).edit().putString("state", json).apply();
            } catch (JSONException ignored) { /* Ignore malformed checkpoints. */ }
        }
        @JavascriptInterface public boolean omitDefaultGroup() {
            return BuildConfig.DEBUG && !getIntent().getBooleanExtra("mock_group", true);
        }
        @JavascriptInterface public void startSpeech(int id) {
            runOnUiThread(() -> {
                MainActivity.this.cancelSpeech(); speechId = id;
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                    MainActivity.this.startSpeech(id);
                else { permissionPending = true; microphonePermission.launch(Manifest.permission.RECORD_AUDIO); }
            });
        }
        @JavascriptInterface public void cancelSpeech(int id) {
            runOnUiThread(() -> { if (id == speechId) MainActivity.this.cancelSpeech(); });
        }
    }
    private void startSpeech(int id) {
        speech.start(new SpeechInputController.Listener() {
            @Override public void ready() { dispatch(id, "start", "{}"); }
            @Override public void partial(String text) { sendResult(id, text, false); }
            @Override public void processing() { dispatch(id, "speechend", "{}"); }
            @Override public void result(String text) {
                sendResult(id, text, true); dispatch(id, "end", "{}");
                if (id == speechId) speechId = -1;
            }
            @Override public void error(String message) { failSpeech(id, message); }
            @Override public void level(float rms) { }
        });
    }
    private void sendResult(int id, String text, boolean complete) {
        dispatch(id, "result", "{\"text\":" + JSONObject.quote(text) + ",\"complete\":" + complete + "}");
    }
    private void failSpeech(int id, String message) {
        dispatch(id, "error", "{\"error\":\"native-speech\",\"message\":" + JSONObject.quote(message) + "}");
        dispatch(id, "end", "{}");
        if (id == speechId) cancelSpeech();
    }
    private void dispatch(int id, String event, String payload) {
        if (id != speechId || web == null || !loaded) return;
        web.evaluateJavascript("window.CoDriveSpeech.dispatch(" + id + "," + JSONObject.quote(event) + "," + payload + ")", null);
    }
    private void cancelSpeech() { speechId = -1; permissionPending = false; speech.cancel(); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
    @Override protected void onPause() {
        if (web != null) {
            if (loaded && !permissionPending) web.evaluateJavascript("window.dispatchEvent(new Event('codrive-pause'))", null);
            web.onPause();
        }
        if (!permissionPending) cancelSpeech();
        super.onPause();
    }
    @Override protected void onDestroy() {
        cancelSpeech();
        if (web != null) {
            web.removeJavascriptInterface("CoDrive"); web.stopLoading();
            ((FrameLayout)web.getParent()).removeView(web); web.destroy(); web = null;
        }
        super.onDestroy();
    }
}
