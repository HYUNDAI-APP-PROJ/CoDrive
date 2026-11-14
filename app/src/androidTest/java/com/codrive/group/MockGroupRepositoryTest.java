package com.codrive.group;

import androidx.test.platform.app.InstrumentationRegistry;
import com.codrive.group.data.model.GroupSession;
import com.codrive.group.data.repository.MockGroupRepository;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Uses a test installation; never shares data with another device. */
public class MockGroupRepositoryTest {
    private MockGroupRepository repository;
    @Before public void setUp() {
        repository = MockGroupRepository.getInstance(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        String active = repository.activeGroupId();
        if (active != null) repository.leaveGroup(active, repository.activeMemberId());
    }
    @Test public void validatesReadinessAndLeaderAuthority() {
        GroupSession group = repository.createGroup("여행", "리더 차량", 2, "MAJORITY");
        assertTrue(group.inviteCode.matches("[A-Z0-9]{6}"));
        String guest = repository.addLocalVehicle(group.groupId, "친구 차량", 3);
        repository.updateReadyStatus(group.groupId, group.leaderId, true);
        try {
            repository.startTrip(group.groupId, group.leaderId);
            fail("Unready guest must block departure");
        } catch (IllegalArgumentException expected) { }
        repository.updateReadyStatus(group.groupId, guest, true);
        repository.setConnected(group.groupId, guest, false);
        assertFalse(repository.getGroup(group.groupId).canStart());
        repository.setConnected(group.groupId, guest, true);
        try {
            repository.startTrip(group.groupId, guest);
            fail("Only leader can start");
        } catch (IllegalArgumentException expected) { }
        repository.startTrip(group.groupId, group.leaderId);
        assertEquals(GroupSession.STARTED, repository.getGroup(group.groupId).status);
        repository.leaveGroup(group.groupId, guest);
        assertEquals(1, repository.getGroup(group.groupId).members.size());
        repository.dissolveGroup(group.groupId, group.leaderId);
        assertEquals(GroupSession.CLOSED, repository.getGroup(group.groupId).status);
    }
    @Test public void joinsByCodeWithSeparateLocalIdentity() {
        GroupSession group = repository.createGroup("코드 참여", "리더", 1, "LEADER");
        repository.clearLocalIdentityForTesting();
        try {
            repository.joinGroup("!!!!!!", "친구", 2);
            fail("Unknown code must fail");
        } catch (IllegalArgumentException expected) { }
        GroupSession joined = repository.joinGroup(group.inviteCode.toLowerCase(java.util.Locale.ROOT),
                "친구", 2);
        assertEquals(2, joined.members.size());
        String guest = repository.activeMemberId();
        assertFalse(joined.member(guest).isLeader);
        repository.leaveGroup(group.groupId, guest);
        assertNull(repository.activeGroupId());
        assertEquals(1, repository.getGroup(group.groupId).members.size());
        repository.dissolveGroup(group.groupId, group.leaderId);
    }
    @Test public void reconnectBeforeDeadlineKeepsGroupAndTimeoutClosesIt() throws InterruptedException {
        GroupSession group = repository.createGroup("재접속", "리더", 2, "MAJORITY");
        repository.setConnected(group.groupId, group.leaderId, false);
        long deadline = repository.getGroup(group.groupId).leaderDisconnectDeadline;
        assertTrue(deadline > System.currentTimeMillis());
        repository.setConnected(group.groupId, group.leaderId, false);
        assertEquals(deadline, repository.getGroup(group.groupId).leaderDisconnectDeadline);
        repository.setConnected(group.groupId, group.leaderId, true);
        assertEquals(0, repository.getGroup(group.groupId).leaderDisconnectDeadline);
        assertEquals(GroupSession.WAITING, repository.getGroup(group.groupId).status);
        repository.setConnected(group.groupId, group.leaderId, false);
        Thread.sleep(30_200);
        assertEquals(GroupSession.CLOSED, repository.getGroup(group.groupId).status);
        assertNull(repository.activeGroupId());
    }
}
