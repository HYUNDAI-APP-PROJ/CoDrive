package com.codrive.convoy.data.model;

import java.io.Serializable;
import java.util.Objects;

/** Normalized observation. Unavailable/private values never cross the repository boundary. */
public final class Measurement<T extends Serializable> implements Serializable {
    public enum Quality { AVAILABLE, LOADING, NO_DATA, NOT_SHARED, UNSUPPORTED, PERMISSION_DENIED, STALE, ERROR }
    public enum Source { MOCK, VEHICLE_SDK, NAVIHELPER, FUSED_LOCATION, USER_INPUT }
    public final T value;
    public final Quality quality;
    public final long observedAt;
    public final Source source;

    public Measurement(T value, Quality quality, long observedAt, Source source) {
        this.source = Objects.requireNonNull(source);
        boolean canKeep = quality == Quality.AVAILABLE || quality == Quality.STALE;
        this.quality = canKeep && (value == null || observedAt <= 0) ? Quality.NO_DATA : quality;
        this.value = canKeep && this.quality == quality ? value : null;
        this.observedAt = observedAt;
    }

    public static <T extends Serializable> Measurement<T> mock(T value, long at) {
        return new Measurement<>(value, Quality.AVAILABLE, at, Source.MOCK);
    }

    public static <T extends Serializable> Measurement<T> missing(Quality quality) {
        return new Measurement<>(null, quality, 0, Source.MOCK);
    }

    public static Measurement<Double> number(Double value, Quality quality, long at, Source source, double max) {
        if (quality != Quality.AVAILABLE && quality != Quality.STALE) return new Measurement<>(null, quality, at, source);
        if (value != null && (!Double.isFinite(value) || value < 0 || value > max)) {
            return new Measurement<>(null, Quality.ERROR, at, source);
        }
        return new Measurement<>(value, quality, at, source);
    }

    public boolean available() { return quality == Quality.AVAILABLE && value != null; }
    public boolean hasLastValue() { return value != null && (available() || quality == Quality.STALE); }

    public Measurement<T> expire(long now, long thresholdMillis) {
        return available() && now - observedAt > thresholdMillis
                ? new Measurement<>(value, Quality.STALE, observedAt, source) : this;
    }
}
