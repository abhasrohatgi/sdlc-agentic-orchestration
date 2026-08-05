package com.agenticsdlc.shortener.adapter.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/links}.
 *
 * <p>Kept separate from {@code CreateLinkCommand} so that the wire format can change
 * without disturbing the application layer, and so that Bean Validation annotations do not
 * leak into the domain.
 *
 * <p>{@code url} is a {@link String} rather than a {@code URI} on purpose: binding directly
 * to {@code URI} would surface a malformed value as a deserialization failure with an
 * unhelpful message, whereas parsing it ourselves produces a proper 400 naming the problem.
 *
 * @param url          the target URL
 * @param alias        optional caller-chosen code
 * @param ttlSeconds   optional lifetime in seconds; omit for a link that never expires
 */
public record CreateLinkRequest(
        @NotBlank(message = "url is required")
        @Size(max = 2048, message = "url must be at most 2048 characters")
        String url,

        @Size(max = 32, message = "alias must be at most 32 characters")
        String alias,

        @Positive(message = "ttlSeconds must be positive")
        Long ttlSeconds) {
}
