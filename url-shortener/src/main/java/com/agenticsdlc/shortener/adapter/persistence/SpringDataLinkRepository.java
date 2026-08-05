package com.agenticsdlc.shortener.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data interface for {@link LinkEntity}.
 *
 * <p>Package-private in effect: the application depends on the
 * {@link com.agenticsdlc.shortener.port.LinkRepository} port, and {@link JpaLinkRepository}
 * is the only thing that should touch this. Keeping Spring Data out of the service layer is
 * what lets the in-memory adapter be a peer rather than a stand-in.
 */
public interface SpringDataLinkRepository extends JpaRepository<LinkEntity, String> {
}
