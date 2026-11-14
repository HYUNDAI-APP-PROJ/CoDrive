package com.codrive.app;

import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.os.BundleCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.codrive.BuildConfig;
import com.codrive.R;
import com.codrive.convoy.data.source.DemoScenario;
import com.codrive.convoy.ui.DrivingStatusScreen;
import com.codrive.convoy.ui.DrivingStatusViewModel;

/** Independent mock entry point; opening this screen never creates a real group/trip. */
public final class DrivingStatusActivity extends AppCompatActivity {
    private static final String SAVED_SESSION="convoy.session";
    private DrivingStatusViewModel model;
    private DrivingStatusScreen screen;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
        setContentView(R.layout.activity_driving_status);
        View root=findViewById(R.id.convoy_root);
        int padding=Math.round(16*getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(root,(view,insets)->{
            Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());
            view.setPadding(padding+bars.left,padding+bars.top,padding+bars.right,padding+bars.bottom); return insets;
        });
        boolean light=(getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)!=Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.getInsetsController(getWindow(),root).setAppearanceLightStatusBars(light);
        WindowCompat.getInsetsController(getWindow(),root).setAppearanceLightNavigationBars(light);
        DrivingStatusViewModel.SavedSession saved=savedInstanceState==null?null:
                BundleCompat.getSerializable(savedInstanceState,SAVED_SESSION,DrivingStatusViewModel.SavedSession.class);
        Handler handler=new Handler(Looper.getMainLooper());
        model=new ViewModelProvider(this,new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type) {
                return type.cast(new DrivingStatusViewModel((delay,task)->handler.postDelayed(task,delay),saved));
            }
        }).get(DrivingStatusViewModel.class);
        if(BuildConfig.DEBUG && savedInstanceState==null && getIntent().hasExtra("demo_scenario"))
            model.scenario(DemoScenario.parse(getIntent().getStringExtra("demo_scenario")));
        screen=new DrivingStatusScreen(root,model);
        model.state().observe(this,screen::bind);
        ViewCompat.requestApplyInsets(root);
    }
    @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putSerializable(SAVED_SESSION,model.save()); super.onSaveInstanceState(outState);
    }
    @Override protected void onStop() { screen.close(); super.onStop(); }
}
