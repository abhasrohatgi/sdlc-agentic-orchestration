package com.agenticsdlc.shortener.adapter.web;

import com.agenticsdlc.shortener.application.CreateLinkCommand;
import com.agenticsdlc.shortener.application.IdempotencyStore;
import com.agenticsdlc.shortener.application.LinkService;
import com.agenticsdlc.shortener.config.ShortenerProperties;
import com.agenticsdlc.shortener.domain.InvalidTargetException;
import com.agenticsdlc.shortener.domain.ShortCode;
import com.agenticsdlc.shortener.domain.ShortLink;
import jakarta.validation.Valid;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Management API for short links. Redirection lives in {@link RedirectController}. */
@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

    /** Header carrying a client-generated key that makes a retried create safe. */
    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final LinkService linkService;
    private final IdempotencyStore idempotencyStore;
    private final String baseUrl;

    public LinkController(LinkService linkService, IdempotencyStore idempotencyStore,
                          ShortenerProperties properties) {
        this.linkService = linkService;
        this.idempotencyStore = idempotencyStore;
        // Normalised once so every response is consistent regardless of how it was configured.
        String configured = properties.baseUrl();
        this.baseUrl = configured.endsWith("/")
                ? configured.substring(0, configured.length() - 1)
                : configured;
    }

    /**
     * Creates a short link.
     *
     * <p>Honours an optional {@code Idempotency-Key}. A retry carrying a key already seen
     * returns the link the first attempt created, rather than creating a second one. The
     * replay is a 200 rather than a 201, because nothing was created by this request - and
     * the response is rebuilt from current state, so time-dependent fields such as
     * {@code expired} stay truthful instead of replaying a frozen body.
     *
     * @return 201 with a {@code Location} header, or 200 when replaying a known key
     */
    @PostMapping
    public ResponseEntity<LinkResponse> create(
            @Valid @RequestBody CreateLinkRequest request,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey) {

        Optional<ShortCode> alreadyCreated = idempotencyStore.lookup(idempotencyKey);
        if (alreadyCreated.isPresent()) {
            // If the link has since been deleted this throws LinkNotFoundException and the
            // caller gets a 404. That is the honest answer: the resource really is gone,
            // and resurrecting it would be worse than reporting it.
            ShortLink existing = linkService.get(alreadyCreated.get());
            return ResponseEntity.ok(LinkResponse.from(existing, baseUrl, linkService.now()));
        }

        ShortLink link = linkService.create(new CreateLinkCommand(
                parseTarget(request.url()),
                parseAlias(request.alias()),
                request.ttlSeconds() == null ? null : Duration.ofSeconds(request.ttlSeconds())));

        idempotencyStore.remember(idempotencyKey, link.code());

        return ResponseEntity
                .created(URI.create("/api/v1/links/" + link.code().value()))
                .body(LinkResponse.from(link, baseUrl, linkService.now()));
    }

    /** Returns metadata for a link, including expired ones. */
    @GetMapping("/{code}")
    public LinkResponse get(@PathVariable String code) {
        ShortLink link = linkService.get(parseCode(code));
        return LinkResponse.from(link, baseUrl, linkService.now());
    }

    /** Click statistics for a link. Available for expired links too. */
    @GetMapping("/{code}/stats")
    public StatsResponse stats(@PathVariable String code) {
        return StatsResponse.from(linkService.statsFor(parseCode(code)));
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
