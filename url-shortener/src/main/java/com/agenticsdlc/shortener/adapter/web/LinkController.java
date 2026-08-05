package com.agenticsdlc.shortener.adapter.web;

import com.agenticsdlc.shortener.application.CreateLinkCommand;
import com.agenticsdlc.shortener.application.LinkService;
import com.agenticsdlc.shortener.domain.InvalidTargetException;
import com.agenticsdlc.shortener.domain.ShortCode;
import com.agenticsdlc.shortener.domain.ShortLink;
import jakarta.validation.Valid;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Management API for short links. Redirection lives in {@link RedirectController}. */
@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

    private final LinkService linkService;
    private final String baseUrl;

    public LinkController(LinkService linkService,
                          @Value("${shortener.base-url:http://localhost:8081}") String baseUrl) {
        this.linkService = linkService;
        // Normalised once so every response is consistent regardless of how it was configured.
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * Creates a short link.
     *
     * @return 201 with a {@code Location} header pointing at the new resource
     */
    @PostMapping
    public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        ShortLink link = linkService.create(new CreateLinkCommand(
                parseTarget(request.url()),
                parseAlias(request.alias()),
                request.ttlSeconds() == null ? null : Duration.ofSeconds(request.ttlSeconds())));

        Instant now = linkService.now();
        return ResponseEntity
                .created(URI.create("/api/v1/links/" + link.code().value()))
                .body(LinkResponse.from(link, baseUrl, now));
    }

    /** Returns metadata for a link, including expired ones. */
    @GetMapping("/{code}")
    public LinkResponse get(@PathVariable String code) {
        ShortLink link = linkService.get(parseCode(code));
        return LinkResponse.from(link, baseUrl, linkService.now());
    }

    /** Deletes a link. Returns 204, or 404 if the code is unknown. */
    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        linkService.delete(parseCode(code));
        return ResponseEntity.noContent().build();
    }

    private static URI parseTarget(String raw) {
        try {
            return new URI(raw.trim());
        } catch (URISyntaxException e) {
            // Translated here so the caller gets a 400 naming the problem, rather than a
            // 500 from an unchecked URI.create failure deeper in the stack.
            throw new InvalidTargetException(URI.create("about:blank"),
                    "'" + raw + "' is not a valid URL: " + e.getReason());
        }
    }

    private static ShortCode parseAlias(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return parseCode(raw.trim());
    }

    private static ShortCode parseCode(String raw) {
        // ShortCode's constructor throws IllegalArgumentException with a specific message,
        // which ApiExceptionHandler maps to a 400.
        return new ShortCode(raw);
    }
}
