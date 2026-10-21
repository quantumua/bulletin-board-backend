package com.example.bulletinboard.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A test clock that stands still until a test advances it, so lock expiry can be tested without
 * sleeping.
 */
public class MutableClock extends Clock {

    public static final Instant START = Instant.parse("2026-10-20T10:00:00Z");

    private volatile Instant now = START;

    public void reset() {
        now = START;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
