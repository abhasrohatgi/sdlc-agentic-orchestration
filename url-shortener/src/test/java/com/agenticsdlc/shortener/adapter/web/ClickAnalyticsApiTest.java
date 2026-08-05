package com.agenticsdlc.shortener.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agenticsdlc.shortener.adapter.analytics.AsyncClickEventSink;
import com.agenticsdlc.shortener.support.FixedClockTestConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Click analytics through the full HTTP path. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockTestConfiguration.class)
class ClickAnalyticsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AsyncClickEventSink sink;

    @Test
    @DisplayName("a new link reports zero clicks rather than 404")
    void newLinkHasEmptyStats() throws Exception {
        create("https://example.com/fresh", "stats-new");

        mockMvc.perform(get("/api/v1/links/stats-new/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("stats-new"))
                .andExpect(jsonPath("$.totalClicks").value(0))
                .andExpect(jsonPath("$.firstClickAt").doesNotExist());
    }

    @Test
    @DisplayName("redirects are counted and attributed to their referrer")
    void countsAndAttributesClicks() throws Exception {
        create("https://example.com/counted", "stats-1");

        mockMvc.perform(get("/stats-1").header(HttpHeaders.REFERER, "https://news.example"))
                .andExpect(status().isFound());
        mockMvc.perform(get("/stats-1").header(HttpHeaders.REFERER, "https://news.example"))
                .andExpect(status().isFound());
        mockMvc.perform(get("/stats-1")).andExpect(status().isFound());

        awaitAnalytics();

        mockMvc.perform(get("/api/v1/links/stats-1/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(3))
                .andExpect(jsonPath("$.firstClickAt").isNotEmpty())
                .andExpect(jsonPath("$.lastClickAt").isNotEmpty())
                .andExpect(jsonPath("$.topReferrers['https://news.example']").value(2))
                .andExpect(jsonPath("$.topReferrers['(direct)']").value(1))
                .andExpect(jsonPath("$.clicksByDay['2026-08-05']").value(3));
    }

    @Test
    @DisplayName("a failed lookup is not counted as a click")
    void doesNotCountMisses() throws Exception {
        // A 404 is not a click on anything; counting it would make the metric measure
        // lookup attempts rather than served redirects.
        create("https://example.com/real", "stats-2");
        mockMvc.perform(get("/nosuch9")).andExpect(status().isNotFound());

        awaitAnalytics();

        mockMvc.perform(get("/api/v1/links/stats-2/stats"))
                .andExpect(jsonPath("$.totalClicks").value(0));
    }

    @Test
    @DisplayName("stats for an unknown code are a 404, not an empty document")
    void unknownCodeIsNotFound() throws Exception {
        // An empty stats document would wrongly imply the link exists and has no clicks.
        mockMvc.perform(get("/api/v1/links/nosuch9/stats"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("deleting a link discards its statistics")
    void deleteForgetsStats() throws Exception {
        create("https://example.com/temp", "stats-3");
        mockMvc.perform(get("/stats-3")).andExpect(status().isFound());
        awaitAnalytics();

        mockMvc.perform(delete("/api/v1/links/stats-3")).andExpect(status().isNoContent());

        // Recreating the same alias must not inherit the previous link's history.
        create("https://example.com/reused", "stats-3");
        mockMvc.perform(get("/api/v1/links/stats-3/stats"))
                .andExpect(jsonPath("$.totalClicks").value(0));
    }

    private void awaitAnalytics() throws InterruptedException {
        // Deterministic rather than a sleep: waits for the consumer to drain the queue.
        sink.awaitQuiescence(Duration.ofSeconds(2));
    }

    private void create(String url, String alias) throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"" + url + "\", \"alias\": \"" + alias + "\"}"))
                .andExpect(status().isCreated());
    }
}
