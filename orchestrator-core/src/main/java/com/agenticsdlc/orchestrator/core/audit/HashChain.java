package com.agenticsdlc.orchestrator.core.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes the tamper-evident hash chain over the run event log.
 *
 * <p>Each event's hash covers both its own canonical payload and the hash of the event
 * before it, so altering any historical event invalidates every hash that follows it.
 *
 * <h2>What this actually protects against, and what it does not</h2>
 *
 * <p>It is worth being precise here, because "hash-chained audit log" is often claimed to
 * mean more than it delivers.
 *
 * <p><strong>Provides:</strong> integrity against post-hoc tampering with a single store,
 * and detection of accidental corruption or partial writes. Because the event log is the
 * orchestrator's <em>system of record</em> rather than a side-channel written alongside it,
 * editing the log genuinely is rewriting history - there is no separate "real" state that
 * could drift away from what the log says.
 *
 * <p><strong>Does not provide:</strong> non-repudiation. An actor who can rewrite the whole
 * log can recompute the whole chain. Mitigation is to anchor the head hash somewhere the
 * orchestrator cannot reach - this implementation writes it to a git tag in the run
 * workspace and to {@code RUN_MANIFEST.txt} in the project repository, so forgery requires
 * editing two independent stores in agreement. Genuine non-repudiation would require
 * signing with a key held outside the process (KMS or HSM) plus periodic anchoring to an
 * external append-only store; that is out of scope, and {@code AuditAnchor} is the seam
 * where it would attach.
 *
 * <p>This class is stateless and thread-safe.
 */
public final class HashChain {

    /** Hash recorded as the predecessor of the first event in a run. */
    public static final String GENESIS = "0".repeat(64);

    private HashChain() {
    }

    /**
     * Computes the hash for an event given its predecessor's hash and its payload.
     *
     * <p>The previous hash and the canonical payload are joined by a colon before hashing.
     * The separator matters: without it, a payload could be crafted to shift bytes across
     * the boundary and produce a chain that verifies against a different history.
     *
     * @param previousHash hash of the preceding event, or {@link #GENESIS} for the first
     * @param payload      the event payload; canonically encoded before hashing
     * @return lowercase hex SHA-256
     */
    public static String link(String previousHash, Object payload) {
        if (previousHash == null || previousHash.length() != 64) {
            throw new IllegalArgumentException(
                    "previousHash must be a 64-character hex SHA-256 (use GENESIS for the first "
                            + "event), got: " + previousHash);
        }
        return sha256Hex(previousHash + ":" + CanonicalJson.encode(payload));
    }

    /**
     * Verifies a contiguous chain segment.
     *
     * @param startingFrom  hash preceding the first payload; {@link #GENESIS} for a whole run
     * @param payloads      event payloads in log order
     * @param recordedHashes hashes as stored, parallel to {@code payloads}
     * @return the index of the first event whose hash does not match, or {@code -1} if the
     *         whole segment verifies
     */
    public static int findFirstBrokenLink(String startingFrom,
                                          java.util.List<?> payloads,
                                          java.util.List<String> recordedHashes) {
        if (payloads.size() != recordedHashes.size()) {
            throw new IllegalArgumentException(
                    "payloads (" + payloads.size() + ") and recordedHashes ("
                            + recordedHashes.size() + ") must be the same length");
        }
        String previous = startingFrom;
        for (int i = 0; i < payloads.size(); i++) {
            String expected = link(previous, payloads.get(i));
            if (!expected.equals(recordedHashes.get(i))) {
                return i;
            }
            previous = expected;
        }
        return -1;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS for every conformant JDK.
            throw new IllegalStateException("SHA-256 unavailable on this JVM", e);
        }
    }
}
