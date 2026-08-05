package com.agenticsdlc.shortener.domain;

import java.net.URI;

/** Thrown when a target URL is not acceptable. Surfaces as HTTP 400. */
public class InvalidTargetException extends RuntimeException {

    private final URI target;

    public InvalidTargetException(URI target, String reason) {
        super("Invalid target '" + target + "': " + reason);
        this.target = target;
    }

    public URI target() {
        return target;
    }
}
