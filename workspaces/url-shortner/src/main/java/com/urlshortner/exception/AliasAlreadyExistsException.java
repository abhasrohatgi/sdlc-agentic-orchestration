package com.urlshortner.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Custom runtime exception thrown when a requested custom short code alias is already in use.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class AliasAlreadyExistsException extends RuntimeException {

    private final String alias;

    /**
     * Constructs a new AliasAlreadyExistsException with a custom message.
     *
     * @param message descriptive error message
     */
    public AliasAlreadyExistsException(String message) {
        super(message);
        this.alias = null;
    }

    /**
     * Constructs a new AliasAlreadyExistsException for a specific alias with a custom message.
     *
     * @param alias   the custom alias that is already taken
     * @param message descriptive error message
     */
    public AliasAlreadyExistsException(String alias, String message) {
        super(message);
        this.alias = alias;
    }

    /**
     * Constructs a new AliasAlreadyExistsException with a custom message and root cause.
     *
     * @param message descriptive error message
     * @param cause   underlying cause of the exception
     */
    public AliasAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
        this.alias = null;
    }

    /**
     * Helper factory method to create an exception for a conflicting custom alias.
     *
     * @param alias the custom alias that already exists
     * @return populated {@link AliasAlreadyExistsException}
     */
    public static AliasAlreadyExistsException forAlias(String alias) {
        return new AliasAlreadyExistsException(
            alias,
            String.format("Custom alias '%s' is already in use", alias)
        );
    }

    /**
     * Gets the custom alias associated with this exception.
     *
     * @return the conflicting alias, or null if not specified
     */
    public String getAlias() {
        return alias;
    }
}