package com.codrive.convoy.data.source;

import com.codrive.convoy.data.model.VehicleStatus;

/**
 * Read-only integration contract. A Pleos adapter supplies ONLY this car; a group transport
 * supplies peers. Normalize units, capabilities, permissions and observation times before emit.
 * No guessed SDK signatures, vehicle controls or silent MOCK fallback belong here.
 */
public interface VehicleStatusSource {
    interface Listener { void onVehicle(VehicleStatus vehicle); }
    AutoCloseable subscribe(String tripId, Listener listener);
}
