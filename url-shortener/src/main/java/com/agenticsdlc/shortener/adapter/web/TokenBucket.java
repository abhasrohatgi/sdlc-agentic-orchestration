package com.agenticsdlc.shortener.adapter.web;

import java.time.Duration;
import java.time.Instant;

/**
 * A token bucket, with time supplied by the caller.
 *
 * <p>Chosen over a fixed window because a fixed window allows twice the intended rate across
 * a boundary: a client can spend a full quota in the last instant of one window and another
 * full quota in the first instant of the next. A token bucket permits a burst up to
 * {@code capacity} and then settles to the refill rate, with no boundary to exploit.
 *
 * <p>Refill is computed lazily from elapsed time rather than driven by a scheduler. That
 * means no background thread per client and no work at all for idle clients.
 *
 * <p>Time is a parameter rather than read from a clock inside, so rate-limit behaviour is
 * testable without sleeping.
 *
 * <p>Thread-safe by synchronisation. Contention is per client key, not global.
 */
public final class TokenBucket {

    private final double capacity;
    private final double tokensPerNano;

    private double tokens;
    private long lastRefillNanos;

    /**
     * @param capacity     burst size; also the starting number of tokens
     * @param refillTokens tokens restored per {@code refillPeriod}
     * @param refillPeriod window over which {@code refillTokens} are restored
     * @param now          current time
     */
    public TokenBucket(int capacity, int refillTokens, Duration refillPeriod, Instant now) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive, got " + capacity);
        }
        if (refillTokens < 1) {
            throw new IllegalArgumentException("refillTokens must be positive, got " + refillTokens);
        }
        if (refillPeriod.isZero() || refillPeriod.isNegative()) {
            throw new IllegalArgumentException("refillPeriod must be positive, got " + refillPeriod);
        }
        this.capacity = capacity;
        this.tokensPerNano = (double) refillTokens / refillPeriod.toNanos();
        this.tokens = capacity;
        this.lastRefillNanos = toNanos(now);
    }

    /**
     * Takes one token if available.
     *
     * @return {@code true} if the request is allowed
     */
    public synchronized boolean tryConsume(Instant now) {
        refill(now);
        if (tokens >= 1.0d) {
            tokens -= 1.0d;
            return true;
        }
        return false;
    }

    /**
     * How long until at least one token is available.
     *
     * <p>Used for the {@code Retry-After} header. Telling a rejected client when to come
     * back is what turns a 429 into something a well-behaved client can act on, rather than
     * an invitation to retry immediately and make the overload worse.
     */
    public synchronized Duration timeUntilNextToken(Instant now) {
        refill(now);
        if (tokens >= 1.0d) {
            return Duration.ZERO;
        }
        double needed = 1.0d - tokens;
        return Duration.ofNanos((long) Math.ceil(needed / tokensPerNano));
    }

    /** Tokens currently available. For tests and diagnostics. */
    public synchronized double availableTokens(Instant now) {
        refill(now);
        return tokens;
    }

    private void refill(Instant now) {
        long nowNanos = toNanos(now);
        long elapsed = nowNanos - lastRefillNanos;
        if (elapsed <= 0) {
            // A clock that moved backwards must not remove tokens; treat it as no time
            // passing rather than trusting the new reading.
            lastRefillNanos = nowNanos;
            return;
        }
        tokens = Math.min(capacity, tokens + elapsed * tokensPerNano);
        lastRefillNanos = nowNanos;
    }

    private static long toNanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }
}
