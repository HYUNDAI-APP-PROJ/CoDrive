package com.codrive.convoy.data.repository;

import com.codrive.convoy.data.model.*;
import com.codrive.convoy.data.source.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public final class MockVehicleStatusRepository implements VehicleStatusRepository {
    public static final long SENSOR_STALE_MS=10000, CONNECTION_OFFLINE_MS=30000;
    public static final class Snapshot implements Serializable {
        final TripStatus trip;
        final long now, sequence;
        final DemoScenario scenario;
        final boolean failNext, pendingFailure;
        Snapshot(TripStatus trip,long now,long sequence,DemoScenario scenario,boolean failNext,boolean pendingFailure) {
            this.trip=trip; this.now=now; this.sequence=sequence; this.scenario=scenario;
            this.failNext=failNext; this.pendingFailure=pendingFailure;
        }
    }
    private TripStatus trip;
    private final MockClock clock;
    private final TaskScheduler scheduler;
    private final List<Observer> observers=new ArrayList<>();
    private DemoScenario scenario;
    private long sequence;
    private boolean failNext, pendingFailure, closed;

    public MockVehicleStatusRepository(MockClock clock,TaskScheduler scheduler,Snapshot saved) {
        this.clock=clock; this.scheduler=scheduler;
        if (saved==null) {
            scenario=DemoScenario.NORMAL; trip=DemoFixtures.create(scenario,clock.millis());
        } else {
            trip=saved.trip; clock.set(saved.now); sequence=saved.sequence; scenario=saved.scenario;
            failNext=saved.failNext; pendingFailure=saved.pendingFailure;
            if (trip.mine().request.inFlight()) scheduleResult(trip.mine().request.id,pendingFailure);
        }
    }
    public Snapshot save() { return new Snapshot(trip,clock.millis(),sequence,scenario,failNext,pendingFailure); }
    public long now() { return clock.millis(); }
    public DemoScenario scenario() { return scenario; }
    @Override public TripStatus current() {
        List<VehicleStatus> normalized=new ArrayList<>();
        for (VehicleStatus v : trip.vehicles) {
            VehicleStatus.Builder b=v.copy();
            b.distance=v.distance.expire(clock.millis(),SENSOR_STALE_MS);
            b.remainingSeconds=v.remainingSeconds.expire(clock.millis(),SENSOR_STALE_MS);
            if (clock.millis()-v.lastMessageAt>CONNECTION_OFFLINE_MS) b.connection=VehicleStatus.Connection.OFFLINE;
            normalized.add(b.build());
        }
        return trip.withVehicles(normalized);
    }
    @Override public Subscription observe(Observer observer) {
        observers.add(observer); observer.onTrip(current());
        return () -> observers.remove(observer);
    }
    private void emit() { TripStatus value=current(); for (Observer o : new ArrayList<>(observers)) o.onTrip(value); }

    @Override public void toggleRequest(VehicleRequest.Type type) {
        if (closed || !trip.actionsAllowed || type==null) return;
        VehicleRequest previous=trip.mine().request;
        if (previous.inFlight() && previous.desired==type) return;
        // Failed cancellation must retry cancellation, not create another request.
        VehicleRequest.Type desired=previous.status==VehicleRequest.Status.FAILED
                && (previous.desired==type || (previous.desired==null && previous.shared==type))
                ? previous.desired : previous.shared==type ? null : type;
        if (previous.inFlight() && previous.desired==desired) return;
        long id=++sequence;
        setRequest(new VehicleRequest(id,desired,previous.shared,desired==null ? VehicleRequest.Status.CANCELLING
                : VehicleRequest.Status.PENDING,clock.millis()));
        pendingFailure=failNext; failNext=false;
        scheduleResult(id,pendingFailure); emit();
    }
    private void scheduleResult(long id,boolean failure) { scheduler.after(650,() -> completeRequest(id,!failure)); }
    /** Late acknowledgements from a previous intent/trip are discarded. */
    public void completeRequest(long id,boolean success) {
        VehicleRequest active=trip.mine().request;
        if (closed || active.id!=id || !active.inFlight()) return;
        setRequest(new VehicleRequest(id,active.desired,success ? active.desired : active.shared,
                success ? active.desired==null ? VehicleRequest.Status.IDLE : VehicleRequest.Status.SHARED
                        : VehicleRequest.Status.FAILED,active.createdAt));
        emit();
    }
    private void setRequest(VehicleRequest request) {
        List<VehicleStatus> next=new ArrayList<>(trip.vehicles);
        VehicleStatus.Builder b=trip.mine().copy(); b.request=request; next.set(0,b.build()); trip=trip.withVehicles(next);
    }
    public void select(DemoScenario scenario) {
        sequence++; this.scenario=scenario; clock.set(MockClock.localStart(clock.getZone()));
        trip=DemoFixtures.create(scenario,clock.millis());
        failNext=scenario==DemoScenario.REQUEST_FAILURE; pendingFailure=false; emit();
    }
    public void advance(long millis) { clock.advance(millis); emit(); }
    /** A heartbeat must never refresh the observation timestamps of distance/ETA. */
    public void heartbeat(String vehicleId) {
        List<VehicleStatus> next=new ArrayList<>();
        for (VehicleStatus v:trip.vehicles) {
            VehicleStatus.Builder b=v.copy();
            if (v.vehicleId.equals(vehicleId)) { b.lastMessageAt=clock.millis(); b.connection=VehicleStatus.Connection.ONLINE; }
            next.add(b.build());
        }
        trip=trip.withVehicles(next); emit();
    }
    public void recover() {
        List<VehicleStatus> next=new ArrayList<>();
        for (VehicleStatus v:trip.vehicles) {
            VehicleStatus.Builder b=v.copy(); b.connection=VehicleStatus.Connection.ONLINE; b.lastMessageAt=clock.millis();
            b.distance=refresh(v.distance); b.remainingSeconds=refresh(v.remainingSeconds);
            // Explicit mock observations; warnings are kept until an explicit clear signal.
            next.add(b.build());
        }
        trip=trip.withVehicles(next); emit();
    }
    private Measurement<Double> refresh(Measurement<Double> m) {
        return m.hasLastValue() ? Measurement.mock(m.value,clock.millis()) : m;
    }
    public void close() { closed=true; observers.clear(); }
}
