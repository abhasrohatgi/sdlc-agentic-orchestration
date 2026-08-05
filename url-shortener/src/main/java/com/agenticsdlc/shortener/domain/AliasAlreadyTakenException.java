package com.agenticsdlc.shortener.domain;

/** Thrown when a requested custom alias is already in use. Surfaces as HTTP 409 Conflict. */
public class AliasAlreadyTakenException extends RuntimeException {

    private final ShortCode code;

    public AliasAlreadyTakenException(ShortCode code) {
        super("Alias '" + code + "' is already taken");
        this.code = code;
    }

    public ShortCode code() {
        return code;
    }
}
