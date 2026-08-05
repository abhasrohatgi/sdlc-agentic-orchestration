package com.agenticsdlc.shortener.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agenticsdlc.shortener.support.FixedClockTestConfiguration;
import com.agenticsdlc.shortener.support.MutableClock;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Rate limiting through the full HTTP path.
 *
 * <p>The limit is turned down to 3 per minute so the test is fast, and the clock is
 * controlled so refill can be asserted without waiting a real minute.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockTestConfiguration.class)
@TestPropertySource(properties = {
        "shortener.rate-limit.capacity=3",
        "shortener.rate-limit.refill=3",
        "shortener.rate-limit.refill-period=60s"
})
class RateLimitApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Clock clock;

    private MutableClock clock() {
        return (MutableClock) clock;
    }

    /**
     * Refills every bucket before each test.
     *
     * <p>The filter is a singleton in a Spring context that is cached across test methods,
     * so buckets carry over and one test's traffic would exhaust the next test's budget.
     * Advancing the controlled clock past a full refill period is cleaner than adding a
     * reset method to production code purely for tests, and it exercises the real refill
     * path rather than bypassing it.
     */
    @BeforeEach
    void refillBuckets() {
        clock().advance(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("requests beyond the burst get 429 with a problem detail and Retry-After")
    void rejectsBeyondBurst() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/links/whatever"))
                    .andExpect(status().isNotFound());   // allowed through; code just does not exist
        }

        mockMvc.perform(get("/api/v1/links/whatever"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.type").value(
                        "https://agenticsdlc.example/problems/rate-limit-exceeded"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    @DisplayName("Retry-After is a usable number of seconds, not zero")
    void retryAfterIsActionable() throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(get("/api/v1/links/whatever"));
        }

        String retryAfter = mockMvc.perform(get("/api/v1/links/whatever"))
                .andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getHeader("Retry-After");

        // Telling a client to retry immediately would just amplify the overload.
        assertThat(Long.parseLong(retryAfter)).isPositive();
    }

    @Test
    @DisplayName("capacity returns as the bucket refills")
    void refillsOverTime() throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(get("/api/v1/links/whatever"));
        }
        mockMvc.perform(get("/api/v1/links/whatever")).andExpect(status().isTooManyRequests());

        clock().advance(Duration.ofSeconds(30));

        mockMvc.perform(get("/api/v1/links/whatever")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("redirects are never rate limited")
    void redirectsAreExempt() throws Exception {
        // The redirect path is the service's entire purpose, and a popular link legitimately
        // receives a burst from many clients that a per-IP limit would misread as abuse.
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com/hot", "alias": "hotlink"}"""))
                .andExpect(status().isCreated());

        for (int i = 0; i < 50; i++) {
            mockMvc.perform(get("/hotlink"))
                    .andExpect(status().isFound());
        }
    }

    @Test
    @DisplayName("actuator endpoints are not rate limited, so health checks keep working")
    void actuatorIsExempt() throws Exception {
        // A health probe blocked by a rate limiter reads as an outage and can trigger a
        // restart loop under exactly the load the limiter was protecting against.
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }
    }
}
