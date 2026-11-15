package com.codrive;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.webkit.WebView;
import androidx.test.platform.app.InstrumentationRegistry;
import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import java.util.function.Consumer;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.codrive.app.MainActivity;
import org.json.JSONTokener;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RecommendationFlowTest {
    // CSS animations need not become idle before WebView commands can be tested.
    private static final class Host implements AutoCloseable, Application.ActivityLifecycleCallbacks {
        private final Application application = ApplicationProvider.getApplicationContext();
        private volatile MainActivity activity;
        private CountDownLatch resumed = new CountDownLatch(1);
        Host(Intent intent) throws Exception {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                application.registerActivityLifecycleCallbacks(this);
                application.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            });
            assertTrue("Activity resume timed out", resumed.await(10, TimeUnit.SECONDS));
        }
        void onActivity(Consumer<MainActivity> action) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> action.accept(activity));
        }
        void recreate() throws Exception {
            resumed = new CountDownLatch(1);
            onActivity(Activity::recreate);
            assertTrue("Activity recreation timed out", resumed.await(10, TimeUnit.SECONDS));
        }
        @Override public void close() {
            onActivity(Activity::finish);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> application.unregisterActivityLifecycleCallbacks(this));
        }
        public void onActivityResumed(Activity value) {
            if (value instanceof MainActivity) { activity = (MainActivity)value; resumed.countDown(); }
        }
        public void onActivityCreated(Activity value, Bundle saved) { }
        public void onActivityStarted(Activity value) { }
        public void onActivityPaused(Activity value) { }
        public void onActivityStopped(Activity value) { }
        public void onActivitySaveInstanceState(Activity value, Bundle saved) { }
        public void onActivityDestroyed(Activity value) { }
    }
    @Before public void reset() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("recommendation_screen", Context.MODE_PRIVATE).edit().clear().commit();
    }
    private Host launch(boolean group) throws Exception {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), MainActivity.class).putExtra("mock_group", group);
        Host scenario = new Host(intent);
        await(scenario, "Boolean(document.getElementById('prompt') && window.CoDriveSpeech)", 10000);
        return scenario;
    }
    private Object js(Host scenario, String expression) throws Exception {
        AtomicReference<String> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        scenario.onActivity(activity -> ((WebView)activity.findViewById(R.id.recommendation_web))
                .evaluateJavascript(expression, value -> { result.set(value); latch.countDown(); }));
        assertTrue("JavaScript response timed out", latch.await(5, TimeUnit.SECONDS));
        return new JSONTokener(result.get() == null ? "null" : result.get()).nextValue();
    }
    private void await(Host scenario, String condition, long timeout) throws Exception {
        long end = SystemClock.elapsedRealtime() + timeout;
        do {
            if (Boolean.TRUE.equals(js(scenario, condition))) return;
            SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < end);
        fail("Condition timed out: " + condition);
    }
    private void click(Host scenario, String id) throws Exception {
        js(scenario, "document.getElementById('" + id + "').click()");
    }
    private void type(Host scenario, String id, String text) throws Exception {
        js(scenario, "(()=>{const e=document.getElementById('" + id + "');e.value="
                + org.json.JSONObject.quote(text) + ";e.dispatchEvent(new Event('input',{bubbles:true}));})()");
    }
    private void send(Host scenario, String text) throws Exception {
        type(scenario, "prompt", text); click(scenario, "send-button");
        await(scenario, "document.querySelectorAll('.result').length>0 && !document.querySelector('.turn:last-child .result').classList.contains('is-loading')", 5000);
    }
    private void owner(Host scenario, String label) throws Exception {
        await(scenario, "!document.getElementById('route-active').hidden && document.getElementById('route-owner').textContent==="
                + org.json.JSONObject.quote(label), 5000);
    }
    @Test public void offSelectionOnTimeoutAcceptRejectAndRecreation() throws Exception {
        try (Host scenario = launch(true)) {
            assertEquals(Boolean.TRUE, js(scenario, "document.getElementById('route-empty').hidden===false"));
            send(scenario, "어떤 문구든 같은 데이터");
            assertEquals(3, ((Number)js(scenario, "document.querySelectorAll('.place-choice').length")).intValue());
            js(scenario, "document.querySelectorAll('.place-choice')[1].click()");
            assertEquals(Boolean.TRUE, js(scenario, "!document.getElementById('route-loading').hidden"));
            owner(scenario, "개인");
            click(scenario, "mode-group"); click(scenario, "auto-route");
            send(scenario, "그룹 경로");
            await(scenario, "document.getElementById('route-confirm').open", 2000);
            click(scenario, "route-reject");
            assertEquals("개인", js(scenario, "document.getElementById('route-owner').textContent"));
            send(scenario, "다시 그룹 경로");
            await(scenario, "document.getElementById('route-confirm').open", 2000);
            SystemClock.sleep(1100);
            scenario.recreate();
            await(scenario, "Boolean(window.CoDriveSpeech && document.getElementById('route-confirm').open)", 5000);
            assertTrue(((Number)js(scenario, "Number(document.getElementById('route-countdown').textContent)")).intValue() < 10);
            await(scenario, "!document.getElementById('route-confirm').open", 11000);
            owner(scenario, "강릉 주말 여행");
            assertEquals(4, ((Number)js(scenario, "document.querySelectorAll('.route-point').length")).intValue());
            click(scenario, "mode-personal"); click(scenario, "auto-route");
            send(scenario, "직접 바꿀 장소");
            js(scenario, "document.querySelector('.turn:last-child .place-choice').click()");
            await(scenario, "document.getElementById('route-confirm').open", 2000);
            click(scenario, "route-accept");
            owner(scenario, "개인");
            type(scenario, "prompt", "이어갈 입력 초안");
            await(scenario, "JSON.parse(CoDrive.readState()).draft==='이어갈 입력 초안'", 2000);
            scenario.recreate();
            await(scenario, "document.getElementById('prompt')?.value==='이어갈 입력 초안'", 5000);
            owner(scenario, "개인");
        }
    }
    @Test public void missingGroupCreationPreservesAliasDraftAndExampleSubmission() throws Exception {
        try (Host scenario = launch(false)) {
            type(scenario, "prompt", "그룹 생성 전 초안");
            click(scenario, "mode-group");
            type(scenario, "group-name", "제주 드라이브");
            await(scenario, "JSON.parse(CoDrive.readState()).groupNameDraft==='제주 드라이브'", 2000);
            scenario.recreate();
            await(scenario, "document.getElementById('group-name')?.value==='제주 드라이브'", 5000);
            js(scenario, "document.getElementById('group-create-form').requestSubmit()");
            assertEquals("제주 드라이브", js(scenario, "document.getElementById('context-title').textContent"));
            assertEquals("그룹 생성 전 초안", js(scenario, "document.getElementById('prompt').value"));
            click(scenario, "auto-route");
            js(scenario, "document.querySelector('[data-example]').click()");
            owner(scenario, "제주 드라이브");
            click(scenario, "mode-personal");
            assertEquals("제주 드라이브", js(scenario, "document.getElementById('route-owner').textContent"));
            scenario.recreate();
            await(scenario, "Boolean(document.getElementById('prompt') && window.CoDriveSpeech)", 5000);
            click(scenario, "mode-group");
            assertEquals("제주 드라이브", js(scenario, "document.getElementById('context-title').textContent"));
        }
    }
    @Test public void fullSplitCompactAndLargeTextStayWithinViewport() throws Exception {
        try (Host scenario = launch(true)) {
            assertEquals(Boolean.TRUE, js(scenario, "document.querySelector('.send svg').getBBox().width>0"));
            for (int width : new int[]{1682, 829, 320}) for (int zoom : new int[]{100, 150}) {
                scenario.onActivity(activity -> {
                    WebView web = activity.findViewById(R.id.recommendation_web);
                    ViewGroup.LayoutParams params = web.getLayoutParams();
                    params.width = Math.round(width * activity.getResources().getDisplayMetrics().density);
                    web.setLayoutParams(params); web.getSettings().setTextZoom(zoom);
                });
                SystemClock.sleep(150);
                android.util.Log.i("RecommendationLayout", "width=" + width + " zoom=" + zoom + " actual="
                        + js(scenario, "JSON.stringify({width:innerWidth,ratio:devicePixelRatio,scroll:document.documentElement.scrollWidth})"));
                await(scenario, "Math.abs(innerWidth-" + width + ")<=1", 3000);
                assertEquals("horizontal overflow at " + width + "/" + zoom, Boolean.TRUE,
                        js(scenario, "document.documentElement.scrollWidth<=innerWidth+1"));
                assertEquals(Boolean.TRUE, js(scenario, "(()=>{const a=document.querySelector('.composer').getBoundingClientRect(),b=document.getElementById('send-button').getBoundingClientRect();return b.right<=a.right+1 && document.getElementById('prompt').getBoundingClientRect().width>20})()"));
            }
        }
    }
}
