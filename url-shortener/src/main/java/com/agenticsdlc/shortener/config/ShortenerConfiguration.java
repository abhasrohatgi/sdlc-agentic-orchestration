package com.agenticsdlc.shortener.config;

import com.agenticsdlc.shortener.adapter.analytics.AsyncClickEventSink;
import com.agenticsdlc.shortener.adapter.analytics.InMemoryClickStatsRepository;
import com.agenticsdlc.shortener.adapter.codegen.Base62CodeGenerator;
import com.agenticsdlc.shortener.adapter.persistence.InMemoryLinkRepository;
import com.agenticsdlc.shortener.adapter.persistence.JpaLinkRepository;
import com.agenticsdlc.shortener.adapter.persistence.SpringDataLinkRepository;
import com.agenticsdlc.shortener.adapter.safety.SsrfAwareUrlSafetyChecker;
import com.agenticsdlc.shortener.adapter.web.CorrelationIdFilter;
import com.agenticsdlc.shortener.adapter.web.RateLimitFilter;
import com.agenticsdlc.shortener.application.IdempotencyStore;
import com.agenticsdlc.shortener.application.LinkService;
import com.agenticsdlc.shortener.port.ClickEventSink;
import com.agenticsdlc.shortener.port.ClickStatsRepository;
import com.agenticsdlc.shortener.port.CodeGenerator;
import com.agenticsdlc.shortener.port.LinkRepository;
import com.agenticsdlc.shortener.port.UrlSafetyChecker;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Wires the hexagon.
 *
 * <p>All Spring knowledge about the application lives here. The domain, the application
 * service and the adapters are plain classes with constructor injection, which is what keeps
 * them testable without an application context and makes the port boundaries real rather
 * than annotated.
 */
@Configuration
@EnableConfigurationProperties(ShortenerProperties.class)
public class ShortenerConfiguration {

    /**
     * The system clock.
     *
     * <p>Exposed as a bean so tests can substitute a fixed one. No code in this service
     * calls {@code Instant.now()} directly; expiry behaviour must be assertable without
     * sleeping.
     */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public CodeGenerator codeGenerator() {
        return new Base62CodeGenerator();
    }

    /**
     * JPA-backed storage. The default.
     *
     * <p>Chosen as the default because it exercises the more realistic path - a real
     * transaction, a real primary-key constraint arbitrating concurrent alias claims - and
     * because H2 runs in-process, so it costs a reviewer nothing.
     */
    @Bean
    @ConditionalOnProperty(name = "shortener.repository", havingValue = "jpa", matchIfMissing = true)
    public LinkRepository jpaLinkRepository(SpringDataLinkRepository delegate) {
        return new JpaLinkRepository(delegate);
    }

    /**
     * In-process storage, selected with {@code shortener.repository=in-memory}.
     *
     * <p>A peer adapter, not a test double - it passes the same contract test as the JPA one.
     */
    @Bean
    @ConditionalOnProperty(name = "shortener.repository", havingValue = "in-memory")
    public LinkRepository inMemoryLinkRepository() {
        return new InMemoryLinkRepository();
    }

    /**
     * Target safety policy.
     *
     * <p>DNS resolution is off by default. See {@link SsrfAwareUrlSafetyChecker} for why
     * resolving at create time buys less than it appears to.
     */
    @Bean
    @ConditionalOnMissingBean
    public UrlSafetyChecker urlSafetyChecker(ShortenerProperties properties) {
        return new SsrfAwareUrlSafetyChecker(
                properties.safety().resolveDns(),
                properties.safety().deniedDomains());
    }

    @Bean
    @ConditionalOnMissingBean
    public InMemoryClickStatsRepository clickStatsRepository() {
        return new InMemoryClickStatsRepository();
    }

    /**
     * Click ingestion.
     *
     * <p>Registered as a bean so its lifecycle is managed: {@code AsyncClickEventSink}
     * implements {@link AutoCloseable}, and Spring calls {@code close()} on shutdown, which
     * drains the queue rather than losing whatever was in flight.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(ClickEventSink.class)
    public AsyncClickEventSink clickEventSink(ClickStatsRepository stats,
                                              ShortenerProperties properties) {
        return new AsyncClickEventSink(stats, properties.analytics().queueCapacity());
    }

    @Bean
    public LinkService linkService(LinkRepository repository, CodeGenerator codeGenerator,
                                   UrlSafetyChecker safetyChecker, ClickStatsRepository clickStats,
                                   Clock clock) {
        return new LinkService(repository, codeGenerator, safetyChecker, clickStats, clock);
    }

    @Bean
    public IdempotencyStore idempotencyStore(ShortenerProperties properties, Clock clock) {
        return new IdempotencyStore(
                properties.idempotency().ttl(), properties.idempotency().maxKeys(), clock);
    }

    /**
     * Correlation id, registered at the highest precedence.
     *
     * <p>Ordering is load-bearing: anything logged by a later filter - including a
     * rate-limit rejection - must already carry the id, or the requests that are most worth
     * investigating are exactly the ones that cannot be traced.
     */
    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        var registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public RateLimitFilter rateLimitFilter(ShortenerProperties properties, Clock clock) {
        return new RateLimitFilter(properties.rateLimit(), clock);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    public ShortenerMetrics shortenerMetrics(AsyncClickEventSink clickEventSink,
                                             RateLimitFilter rateLimitFilter,
                                             IdempotencyStore idempotencyStore,
                                             LinkRepository linkRepository) {
        return new ShortenerMetrics(clickEventSink, rateLimitFilter, idempotencyStore,
                linkRepository);
    }
}
