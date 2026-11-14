package com.codrive.convoy.data.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

public final class TripStatus implements Serializable {
    public enum Mode { MOCK, LIVE }
    public final String tripId, destinationId, destinationName, currentVehicleId;
    public final int destinationVersion;
    public final Mode mode;
    public final List<VehicleStatus> vehicles;
    public final boolean actionsAllowed, detailsAllowed;

    public TripStatus(String tripId, Mode mode, String destinationId, int destinationVersion,
                      String destinationName, String currentVehicleId, List<VehicleStatus> vehicles,
                      boolean actionsAllowed, boolean detailsAllowed) {
        if (vehicles.isEmpty() || vehicles.size() > 3) throw new IllegalArgumentException("Expected 1–3 vehicles");
        HashSet<String> ids = new HashSet<>();
        for (VehicleStatus vehicle : vehicles) {
            if (!ids.add(vehicle.vehicleId)) throw new IllegalArgumentException("Duplicate vehicle ID");
        }
        if (!ids.contains(currentVehicleId)) throw new IllegalArgumentException("Missing current vehicle");
        this.tripId=tripId; this.mode=mode; this.destinationId=destinationId; this.destinationVersion=destinationVersion;
        this.destinationName=destinationName; this.currentVehicleId=currentVehicleId;
        ArrayList<VehicleStatus> sorted=new ArrayList<>(vehicles);
        sorted.sort(Comparator.comparingInt(v -> v.vehicleId.equals(currentVehicleId) ? -1 : v.displayOrder));
        this.vehicles=Collections.unmodifiableList(sorted);
        this.actionsAllowed=actionsAllowed; this.detailsAllowed=detailsAllowed;
    }
    public VehicleStatus mine() { return vehicles.get(0); }
    public boolean sameDestination(VehicleStatus v) {
        return v.applied == VehicleStatus.Applied.APPLIED && destinationId.equals(v.destinationId)
                && destinationVersion == v.destinationVersion;
    }
    public TripStatus withVehicles(List<VehicleStatus> next) {
        return new TripStatus(tripId, mode, destinationId, destinationVersion, destinationName,
                currentVehicleId, next, actionsAllowed, detailsAllowed);
    }
}
