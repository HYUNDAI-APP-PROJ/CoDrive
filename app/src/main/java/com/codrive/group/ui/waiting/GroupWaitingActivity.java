package com.codrive.group.ui.waiting;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.codrive.R;
import com.codrive.group.data.model.GroupMember;
import com.codrive.group.data.model.GroupSession;
import com.codrive.group.data.repository.MockGroupRepository;
import com.codrive.group.ui.GroupScreenActivity;
import com.codrive.group.ui.lobby.GroupLobbyActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class GroupWaitingActivity extends GroupScreenActivity {
    public static final String EXTRA_GROUP_ID = "com.codrive.group.GROUP_ID";
    private GroupWaitingViewModel model;
    private GroupWaitingUiState latest;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            model.refresh();
            handler.postDelayed(this, 1000);
        }
    };
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_group_waiting);
        bindChrome();
        String id = getIntent().getStringExtra(EXTRA_GROUP_ID);
        model = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> type) {
                return java.util.Objects.requireNonNull(type.cast(new GroupWaitingViewModel(
                        MockGroupRepository.getInstance(getApplication()), id)));
            }
        }).get(GroupWaitingViewModel.class);
        findViewById(R.id.copy_code).setOnClickListener(v -> {
            if (latest == null || latest.closed) return;
            ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.group_copy_code),
                    latest.group.inviteCode));
            Toast.makeText(this, R.string.group_copied, Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.toggle_ready).setOnClickListener(v -> model.toggleReady());
        findViewById(R.id.start_trip).setOnClickListener(v -> model.start());
        findViewById(R.id.leave_group).setOnClickListener(v -> confirmLeave());
        findViewById(R.id.back_lobby).setOnClickListener(v -> openLobby());
        model.state().observe(this, this::render);
    }
    @Override protected void onStart() {
        super.onStart();
        handler.post(tick);
    }
    @Override protected void onStop() {
        handler.removeCallbacks(tick);
        super.onStop();
    }
    private void openLobby() {
        startActivity(new Intent(this, GroupLobbyActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }
    private void confirmLeave() {
        if (latest == null || latest.closed) return;
        boolean leader = latest.me.isLeader;
        new MaterialAlertDialogBuilder(this)
                .setTitle(leader ? R.string.group_dissolve_question : R.string.group_leave_question)
                .setMessage(leader ? R.string.group_dissolve_message : R.string.group_leave_message)
                .setNegativeButton(R.string.group_cancel, null)
                .setPositiveButton(leader ? R.string.group_dissolve : R.string.group_leave,
                        (dialog, which) -> {
                            model.leave();
                            if (model.state().getValue() != null && model.state().getValue().closed)
                                openLobby();
                        }).show();
    }
    private void text(int id, CharSequence value) { ((TextView)findViewById(id)).setText(value); }
    private void visible(int id, boolean show) { findViewById(id).setVisibility(show ? View.VISIBLE : View.GONE); }
    private void render(GroupWaitingUiState state) {
        latest = state;
        text(R.id.waiting_error, state.message);
        visible(R.id.waiting_error, state.message != null);
        visible(R.id.back_lobby, state.closed);
        visible(R.id.toggle_ready, !state.closed && !state.started);
        visible(R.id.start_trip, !state.closed && !state.started && state.me.isLeader);
        visible(R.id.leave_group, !state.closed);
        findViewById(R.id.copy_code).setEnabled(!state.closed);
        if (state.closed) {
            text(R.id.waiting_title, getString(R.string.group_closed));
            text(R.id.waiting_intro, "");
            text(R.id.waiting_hint, getString(R.string.group_closed));
            return;
        }
        GroupSession group = state.group;
        text(R.id.waiting_group_name, group.groupName);
        text(R.id.waiting_code, group.inviteCode);
        text(R.id.waiting_stats, getString(R.string.group_stats, group.members.size(), group.passengerCount()));
        text(R.id.waiting_rule, getString(R.string.group_rule_summary,
                getString("LEADER".equals(group.decisionRule) ? R.string.group_leader_rule : R.string.group_majority)));
        text(R.id.waiting_title, getString(state.started ? R.string.group_started_title : R.string.group_waiting_title));
        text(R.id.waiting_hero_title, getString(state.started ? R.string.group_started_title : R.string.group_waiting_hero));
        text(R.id.waiting_intro, getString(state.started ? R.string.group_started_message : R.string.group_waiting_intro));
        text(R.id.toggle_ready, getString(state.me.isReady ? R.string.group_unready : R.string.group_ready));
        text(R.id.leave_group, getString(state.me.isLeader ? R.string.group_dissolve : R.string.group_leave));
        findViewById(R.id.toggle_ready).setEnabled(state.me.isConnected);
        findViewById(R.id.start_trip).setEnabled(state.canStart);
        int ready = 0;
        for (GroupMember member : group.members) if (member.isConnected && member.isReady) ready++;
        text(R.id.waiting_progress, getString(R.string.group_progress, ready, group.members.size()));
        ProgressBar progress = findViewById(R.id.ready_progress);
        progress.setMax(group.members.size()); progress.setProgress(ready);
        String hint = getString(state.started ? R.string.group_started_message
                : group.canStart() ? R.string.group_all_ready
                : group.members.size() == 1 ? R.string.group_invite_hint : R.string.group_waiting_hint);
        if (group.leaderDisconnectDeadline > 0) {
            long seconds = Math.max(0, (group.leaderDisconnectDeadline - System.currentTimeMillis() + 999) / 1000);
            hint = getString(R.string.group_leader_reconnect, seconds);
        }
        text(R.id.waiting_hint, hint);
        LinearLayout list = findViewById(R.id.member_list);
        list.removeAllViews();
        for (GroupMember member : group.members) {
            View row = getLayoutInflater().inflate(R.layout.item_group_member, list, false);
            ((TextView)row.findViewById(R.id.member_alias)).setText(member.alias);
            ((TextView)row.findViewById(R.id.member_detail)).setText(getString(R.string.group_vehicle_detail,
                    member.passengerCount, getString(member.isLeader ? R.string.group_leader : R.string.group_vehicle),
                    member.id.equals(state.me.id) ? getString(R.string.group_me) : ""));
            ((TextView)row.findViewById(R.id.member_status)).setText(!member.isConnected
                    ? R.string.group_disconnected : state.started ? R.string.group_started_member
                    : member.isReady ? R.string.group_ready : R.string.group_waiting);
            list.addView(row);
        }
    }
}
