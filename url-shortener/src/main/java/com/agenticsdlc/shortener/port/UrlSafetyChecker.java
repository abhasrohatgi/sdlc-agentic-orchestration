package com.agenticsdlc.shortener.port;

import java.net.URI;

/**
 * Decides whether a target URL is acceptable to shorten.
 *
 * <p>A separate port rather than a method on the service because "what counts as safe" is
 * policy that changes independently of the shortening use case, and because it is the seam
 * where a real threat-intelligence feed would attach.
 */
public interface UrlSafetyChecker {

    /**
     * The outcome of a safety check.
     *
     * <p>A verdict rather than a thrown exception, so that the port stays side-effect free
     * and callers can decide how to surface a rejection. The reason is intended to be shown
     * to the caller, so it must not leak internal detail such as a denylist source.
     */
    record Verdict(boolean safe, String reason) {

        private static final Verdict ALLOWED = new Verdict(true, null);

        /** The target may be shortened. */
        public static Verdict allowed() {
            return ALLOWED;
        }

        /**
         * The target is refused.
         *
         * @param reason shown to the caller, so it must not disclose internal detail such as
         *               which denylist matched
         */
        public static Verdict rejected(String reason) {
            return new Verdict(false, reason);
        }
    }

    Verdict check(URI target);
}
