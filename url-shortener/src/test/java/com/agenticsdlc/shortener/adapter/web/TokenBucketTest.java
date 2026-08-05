package com.agenticsdlc.shortener.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenBucketTest {

    private static final Instant T0 = Instant.parse("2026-08-05T10:00:00Z");

    @Test
    @DisplayName("a fresh bucket allows a full burst then refuses")
    void allowsBurstThenRefuses() {
        TokenBucket bucket = new TokenBucket(5, 5, Duration.ofMinutes(1), T0);

        for (int i = 0; i < 5; i++) {
            assertThat(bucket.tryConsume(T0)).as("request %d", i + 1).isTrue();
        }
        assertThat(bucket.tryConsume(T0)).isFalse();
    }

    @Test
    @DisplayName("tokens come back gradually as time passes")
    void refillsOverTime() {
        TokenBucket bucket = new TokenBucket(60, 60, Duration.ofMinutes(1), T0);
        for (int i = 0; i < 60; i++) {
            bucket.tryConsume(T0);
        }
        assertThat(bucket.tryConsume(T0)).isFalse();

        // 60 tokens per minute is one per second.
        assertThat(bucket.tryConsume(T0.plusSeconds(1))).isTrue();
        assertThat(bucket.tryConsume(T0.plusSeconds(1))).isFalse();

        assertThat(bucket.availableTokens(T0.plusSeconds(11))).isCloseTo(10.0d,
                org.assertj.core.data.Offset.offset(0.001d));
    }

    @Test
    @DisplayName("refill never exceeds capacity, so idle time does not bank unlimited credit")
    void refillIsCappedAtCapacity() {
        TokenBucket bucket = new TokenBucket(10, 10, Duration.ofMinutes(1), T0);

        assertThat(bucket.availableTokens(T0.plus(Duration.ofDays(7)))).isEqualTo(10.0d);
    }

    @Test
    @DisplayName("there is no window boundary to exploit")
    void noBoundaryBurst() {
        // A fixed-window limiter of 10 per minute allows 20 requests across a boundary: ten
        // at 00:59.999 and ten at 01:00.000. A token bucket does not, and that difference is
        // the reason for choosing it.
        TokenBucket bucket = new TokenBucket(10, 10, Duration.ofMinutes(1), T0);

        Instant endOfWindow = T0.plusSeconds(59);
        int allowedLate = 0;
        for (int i = 0; i < 10; i++) {
            if (bucket.tryConsume(endOfWindow)) {
                allowedLate++;
            }
        }

        Instant startOfNext = T0.plusSeconds(60);
        int allowedEarly = 0;
        for (int i = 0; i < 10; i++) {
            if (bucket.tryConsume(startOfNext)) {
                allowedEarly++;
            }
        }

        // Across the boundary the total stays near the rate rather than doubling it.
        assertThat(allowedLate + allowedEarly).isLessThanOrEqualTo(11);
    }

    @Test
    @DisplayName("reports how long until the next token, for Retry-After")
    void reportsTimeUntilNextToken() {
        TokenBucket bucket = new TokenBucket(2, 2, Duration.ofSeconds(2), T0);
        bucket.tryConsume(T0);
        bucket.tryConsume(T0);

        assertThat(bucket.timeUntilNextToken(T0)).isEqualTo(Duration.ofSeconds(1));
        assertThat(bucket.timeUntilNextToken(T0.plusSeconds(1))).isZero();
    }

    @Test
    @DisplayName("a clock moving backwards does not remove tokens")
    void toleratesBackwardClock() {
        // NTP corrections and leap-second handling do move wall clocks backwards. Treating
        // negative elapsed time as a refill would compute a negative token delta and could
        // lock a client out for as long as the skew.
        TokenBucket bucket = new TokenBucket(10, 10, Duration.ofMinutes(1), T0);
        bucket.tryConsume(T0);

        double before = bucket.availableTokens(T0);
        double afterRewind = bucket.availableTokens(T0.minusSeconds(30));

        assertThat(afterRewind).isEqualTo(before);
        assertThat(bucket.tryConsume(T0.minusSeconds(30))).isTrue();
    }

    @Test
    @DisplayName("rejects nonsensical configuration at construction")
    void rejectsBadConfiguration() {
        assertThatThrownBy(() -> new TokenBucket(0, 1, Duration.ofMinutes(1), T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucket(1, 0, Duration.ofMinutes(1), T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucket(1, 1, Duration.ZERO, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
