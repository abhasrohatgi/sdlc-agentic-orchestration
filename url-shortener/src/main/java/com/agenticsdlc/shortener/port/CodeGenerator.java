package com.agenticsdlc.shortener.port;

import com.agenticsdlc.shortener.domain.ShortCode;

/** Produces short codes for links whose caller did not request a custom alias. */
public interface CodeGenerator {

    /**
     * Returns the next code.
     *
     * <p>Implementations are not required to check whether the code is free - a caller may
     * still lose a race against a custom alias claiming the same string - so callers must
     * treat a rejected write as a reason to ask for another code rather than as a failure.
     */
    ShortCode generate();
}
