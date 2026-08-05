package com.agenticsdlc.shortener.config;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunable behaviour of the shortener, bound from {@code shortener.*}.
 *
 * <p>Every value has a default that is safe to run with, so the service starts with no
 * configuration at all. Records with {@link DefaultValue} give constructor binding, which
 * means the configuration object is immutable and validated once at startup rather than
 * being mutable state that could be rebound later.
 *
 * @param baseUrl    origin short links are served from, used to build {@code shortUrl}
 * @param repository which storage adapter to activate: {@code jpa} or {@code in-memory}
 */
@ConfigurationProperties(prefix = "shortener")
public record ShortenerProperties(
        @DefaultValue("http://localhost:8081") String baseUrl,
        @DefaultValue("jpa") String repository,
        @DefaultValue Analytics analytics,
        @DefaultValue Safety safety,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue Idempotency idempotency) {

    /**
     * @param queueCapacity how many clicks may be awaiting aggregation before events are
     *                      dropped. Bounded on purpose - an unbounded queue converts a
     *                      latency problem into an out-of-memory one under peak load.
     */
    public record Analytics(@DefaultValue("10000") int queueCapacity) {
    }

    /**
     * @param resolveDns    resolve target hostnames and check the resulting addresses.
     *                      Off by default: it puts DNS on the create path and is defeated by
     *                      rebinding, so it buys less than it appears to.
     * @param deniedDomains domains to refuse, matched against the host and its parents
     */
    public record Safety(
            @DefaultValue("false") boolean resolveDns,
            @DefaultValue({}) Set<String> deniedDomains) {
    }

    /**
     * @param enabled  whether to rate limit at all
     * @param capacity burst size - requests allowed instantaneously
     * @param refill   how many tokens are restored per {@code refillPeriod}
     * @param refillPeriod window over which {@code refill} tokens are restored
     */
    public record RateLimit(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("60") int capacity,
            @DefaultValue("60") int refill,
            @DefaultValue("1m") Duration refillPeriod) {
    }

    /**
     * @param ttl     how long a recorded {@code Idempotency-Key} result stays replayable
     * @param maxKeys bound on retained keys, so the store cannot be grown without limit by
     *                a client sending a fresh key on every request
     */
    public record Idempotency(
            @DefaultValue("10m") Duration ttl,
            @DefaultValue("10000") int maxKeys) {
    }
}
