package com.agenticsdlc.shortener.application;

import com.agenticsdlc.shortener.domain.ShortCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers which short code an {@code Idempotency-Key} produced, so a retried create
 * returns the original link instead of making a second one.
 *
 * <h2>The problem this solves</h2>
 *
 * <p>Creating a link is not naturally idempotent: the same request sent twice produces two
 * links with different codes. A client that times out and retries - which is the correct
 * thing for a client to do - therefore silently creates a duplicate, and has no way to
 * discover which of the two the first attempt returned.
 *
 * <h2>Why the key maps to a code rather than a cached response</h2>
 *
 * <p>Storing the rendered response would freeze fields that are computed at read time, most
 * obviously {@code expired}. Replaying a stale body would report a link as live after it had
 * expired. Storing only the code and re-reading the link keeps the replayed response
 * truthful.
 *
 * <p>Consequence, stated rather than hidden: if the link is deleted between the original
 * request and the retry, the retry gets a 404 rather than a replay. That is the honest
 * answer - the resource genuinely is gone - and is preferable to resurrecting it.
 *
 * <h2>Bounds</h2>
 *
 * <p>Entries expire after a TTL and the map is capped. Both matter because the key is
 * caller-supplied: without them, a client sending a fresh key per request grows the store
 * without limit. At the cap, the store stops accepting new keys and creates proceed
 * non-idempotently rather than being rejected - degrading the guarantee is better than
 * refusing service, and the condition is exported as a metric.
 *
 * <p>State is in memory, so idempotency does not survive a restart or span instances. A
 * shared store would be needed for either; the port boundary is this class.
 */
public class IdempotencyStore {

    private record Entry(ShortCode code, Instant expiresAt) {
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final int maxKeys;
    private final Clock clock;

    public IdempotencyStore(Duration ttl, int maxKeys, Clock clock) {
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
        this.maxKeys = maxKeys;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive, got " + ttl);
        }
        if (maxKeys < 1) {
            throw new IllegalArgumentException("maxKeys must be positive, got " + maxKeys);
        }
    }

    /** The code a previous request with this key produced, if still remembered. */
    public Optional<ShortCode> lookup(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(entry.expiresAt())) {
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.code());
    }

    /** Records the outcome of a create, unless the store is at capacity. */
    public void remember(String key, ShortCode code) {
        if (key == null || key.isBlank()) {
            return;
        }
        Instant now = clock.instant();
        if (entries.size() >= maxKeys) {
            evictExpired(now);
            if (entries.size() >= maxKeys) {
                // Degrade the guarantee rather than reject the request.
                return;
            }
        }
        entries.put(key, new Entry(code, now.plus(ttl)));
    }

    /** Number of keys currently retained. Exported as a metric so the cap is observable. */
    public int size() {
        return entries.size();
    }

    private void evictExpired(Instant now) {
        entries.values().removeIf(e -> !now.isBefore(e.expiresAt()));
    }
}
