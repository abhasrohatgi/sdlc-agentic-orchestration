package com.agenticsdlc.shortener.application;

import com.agenticsdlc.shortener.domain.AliasAlreadyTakenException;
import com.agenticsdlc.shortener.domain.InvalidTargetException;
import com.agenticsdlc.shortener.domain.LinkExpiredException;
import com.agenticsdlc.shortener.domain.LinkNotFoundException;
import com.agenticsdlc.shortener.domain.LinkStats;
import com.agenticsdlc.shortener.domain.ShortCode;
import com.agenticsdlc.shortener.domain.ShortLink;
import com.agenticsdlc.shortener.port.ClickStatsRepository;
import com.agenticsdlc.shortener.port.CodeGenerator;
import com.agenticsdlc.shortener.port.LinkRepository;
import com.agenticsdlc.shortener.port.UrlSafetyChecker;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

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

    private final LinkRepository repository;
    private final CodeGenerator codeGenerator;
    private final UrlSafetyChecker safetyChecker;
    private final ClickStatsRepository clickStats;
    private final Clock clock;

    public LinkService(LinkRepository repository, CodeGenerator codeGenerator,
                       UrlSafetyChecker safetyChecker, ClickStatsRepository clickStats,
                       Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.codeGenerator = Objects.requireNonNull(codeGenerator, "codeGenerator must not be null");
        this.safetyChecker = Objects.requireNonNull(safetyChecker, "safetyChecker must not be null");
        this.clickStats = Objects.requireNonNull(clickStats, "clickStats must not be null");
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
     * Deletes a link and discards its click statistics.
     *
     * <p>Statistics are dropped along with the link rather than orphaned. Keeping them would
     * leak the existence and click volume of a deleted link through the stats endpoint if a
     * code were later reissued, and would grow memory for links nobody can reach.
     *
     * @throws LinkNotFoundException if no such code exists
     */
    public void delete(ShortCode code) {
        Objects.requireNonNull(code, "code must not be null");
        if (!repository.deleteByCode(code)) {
            throw new LinkNotFoundException(code);
        }
        clickStats.forget(code);
    }

    /**
     * Click statistics for a link.
     *
     * <p>Expiry does not hide statistics: an expired link's history is still what an
     * operator needs to see.
     *
     * @throws LinkNotFoundException if no such code exists
     */
    public LinkStats statsFor(ShortCode code) {
        Objects.requireNonNull(code, "code must not be null");
        // Existence is checked first so an unknown code is a 404 rather than an empty
        // statistics document, which would imply the link exists and has no clicks.
        get(code);
        return clickStats.statsFor(code);
    }

    /** The clock this service reads, exposed so callers can report consistent timestamps. */
    public Instant now() {
        return clock.instant();
    }

    /**
     * Applies the safety policy, translating a rejection into the exception the web layer
     * knows how to render.
     *
     * <p>The policy itself lives behind {@link UrlSafetyChecker} rather than here, because
     * what counts as an unacceptable target changes independently of the shortening use
     * case, and because that port is where a threat-intelligence feed would attach.
     */
    private void validateTarget(URI target) {
        UrlSafetyChecker.Verdict verdict = safetyChecker.check(target);
        if (!verdict.safe()) {
            throw new InvalidTargetException(target, verdict.reason());
        }
    }
}
