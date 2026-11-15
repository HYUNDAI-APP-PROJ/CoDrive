package com.codrive.convoy.data.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VehicleStatus implements Serializable {
    public enum Powertrain { EV, ICE, HEV, PHEV, UNKNOWN }
    public enum Connection { ONLINE, RECONNECTING, OFFLINE }
    public enum Applied { APPLIED, PENDING, FAILED, UNKNOWN }
    public enum Motion { MOVING, STOPPED, UNKNOWN }
    public enum Navigation { GUIDING, ARRIVED, NO_ROUTE, UNKNOWN }
    public enum Charging { CHARGING, NOT_CHARGING, UNKNOWN }

    public static final class Alert implements Serializable {
        public enum Kind { SAFETY, ENERGY, GENERAL }
        public final String id, message;
        public final Kind kind;
        public final int severity;
        public final boolean active;
        public final long observedAt;
        public Alert(String id, Kind kind, int severity, String message, boolean active, long at) {
            this.id = id; this.kind = kind; this.severity = severity;
            this.message = message; this.active = active; this.observedAt = at;
        }
    }

    public final String vehicleId, displayName, destinationId;
    public final int displayOrder, destinationVersion;
    public final Powertrain powertrain;
    public final Connection connection;
    public final Applied applied;
    public final long lastMessageAt;
    public final Measurement<Motion> motion;
    public final Measurement<Navigation> navigation;
    public final Measurement<Double> distance, remainingSeconds, battery, fuel, drivingSeconds, tripDistance, range;
    public final Measurement<Charging> charging;
    public final List<Alert> alerts;
    public final Measurement.Quality alertsQuality;
    public final VehicleRequest request;

    private VehicleStatus(Builder b) {
        vehicleId = b.vehicleId; displayName = b.displayName; displayOrder = b.displayOrder;
        destinationId = b.destinationId; destinationVersion = b.destinationVersion;
        powertrain = b.powertrain; connection = b.connection; applied = b.applied; lastMessageAt = b.lastMessageAt;
        motion = b.motion; navigation = b.navigation;
        distance = normalize(b.distance, Double.MAX_VALUE); remainingSeconds = normalize(b.remainingSeconds, Double.MAX_VALUE);
        battery = normalize(b.battery, 100); fuel = normalize(b.fuel, 100);
        drivingSeconds = normalize(b.drivingSeconds, Double.MAX_VALUE); tripDistance = normalize(b.tripDistance, Double.MAX_VALUE);
        range = normalize(b.range, Double.MAX_VALUE); charging = b.charging;
        alerts = Collections.unmodifiableList(new ArrayList<>(b.alerts)); alertsQuality = b.alertsQuality;
        request = b.request;
    }

    private static Measurement<Double> normalize(Measurement<Double> m, double max) {
        return Measurement.number(m.value, m.quality, m.observedAt, m.source, max);
    }

    public Builder copy() { return new Builder(this); }

    /** Builder exists only on the data side; emitted vehicle snapshots are immutable. */
    public static final class Builder {
        public String vehicleId, displayName, destinationId = "destination-1";
        public int displayOrder, destinationVersion = 1;
        public Powertrain powertrain = Powertrain.UNKNOWN;
        public Connection connection = Connection.ONLINE;
        public Applied applied = Applied.APPLIED;
        public long lastMessageAt;
        public Measurement<Motion> motion = Measurement.missing(Measurement.Quality.LOADING);
        public Measurement<Navigation> navigation = Measurement.missing(Measurement.Quality.LOADING);
        public Measurement<Double> distance = missing(), remainingSeconds = missing(), battery = missing(), fuel = missing();
        public Measurement<Double> drivingSeconds = missing(), tripDistance = missing(), range = missing();
        public Measurement<Charging> charging = Measurement.missing(Measurement.Quality.UNSUPPORTED);
        public List<Alert> alerts = new ArrayList<>();
        public Measurement.Quality alertsQuality = Measurement.Quality.NO_DATA;
        public VehicleRequest request = VehicleRequest.idle();
        public Builder(String id, String name, int order) { vehicleId = id; displayName = name; displayOrder = order; }
        private static Measurement<Double> missing() { return Measurement.missing(Measurement.Quality.NO_DATA); }
        private Builder(VehicleStatus v) {
            vehicleId=v.vehicleId; displayName=v.displayName; displayOrder=v.displayOrder;
            destinationId=v.destinationId; destinationVersion=v.destinationVersion; powertrain=v.powertrain;
            connection=v.connection; applied=v.applied; lastMessageAt=v.lastMessageAt;
            motion=v.motion; navigation=v.navigation; distance=v.distance; remainingSeconds=v.remainingSeconds;
            battery=v.battery; fuel=v.fuel; drivingSeconds=v.drivingSeconds; tripDistance=v.tripDistance;
            range=v.range; charging=v.charging; alerts=new ArrayList<>(v.alerts); alertsQuality=v.alertsQuality;
            request=v.request;
        }
        public VehicleStatus build() { return new VehicleStatus(this); }
    }
}
