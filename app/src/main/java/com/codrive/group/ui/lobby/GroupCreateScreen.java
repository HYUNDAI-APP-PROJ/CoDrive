package com.codrive.group.ui.lobby;

import static android.view.ViewGroup.LayoutParams.*;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.codrive.R;
import com.codrive.core.ui.Ui;
import java.util.function.Consumer;

/** 그룹이 없는 상태에서 표시하는 모의 그룹 생성 화면입니다. */
public final class GroupCreateScreen {
    public final View view;
    public GroupCreateScreen(Context context, String draft, Consumer<String> onEdit,
            Consumer<String> onCreate, Runnable onBack) {
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        LinearLayout center = Ui.column(context);
        center.setGravity(Gravity.CENTER);
        Ui.pad(center, 20, 24, 20, 24);
        scroll.addView(center);
        LinearLayout content = Ui.column(context);
        int width = Math.min(620, context.getResources().getConfiguration().screenWidthDp - 120);
        center.addView(content, Ui.size(context, width, WRAP_CONTENT));
        TextView title = Ui.text(context, "함께 떠날 그룹을 만들어볼까요?", 28, Ui.TEXT);
        Ui.bold(title);
        content.addView(title);
        Ui.gap(content, 12);
        content.addView(Ui.text(context, "그룹 추천을 받으려면 먼저 여행 그룹이 필요해요.", 14, Ui.MUTED));
        Ui.gap(content, 28);
        LinearLayout panel = Ui.column(context);
        panel.setBackgroundResource(R.drawable.bg_panel);
        Ui.pad(panel, 26, 26, 26, 20);
        content.addView(panel, Ui.size(context, MATCH_PARENT, WRAP_CONTENT));
        TextView label = Ui.text(context, "여행 그룹 이름", 14, Ui.TEXT);
        panel.addView(label);
        Ui.gap(panel, 12);
        EditText name = new EditText(context);
        name.setId(R.id.group_name);
        label.setLabelFor(R.id.group_name);
        name.setSingleLine();
        name.setTextSize(17);
        name.setTextColor(Ui.TEXT);
        name.setHintTextColor(Ui.MUTED);
        name.setHint("예: 강릉 주말 여행");
        name.setText(draft);
        name.setFilters(new InputFilter[] {new InputFilter.LengthFilter(30)});
        name.setBackgroundResource(R.drawable.bg_field);
        Ui.pad(name, 16, 12, 16, 12);
        panel.addView(name, Ui.size(context, MATCH_PARENT, 64));
        name.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                onEdit.accept(s.toString());
            }
            @Override
            public void afterTextChanged(Editable e) {}
        });
        Ui.gap(panel, 22);
        panel.addView(Ui.text(context, "A   ·   B   ·   C     미리보기 참여 차량 3대", 13, Ui.MUTED));
        Ui.gap(panel, 22);
        TextView create = Ui.button(context, "그룹 만들고 추천받기", Ui.MINT);
        create.setId(R.id.create_group);
        create.setTextColor(0xff0b302b);
        Ui.bold(create);
        panel.addView(create, Ui.size(context, MATCH_PARENT, 64));
        TextView error = Ui.text(context, "", 12, 0xfff7c98c);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        create.setOnClickListener(v -> {
            if (name.getText().toString().trim().isEmpty()) {
                error.setText("그룹 이름을 1~30자로 입력해주세요.");
                name.requestFocus();
            } else
                onCreate.accept(name.getText().toString());
        });
        TextView back = Ui.button(context, "개인 추천으로 돌아가기", android.graphics.Color.TRANSPARENT);
        back.setId(R.id.back_personal);
        back.setTextColor(Ui.MUTED);
        panel.addView(back, Ui.size(context, MATCH_PARENT, 56));
        back.setOnClickListener(v -> onBack.run());
        panel.addView(error);
        center.setFocusableInTouchMode(true);
        center.requestFocus();
        view = scroll;
    }
}
