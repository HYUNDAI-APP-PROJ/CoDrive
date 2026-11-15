package com.codrive.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import android.widget.FrameLayout;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.*;
import com.codrive.BuildConfig;
import com.codrive.R;
import com.codrive.core.speech.SpeechInputController;
import org.json.*;
import java.io.*;
import java.util.ArrayList;
import java.util.regex.*;

/** Bundled HTML design with Android speech input and persistent screen checkpoints. */
public final class MainActivity extends AppCompatActivity {
    private WebView web;
    private SpeechInputController speech;
    private int speechId = -1;
    private boolean permissionPending, loaded;
    private volatile int viewportWidth;
    private float density;
    private String currentUrl;
    private final ArrayList<String> pageHistory = new ArrayList<>();
    private int navigation;
    private OnBackPressedCallback pageBack;
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
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (request.isForMainFrame() && screenFor(url) != null) selectPage(url, true);
                return true;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (!url.equals(currentUrl)) return;
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
        pageBack = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() {
                String previous = pageHistory.remove(pageHistory.size() - 1);
                setEnabled(!pageHistory.isEmpty());
                selectPage(previous, false);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, pageBack);
        if (saved != null && saved.getStringArrayList("navigation.history") != null)
            pageHistory.addAll(saved.getStringArrayList("navigation.history"));
        selectPage(saved == null ? pageUrl(getIntent().getStringExtra(AppNavigation.SCREEN))
                : saved.getString("navigation.url", pageUrl("recommendation")), false);
    }
    private String pageUrl(String screen) {
        String page = AppNavigation.page(screen);
        if (page == null) page = AppNavigation.page("recommendation");
        return "https://codrive.local/" + page + (BuildConfig.DEBUG && !page.equals("route-recommendation.html") ? "?dev=1" : "");
    }
    private String screenFor(String url) {
        Uri uri = Uri.parse(url);
        if (!"https".equals(uri.getScheme()) || !"codrive.local".equals(uri.getHost()) || uri.getPort() != -1) return null;
        for (String screen : new String[]{"recommendation", "games", "challenges", "history"})
            if (("/" + AppNavigation.page(screen)).equals(uri.getPath())) return screen;
        return null;
    }
    private void selectPage(String url, boolean remember) {
        if (screenFor(url) == null) return;
        int request = ++navigation;
        Runnable load = () -> { if (request == navigation && !isDestroyed()) loadPage(url, remember); };
        if (loaded) web.evaluateJavascript("window.CoDriveSavePage?.()", ignored -> load.run());
        else load.run();
    }
    private void loadPage(String url, boolean remember) {
        String screen = screenFor(url);
        if (remember && currentUrl != null && !url.equals(currentUrl)) pageHistory.add(currentUrl);
        currentUrl = url; loaded = false; cancelSpeech();
        pageBack.setEnabled(!pageHistory.isEmpty());
        try {
            String html = asset(AppNavigation.page(screen));
            Matcher scripts = Pattern.compile("<script src=\"(js/[a-z-]+\\.js)\"></script>").matcher(html);
            StringBuffer bundled = new StringBuffer();
            while (scripts.find()) scripts.appendReplacement(bundled, Matcher.quoteReplacement("<script>" + asset(scripts.group(1)) + "</script>"));
            scripts.appendTail(bundled);
            html = bundled.toString()
                    .replace("<head>", "<head><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; connect-src 'none'\">")
                    .replace("</head>", "<script>" + (screen.equals("recommendation") ? asset("native-adapter.js") : "")
                            + asset("app-navigation.js") + "</script></head>");
            web.loadDataWithBaseURL(url, html, "text/html", "UTF-8", null);
        } catch (IOException error) { throw new IllegalStateException("화면을 불러오지 못했어요.", error); }
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        String screen = intent.getStringExtra(AppNavigation.SCREEN);
        if (AppNavigation.page(screen) != null && !screen.equals(screenFor(currentUrl))) selectPage(pageUrl(screen), true);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("navigation.url", currentUrl);
        state.putStringArrayList("navigation.history", new ArrayList<>(pageHistory));
        super.onSaveInstanceState(state);
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
        @JavascriptInterface public void saveRoutePreview(String json) {
            RoutePanelStore.save(MainActivity.this,json);
        }
        @JavascriptInterface public String menu() { return AppNavigation.menu(MainActivity.this); }
        @JavascriptInterface public void navigate(String screen) {
            runOnUiThread(() -> {
                if (AppNavigation.page(screen) != null) {
                    if (!screen.equals(screenFor(currentUrl))) selectPage(pageUrl(screen), true);
                } else AppNavigation.open(MainActivity.this, screen);
            });
        }
        @JavascriptInterface public String readWebState() {
            return getSharedPreferences("web_screens", MODE_PRIVATE).getString("state", "{}");
        }
        @JavascriptInterface public void saveWebState(String json) {
            if (json == null || json.length() > 262144) return;
            try {
                new JSONObject(json);
                getSharedPreferences("web_screens", MODE_PRIVATE).edit().putString("state", json).apply();
            } catch (JSONException ignored) { /* Ignore malformed checkpoints. */ }
        }
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
            if (loaded && !permissionPending) web.evaluateJavascript("window.CoDriveSavePage?.()", null);
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
