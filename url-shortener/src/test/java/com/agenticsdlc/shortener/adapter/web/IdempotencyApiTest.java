package com.agenticsdlc.shortener.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agenticsdlc.shortener.support.FixedClockTestConfiguration;
import com.agenticsdlc.shortener.support.MutableClock;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** Idempotent creation via the {@code Idempotency-Key} header. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockTestConfiguration.class)
class IdempotencyApiTest {

    private static final String BODY = """
            {"url": "https://example.com/idempotent"}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("a retry with the same key returns the original link instead of a duplicate")
    void retryReturnsTheOriginal() throws Exception {
        // Without this, a client that times out and retries - the correct behaviour for a
        // client - silently creates two links and cannot tell which one the first attempt
        // returned.
        String first = postWithKey("retry-key-1").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String second = postWithKey("retry-key-1").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String firstCode = objectMapper.readTree(first).get("code").asText();
        String secondCode = objectMapper.readTree(second).get("code").asText();

        assertThat(secondCode).isEqualTo(firstCode);
    }

    @Test
    @DisplayName("the replay is 200, not 201, because nothing was created")
    void replayIsNotCreated() throws Exception {
        postWithKey("status-key").andExpect(status().isCreated());
        postWithKey("status-key").andExpect(status().isOk());
    }

    @Test
    @DisplayName("different keys create different links")
    void differentKeysCreateDifferentLinks() throws Exception {
        String a = postWithKey("key-a").andReturn().getResponse().getContentAsString();
        String b = postWithKey("key-b").andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(a).get("code").asText())
                .isNotEqualTo(objectMapper.readTree(b).get("code").asText());
    }

    @Test
    @DisplayName("omitting the header creates a new link each time")
    void withoutKeyEachRequestCreates() throws Exception {
        String a = mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String b = mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(a).get("code").asText())
                .isNotEqualTo(objectMapper.readTree(b).get("code").asText());
    }

    @Test
    @DisplayName("the replayed body is rebuilt from current state, not a frozen copy")
    void replayReflectsCurrentState() throws Exception {
        // Caching the rendered response would freeze read-time fields such as `expired` and
        // report an expired link as live.
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(LinkController.IDEMPOTENCY_HEADER, "expiring-key")
                        .content("""
                                {"url": "https://example.com/short-lived", "ttlSeconds": 60}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expired").value(false));

        ((MutableClock) clock).advance(Duration.ofSeconds(61));

        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(LinkController.IDEMPOTENCY_HEADER, "expiring-key")
                        .content("""
                                {"url": "https://example.com/short-lived", "ttlSeconds": 60}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expired").value(true));
    }

    @Test
    @DisplayName("if the link was deleted, the retry reports 404 rather than resurrecting it")
    void deletedLinkIsNotResurrected() throws Exception {
        String created = postWithKey("doomed-key").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String code = objectMapper.readTree(created).get("code").asText();

        mockMvc.perform(delete("/api/v1/links/" + code)).andExpect(status().isNoContent());

        // The honest answer: the resource really is gone.
        postWithKey("doomed-key").andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions postWithKey(String key) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/links")
                .contentType(MediaType.APPLICATION_JSON)
                .header(LinkController.IDEMPOTENCY_HEADER, key)
                .content(BODY));
    }
}
