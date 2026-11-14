package com.codrive.group.ui.lobby;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.AbstractSavedStateViewModelFactory;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.codrive.R;
import com.codrive.group.data.repository.MockGroupRepository;
import com.codrive.group.ui.GroupScreenActivity;
import com.codrive.group.ui.waiting.GroupWaitingActivity;

public final class GroupLobbyActivity extends GroupScreenActivity {
    private GroupLobbyViewModel model;
    private EditText name, code, alias, count;
    private RadioGroup rule;
    private boolean rendering, navigating;
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_group_lobby);
        bindChrome();
        name = findViewById(R.id.group_name); code = findViewById(R.id.invite_code);
        alias = findViewById(R.id.vehicle_alias); count = findViewById(R.id.passengers);
        rule = findViewById(R.id.decision_rule);
        model = new ViewModelProvider(this, new AbstractSavedStateViewModelFactory(this, saved) {
            @NonNull @Override protected <T extends ViewModel> T create(@NonNull String key,
                    @NonNull Class<T> type, @NonNull SavedStateHandle handle) {
                return java.util.Objects.requireNonNull(type.cast(new GroupLobbyViewModel(handle,
                        MockGroupRepository.getInstance(getApplication()))));
            }
        }).get(GroupLobbyViewModel.class);
        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int before, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int after) { capture(); }
            public void afterTextChanged(Editable e) { }
        };
        for (EditText input : new EditText[]{name, code, alias, count}) input.addTextChangedListener(watcher);
        name.setOnEditorActionListener((view, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId != EditorInfo.IME_ACTION_DONE && !enter) return false;
            if (event == null || event.getAction() == KeyEvent.ACTION_UP) {
                capture();
                findViewById(R.id.group_root).requestFocus();
                WindowCompat.getInsetsController(getWindow(), view)
                        .hide(WindowInsetsCompat.Type.ime());
            }
            return true;
        });
        rule.setOnCheckedChangeListener((group, checked) -> capture());
        findViewById(R.id.passengers_minus).setOnClickListener(v -> changeCount(-1));
        findViewById(R.id.passengers_plus).setOnClickListener(v -> changeCount(1));
        findViewById(R.id.create_tab).setOnClickListener(v -> switchTab(false));
        findViewById(R.id.join_tab).setOnClickListener(v -> switchTab(true));
        findViewById(R.id.submit).setOnClickListener(v -> { capture(); model.submit(); });
        model.state().observe(this, this::render);
    }
    @Override protected void onStart() {
        super.onStart();
        model.resumeGroup();
    }
    private void capture() {
        if (rendering || model == null) return;
        model.input(name.getText().toString(), code.getText().toString(),
                alias.getText().toString(), count.getText().toString(),
                rule.getCheckedRadioButtonId() == R.id.rule_leader ? "LEADER" : "MAJORITY");
    }
    private void changeCount(int delta) {
        int current;
        try { current = Integer.parseInt(count.getText().toString()); }
        catch (NumberFormatException ignored) { current = 2; }
        count.setText(String.valueOf(Math.max(1, Math.min(9, current + delta))));
        capture();
        updateCounter();
    }

    private void updateCounter() {
        int current;
        try { current = Integer.parseInt(count.getText().toString()); }
        catch (NumberFormatException ignored) { current = 2; }
        findViewById(R.id.passengers_minus).setEnabled(current > 1);
        findViewById(R.id.passengers_plus).setEnabled(current < 9);
    }

    private void switchTab(boolean joining) { capture(); model.tab(joining); }
    private void set(EditText input, String text) {
        if (!input.getText().toString().equals(text)) input.setText(text);
    }
    private void render(GroupLobbyUiState state) {
        if (state.groupId != null && !navigating) {
            navigating = true;
            startActivity(new Intent(this, GroupWaitingActivity.class)
                    .putExtra(GroupWaitingActivity.EXTRA_GROUP_ID, state.groupId));
            finish();
            return;
        }
        rendering = true;
        set(name, state.name); set(code, state.code); set(alias, state.alias); set(count, state.passengers);
        rule.check("LEADER".equals(state.rule) ? R.id.rule_leader : R.id.rule_majority);
        findViewById(R.id.name_field).setVisibility(state.joining ? View.GONE : View.VISIBLE);
        findViewById(R.id.code_field).setVisibility(state.joining ? View.VISIBLE : View.GONE);
        findViewById(R.id.rule_field).setVisibility(state.joining ? View.GONE : View.VISIBLE);
        findViewById(R.id.create_tab).setSelected(!state.joining);
        findViewById(R.id.join_tab).setSelected(state.joining);
        ((TextView)findViewById(R.id.create_tab)).setTextColor(getColor(
                state.joining ? R.color.group_muted : R.color.group_ink));
        ((TextView)findViewById(R.id.join_tab)).setTextColor(getColor(
                state.joining ? R.color.group_ink : R.color.group_muted));
        ((TextView)findViewById(R.id.form_subtitle)).setText(state.joining
                ? R.string.group_join_subtitle : R.string.group_create_subtitle);
        findViewById(R.id.join_help).setVisibility(state.joining ? View.VISIBLE : View.GONE);
        findViewById(R.id.form_note).setVisibility(state.joining ? View.GONE : View.VISIBLE);
        alias.setHint(state.joining ? R.string.group_alias_join_hint : R.string.group_alias_hint);
        code.setHint(R.string.group_code_hint);
        updateCounter();
        ((TextView)findViewById(R.id.form_title)).setText(state.joining
                ? R.string.group_join_title : R.string.group_create_title);
        ((TextView)findViewById(R.id.form_note)).setText(R.string.group_rule_note);
        ((TextView)findViewById(R.id.submit)).setText(state.joining
                ? R.string.group_join_action : R.string.group_create);
        name.setError(state.nameError); code.setError(state.codeError);
        alias.setError(state.aliasError); count.setError(state.passengersError);
        TextView error = findViewById(R.id.form_error);
        error.setText(state.message);
        error.setVisibility(state.message == null ? View.GONE : View.VISIBLE);
        rendering = false;
    }
}
