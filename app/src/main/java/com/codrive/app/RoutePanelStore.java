package com.codrive.app;

import android.content.Context;
import android.content.SharedPreferences;
import com.codrive.convoy.ui.RoutePanelState;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** App boundary between the bundled pages' route panel and the native driving screen. */
final class RoutePanelStore {
    private static final String PREFERENCES="route_panel", KEY="snapshot";
    private RoutePanelStore() { }

    static void save(Context context,String json) {
        if(json==null || json.length()>16384) return;
        try {
            decode(json);
            context.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE).edit().putString(KEY,json).apply();
        } catch(JSONException | IllegalArgumentException ignored) { /* Keep the last valid display snapshot. */ }
    }

    static RoutePanelState read(Context context) {
        String json=context.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE).getString(KEY,"");
        if(json==null || json.length()>16384) return RoutePanelState.empty();
        try { return decode(json); }
        catch(JSONException | IllegalArgumentException ignored) { return RoutePanelState.empty(); }
    }

    static Runnable observe(Context context,Runnable onChange) {
        SharedPreferences preferences=context.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE);
        SharedPreferences.OnSharedPreferenceChangeListener listener=(prefs,key)->{
            if(KEY.equals(key)) onChange.run();
        };
        preferences.registerOnSharedPreferenceChangeListener(listener);
        return ()->preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }

    private static RoutePanelState decode(String json) throws JSONException {
        JSONObject object=new JSONObject(json);
        RoutePanelState.Status status;
        switch(object.getString("status")) {
            case "empty":status=RoutePanelState.Status.EMPTY;break;
            case "loading":status=RoutePanelState.Status.LOADING;break;
            case "active":status=RoutePanelState.Status.ACTIVE;break;
            default:throw new IllegalArgumentException("Unknown route state");
        }
        List<RoutePanelState.Step> steps=new ArrayList<>();
        if(status==RoutePanelState.Status.ACTIVE) {
            JSONArray points=object.getJSONArray("steps");
            if(points.length()>32) throw new IllegalArgumentException("Too many route steps");
            for(int i=0;i<points.length();i++) {
                JSONObject step=points.getJSONObject(i);
                steps.add(new RoutePanelState.Step(step.getString("label"),step.getString("name")));
            }
        }
        return new RoutePanelState(status,object.optString("owner",""),object.optString("source",""),steps);
    }
}
