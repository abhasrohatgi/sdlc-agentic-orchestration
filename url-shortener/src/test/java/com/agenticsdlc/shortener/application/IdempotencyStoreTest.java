package com.agenticsdlc.shortener.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agenticsdlc.shortener.domain.ShortCode;
import com.agenticsdlc.shortener.support.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdempotencyStoreTest {

    private static final ShortCode CODE = ShortCode.of("aB3xK9p");

    private MutableClock clock;
    private IdempotencyStore store;

    @BeforeEach
    void setUp() {
        clock = MutableClock.at("2026-08-05T10:00:00Z");
        store = new IdempotencyStore(Duration.ofMinutes(10), 100, clock);
    }

    @Test
    @DisplayName("an unseen key returns nothing")
    void unknownKey() {
        assertThat(store.lookup("never-seen")).isEmpty();
    }

    @Test
    @DisplayName("a remembered key returns the code the original request produced")
    void remembersCode() {
        store.remember("key-1", CODE);

        assertThat(store.lookup("key-1")).contains(CODE);
    }

    @Test
    @DisplayName("keys are independent")
    void keysAreIndependent() {
        ShortCode other = ShortCode.of("other12");
        store.remember("key-1", CODE);
        store.remember("key-2", other);

        assertThat(store.lookup("key-1")).contains(CODE);
        assertThat(store.lookup("key-2")).contains(other);
    }

    @Test
    @DisplayName("a key stops being replayable once its TTL passes")
    void expiresAfterTtl() {
        store.remember("key-1", CODE);

        clock.advance(Duration.ofMinutes(9));
        assertThat(store.lookup("key-1")).contains(CODE);

        clock.advance(Duration.ofMinutes(2));
        assertThat(store.lookup("key-1")).isEmpty();
    }

    @Test
    @DisplayName("an expired key is removed on lookup rather than left to accumulate")
    void expiredKeysAreReclaimed() {
        store.remember("key-1", CODE);
        clock.advance(Duration.ofMinutes(11));

        store.lookup("key-1");

        assertThat(store.size()).isZero();
    }

    @Test
    @DisplayName("null and blank keys are ignored, so a client that omits the header still works")
    void ignoresAbsentKeys() {
        store.remember(null, CODE);
        store.remember("   ", CODE);

        assertThat(store.lookup(null)).isEmpty();
        assertThat(store.lookup("   ")).isEmpty();
        assertThat(store.size()).isZero();
    }

    @Test
    @DisplayName("at capacity, expired entries are reclaimed before giving up")
    void reclaimsBeforeRefusing() {
        IdempotencyStore small = new IdempotencyStore(Duration.ofMinutes(1), 2, clock);
        small.remember("old-1", CODE);
        small.remember("old-2", CODE);

        clock.advance(Duration.ofMinutes(2));
        small.remember("fresh", CODE);

        assertThat(small.lookup("fresh")).contains(CODE);
    }

    @Test
    @DisplayName("at capacity with nothing to reclaim, the guarantee degrades rather than failing")
    void degradesAtCapacity() {
        // The key is caller-supplied, so an unbounded store is a memory exhaustion vector.
        // Refusing the request would be worse than losing idempotency for it: the client
        // asked to create a link, and it should still get one.
        IdempotencyStore small = new IdempotencyStore(Duration.ofHours(1), 2, clock);
        small.remember("a", CODE);
        small.remember("b", CODE);
        small.remember("c", CODE);

        assertThat(small.size()).isEqualTo(2);
        assertThat(small.lookup("c")).isEmpty();
        // The earlier keys keep working; only the overflow loses its guarantee.
        assertThat(small.lookup("a")).contains(CODE);
    }

    @Test
    @DisplayName("rejects nonsensical configuration at construction")
    void rejectsBadConfiguration() {
        assertThatThrownBy(() -> new IdempotencyStore(Duration.ZERO, 10, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyStore(Duration.ofMinutes(1), 0, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
