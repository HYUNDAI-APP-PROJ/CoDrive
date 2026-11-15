package com.codrive.app;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.codrive.R;
import com.codrive.group.data.repository.MockGroupRepository;
import com.codrive.group.ui.lobby.GroupLobbyActivity;
import com.codrive.group.ui.waiting.GroupWaitingActivity;
import org.json.*;

/** The recommendation sidebar's destinations, shared by native and bundled HTML screens. */
public final class AppNavigation {
    public static final String SCREEN = "com.codrive.SCREEN";
    private static final String[] SCREENS = {"start", "group", "driving", "recommendation", "games", "challenges", "voice", "history"};
    private static final int[] LABELS = {R.string.nav_start, R.string.nav_group, R.string.nav_driving,
            R.string.nav_recommendation, R.string.nav_games, R.string.nav_challenges, R.string.nav_voice, R.string.nav_history};
    private static final int[] ICONS = {R.drawable.ic_home, R.drawable.ic_group, R.drawable.convoy_ic_drive,
            R.drawable.ic_route, R.drawable.ic_spark, R.drawable.group_ic_sun, R.drawable.ic_mic, R.drawable.ic_history};
    private static final int[] IDS = {R.id.nav_start, R.id.nav_group, R.id.nav_driving, R.id.nav_recommendation,
            R.id.nav_games, R.id.nav_challenges, R.id.nav_voice, R.id.nav_history};
    private static final int CREAM = Color.rgb(243, 240, 233), INK = Color.rgb(36, 34, 30), MUTED = Color.rgb(170, 167, 160);
    private AppNavigation() { }

    public static String menu(Activity activity) {
        JSONArray menu = new JSONArray();
        try {
            for (int i = 0; i < SCREENS.length; i++) menu.put(new JSONObject()
                    .put("id", SCREENS[i]).put("label", activity.getString(LABELS[i])).put("enabled", !SCREENS[i].equals("voice")));
        } catch (JSONException error) { throw new IllegalStateException(error); }
        return menu.toString();
    }

    public static String page(String screen) {
        if (screen == null) return null;
        return switch (screen) {
            case "recommendation" -> "route-recommendation.html";
            case "games" -> "fun-stop-games.html";
            case "challenges" -> "fun-challenges.html";
            case "history" -> "trip-card.html";
            default -> null;
        };
    }

    public static void open(Activity activity, String screen) {
        Intent intent;
        if ("start".equals(screen) || "group".equals(screen)) {
            String group = MockGroupRepository.getInstance(activity).activeGroupId();
            intent = group == null ? new Intent(activity, GroupLobbyActivity.class)
                    : new Intent(activity, GroupWaitingActivity.class).putExtra(GroupWaitingActivity.EXTRA_GROUP_ID, group);
        } else if ("driving".equals(screen)) intent = new Intent(activity, DrivingStatusActivity.class);
        else if (page(screen) != null) intent = new Intent(activity, MainActivity.class).putExtra(SCREEN, screen);
        else return;
        if (activity.getClass().getName().equals(intent.getComponent().getClassName()) && !(activity instanceof MainActivity)) return;
        activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }

    private static int dp(Activity activity, int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    public static void fillSidebar(Activity activity, LinearLayout container, String selected) {
        container.removeAllViews();
        container.setBackgroundColor(Color.rgb(21, 21, 21));
        TextView brand = new TextView(activity);
        brand.setText("CoDrive"); brand.setTextColor(CREAM); brand.setTextSize(26); brand.setTypeface(null, Typeface.BOLD);
        brand.setGravity(Gravity.CENTER_VERTICAL); brand.setContentDescription("CoDrive");
        LinearLayout.LayoutParams brandSize = new LinearLayout.LayoutParams(-1, dp(activity, 64));
        brandSize.bottomMargin = dp(activity, 24); container.addView(brand, brandSize);
        for (int i = 0; i < SCREENS.length; i++) {
            String screen = SCREENS[i];
            boolean active = screen.equals(selected), enabled = !screen.equals("voice");
            LinearLayout item = new LinearLayout(activity);
            item.setId(IDS[i]); item.setGravity(Gravity.CENTER_VERTICAL); item.setMinimumHeight(dp(activity, 64));
            item.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));
            item.setContentDescription(activity.getString(LABELS[i]) + (active ? " · 현재 화면" : enabled ? "" : " · 준비 중"));
            item.setSelected(active); item.setEnabled(enabled); item.setFocusable(enabled); item.setClickable(enabled);
            if (enabled) item.setOnClickListener(v -> { if (!active) open(activity, screen); });
            GradientDrawable background = new GradientDrawable();
            background.setColor(active ? CREAM : Color.TRANSPARENT); background.setCornerRadius(dp(activity, 6));
            item.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33393938), background, null));
            AppCompatImageView icon = new AppCompatImageView(activity);
            icon.setImageResource(ICONS[i]); icon.setImageTintList(ColorStateList.valueOf(active ? INK : MUTED));
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            item.addView(icon, new LinearLayout.LayoutParams(dp(activity, 24), dp(activity, 24)));
            TextView label = new TextView(activity);
            label.setText(LABELS[i]); label.setTextColor(active ? INK : MUTED); label.setTextSize(18);
            LinearLayout.LayoutParams labelSize = new LinearLayout.LayoutParams(0, -2, 1); labelSize.setMarginStart(dp(activity, 12));
            item.addView(label, labelSize); if (!enabled) item.setAlpha(.55f);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2); size.bottomMargin = dp(activity, 8);
            container.addView(item, size);
        }
        container.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            boolean wide = r - l >= dp(activity, 180);
            brand.setText(wide ? "CoDrive" : "Co"); brand.setGravity(wide ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
            for (int i = 1; i < container.getChildCount(); i++) {
                LinearLayout item = (LinearLayout) container.getChildAt(i);
                item.getChildAt(1).setVisibility(wide ? View.VISIBLE : View.GONE);
                item.setGravity(wide ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
            }
        });
    }

    public static TextView menuButton(Activity activity, String selected) {
        TextView button = new TextView(activity);
        button.setId(R.id.nav_menu); button.setText(R.string.nav_menu); button.setTextSize(18);
        button.setTextColor(INK); button.setBackgroundColor(CREAM); button.setGravity(Gravity.CENTER);
        button.setPadding(dp(activity, 16), 0, dp(activity, 16), 0); button.setMinimumHeight(dp(activity, 64));
        button.setFocusable(true); button.setOnClickListener(v -> {
            String[] labels = new String[SCREENS.length - 1];
            String[] screens = new String[labels.length];
            for (int i = 0, j = 0; i < SCREENS.length; i++) if (!SCREENS[i].equals("voice")) {
                labels[j] = activity.getString(LABELS[i]); screens[j++] = SCREENS[i];
            }
            new AlertDialog.Builder(activity).setTitle(R.string.nav_menu).setItems(labels, (dialog, index) -> {
                if (!screens[index].equals(selected)) open(activity, screens[index]);
            }).show();
        });
        return button;
    }

    public static void attachGroup(Activity activity, View root) {
        ViewGroup parent = (ViewGroup) root.getParent();
        ViewGroup.LayoutParams original = root.getLayoutParams(); parent.removeView(root);
        LinearLayout shell = new LinearLayout(activity); shell.setOrientation(LinearLayout.VERTICAL);
        TextView compactMenu = menuButton(activity, "group"); shell.addView(compactMenu, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout body = new LinearLayout(activity); shell.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        ScrollView sidebar = new ScrollView(activity); sidebar.setBackgroundColor(Color.rgb(21, 21, 21)); sidebar.setFillViewport(true);
        LinearLayout items = new LinearLayout(activity); items.setOrientation(LinearLayout.VERTICAL);
        items.setPadding(dp(activity, 16), dp(activity, 32), dp(activity, 16), dp(activity, 24));
        sidebar.addView(items); fillSidebar(activity, items, "group");
        body.addView(sidebar, new LinearLayout.LayoutParams(dp(activity, 220), -1));
        body.addView(root, new LinearLayout.LayoutParams(0, -1, 1)); parent.addView(shell, original);
        ViewCompat.setOnApplyWindowInsetsListener(shell, (view, insets) -> {
            androidx.core.graphics.Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom); return WindowInsetsCompat.CONSUMED;
        });
        shell.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int width = Math.round((r - l - shell.getPaddingLeft() - shell.getPaddingRight()) / activity.getResources().getDisplayMetrics().density);
            int rail = width > 1100 ? 220 : width > 600 ? 80 : 0;
            sidebar.setVisibility(rail == 0 ? View.GONE : View.VISIBLE);
            if (sidebar.getLayoutParams().width != dp(activity, rail)) { sidebar.getLayoutParams().width = dp(activity, rail); sidebar.requestLayout(); }
            compactMenu.setVisibility(rail == 0 ? View.VISIBLE : View.GONE);
        });
        ViewCompat.requestApplyInsets(shell);
    }
}
