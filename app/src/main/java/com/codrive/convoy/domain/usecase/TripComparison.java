package com.codrive.convoy.domain.usecase;

import com.codrive.convoy.data.model.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compares fields independently, only against the same destination ID AND version. */
public final class TripComparison {
    public final Map<String,Double> positions;
    public final List<VehicleStatus> excluded;
    public final List<VehicleStatus> vehiclesByRemainingDistance;
    public final Map<String,Long> arrivalTimes;
    public final double maxDistance;
    public final Long arrivalGapMillis;
    public final boolean allArrived;

    public TripComparison(TripStatus trip) {
        Map<String,Double> distances=new LinkedHashMap<>();
        Map<String,Long> arrivals=new LinkedHashMap<>();
        List<VehicleStatus> omitted=new ArrayList<>();
        List<VehicleStatus> ordered=new ArrayList<>();
        double max=0; boolean arrived=true;
        for(VehicleStatus v:trip.vehicles) {
            if(!trip.sameDestination(v) || !v.navigation.available() || v.navigation.value!=VehicleStatus.Navigation.ARRIVED) arrived=false;
            if(eligible(trip,v) && valid(v.distance)) {
                distances.put(v.vehicleId,v.distance.value); max=Math.max(max,v.distance.value);
                ordered.add(v);
            } else omitted.add(v);
            Long eta=absoluteArrival(v.remainingSeconds);
            if(eligible(trip,v) && eta!=null) arrivals.put(v.vehicleId,eta);
        }
        Map<String,Double> normalized=new LinkedHashMap<>();
        for(Map.Entry<String,Double> entry:distances.entrySet()) normalized.put(entry.getKey(),max==0 ? 1 : 1-entry.getValue()/max);
        positions=Collections.unmodifiableMap(normalized); arrivalTimes=Collections.unmodifiableMap(arrivals);
        // Stable sorting keeps equal distances in the trip's existing vehicle order.
        ordered.sort(Comparator.comparingDouble(vehicle->vehicle.distance.value));
        vehiclesByRemainingDistance=Collections.unmodifiableList(ordered);
        excluded=Collections.unmodifiableList(omitted); maxDistance=max; allArrived=arrived;
        arrivalGapMillis=arrivals.size()<2 ? null : Collections.max(arrivals.values())-Collections.min(arrivals.values());
    }
    public static boolean eligible(TripStatus trip,VehicleStatus v) {
        return v.connection==VehicleStatus.Connection.ONLINE && trip.sameDestination(v) && v.navigation.available()
                && (v.navigation.value==VehicleStatus.Navigation.GUIDING || v.navigation.value==VehicleStatus.Navigation.ARRIVED);
    }
    public static boolean valid(Measurement<Double> field) {
        return field.available() && Double.isFinite(field.value) && field.value>=0;
    }
    public static Long absoluteArrival(Measurement<Double> field) {
        if(!valid(field) || field.observedAt<=0) return null;
        double millis=field.value*1000;
        if(!Double.isFinite(millis) || millis>=Long.MAX_VALUE-field.observedAt) return null;
        return field.observedAt+Math.round(millis);
    }
}
