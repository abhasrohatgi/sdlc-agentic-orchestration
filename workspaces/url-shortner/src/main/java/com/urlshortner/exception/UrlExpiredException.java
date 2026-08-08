package com.urlshortner.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Custom runtime exception thrown when accessing a shortened URL that has expired.
 */
@ResponseStatus(HttpStatus.GONE)
public class UrlExpiredException extends RuntimeException {

    private final String shortCode;

    /**
     * Constructs a new UrlExpiredException with a custom message.
     *
     * @param message descriptive error message
     */
    public UrlExpiredException(String message) {
        super(message);
        this.shortCode = null;
    }

    /**
     * Constructs a new UrlExpiredException for a specific short code with a custom message.
     *
     * @param shortCode the short code that has expired
     * @param message   descriptive error message
     */
    public UrlExpiredException(String shortCode, String message) {
        super(message);
        this.shortCode = shortCode;
    }

    /**
     * Constructs a new UrlExpiredException with a custom message and root cause.
     *
     * @param message descriptive error message
     * @param cause   underlying cause of the exception
     */
    public UrlExpiredException(String message, Throwable cause) {
        super(message, cause);
        this.shortCode = null;
    }

    /**
     * Helper factory method to create an exception for an expired short code.
     *
     * @param shortCode the short code that has expired
     * @return populated {@link UrlExpiredException}
     */
    public static UrlExpiredException forShortCode(String shortCode) {
        return new UrlExpiredException(
            shortCode,
            String.format("URL mapping has expired for short code: %s", shortCode)
        );
    }

    /**
     * Gets the short code associated with this exception.
     *
     * @return short code string, or null if not set
     */
    public String getShortCode() {
        return shortCode;
    }
}