package com.agenticsdlc.shortener.behaviour;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agenticsdlc.shortener.support.FixedClockTestConfiguration;
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
 * A deleted link must stop redirecting immediately, through the full HTTP path.
 *
 * <h2>Why this test matters more than it looks</h2>
 *
 * <p>Read on its own this is an unremarkable CRUD assertion. Its real purpose is to be the
 * <strong>trap that a naive caching implementation springs</strong>.
 *
 * <p>The service currently ships with no cache on the redirect path. The brownfield
 * orchestration scenario asks the agent to add one "without changing behaviour". The
 * straightforward way to add a read-through cache - populate on lookup, never invalidate on
 * write - passes every unit test in the repository layer and breaks exactly this: the
 * deleted link keeps redirecting from a stale cache entry until the entry ages out.
 *
 * <p>Nothing here is seeded or contrived. This is what happens when you cache a mutable read
 * path, and it is caught by a test that was already in the suite and already passing. That
 * distinction matters: the orchestrator's gate is not detecting a planted failure, it is
 * detecting a real regression using a real test.
 *
 * <p>The assertion deliberately goes through MockMvc rather than calling the service, so
 * that a cache introduced at <em>any</em> layer - controller, service, or repository - is on
 * the path being exercised. A service-level test could be bypassed by a controller-level
 * cache.
 *
 * <p><strong>If this test starts failing after a caching change, fix the invalidation. Do
 * not relax the test.</strong>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockTestConfiguration.class)
class DeleteRemovesLinkImmediatelyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("a deleted link stops redirecting immediately, with no stale read")
    void deletedLinkStopsRedirectingImmediately() throws Exception {
        String code = createLink("https://example.com/original");

        // Warm whatever read path exists. A cache populated here is precisely what a later
        // delete must invalidate, and omitting this step would let a lazily-populated cache
        // pass by never having been filled.
        mockMvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/original"));

        mockMvc.perform(delete("/api/v1/links/" + code))
                .andExpect(status().isNoContent());

        // The redirect must be gone on the very next request - not eventually.
        mockMvc.perform(get("/" + code))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/links/" + code))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("repeated reads before deletion do not make the deletion less immediate")
    void repeatedReadsDoNotDelayDeletion() throws Exception {
        // A cache with a hit-count-based promotion policy could survive a single
        // invalidation while a hot entry stays resident. Reading several times first makes
        // that failure mode visible too.
        String code = createLink("https://example.com/hot");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/" + code)).andExpect(status().isFound());
        }

        mockMvc.perform(delete("/api/v1/links/" + code)).andExpect(status().isNoContent());

        mockMvc.perform(get("/" + code)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("deleting one link does not affect another")
    void deletionIsScopedToOneLink() throws Exception {
        // Guards the opposite mistake: invalidating too much, or keying the cache wrongly.
        String doomed = createLink("https://example.com/doomed");
        String survivor = createLink("https://example.com/survivor");

        mockMvc.perform(get("/" + doomed)).andExpect(status().isFound());
        mockMvc.perform(get("/" + survivor)).andExpect(status().isFound());

        mockMvc.perform(delete("/api/v1/links/" + doomed)).andExpect(status().isNoContent());

        mockMvc.perform(get("/" + doomed)).andExpect(status().isNotFound());
        mockMvc.perform(get("/" + survivor))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/survivor"));
    }

    private String createLink(String url) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("url", url));

        String response = mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("code").asText();
    }
}
