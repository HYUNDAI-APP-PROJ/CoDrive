package com.codrive.convoy.data.repository;

import com.codrive.convoy.data.model.TripStatus;
import com.codrive.convoy.data.model.VehicleRequest;

public interface VehicleStatusRepository {
    interface Observer { void onTrip(TripStatus trip); }
    interface Subscription { void close(); }
    TripStatus current();
    Subscription observe(Observer observer);
    void toggleRequest(VehicleRequest.Type type);
}
