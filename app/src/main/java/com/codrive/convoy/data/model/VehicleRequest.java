package com.codrive.convoy.data.model;

import java.io.Serializable;

/** Desired mutation and the last server-accepted request are intentionally separate. */
public final class VehicleRequest implements Serializable {
    public enum Type { REST, REFUEL, CHARGE, ENERGY }
    public enum Status { IDLE, PENDING, SHARED, FAILED, CANCELLING }
    public final long id;
    public final Type desired;
    public final Type shared;
    public final Status status;
    public final long createdAt;

    public VehicleRequest(long id, Type desired, Type shared, Status status, long createdAt) {
        this.id = id;
        this.desired = desired;
        this.shared = shared;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static VehicleRequest idle() { return new VehicleRequest(0, null, null, Status.IDLE, 0); }
    public boolean inFlight() { return status == Status.PENDING || status == Status.CANCELLING; }
}
