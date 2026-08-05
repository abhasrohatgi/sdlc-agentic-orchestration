package com.agenticsdlc.shortener.adapter.analytics;

import com.agenticsdlc.shortener.domain.ClickEvent;
import com.agenticsdlc.shortener.domain.LinkStats;
import com.agenticsdlc.shortener.domain.ShortCode;
import com.agenticsdlc.shortener.port.ClickStatsRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps click aggregates in memory.
 *
 * <p>Aggregates rather than raw events: storing every click would grow without bound and is
 * not needed to answer the questions this endpoint exists for. The cost is that a new
 * question ("clicks by hour") cannot be answered retroactively - accepted deliberately, and
 * the reason a real deployment would tee raw events to a warehouse alongside this.
 *
 * <h2>Bounds</h2>
 *
 * <p>Two dimensions could grow without limit and both are capped:
 * <ul>
 *   <li><strong>Days.</strong> Only {@value #RETAINED_DAYS} days of buckets are kept per
 *       link. Older buckets are pruned as new ones appear, so a link clicked for years does
 *       not accumulate an unbounded map.</li>
 *   <li><strong>Referrers.</strong> Only {@value #MAX_TRACKED_REFERRERS} distinct referrers
 *       are tracked per link. Without this, an attacker could send one click per forged
 *       {@code Referer} value and grow the map indefinitely - a memory exhaustion vector
 *       reachable by anyone who can click a link.</li>
 * </ul>
 *
 * <p>All state is lost on restart. That is a real limitation and is stated in the docs
 * rather than hidden; durable analytics would put the aggregates behind the same port with
 * a database adapter.
 *
 * <p>Thread-safe. In practice it is written by a single consumer thread and read by request
 * threads, but concurrent structures are used rather than relying on that arrangement.
 */
public class InMemoryClickStatsRepository implements ClickStatsRepository {

    static final int RETAINED_DAYS = 30;
    static final int MAX_TRACKED_REFERRERS = 50;

    /** Placeholder for a click with no {@code Referer} header, so direct traffic is visible. */
    static final String DIRECT = "(direct)";

    private final Map<ShortCode, Aggregate> aggregates = new ConcurrentHashMap<>();

    @Override
    public void apply(ClickEvent event) {
        aggregates.computeIfAbsent(event.code(), c -> new Aggregate()).add(event);
    }

    @Override
    public LinkStats statsFor(ShortCode code) {
        Aggregate aggregate = aggregates.get(code);
        return aggregate == null ? LinkStats.empty(code) : aggregate.snapshot(code);
    }

    @Override
    public void forget(ShortCode code) {
        aggregates.remove(code);
    }

    /** Discards everything. For tests. */
    public void clear() {
        aggregates.clear();
    }

    private static final class Aggregate {

        private final AtomicLong total = new AtomicLong();
        private final Map<LocalDate, AtomicLong> byDay = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> byReferrer = new ConcurrentHashMap<>();
        private volatile Instant first;
        private volatile Instant last;

        synchronized void add(ClickEvent event) {
            total.incrementAndGet();

            Instant at = event.occurredAt();
            if (first == null || at.isBefore(first)) {
                first = at;
            }
            if (last == null || at.isAfter(last)) {
                last = at;
            }

            LocalDate day = at.atZone(ZoneOffset.UTC).toLocalDate();
            byDay.computeIfAbsent(day, d -> new AtomicLong()).incrementAndGet();
            pruneOldDays(day);

            String referrer = event.referrerValue().orElse(DIRECT);
            AtomicLong counter = byReferrer.get(referrer);
            if (counter != null) {
                counter.incrementAndGet();
            } else if (byReferrer.size() < MAX_TRACKED_REFERRERS) {
                byReferrer.computeIfAbsent(referrer, r -> new AtomicLong()).incrementAndGet();
            }
            // Once the cap is reached, further distinct referrers are not tracked. The click
            // still counts toward the total, so no traffic goes missing - only its
            // attribution. Better than an unbounded map an attacker controls the keys of.
        }

        private void pruneOldDays(LocalDate newest) {
            if (byDay.size() <= RETAINED_DAYS) {
                return;
            }
            LocalDate cutoff = newest.minusDays(RETAINED_DAYS - 1L);
            byDay.keySet().removeIf(d -> d.isBefore(cutoff));
        }

        LinkStats snapshot(ShortCode code) {
            Map<LocalDate, Long> days = new java.util.TreeMap<>();
            byDay.forEach((day, count) -> days.put(day, count.get()));

            Map<String, Long> referrers = byReferrer.entrySet().stream()
                    .sorted(Comparator
                            .<Map.Entry<String, AtomicLong>>comparingLong(e -> -e.getValue().get())
                            .thenComparing(Map.Entry::getKey))
                    .collect(LinkedHashMap::new,
                            (m, e) -> m.put(e.getKey(), e.getValue().get()),
                            LinkedHashMap::putAll);

            // unmodifiableMap over the TreeMap/LinkedHashMap rather than Map.copyOf, whose
            // iteration order is unspecified - the day series must stay chronological and
            // the referrer list must stay ranked once serialised.
            return new LinkStats(code, total.get(), first, last,
                    java.util.Collections.unmodifiableMap(days),
                    java.util.Collections.unmodifiableMap(referrers));
        }
    }
}
