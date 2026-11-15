package com.codrive.convoy;

import com.codrive.convoy.ui.RoutePanelState;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class RoutePanelStateTest {
    @Test public void activeRouteKeepsOwnerAndOrderedStepsWithoutSharingAMutableList() {
        List<RoutePanelState.Step> steps=new ArrayList<>(Arrays.asList(
                new RoutePanelState.Step("현재 위치","대전 유성구"),
                new RoutePanelState.Step("경유지 1","호숫가 식당"),
                new RoutePanelState.Step("목적지","전망 카페")));
        RoutePanelState state=new RoutePanelState(RoutePanelState.Status.ACTIVE," 주말 여행 ","추천받은 경로",steps);
        steps.clear();
        assertEquals("주말 여행",state.owner); assertEquals(3,state.steps.size());
        assertEquals("현재 위치",state.steps.get(0).label); assertEquals("전망 카페",state.steps.get(2).name);
        assertThrows(UnsupportedOperationException.class,()->state.steps.clear());
    }
    @Test public void emptyOrLoadingRouteDoesNotLeakPreviousStops() {
        RoutePanelState empty=new RoutePanelState(RoutePanelState.Status.ACTIVE,"개인","이전 경로",Collections.emptyList());
        assertEquals(RoutePanelState.Status.EMPTY,empty.status); assertEquals("",empty.owner);
        RoutePanelState loading=new RoutePanelState(RoutePanelState.Status.LOADING,"개인","이전 경로",
                Collections.singletonList(new RoutePanelState.Step("목적지","이전 목적지")));
        assertTrue(loading.steps.isEmpty()); assertEquals("",loading.source);
        assertEquals(RoutePanelState.Status.LOADING,loading.status);
    }
    @Test public void malformedOrUnboundedRouteTextIsRejected() {
        assertThrows(IllegalArgumentException.class,()->new RoutePanelState.Step("목적지"," "));
        assertThrows(IllegalArgumentException.class,()->new RoutePanelState.Step("목적지",new String(new char[161]).replace('\0','가')));
        List<RoutePanelState.Step> tooMany=Collections.nCopies(33,new RoutePanelState.Step("경유지","장소"));
        assertThrows(IllegalArgumentException.class,()->new RoutePanelState(RoutePanelState.Status.ACTIVE,"","",tooMany));
    }
}
