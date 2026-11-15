package com.codrive;

import static org.junit.Assert.*;

import com.codrive.group.data.model.TravelGroup;
import com.codrive.recommendation.ui.RecommendationState;
import java.io.*;
import org.junit.Test;

public class RecommendationStateTest {
    @Test
    public void historyIsSeparatedAndSurvivesRestoration() throws Exception {
        RecommendationState state = new RecommendationState();
        state.group = new TravelGroup("default", "강릉 주말 여행", 3);
        state.draft = "  조용한 카페  ";
        assertTrue(state.submit());
        assertEquals("", state.draft);
        state.groupMode = true;
        assertTrue(state.turns().isEmpty());
        state.autoRoute = true;
        state.draft = "함께 갈 맛집";
        assertTrue(state.submit());
        assertTrue(state.turns().get(0).reply().contains("그룹 전원의 수락"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(state);
        RecommendationState restored =
                (RecommendationState) new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))
                        .readObject();
        assertEquals("함께 갈 맛집", restored.turns().get(0).prompt);
        restored.groupMode = false;
        assertEquals("조용한 카페", restored.turns().get(0).prompt);
    }
    @Test
    public void missingGroupAndBlankPromptCannotSubmit() {
        RecommendationState state = new RecommendationState();
        state.draft = "  ";
        assertFalse(state.submit());
        state.groupMode = true;
        state.draft = "맛집";
        assertTrue(state.needsGroup());
        assertFalse(state.submit());
        state.group = new TravelGroup("first", "우리 여행", 3);
        assertEquals("우리 여행", state.group.name);
        assertTrue(state.submit());
        state.group = new TravelGroup("second", "새 여행", 3);
        assertTrue(state.turns().isEmpty());
    }
    @Test
    public void historyHasBounds() {
        RecommendationState state = new RecommendationState();
        for (int i = 0; i < 35; i++) {
            state.draft = "요청 " + i;
            assertTrue(state.submit());
        }
        assertEquals(30, state.turns().size());
        assertEquals("요청 5", state.turns().get(0).prompt);
    }
}
