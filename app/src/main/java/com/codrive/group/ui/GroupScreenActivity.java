package com.codrive.group.ui;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.codrive.R;
import com.codrive.app.AppNavigation;

/** Shared chrome: explicit day/night choice, persisted independently of group membership. */
public abstract class GroupScreenActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        int mode = getSharedPreferences("group_appearance", MODE_PRIVATE)
                .getInt("mode", AppCompatDelegate.MODE_NIGHT_NO);
        if (mode != AppCompatDelegate.MODE_NIGHT_YES) mode = AppCompatDelegate.MODE_NIGHT_NO;
        getDelegate().setLocalNightMode(mode);
        super.onCreate(state);
    }

    protected void bindChrome() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View root = findViewById(R.id.group_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets safe = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
                            | WindowInsetsCompat.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightStatusBars(!night);
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightNavigationBars(!night);
        ImageButton theme = findViewById(R.id.theme);
        theme.setImageResource(night ? R.drawable.group_ic_sun : R.drawable.group_ic_moon);
        theme.setContentDescription(getString(night ? R.string.group_switch_day : R.string.group_switch_night));
        theme.setOnClickListener(view -> {
            int next = night ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;
            getSharedPreferences("group_appearance", MODE_PRIVATE).edit().putInt("mode", next).apply();
            getDelegate().setLocalNightMode(next);
        });
        AppNavigation.attachGroup(this, root);
    }
}
