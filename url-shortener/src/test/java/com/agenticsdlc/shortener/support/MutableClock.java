package com.agenticsdlc.shortener.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A clock whose time the test controls.
 *
 * <p>Expiry is a time-dependent behaviour, and the only two ways to test it are to sleep or
 * to control the clock. Sleeping makes the suite slow and flaky, so nothing in this service
 * calls {@code Instant.now()} directly - every component takes a {@link Clock}.
 */
public final class MutableClock extends Clock {

    private final ZoneId zone;
    private volatile Instant instant;

    public MutableClock(Instant start) {
        this(start, ZoneId.of("UTC"));
    }

    private MutableClock(Instant start, ZoneId zone) {
        this.instant = start;
        this.zone = zone;
    }

    /** A clock starting at a fixed, readable instant. */
    public static MutableClock at(String isoInstant) {
        return new MutableClock(Instant.parse(isoInstant));
    }

    public void advance(Duration amount) {
        instant = instant.plus(amount);
    }

    public void set(Instant newInstant) {
        instant = newInstant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(instant, newZone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
