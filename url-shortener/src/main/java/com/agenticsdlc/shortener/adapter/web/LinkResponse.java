package com.agenticsdlc.shortener.adapter.web;

import com.agenticsdlc.shortener.domain.ShortLink;
import java.time.Instant;

/**
 * Representation of a short link returned by the API.
 *
 * @param code        the short code
 * @param shortUrl    the full URL a user would share
 * @param target      where it redirects to
 * @param createdAt   creation timestamp
 * @param expiresAt   expiry timestamp, or {@code null} if the link never expires
 * @param customAlias whether the code was caller-chosen
 * @param expired     whether the link has already expired, evaluated at response time
 */
public record LinkResponse(
        String code,
        String shortUrl,
        String target,
        Instant createdAt,
        Instant expiresAt,
        boolean customAlias,
        boolean expired) {

    /**
     * @param baseUrl origin the short link is served from, with no trailing slash
     * @param now     instant to evaluate expiry against, so the flag agrees with what a
     *                redirect would do at the same moment
     */
    public static LinkResponse from(ShortLink link, String baseUrl, Instant now) {
        return new LinkResponse(
                link.code().value(),
                baseUrl + "/" + link.code().value(),
                link.target().toString(),
                link.createdAt(),
                link.expiresAt(),
                link.customAlias(),
                link.isExpired(now));
    }
}
