package com.agenticsdlc.shortener.application;

import com.agenticsdlc.shortener.domain.AliasAlreadyTakenException;
import com.agenticsdlc.shortener.domain.InvalidTargetException;
import com.agenticsdlc.shortener.domain.LinkExpiredException;
import com.agenticsdlc.shortener.domain.LinkNotFoundException;
import com.agenticsdlc.shortener.domain.ShortCode;
import com.agenticsdlc.shortener.domain.ShortLink;
import com.agenticsdlc.shortener.port.CodeGenerator;
import com.agenticsdlc.shortener.port.LinkRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Application service for creating, resolving and removing short links.
 *
 * <p>Holds the use-case logic that is not the domain's business (retrying a lost code race)
 * and not the web layer's business (deciding that an expired link is a distinct outcome from
 * a missing one).
 *
 * <p>Framework-free by construction - a plain class with constructor injection, wired in
 * {@code ShortenerConfiguration}. That keeps it unit-testable without a Spring context.
 */
public class LinkService {

    /**
     * How many times to ask for a fresh code when a write loses a race.
     *
     * <p>The generator is collision-free by construction, so the only way a write is
     * rejected is a custom alias having claimed that exact string. That is rare enough that
     * a handful of attempts is generous, and bounded so a pathological case fails loudly
     * instead of spinning.
     */
    private static final int MAX_CODE_ATTEMPTS = 5;

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private final LinkRepository repository;
    private final CodeGenerator codeGenerator;
    private final Clock clock;

    public LinkService(LinkRepository repository, CodeGenerator codeGenerator, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.codeGenerator = Objects.requireNonNull(codeGenerator, "codeGenerator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Creates a link.
     *
     * @throws InvalidTargetException      if the target is not an absolute http(s) URL
     * @throws AliasAlreadyTakenException  if a requested custom alias is in use
     */
    public ShortLink create(CreateLinkCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        validateTarget(command.target());

        Instant now = clock.instant();
        Instant expiresAt = command.ttl().map(now::plus).orElse(null);

        if (command.customAlias() != null) {
            ShortLink link =
                    new ShortLink(command.customAlias(), command.target(), now, expiresAt, true);
            if (!repository.saveIfAbsent(link)) {
                // Reported rather than silently reassigned: a caller who asked for a
                // specific alias wants that alias, not a substitute.
                throw new AliasAlreadyTakenException(command.customAlias());
            }
            return link;
        }

        for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {
            ShortLink link =
                    new ShortLink(codeGenerator.generate(), command.target(), now, expiresAt, false);
            if (repository.saveIfAbsent(link)) {
                return link;
            }
        }
        throw new IllegalStateException(
                "Could not obtain a free short code after " + MAX_CODE_ATTEMPTS + " attempts. "
                        + "The generator is collision-free by construction, so this indicates "
                        + "either a broken sequence source or an implausible number of custom "
                        + "aliases occupying generated codes.");
    }

    /**
     * Resolves a code to its link for redirection.
     *
     * @throws LinkNotFoundException if no such code exists
     * @throws LinkExpiredException  if the link exists but has expired
     */
    public ShortLink resolve(ShortCode code) {
        Objects.requireNonNull(code, "code must not be null");
        ShortLink link = repository.findByCode(code).orElseThrow(() -> new LinkNotFoundException(code));

        Instant now = clock.instant();
        if (link.isExpired(now)) {
            // Expired links are not deleted here. Removing data as a side effect of a read
            // would make GET destructive, and would race with a concurrent read of the same
            // code. Reclamation belongs in a separate sweep.
            throw new LinkExpiredException(code, link.expiresAt());
        }
        return link;
    }

    /**
     * Returns a link's metadata, whether or not it has expired.
     *
     * <p>Unlike {@link #resolve}, this does not treat expiry as an error - an operator
     * inspecting a dead link needs to see it, and the response carries an expiry flag.
     *
     * @throws LinkNotFoundException if no such code exists
     */
    public ShortLink get(ShortCode code) {
        Objects.requireNonNull(code, "code must not be null");
        return repository.findByCode(code).orElseThrow(() -> new LinkNotFoundException(code));
    }

    /**
     * Deletes a link.
     *
     * @throws LinkNotFoundException if no such code exists
     */
    public void delete(ShortCode code) {
        Objects.requireNonNull(code, "code must not be null");
        if (!repository.deleteByCode(code)) {
            throw new LinkNotFoundException(code);
        }
    }

    /** The clock this service reads, exposed so callers can report consistent timestamps. */
    public Instant now() {
        return clock.instant();
    }

    private static void validateTarget(URI target) {
        if (!target.isAbsolute() || target.getScheme() == null) {
            throw new InvalidTargetException(target,
                    "target must be an absolute URL including a scheme");
        }
        String scheme = target.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            // Rejecting anything other than http(s) closes off javascript:, data: and file:
            // targets, which would otherwise turn every short link into a redirect-based
            // delivery mechanism for whatever the creator wanted.
            throw new InvalidTargetException(target,
                    "scheme '" + scheme + "' is not allowed; use http or https");
        }
        if (target.getHost() == null || target.getHost().isBlank()) {
            throw new InvalidTargetException(target, "target must include a host");
        }
    }
}
