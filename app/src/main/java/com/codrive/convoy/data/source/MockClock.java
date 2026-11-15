package com.codrive.convoy.data.source;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Frozen until the developer explicitly advances it; never ages from wall-clock time. */
public final class MockClock extends Clock {
    public static final long START = Instant.parse("2026-11-14T05:30:00Z").toEpochMilli();
    private long now;
    private final ZoneId zone;
    public MockClock(long now) { this(now, ZoneId.systemDefault()); }
    public MockClock(long now, ZoneId zone) { this.now=now; this.zone=zone; }
    public static long localStart(ZoneId zone) {
        return LocalDateTime.of(2026,11,14,14,30).atZone(zone).toInstant().toEpochMilli();
    }
    public void advance(long millis) { if (millis < 0) throw new IllegalArgumentException(); now+=millis; }
    public void set(long now) { this.now=now; }
    @Override public long millis() { return now; }
    @Override public Instant instant() { return Instant.ofEpochMilli(now); }
    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId zone) { return new MockClock(now, zone); }
}
