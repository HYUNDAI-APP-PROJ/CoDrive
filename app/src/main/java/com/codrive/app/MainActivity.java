package com.codrive.app;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.codrive.group.ui.lobby.GroupLobbyActivity;

public final class MainActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        startActivity(new Intent(this, GroupLobbyActivity.class));
        finish();
    }
}
