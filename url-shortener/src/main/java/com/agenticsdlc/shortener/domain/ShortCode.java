package com.agenticsdlc.shortener.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The public identifier of a short link - the part after the host in {@code /aB3xK9p}.
 *
 * <p>Modelled as a value object rather than a bare {@link String} so that validation happens
 * once, at construction, instead of at each of the several call sites that would otherwise
 * need to remember it. An instance of this type is always a syntactically valid code.
 *
 * @param value the code text
 */
public record ShortCode(String value) implements Comparable<ShortCode> {

    /** Longest code we accept. Generated codes are 7 characters; the rest is alias headroom. */
    public static final int MAX_LENGTH = 32;

    /** Shortest code we accept. One-character aliases are permitted but discouraged. */
    public static final int MIN_LENGTH = 1;

    /**
     * URL-safe characters only. Hyphen and underscore are allowed for readable custom
     * aliases ({@code /q3-2026-report}); everything else is alphanumeric so that a code
     * never needs percent-encoding and never changes meaning when copied out of an email.
     */
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_-]+");

    /**
     * Path segments the router owns. A custom alias matching one of these would shadow a
     * real endpoint, so it is rejected at construction rather than discovered in production.
     *
     * <p>Only entries that could otherwise pass the character check belong here. Names
     * containing a dot ({@code favicon.ico}, {@code robots.txt}) are already unreachable
     * because {@code .} is not in the allowed character set, so listing them would be dead
     * configuration that reads as protection.
     */
    private static final java.util.Set<String> RESERVED =
            java.util.Set.of("api", "actuator", "health", "metrics");

    public ShortCode {
        Objects.requireNonNull(value, "value must not be null");
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Short code must be between " + MIN_LENGTH + " and " + MAX_LENGTH
                            + " characters, got " + value.length() + ": '" + value + "'");
        }
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Short code may contain only letters, digits, '-' and '_', got: '"
                            + value + "'");
        }
        if (RESERVED.contains(value.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Short code '" + value + "' is reserved because it would shadow an "
                            + "application route.");
        }
    }

    /** Convenience factory so call sites read as {@code ShortCode.of("aB3xK9p")}. */
    public static ShortCode of(String value) {
        return new ShortCode(value);
    }

    /**
     * Whether a candidate string could be a valid code, without throwing.
     *
     * <p>Useful for request validation, where a rejection should become a 400 rather than
     * propagate as an exception.
     */
    public static boolean isValid(String candidate) {
        try {
            new ShortCode(candidate);
            return true;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    @Override
    public int compareTo(ShortCode other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
