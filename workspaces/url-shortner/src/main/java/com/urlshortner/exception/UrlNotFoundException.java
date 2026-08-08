package com.urlshortner.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Custom runtime exception thrown when a requested short code does not exist,
 * has been deactivated, or has expired.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class UrlNotFoundException extends RuntimeException {

    private final String shortCode;

    /**
     * Constructs a new UrlNotFoundException with a custom message.
     *
     * @param message descriptive error message
     */
    public UrlNotFoundException(String message) {
        super(message);
        this.shortCode = null;
    }

    /**
     * Constructs a new UrlNotFoundException for a specific short code with a custom message.
     *
     * @param shortCode the short code that failed resolution
     * @param message   descriptive error message
     */
    public UrlNotFoundException(String shortCode, String message) {
        super(message);
        this.shortCode = shortCode;
    }

    /**
     * Constructs a new UrlNotFoundException with a custom message and root cause.
     *
     * @param message descriptive error message
     * @param cause   underlying cause of the exception
     */
    public UrlNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.shortCode = null;
    }

    /**
     * Helper factory method to create an exception for a missing short code.
     *
     * @param shortCode the short code that could not be found
     * @return populated {@link UrlNotFoundException}
     */
    public static UrlNotFoundException forShortCode(String shortCode) {
        return new UrlNotFoundException(
            shortCode,
            String.format("URL mapping not found for short code: %s", shortCode)
        );
    }

    /**
     * Gets the short code associated with this exception, if available.
     *
     * @return short code string, or null if not provided
     */
    public String getShortCode() {
        return shortCode;
    }
}