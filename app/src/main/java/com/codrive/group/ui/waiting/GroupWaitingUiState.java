package com.codrive.group.ui.waiting;
import com.codrive.group.data.model.GroupMember;
import com.codrive.group.data.model.GroupSession;

public final class GroupWaitingUiState {
    public final GroupSession group;
    public final GroupMember me;
    public final boolean closed, started, canStart;
    public final String message;
    public GroupWaitingUiState(GroupSession group, String memberId, String message) {
        this.group = group;
        me = group == null ? null : group.member(memberId);
        closed = group == null || me == null || GroupSession.CLOSED.equals(group.status);
        started = !closed && GroupSession.STARTED.equals(group.status);
        canStart = !closed && me.isLeader && group.canStart();
        this.message = message;
    }
}
