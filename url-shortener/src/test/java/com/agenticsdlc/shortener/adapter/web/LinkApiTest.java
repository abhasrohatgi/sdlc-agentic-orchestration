package com.agenticsdlc.shortener.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agenticsdlc.shortener.support.FixedClockTestConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** End-to-end HTTP behaviour of the link management API and the redirect. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockTestConfiguration.class)
class LinkApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("POST /api/v1/links")
    class Create {

        @Test
        @DisplayName("returns 201 with a Location header and the full representation")
        void createsLink() throws Exception {
            mockMvc.perform(post("/api/v1/links")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"url": "https://example.com/some/page"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.code").isNotEmpty())
                    .andExpect(jsonPath("$.target").value("https://example.com/some/page"))
                    .andExpect(jsonPath("$.shortUrl").isNotEmpty())
                    .andExpect(jsonPath("$.customAlias").value(false))
                    .andExpect(jsonPath("$.expired").value(false))
                    .andExpect(jsonPath("$.expiresAt").doesNotExist());
        }

        @Test
        @DisplayName("honours a custom alias")
        void createsWithAlias() throws Exception {
            mockMvc.perform(post("/api/v1/links")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"url": "https://example.com/report", "alias": "q3-report"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value("q3-report"))
                    .andExpect(jsonPath("$.customAlias").value(true));
        }

        @Test
        @DisplayName("returns 409 with a problem detail when an alias is taken")
        void rejectsDuplicateAlias() throws Exception {
            String body = """
                    {"url": "https://example.com/first", "alias": "taken-alias"}""";
            mockMvc.perform(post("/api/v1/links")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/v1/links")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"url": "https://other.example", "alias": "taken-alias"}"""))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type").value(
                            "https://agenticsdlc.example/problems/alias-already-taken"))
                    .andExpect(jsonPath("$.title").value("Alias already taken"));
        }

        @Test
        @DisplayName("returns 400 for a non-http scheme")
        void rejectsBadScheme() throws Exception {
            mockMvc.perform(post("/api/v1/links")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"url": "javascript:alert(1)"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value(
                            "https://agenticsdlc.example/problems/invalid-target"));
        }

        @Test
        @DisplayName("returns 400 naming the field when the body fails validation")
        void rejectsMissingUrl() throws Exception {
            mockMvc.perform(post("/api/v1/links")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value(
                            org.hamcrest.Matchers.containsString("url")));
        }

        @Test
        @DisplayName("returns 400 for an alias containing characters that are unsafe in a URL")
        void rejectsUnsafeAlias() throws Exception {
            mockMvc.perform(post("/api/v1/links")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"url": "https://example.com", "alias": "not valid"}"""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 for a non-positive time to live")
        void rejectsNonPositiveTtl() throws Exception {
            mockMvc.perform(post("/api/v1/links")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"url": "https://example.com", "ttlSeconds": 0}"""))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /{code}")
    class Redirect {

        @Test
        @DisplayName("redirects with 302 and forbids caching")
        void redirects() throws Exception {
            String code = create("https://example.com/destination", "redir-1");

            mockMvc.perform(get("/" + code))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", "https://example.com/destination"))
                    // 302 plus no-store because links are deletable and expirable; a cached
                    // 301 would keep being followed after the link is gone.
                    .andExpect(header().string("Cache-Control", "no-store"));
        }

        @Test
        @DisplayName("returns 404 with a problem detail for an unknown code")
        void unknownCode() throws Exception {
            mockMvc.perform(get("/nosuch1"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value(
                            "https://agenticsdlc.example/problems/link-not-found"));
        }
    }

    @Nested
    @DisplayName("GET and DELETE /api/v1/links/{code}")
    class ManageOne {

        @Test
        @DisplayName("returns metadata for a known code")
        void getsMetadata() throws Exception {
            String code = create("https://example.com/meta", "meta-1");

            mockMvc.perform(get("/api/v1/links/" + code))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(code))
                    .andExpect(jsonPath("$.target").value("https://example.com/meta"))
                    .andExpect(jsonPath("$.createdAt").isNotEmpty());
        }

        @Test
        @DisplayName("returns 204 on delete and 404 on a second delete")
        void deleteIsNotIdempotentlySilent() throws Exception {
            String code = create("https://example.com/gone", "gone-1");

            mockMvc.perform(delete("/api/v1/links/" + code)).andExpect(status().isNoContent());
            mockMvc.perform(delete("/api/v1/links/" + code)).andExpect(status().isNotFound());
        }
    }

    private String create(String url, String alias) throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"" + url + "\", \"alias\": \"" + alias + "\"}"))
                .andExpect(status().isCreated());
        return alias;
    }
}
