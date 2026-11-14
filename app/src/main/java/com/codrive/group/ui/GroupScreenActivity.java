package com.codrive.group.ui;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.codrive.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Group-specific appearance and safe system/keyboard insets for native XML screens. */
public abstract class GroupScreenActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        int mode = getSharedPreferences("group_appearance", MODE_PRIVATE)
                .getInt("mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
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
        findViewById(R.id.theme).setOnClickListener(view -> {
            String[] labels = {getString(R.string.group_theme_system),
                    getString(R.string.group_theme_day), getString(R.string.group_theme_night)};
            int[] modes = {AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                    AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES};
            int current = getSharedPreferences("group_appearance", MODE_PRIVATE)
                    .getInt("mode", modes[0]);
            int selected = current == modes[1] ? 1 : current == modes[2] ? 2 : 0;
            new MaterialAlertDialogBuilder(this).setTitle(R.string.group_theme)
                    .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                        getSharedPreferences("group_appearance", MODE_PRIVATE).edit()
                                .putInt("mode", modes[which]).apply();
                        dialog.dismiss();
                        getDelegate().setLocalNightMode(modes[which]);
                    }).setNegativeButton(R.string.group_cancel, null).show();
        });
    }
}
