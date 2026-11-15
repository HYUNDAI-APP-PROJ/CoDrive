package com.codrive.group.data.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable group snapshot shared between the repository and presentation layer. */
public final class GroupSession {
    public static final String WAITING = "WAITING";
    public static final String STARTED = "STARTED";
    public static final String CLOSED = "CLOSED";
    public final String groupId, groupName, inviteCode, leaderId, decisionRule, status;
    public final List<GroupMember> members;
    public final long leaderDisconnectDeadline;

    public GroupSession(String groupId, String groupName, String inviteCode, String leaderId,
                        String decisionRule, String status, List<GroupMember> members,
                        long leaderDisconnectDeadline) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.inviteCode = inviteCode;
        this.leaderId = leaderId;
        this.decisionRule = decisionRule;
        this.status = status;
        this.members = Collections.unmodifiableList(new ArrayList<>(members));
        this.leaderDisconnectDeadline = leaderDisconnectDeadline;
    }

    public GroupMember member(String id) {
        for (GroupMember member : members) {
            if (member.id.equals(id)) return member;
        }
        return null;
    }

    public boolean canStart() {
        if (!WAITING.equals(status) || members.isEmpty()) return false;
        for (GroupMember member : members) {
            if (!member.isConnected || !member.isReady) return false;
        }
        return true;
    }

    public int passengerCount() {
        int count = 0;
        for (GroupMember member : members) count += member.passengerCount;
        return count;
    }
}
