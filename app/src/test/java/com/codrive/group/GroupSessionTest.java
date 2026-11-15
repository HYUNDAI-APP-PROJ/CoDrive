package com.codrive.group;

import com.codrive.group.data.model.GroupMember;
import com.codrive.group.data.model.GroupSession;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class GroupSessionTest {
    private GroupMember vehicle(String id, boolean ready, boolean connected) {
        return new GroupMember(id, id, 2, "leader".equals(id), ready, connected);
    }
    private GroupSession group(String status, GroupMember... members) {
        return new GroupSession("g", "여행", "ABC123", "leader", "MAJORITY",
                status, Arrays.asList(members), 0);
    }
    @Test public void departureRequiresEveryVehicleConnectedAndReady() {
        assertFalse(group(GroupSession.WAITING).canStart());
        assertFalse(group(GroupSession.WAITING, vehicle("leader", true, true),
                vehicle("guest", false, true)).canStart());
        assertFalse(group(GroupSession.WAITING, vehicle("leader", true, false)).canStart());
        assertTrue(group(GroupSession.WAITING, vehicle("leader", true, true),
                vehicle("guest", true, true)).canStart());
        assertFalse(group(GroupSession.STARTED, vehicle("leader", true, true)).canStart());
        assertFalse(group(GroupSession.CLOSED, vehicle("leader", true, true)).canStart());
    }
    @Test public void snapshotDoesNotExposeMutableMembership() {
        List<GroupMember> source = new ArrayList<>();
        source.add(vehicle("leader", false, true));
        GroupSession session = new GroupSession("g", "여행", "ABC123", "leader",
                "LEADER", GroupSession.WAITING, source, 0);
        source.clear();
        assertEquals(1, session.members.size());
        assertEquals(2, session.passengerCount());
        try {
            session.members.clear();
            fail("Membership must be immutable");
        } catch (UnsupportedOperationException expected) {
            assertNotNull(session.member("leader"));
        }
    }
}
