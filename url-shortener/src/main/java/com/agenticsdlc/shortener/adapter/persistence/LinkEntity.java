package com.agenticsdlc.shortener.adapter.persistence;

import com.agenticsdlc.shortener.domain.ShortCode;
import com.agenticsdlc.shortener.domain.ShortLink;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.net.URI;
import java.time.Instant;
import org.springframework.data.domain.Persistable;

/**
 * JPA row for a short link.
 *
 * <p>Separate from {@link ShortLink} on purpose. The domain record is immutable, validates in
 * its constructor and knows nothing about persistence; a JPA entity needs a no-arg
 * constructor and mutable fields. Merging the two would force the domain to accept
 * temporarily invalid states so that Hibernate can populate them.
 *
 * <p>The URL is stored as text rather than as a converted {@link URI} so that a value which
 * became unparseable under a newer JDK still round-trips instead of failing the whole read.
 */
@Entity
@Table(name = "short_link")
public class LinkEntity implements Persistable<String> {

    @Id
    @Column(name = "code", nullable = false, length = ShortCode.MAX_LENGTH)
    private String code;

    /** 2048 matches the practical upper bound browsers and proxies accept for a URL. */
    @Column(name = "target", nullable = false, length = 2048)
    private String target;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Null means the link never expires. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "custom_alias", nullable = false)
    private boolean customAlias;

    /**
     * Marks an instance as not-yet-persisted.
     *
     * <p>This exists to make {@code saveIfAbsent} actually mean "if absent". The short code
     * is a caller-assigned primary key, so Spring Data's default {@code isNew()} - which
     * checks whether the id is null - reports {@code false} for every instance and routes
     * {@code save()} to {@code EntityManager.merge()}. Merge happily updates an existing
     * row, so a second claim on a taken code would silently repoint somebody else's link
     * instead of being rejected.
     *
     * <p>Implementing {@link Persistable} lets us say "this is an insert", which routes to
     * {@code persist()} and lets the primary key constraint reject the duplicate. The
     * {@link JpaLinkRepository} contract test {@code saveIfAbsentDoesNotOverwrite} is what
     * caught this; without it the bug would have surfaced as data loss under a race.
     *
     * <p>Defaults to {@code false} so that entities materialised by Hibernate on read are
     * correctly treated as existing.
     */
    @Transient
    private boolean isNew;

    /** Required by JPA. */
    protected LinkEntity() {
    }

    private LinkEntity(String code, String target, Instant createdAt, Instant expiresAt,
                       boolean customAlias, boolean isNew) {
        this.code = code;
        this.target = target;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.customAlias = customAlias;
        this.isNew = isNew;
    }

    /** Builds an entity destined for insertion. */
    static LinkEntity newFrom(ShortLink link) {
        return new LinkEntity(
                link.code().value(),
                link.target().toString(),
                link.createdAt(),
                link.expiresAt(),
                link.customAlias(),
                true);
    }

    @Override
    public String getId() {
        return code;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    ShortLink toDomain() {
        return new ShortLink(
                new ShortCode(code),
                URI.create(target),
                createdAt,
                expiresAt,
                customAlias);
    }

    public String getCode() {
        return code;
    }
}
