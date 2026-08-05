package com.agenticsdlc.shortener.behaviour;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agenticsdlc.shortener.support.FixedClockTestConfiguration;
import com.agenticsdlc.shortener.support.MutableClock;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * An expired link must stop redirecting the moment it expires, through the full HTTP path.
 *
 * <h2>Why this test matters more than it looks</h2>
 *
 * <p>Like {@link DeleteRemovesLinkImmediatelyTest}, this is the <strong>trap that a naive
 * caching implementation springs</strong> during the brownfield orchestration scenario.
 *
 * <p>Expiry is a time-dependent property of a cached value, and a cache that stores the
 * resolved target without also honouring the link's expiry will keep serving a dead link for
 * as long as the entry survives. The failure is especially easy to introduce because a cache
 * with its own TTL <em>looks</em> like it handles expiry - until the cache TTL and the link
 * TTL disagree, which they will.
 *
 * <p>The clock is controlled rather than slept on, so this runs in milliseconds and cannot
 * be flaky. Nothing about the failure is planted: it is the ordinary consequence of caching
 * a value whose validity depends on time.
 *
 * <p><strong>If this test starts failing after a caching change, fix the cache. Do not relax
 * the test.</strong>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockTestConfiguration.class)
class ExpiredLinkIsNotServedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    private MutableClock clock() {
        return (MutableClock) clock;
    }

    @Test
    @DisplayName("a link stops redirecting the moment it expires")
    void expiredLinkStopsRedirecting() throws Exception {
        String code = createLink("https://example.com/temporary", 3600);

        // Warm the read path while the link is still live, so a lazily-populated cache is
        // actually populated before expiry becomes relevant.
        mockMvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/temporary"));

        clock().advance(Duration.ofSeconds(3601));

        // 410 Gone, not 302 and not 404: the link was real and is now expired.
        mockMvc.perform(get("/" + code)).andExpect(status().isGone());
    }

    @Test
    @DisplayName("expiry is inclusive: the link is gone exactly at its expiry instant")
    void goneExactlyAtExpiry() throws Exception {
        String code = createLink("https://example.com/boundary", 60);

        mockMvc.perform(get("/" + code)).andExpect(status().isFound());

        clock().advance(Duration.ofSeconds(59));
        mockMvc.perform(get("/" + code)).andExpect(status().isFound());

        clock().advance(Duration.ofSeconds(1));
        mockMvc.perform(get("/" + code)).andExpect(status().isGone());
    }

    @Test
    @DisplayName("an expired link is still visible through the metadata endpoint")
    void metadataStillAvailableAfterExpiry() throws Exception {
        // Expiry stops the redirect; it does not erase the record. An operator debugging a
        // dead link needs to be able to see that it expired rather than getting a 404 that
        // suggests it never existed.
        String code = createLink("https://example.com/temporary", 60);
        clock().advance(Duration.ofSeconds(61));

        mockMvc.perform(get("/api/v1/links/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expired").value(true))
                .andExpect(jsonPath("$.target").value("https://example.com/temporary"));
    }

    @Test
    @DisplayName("a link without a time to live never expires")
    void permanentLinkNeverExpires() throws Exception {
        String code = createLink("https://example.com/forever", null);

        clock().advance(Duration.ofDays(3650));

        mockMvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/forever"));
    }

    @Test
    @DisplayName("one link expiring does not affect another with a longer lifetime")
    void expiryIsScopedToOneLink() throws Exception {
        String shortLived = createLink("https://example.com/short", 60);
        String longLived = createLink("https://example.com/long", 86400);

        mockMvc.perform(get("/" + shortLived)).andExpect(status().isFound());
        mockMvc.perform(get("/" + longLived)).andExpect(status().isFound());

        clock().advance(Duration.ofSeconds(61));

        mockMvc.perform(get("/" + shortLived)).andExpect(status().isGone());
        mockMvc.perform(get("/" + longLived)).andExpect(status().isFound());
    }

    private String createLink(String url, Integer ttlSeconds) throws Exception {
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("url", url);
        if (ttlSeconds != null) {
            payload.put("ttlSeconds", ttlSeconds);
        }

        String response = mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("code").asText();
    }
}
