package com.codrive;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;

import android.content.Intent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.codrive.app.MainActivity;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RecommendationFlowTest {
    private Intent launch(boolean group) {
        return new Intent(ApplicationProvider.getApplicationContext(), MainActivity.class)
                .putExtra("mock_group", group);
    }
    @Test
    public void personalSendClearsInputAndGroupKeepsSeparateHistory() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(launch(true))) {
            onView(withId(R.id.send)).check(matches(not(isEnabled())));
            onView(withText("조용한 데이트 코스")).perform(scrollTo(), click());
            onView(withId(R.id.mode_group)).perform(click());
            onView(withId(R.id.prompt)).check(matches(withText("대전에서 1시간 안에 갈 수 있는 조용한 데이트 코스를 추천해줘")));
            onView(withId(R.id.mode_personal)).perform(click());
            onView(withId(R.id.prompt)).perform(replaceText("조용한 카페 추천"), closeSoftKeyboard());
            onView(withId(R.id.send)).perform(click());
            onView(withId(R.id.prompt)).check(matches(withText("")));
            onView(withText("조용한 카페 추천")).check(matches(isDisplayed()));
            onView(withId(R.id.mode_group)).perform(click());
            onView(withId(R.id.context_title)).check(matches(withText("강릉 주말 여행  ·  차량 3대")));
            onView(withText("조용한 카페 추천")).check(doesNotExist());
            onView(withId(R.id.prompt)).perform(replaceText("그룹 입력 초안"), closeSoftKeyboard());
            scenario.recreate();
            onView(withId(R.id.prompt)).check(matches(withText("그룹 입력 초안")));
            onView(withId(R.id.mode_personal)).perform(click());
            onView(withText("조용한 카페 추천")).check(matches(isDisplayed()));
        }
    }
    @Test
    public void missingGroupOpensCreationAndPreservesDraft() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(launch(false))) {
            onView(withId(R.id.prompt)).perform(replaceText("이어갈 입력"), closeSoftKeyboard());
            onView(withId(R.id.mode_group)).perform(click());
            onView(withId(R.id.create_group)).perform(scrollTo(), click());
            onView(withText("그룹 이름을 1~30자로 입력해주세요.")).check(matches(isDisplayed()));
            onView(withId(R.id.group_name)).perform(replaceText("부산 여행"), closeSoftKeyboard());
            scenario.recreate();
            onView(withId(R.id.group_name)).check(matches(withText("부산 여행")));
            onView(withId(R.id.create_group)).perform(scrollTo(), click());
            onView(withId(R.id.context_title)).check(matches(withText("부산 여행  ·  차량 3대")));
            onView(withId(R.id.prompt)).check(matches(withText("이어갈 입력")));
            onView(withId(R.id.auto_route)).perform(click());
            onView(withText("그룹 모두가 수락하면 추천 코스를 자동 설정해요.")).check(matches(isDisplayed()));
        }
    }
}
