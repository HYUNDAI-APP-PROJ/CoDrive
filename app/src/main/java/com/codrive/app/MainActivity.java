package com.codrive.app;

import static android.view.ViewGroup.LayoutParams.*;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.codrive.BuildConfig;
import com.codrive.R;
import com.codrive.core.speech.SpeechInputController;
import com.codrive.core.ui.Ui;
import com.codrive.group.data.repository.MockGroupRepository;
import com.codrive.group.ui.lobby.GroupCreateScreen;
import com.codrive.recommendation.ui.RecommendationScreen;
import com.codrive.recommendation.ui.RecommendationState;
import com.codrive.recommendation.ui.RecommendationViewModel;

/** 앱 시작점: 도메인 화면, 목업 데이터와 기기 음성 인식을 조립합니다. */
public final class MainActivity extends AppCompatActivity {
    private final MockGroupRepository groups = new MockGroupRepository();
    private RecommendationState state;
    private RecommendationScreen screen;
    private SpeechInputController speech;
    private FrameLayout host;
    private TextView contextTitle, personal, group;
    private boolean recording;
    private boolean awaitingPermission;
    private final ActivityResultLauncher<String> microphonePermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!awaitingPermission)
                    return;
                awaitingPermission = false;
                if (granted)
                    startSpeech();
                else if (screen != null)
                    screen.status("마이크 권한이 필요해요. 텍스트 입력도 사용할 수 있어요.");
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RecommendationViewModel model = new ViewModelProvider(this).get(RecommendationViewModel.class);
        if (model.state == null) {
            model.state = restore(savedInstanceState);
            if (model.state == null) {
                model.state = new RecommendationState();
                // 디버그 실행 옵션만 기본 모의 그룹을 생략할 수 있습니다.
                if (!BuildConfig.DEBUG || getIntent().getBooleanExtra("mock_group", true))
                    model.state.group = groups.defaultGroup();
            }
        }
        state = model.state;
        speech = new SpeechInputController(this);
        LinearLayout root = Ui.row(this);
        root.setBackgroundColor(0xff0c1420);
        root.setGravity(Gravity.TOP);
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars =
                    insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        int width = getResources().getConfiguration().screenWidthDp;
        if (width >= 600)
            root.addView(sidebar(width >= 1000), Ui.size(this, width >= 1000 ? 220 : 72, MATCH_PARENT));
        LinearLayout main = Ui.column(this);
        Ui.pad(main, width >= 1000 ? 32 : 12, 20, width >= 1000 ? 32 : 12, 16);
        root.addView(main, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
        LinearLayout top = Ui.row(this);
        contextTitle = Ui.text(this, "", 14, Ui.MUTED);
        contextTitle.setId(R.id.context_title);
        contextTitle.setMaxLines(2);
        top.addView(contextTitle, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));
        personal = Ui.button(this, "개인 추천", 0xff172333);
        personal.setId(R.id.mode_personal);
        group = Ui.button(this, "그룹 추천", 0xff172333);
        group.setId(R.id.mode_group);
        top.addView(personal, Ui.size(this, 110, 56));
        top.addView(group, Ui.size(this, 110, 56));
        main.addView(top, Ui.size(this, MATCH_PARENT, WRAP_CONTENT));
        personal.setOnClickListener(v -> switchMode(false));
        group.setOnClickListener(v -> switchMode(true));
        host = new FrameLayout(this);
        main.addView(host, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));
        LinearLayout footer = Ui.row(this);
        TextView location = Ui.text(this, "●  대전 유성구 · 위치 예시", 11, Ui.MUTED);
        footer.addView(location, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));
        footer.addView(Ui.text(this, "프론트 미리보기 · 정차 상태 예시", 11, Ui.MUTED));
        main.addView(footer);
        render();
    }
    @SuppressWarnings("deprecation")
    private RecommendationState restore(Bundle saved) {
        return saved == null ? null : (RecommendationState) saved.getSerializable("recommendation");
    }
    private View sidebar(boolean expanded) {
        LinearLayout side = Ui.column(this);
        side.setBackgroundColor(0xff101b2a);
        Ui.pad(side, expanded ? 16 : 8, 30, expanded ? 16 : 8, 24);
        TextView brand = Ui.text(this, expanded ? "CoDrive." : "Co.", expanded ? 29 : 22, Ui.MINT);
        Ui.bold(brand);
        Ui.pad(brand, expanded ? 12 : 0, 0, 0, 0);
        side.addView(brand);
        Ui.gap(side, 44);
        if (expanded) {
            side.addView(Ui.text(this, "   OUR JOURNEY", 11, Ui.MUTED));
            Ui.gap(side, 12);
        }
        String[] names = {"여행 시작", "우리 그룹", "실시간 주행", "AI 경로 추천", "음성 채널", "여행 기록"};
        int[] icons = {R.drawable.ic_home, R.drawable.ic_group, R.drawable.ic_route, R.drawable.ic_spark,
                R.drawable.ic_mic, R.drawable.ic_history};
        for (int i = 0; i < names.length; i++) {
            LinearLayout item = Ui.row(this);
            Ui.pad(item, expanded ? 14 : 12, 14, 8, 14);
            if (i == 3) {
                item.setBackgroundResource(R.drawable.bg_selected);
                item.setSelected(true);
            }
            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[i]);
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(i == 3 ? Ui.MINT : Ui.MUTED));
            item.addView(icon, Ui.size(this, 22, 22));
            if (expanded) {
                TextView label = Ui.text(this, names[i], 14, i == 3 ? Ui.MINT : Ui.MUTED);
                Ui.pad(label, 12, 0, 0, 0);
                item.addView(label);
            }
            item.setContentDescription(names[i] + (i == 3 ? ", 현재 화면" : ", 준비 중"));
            side.addView(item, Ui.size(this, MATCH_PARENT, 56));
            Ui.gap(side, 7);
        }
        side.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        TextView vehicle = Ui.text(this, expanded ? "B   내 차량\n     목업 차량 3대" : "B", 13, Ui.MUTED);
        vehicle.setLineSpacing(Ui.dp(this, 8), 1);
        Ui.pad(vehicle, 10, 16, 0, 0);
        side.addView(vehicle);
        return side;
    }
    private void switchMode(boolean groupMode) {
        cancelSpeech(true);
        state.groupMode = groupMode;
        render();
    }
    private void render() {
        screen = null;
        host.removeAllViews();
        contextTitle.setText(state.needsGroup() ? "여행 그룹 만들기"
                        : state.groupMode ? state.group.name + "  ·  차량 " + state.group.vehicleCount + "대"
                                          : "장소·코스 추천");
        personal.setTextColor(!state.groupMode ? Ui.MINT : Ui.MUTED);
        personal.setSelected(!state.groupMode);
        personal.setBackground(Ui.shape(this, !state.groupMode ? 0xff194148 : 0xff172333, 11));
        group.setTextColor(state.groupMode ? Ui.MINT : Ui.MUTED);
        group.setSelected(state.groupMode);
        group.setBackground(Ui.shape(this, state.groupMode ? 0xff194148 : 0xff172333, 11));
        if (state.needsGroup()) {
            GroupCreateScreen create = new GroupCreateScreen(
                    this, state.groupNameDraft, name -> state.groupNameDraft = name, name -> {
                        state.group = groups.create(name);
                        state.groupNameDraft = "";
                        render();
                    }, () -> switchMode(false));
            host.addView(create.view);
            return;
        }
        screen = new RecommendationScreen(this, state, new RecommendationScreen.Actions() {
            @Override
            public void microphone() {
                if (recording || awaitingPermission) {
                    cancelSpeech(true);
                    return;
                }
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED)
                    startSpeech();
                else {
                    awaitingPermission = true;
                    microphonePermission.launch(Manifest.permission.RECORD_AUDIO);
                }
            }
            @Override
            public void editing() {
                cancelSpeech(false);
            }
            @Override
            public void submit() {
                submitPrompt();
            }
        });
        host.addView(screen.view);
    }
    private void submitPrompt() {
        if (recording || screen == null)
            return;
        state.draft = screen.prompt.getText().toString();
        if (state.submit()) {
            screen.hideKeyboard();
            render();
        }
    }
    private void startSpeech() {
        if (screen == null || state.needsGroup())
            return;
        screen.hideKeyboard();
        recording = true;
        screen.recording(true, false);
        screen.status("듣고 있어요. 말을 마치면 자동 전송돼요.");
        speech.start(new SpeechInputController.Listener() {
            @Override
            public void partial(String text) {
                if (screen != null && !text.isEmpty())
                    screen.setPrompt(text);
            }
            @Override
            public void processing() {
                if (screen != null) {
                    screen.recording(true, true);
                    screen.status("말씀하신 내용을 정리하고 있어요…");
                }
            }
            @Override
            public void result(String text) {
                recording = false;
                if (screen != null) {
                    screen.recording(false, false);
                    screen.setPrompt(text);
                    submitPrompt();
                }
            }
            @Override
            public void error(String message) {
                recording = false;
                if (screen != null) {
                    screen.recording(false, false);
                    screen.setPrompt(state.draft);
                    screen.status(message);
                }
            }
            @Override
            public void level(float rms) {
                if (screen != null)
                    screen.microphone.setLevel(rms);
            }
        });
    }
    private void cancelSpeech(boolean restoreDraft) {
        awaitingPermission = false;
        if (!recording)
            return;
        recording = false;
        speech.cancel();
        if (screen != null) {
            screen.recording(false, false);
            screen.status("");
            if (restoreDraft)
                screen.setPrompt(state.draft);
        }
    }
    @Override
    protected void onStop() {
        cancelSpeech(true);
        super.onStop();
    }
    @Override
    protected void onSaveInstanceState(Bundle out) {
        cancelSpeech(true);
        out.putSerializable("recommendation", state);
        super.onSaveInstanceState(out);
    }
    @Override
    protected void onDestroy() {
        speech.cancel();
        super.onDestroy();
    }
}
