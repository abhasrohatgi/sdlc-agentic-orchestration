package com.agenticsdlc.shortener.domain;

/** Thrown when no link exists for a code. Surfaces as HTTP 404. */
public class LinkNotFoundException extends RuntimeException {

    private final ShortCode code;

    public LinkNotFoundException(ShortCode code) {
        super("No link found for code '" + code + "'");
        this.code = code;
    }

    public ShortCode code() {
        return code;
    }
}
