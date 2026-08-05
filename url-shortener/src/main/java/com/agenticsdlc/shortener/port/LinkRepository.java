package com.agenticsdlc.shortener.port;

import com.agenticsdlc.shortener.domain.ShortCode;
import com.agenticsdlc.shortener.domain.ShortLink;
import java.util.Optional;

/**
 * Storage port for short links.
 *
 * <p>Two adapters implement this - one in-memory, one JPA - and both are exercised by the
 * same abstract contract test. Two implementations passing one suite is what demonstrates
 * the abstraction actually holds; two independently written suites would demonstrate
 * nothing.
 *
 * <p>Implementations must be safe for concurrent use.
 */
public interface LinkRepository {

    /**
     * Stores a link if, and only if, its code is not already taken.
     *
     * <p>This is deliberately an atomic test-and-set rather than a {@code exists()} check
     * followed by a {@code save()}. Under concurrency the check-then-act version lets two
     * requests both observe a free alias and both write, and whichever loses silently
     * overwrites a link somebody is already using.
     *
     * @return {@code true} if stored, {@code false} if the code was already taken
     */
    boolean saveIfAbsent(ShortLink link);

    /** Finds a link by code, whether or not it has expired. Expiry is the caller's business. */
    Optional<ShortLink> findByCode(ShortCode code);

    /**
     * Removes a link.
     *
     * @return {@code true} if a link was removed, {@code false} if the code was unknown
     */
    boolean deleteByCode(ShortCode code);

    /** Whether a code is currently in use, expired or not. */
    boolean existsByCode(ShortCode code);

    /** Total number of stored links, including expired ones. Intended for metrics and tests. */
    long count();
}
