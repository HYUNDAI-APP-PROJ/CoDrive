package com.codrive.recommendation.ui;

import static android.view.ViewGroup.LayoutParams.*;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.widget.SwitchCompat;
import com.codrive.R;
import com.codrive.core.ui.MicButton;
import com.codrive.core.ui.Ui;
import java.util.List;

/** AI 추천 목업 화면. 사용자 이벤트를 앱 시작점으로 전달합니다. */
public final class RecommendationScreen {
    public interface Actions {
        void microphone();
        void editing();
        void submit();
    }
    private final Context context;
    private final RecommendationState state;
    public final View view;
    public final EditText prompt;
    public final MicButton microphone;
    private final TextView send;
    private final TextView status;
    private boolean binding;
    private boolean recording;

    public RecommendationScreen(Context context, RecommendationState state, Actions actions) {
        this.context = context;
        this.state = state;
        boolean compact = context.getResources().getConfiguration().screenHeightDp < 700;
        ScrollView page = new ScrollView(context);
        page.setFillViewport(true);
        page.setClipToPadding(false);
        LinearLayout center = Ui.column(context);
        center.setGravity(Gravity.CENTER);
        Ui.pad(center, 12, compact ? 12 : 28, 12, 24);
        page.addView(center, new ScrollView.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        LinearLayout content = Ui.column(context);
        int width = Math.min(900,
                context.getResources().getConfiguration().screenWidthDp
                        - (context.getResources().getConfiguration().screenWidthDp >= 1000 ? 300 : 104));
        if (context.getResources().getConfiguration().screenWidthDp < 600)
            width = context.getResources().getConfiguration().screenWidthDp - 48;
        center.addView(content, Ui.size(context, Math.max(280, width), WRAP_CONTENT));
        List<RecommendationState.Turn> turns = state.turns();
        boolean hasHistory = !turns.isEmpty();
        if (!hasHistory) {
            ImageView hero = new ImageView(context);
            hero.setImageResource(R.drawable.ic_route);
            hero.setBackground(Ui.shape(context, 0xff173c3e, 16));
            Ui.pad(hero, 13, 13, 13, 13);
            LinearLayout.LayoutParams hp = Ui.size(context, 52, 52);
            hp.gravity = Gravity.CENTER;
            content.addView(hero, hp);
            Ui.gap(content, compact ? 14 : 24);
            if (!compact) {
                TextView eyebrow = Ui.text(context, "A LITTLE PLAN, A BETTER JOURNEY", 11, Ui.MINT);
                eyebrow.setLetterSpacing(.16f);
                eyebrow.setGravity(Gravity.CENTER);
                content.addView(eyebrow);
                Ui.gap(content, 16);
            }
        }
        TextView title = Ui.text(context, hasHistory ? "다음 여정도 함께 찾아봐요" : "어디로 떠나볼까요?",
                hasHistory ? 24 : 34, Ui.TEXT);
        Ui.bold(title);
        title.setGravity(hasHistory ? Gravity.START : Gravity.CENTER);
        content.addView(title);
        if (!hasHistory) {
            Ui.gap(content, 10);
            TextView subtitle = Ui.text(context,
                    state.groupMode ? "우리 그룹이 함께 즐길 장소와 코스를 찾아보세요."
                                    : "가고 싶은 곳도, 하고 싶은 일도. 편하게 말해주세요.",
                    14, Ui.MUTED);
            subtitle.setGravity(Gravity.CENTER);
            content.addView(subtitle);
        }
        Ui.gap(content, hasHistory ? 16 : compact ? 24 : 36);
        if (hasHistory) {
            ScrollView log = new ScrollView(context);
            log.setId(R.id.conversation);
            log.setContentDescription("추천 대화 기록");
            LinearLayout messages = Ui.column(context);
            Ui.pad(messages, 0, 0, 8, 0);
            for (RecommendationState.Turn turn : turns)
                addTurn(messages, turn);
            log.addView(messages);
            content.addView(log, Ui.size(context, MATCH_PARENT, compact ? 180 : 310));
            log.post(() -> log.fullScroll(View.FOCUS_DOWN));
            Ui.gap(content, 16);
        }
        LinearLayout composer = Ui.column(context);
        composer.setBackgroundResource(R.drawable.bg_panel);
        Ui.pad(composer, 24, 16, 20, 12);
        content.addView(composer, Ui.size(context, MATCH_PARENT, WRAP_CONTENT));
        status = Ui.text(context, "", 13, Ui.MINT);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setVisibility(View.GONE);
        composer.addView(status);
        prompt = new EditText(context);
        prompt.setId(R.id.prompt);
        prompt.setTextColor(Ui.TEXT);
        prompt.setHintTextColor(Ui.MUTED);
        prompt.setTextSize(17);
        prompt.setBackground(null);
        prompt.setGravity(Gravity.TOP | Gravity.START);
        prompt.setHint(
                "오늘은 어떤 여행을 떠나고 싶나요?\n예: 대전에서 1시간 안에 갈 수 있는 조용한 데이트 코스");
        prompt.setContentDescription("원하는 장소나 코스 입력");
        prompt.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        prompt.setFilters(new InputFilter[] {new InputFilter.LengthFilter(1000)});
        prompt.setText(state.draft);
        Ui.pad(prompt, 0, 6, 0, 10);
        composer.addView(prompt, Ui.size(context, MATCH_PARENT, compact ? 86 : 110));
        FrameLayout controls = new FrameLayout(context);
        composer.addView(controls, Ui.size(context, MATCH_PARENT, 100));
        TextView origin = Ui.text(context, "출발지\n대전 유성구", 12, Ui.MUTED);
        origin.setLineSpacing(Ui.dp(context, 5), 1);
        FrameLayout.LayoutParams op = new FrameLayout.LayoutParams(
                WRAP_CONTENT, MATCH_PARENT, Gravity.START | Gravity.CENTER_VERTICAL);
        controls.addView(origin, op);
        microphone = new MicButton(context);
        microphone.setId(R.id.microphone);
        FrameLayout.LayoutParams mp =
                new FrameLayout.LayoutParams(Ui.dp(context, 96), Ui.dp(context, 96), Gravity.CENTER);
        controls.addView(microphone, mp);
        microphone.setOnClickListener(v -> actions.microphone());
        LinearLayout buttons = Ui.row(context);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                WRAP_CONTENT, MATCH_PARENT, Gravity.END | Gravity.CENTER_VERTICAL);
        controls.addView(buttons, bp);
        LinearLayout autoBox = Ui.column(context);
        autoBox.setGravity(Gravity.CENTER);
        autoBox.setMinimumWidth(Ui.dp(context, 134));
        TextView autoLabel = Ui.text(context, "경로까지 자동 결정", 12, Ui.MUTED);
        autoBox.addView(autoLabel);
        Ui.gap(autoBox, 8);
        SwitchCompat toggle = new SwitchCompat(context);
        toggle.setId(R.id.auto_route);
        toggle.setShowText(false);
        toggle.setContentDescription("경로까지 자동 결정");
        toggle.setChecked(state.autoRoute);
        toggle.setThumbTintList(
                new ColorStateList(new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                        new int[] {0xfff0fff9, 0xffb9cbd8}));
        toggle.setTrackTintList(
                new ColorStateList(new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                        new int[] {Ui.MINT, 0xff314657}));
        toggle.setMinHeight(Ui.dp(context, 48));
        autoBox.addView(toggle);
        buttons.addView(autoBox, Ui.size(context, 134, 90));
        send = Ui.button(context, "", Ui.MINT);
        send.setId(R.id.send);
        send.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_up, 0, 0);
        android.graphics.drawable.Drawable arrow = context.getDrawable(R.drawable.ic_up);
        arrow.setTint(0xff0b302b);
        arrow.setBounds(0, 0, Ui.dp(context, 28), Ui.dp(context, 28));
        send.setCompoundDrawables(null, arrow, null, null);
        Ui.pad(send, 20, 22, 20, 8);
        send.setContentDescription("경로 추천받기");
        buttons.addView(send, Ui.size(context, 76, 76));
        send.setOnClickListener(v -> actions.submit());
        // 좁은 창에서는 마이크가 다른 컨트롤과 겹치지 않게 별도 중앙 행으로 배치합니다.
        if (width < 650) {
            controls.removeView(microphone);
            LinearLayout micRow = Ui.row(context);
            micRow.setGravity(Gravity.CENTER);
            micRow.addView(microphone, Ui.size(context, 96, 96));
            composer.addView(micRow);
        }
        TextView autoCopy = Ui.text(context, "", 12, Ui.MUTED);
        autoCopy.setGravity(Gravity.END);
        Ui.pad(autoCopy, 0, 10, 0, 0);
        content.addView(autoCopy);
        Runnable updateCopy = ()
                -> autoCopy.setText(!state.autoRoute ? "추천 결과를 확인하고 직접 선택해요."
                                : state.groupMode    ? "그룹 모두가 수락하면 추천 코스를 자동 설정해요."
                                                     : "추천받은 코스를 내비게이션에 바로 설정해요.");
        updateCopy.run();
        toggle.setOnCheckedChangeListener((button, checked) -> {
            state.autoRoute = checked;
            autoLabel.setTextColor(checked ? Ui.MINT : Ui.MUTED);
            updateCopy.run();
            if (android.animation.ValueAnimator.areAnimatorsEnabled())
                toggle.animate().scaleX(1.08f).scaleY(1.08f).setDuration(140).withEndAction(
                        () -> toggle.animate().scaleX(1).scaleY(1).setDuration(180));
        });
        autoBox.setOnClickListener(v -> toggle.toggle());
        if (!hasHistory) {
            Ui.gap(content, 24);
            content.addView(Ui.text(context, "이렇게 물어보세요", 12, Ui.MUTED));
            Ui.gap(content, 10);
            String[] labels = {
                    "조용한 데이트 코스", "지금 가기 좋은 맛집", "풍경 좋은 드라이브", "잠깐 쉬어갈 곳"};
            String[] queries = {"대전에서 1시간 안에 갈 수 있는 조용한 데이트 코스를 추천해줘",
                    "지금 내 주변에서 주차하기 편한 맛집을 추천해줘",
                    "탁 트인 풍경을 볼 수 있는 드라이브 코스를 추천해줘",
                    "가는 길에 잠깐 쉬어갈 수 있는 카페를 추천해줘"};
            LinearLayout row = null;
            for (int i = 0; i < 4; i++) {
                if (i == 0 || (width < 650 && i == 2)) {
                    row = Ui.row(context);
                    content.addView(row, Ui.size(context, MATCH_PARENT, WRAP_CONTENT));
                }
                TextView chip = Ui.button(context, labels[i], 0xff14202e);
                chip.setTextSize(12);
                chip.setBackgroundResource(R.drawable.bg_chip);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, Ui.dp(context, 56), 1);
                cp.setMargins(0, 0, Ui.dp(context, 8), Ui.dp(context, 8));
                row.addView(chip, cp);
                String query = queries[i];
                chip.setOnClickListener(v -> {
                    actions.editing();
                    state.draft = query;
                    setPrompt(query);
                });
            }
        }
        Ui.gap(content, 18);
        TextView foot = Ui.text(context,
                "음성 입력은 기기의 인식 서비스를 사용해요. 말을 마치면 자동으로 전송돼요.", 11, Ui.MUTED);
        foot.setGravity(Gravity.CENTER);
        content.addView(foot);
        prompt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!binding) {
                    actions.editing();
                    state.draft = s.toString();
                }
                updateSend();
            }
            @Override
            public void afterTextChanged(Editable e) {}
        });
        updateSend();
        view = page;
        page.setFocusableInTouchMode(true);
        page.requestFocus();
    }
    private void addTurn(LinearLayout log, RecommendationState.Turn turn) {
        LinearLayout bubble = Ui.column(context);
        Ui.pad(bubble, 20, 14, 20, 14);
        bubble.setBackgroundResource(R.drawable.bg_bubble);
        bubble.addView(Ui.text(context, turn.author, 11, 0xff8cc7bd));
        Ui.gap(bubble, 7);
        bubble.addView(Ui.text(context, turn.prompt, 15, Ui.TEXT));
        LinearLayout.LayoutParams p = Ui.size(context, WRAP_CONTENT, WRAP_CONTENT);
        p.gravity = Gravity.END;
        log.addView(bubble, p);
        Ui.gap(log, 14);
        LinearLayout reply = Ui.column(context);
        reply.setBackgroundResource(R.drawable.bg_reply);
        Ui.pad(reply, 20, 18, 20, 18);
        TextView heading = Ui.text(context, "여유롭게 다녀오는 반나절 코스   ·   추천 예시", 16, Ui.TEXT);
        Ui.bold(heading);
        reply.addView(heading);
        Ui.gap(reply, 16);
        reply.addView(Ui.text(context, "① 가볍게 산책하기     ② 맛있는 식사     ③ 조용한 카페", 13, Ui.MINT));
        Ui.gap(reply, 14);
        TextView copy = Ui.text(context, turn.reply(), 12, Ui.MUTED);
        copy.setLineSpacing(Ui.dp(context, 4), 1);
        reply.addView(copy);
        log.addView(reply, Ui.size(context, MATCH_PARENT, WRAP_CONTENT));
        Ui.gap(log, 24);
    }
    public void setPrompt(String text) {
        binding = true;
        prompt.setText(text);
        prompt.setSelection(prompt.length());
        binding = false;
        updateSend();
    }
    public void recording(boolean active, boolean processing) {
        recording = active;
        microphone.setRecording(active, processing);
        updateSend();
    }
    public void status(String message) {
        status.setText(message);
        status.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
    }
    private void updateSend() {
        send.setEnabled(!recording && !prompt.getText().toString().trim().isEmpty());
        send.setAlpha(send.isEnabled() ? 1 : .45f);
    }
    public void hideKeyboard() {
        ((InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(prompt.getWindowToken(), 0);
    }
}
