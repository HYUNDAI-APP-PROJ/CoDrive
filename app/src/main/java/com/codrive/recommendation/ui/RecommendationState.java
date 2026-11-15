package com.codrive.recommendation.ui;

import com.codrive.group.data.model.TravelGroup;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** View나 Android 서비스와 분리한 화면 상태. Activity 재생성 시 함께 복원합니다. */
public final class RecommendationState implements Serializable {
    public TravelGroup group;
    public boolean groupMode;
    public boolean autoRoute;
    public String draft = "";
    public String groupNameDraft = "";
    private final Map<String, ArrayList<Turn>> history = new HashMap<>();

    public boolean needsGroup() {
        return groupMode && group == null;
    }
    private String key() {
        return groupMode ? "group:" + group.id : "personal";
    }

    public List<Turn> turns() {
        if (needsGroup())
            return new ArrayList<>();
        return new ArrayList<>(history.computeIfAbsent(key(), ignored -> new ArrayList<>()));
    }

    public boolean submit() {
        if (needsGroup() || draft.trim().isEmpty())
            return false;
        ArrayList<Turn> turns = history.computeIfAbsent(key(), ignored -> new ArrayList<>());
        // ponytail: 최근 30건을 메모리에 유지합니다. 영구 기록은 서버/로컬 저장소 도입 시 분리합니다.
        if (turns.size() == 30)
            turns.remove(0);
        turns.add(new Turn(
                draft.trim(), groupMode ? "나 · " + group.name : "나 · 개인 추천", autoRoute, groupMode));
        draft = "";
        return true;
    }

    public static final class Turn implements Serializable {
        public final String prompt;
        public final String author;
        public final boolean autoRoute;
        public final boolean groupMode;
        Turn(String prompt, String author, boolean autoRoute, boolean groupMode) {
            this.prompt = prompt;
            this.author = author;
            this.autoRoute = autoRoute;
            this.groupMode = groupMode;
        }
        public String reply() {
            String action = !autoRoute ? "추천 결과를 확인한 뒤 코스를 선택할 수 있어요."
                    : groupMode        ? "실제 연동 시 그룹 전원의 수락 후 경로를 설정해요."
                                       : "실제 연동 시 추천 코스를 자동으로 설정해요.";
            return "화면 확인용 추천 예시예요. AI 응답과 실제 내비게이션은 연결되지 않았어요. " + action;
        }
    }
}
