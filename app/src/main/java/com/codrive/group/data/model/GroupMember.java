package com.codrive.group.data.model;

/** Immutable snapshot of a participating vehicle. */
public final class GroupMember {
    public final String id;
    public final String alias;
    public final int passengerCount;
    public final boolean isLeader;
    public final boolean isReady;
    public final boolean isConnected;

    public GroupMember(String id, String alias, int passengerCount, boolean isLeader,
                       boolean isReady, boolean isConnected) {
        this.id = id;
        this.alias = alias;
        this.passengerCount = passengerCount;
        this.isLeader = isLeader;
        this.isReady = isReady;
        this.isConnected = isConnected;
    }
}
