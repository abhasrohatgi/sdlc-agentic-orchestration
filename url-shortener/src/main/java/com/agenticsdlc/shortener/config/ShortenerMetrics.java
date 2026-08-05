package com.agenticsdlc.shortener.config;

import com.agenticsdlc.shortener.adapter.analytics.AsyncClickEventSink;
import com.agenticsdlc.shortener.adapter.web.RateLimitFilter;
import com.agenticsdlc.shortener.application.IdempotencyStore;
import com.agenticsdlc.shortener.port.LinkRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Exports the internal state that would otherwise fail invisibly.
 *
 * <p>Request rate, latency and error counts already come from Spring Boot's
 * {@code http.server.requests}, so they are not duplicated here. What is registered instead
 * is the set of values that describe a <em>degraded but still-serving</em> system - the
 * failures that look like success from outside:
 *
 * <ul>
 *   <li><strong>Dropped clicks.</strong> Analytics drop under overload by design. Without
 *       this metric an analytics outage is indistinguishable from a decline in traffic,
 *       which is how a broken pipeline survives for a quarter unnoticed.</li>
 *   <li><strong>Pending clicks.</strong> A queue that is consistently non-empty means the
 *       consumer is falling behind and drops are coming.</li>
 *   <li><strong>Rate-limit rejections and tracked clients.</strong> Rejections distinguish
 *       "traffic fell" from "we turned traffic away". Tracked clients shows how close the
 *       bucket map is to its cap, beyond which the limiter fails open.</li>
 *   <li><strong>Idempotency keys retained.</strong> At the cap the guarantee silently
 *       degrades, so the approach to it must be visible.</li>
 * </ul>
 *
 * <p>Each of these corresponds to a deliberate degradation decision made elsewhere in the
 * service. A degradation nobody can observe is indistinguishable from a bug.
 */
public class ShortenerMetrics implements MeterBinder {

    private final AsyncClickEventSink clickEventSink;
    private final RateLimitFilter rateLimitFilter;
    private final IdempotencyStore idempotencyStore;
    private final LinkRepository linkRepository;

    public ShortenerMetrics(AsyncClickEventSink clickEventSink, RateLimitFilter rateLimitFilter,
                            IdempotencyStore idempotencyStore, LinkRepository linkRepository) {
        this.clickEventSink = clickEventSink;
        this.rateLimitFilter = rateLimitFilter;
        this.idempotencyStore = idempotencyStore;
        this.linkRepository = linkRepository;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("shortener.analytics.dropped", clickEventSink,
                        AsyncClickEventSink::droppedCount)
                .description("Click events discarded because the analytics queue was full")
                .register(registry);

        Gauge.builder("shortener.analytics.pending", clickEventSink,
                        AsyncClickEventSink::pendingCount)
                .description("Click events accepted but not yet aggregated")
                .register(registry);

        Gauge.builder("shortener.ratelimit.rejected", rateLimitFilter,
                        RateLimitFilter::rejectedCount)
                .description("Requests rejected for exceeding the rate limit")
                .register(registry);

        Gauge.builder("shortener.ratelimit.tracked.clients", rateLimitFilter,
                        RateLimitFilter::trackedClients)
                .description("Clients with an active rate-limit bucket")
                .register(registry);

        Gauge.builder("shortener.idempotency.keys", idempotencyStore, IdempotencyStore::size)
                .description("Idempotency keys currently retained")
                .register(registry);

        Gauge.builder("shortener.links.total", linkRepository, LinkRepository::count)
                .description("Stored links, including expired ones")
                .register(registry);
    }
}
