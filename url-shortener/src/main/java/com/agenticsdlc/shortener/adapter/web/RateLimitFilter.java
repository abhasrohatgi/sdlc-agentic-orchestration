package com.agenticsdlc.shortener.adapter.web;

import com.agenticsdlc.shortener.config.ShortenerProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-client rate limiting.
 *
 * <h2>Scope: writes, not redirects</h2>
 *
 * <p>Only the management API under {@code /api/} is limited. Redirects are deliberately
 * exempt: they are the service's entire purpose, they are cheap, and a popular link
 * legitimately receives a burst of traffic from many clients that a per-IP limit would
 * misread as abuse. Creating links is the expensive, abusable operation, so that is what is
 * bounded.
 *
 * <h2>Client identity</h2>
 *
 * <p>Clients are keyed by remote address only. {@code X-Forwarded-For} is deliberately
 * <strong>not</strong> consulted: any client can set it, so trusting it would hand out a
 * fresh bucket per request - worse than no rate limiting, because it looks like protection.
 *
 * <p>The consequence is that behind a reverse proxy every request appears to come from the
 * proxy and shares one bucket. Deploying this behind a load balancer therefore requires
 * configuring Spring's {@code ForwardedHeaderFilter} together with a trusted-proxy list, so
 * that the header is honoured only from known hops. That is a deployment decision rather
 * than something this class can safely assume, so it is left explicit instead of guessed.
 *
 * <h2>Bounding the bucket map</h2>
 *
 * <p>The map is capped. Without a cap, one address per request would grow it without limit,
 * turning the rate limiter itself into the memory-exhaustion vector. When full, new clients
 * are allowed through rather than blocked: failing open keeps the service available, and a
 * full map means something already went wrong that a 429 would not fix.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    /** Beyond this many tracked clients, new ones are not given buckets. */
    private static final int MAX_TRACKED_CLIENTS = 100_000;

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong rejected = new AtomicLong();
    private final ShortenerProperties.RateLimit config;
    private final Clock clock;

    public RateLimitFilter(ShortenerProperties.RateLimit config, Clock clock) {
        this.config = config;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!config.enabled()) {
            return true;
        }
        String path = request.getRequestURI();
        // Redirects and health checks are exempt; see the class comment.
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Instant now = clock.instant();
        TokenBucket bucket = bucketFor(clientKey(request));

        if (bucket != null && !bucket.tryConsume(now)) {
            rejected.incrementAndGet();
            writeTooManyRequests(response, bucket.timeUntilNextToken(now));
            return;
        }
        chain.doFilter(request, response);
    }

    /** @return the client's bucket, or {@code null} when the map is full and we fail open */
    private TokenBucket bucketFor(String key) {
        TokenBucket existing = buckets.get(key);
        if (existing != null) {
            return existing;
        }
        if (buckets.size() >= MAX_TRACKED_CLIENTS) {
            return null;
        }
        return buckets.computeIfAbsent(key, k -> new TokenBucket(
                config.capacity(), config.refill(), config.refillPeriod(), clock.instant()));
    }

    private static String clientKey(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }

    private static void writeTooManyRequests(HttpServletResponse response, Duration retryAfter)
            throws IOException {
        long seconds = Math.max(1, retryAfter.toSeconds());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", Long.toString(seconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // Written by hand rather than through the serialiser: this path must stay cheap and
        // must not depend on message converters being reachable from a filter.
        response.getWriter().write("""
                {"type":"https://agenticsdlc.example/problems/rate-limit-exceeded",\
                "title":"Too many requests",\
                "status":429,\
                "detail":"Rate limit exceeded. Retry after %d second(s)."}"""
                .formatted(seconds));
    }

    /** Requests rejected for exceeding the limit. Exported as a metric. */
    public long rejectedCount() {
        return rejected.get();
    }

    /** Number of clients currently tracked. Exported as a metric so the cap is observable. */
    public int trackedClients() {
        return buckets.size();
    }
}
