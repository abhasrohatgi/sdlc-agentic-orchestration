package com.urlshortner.service;

/**
 * Service interface for encoding numerical values to Base62 strings and decoding
 * Base62 strings back to long integers for URL shortening and unique identifier mapping.
 */
public interface Base62Service {

    /**
     * Encodes a non-negative 64-bit integer into a Base62 string representation.
     *
     * @param input non-negative long value to encode
     * @return Base62 encoded string representation
     * @throws IllegalArgumentException if input is negative
     */
    String encode(long input);

    /**
     * Decodes a Base62 encoded string back into its original 64-bit integer value.
     *
     * @param base62String valid Base62 string containing [0-9a-zA-Z]
     * @return original 64-bit long integer
     * @throws IllegalArgumentException if input is null, empty, or contains invalid characters
     */
    long decode(String base62String);
}