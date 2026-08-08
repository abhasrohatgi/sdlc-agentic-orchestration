package com.urlshortner.service;

/**
 * Service interface defining MurmurHash3 calculation operations for generating non-cryptographic,
 * high-throughput, low-collision hash values used in URL shortening workflows.
 */
public interface HashService {

    /**
     * Computes a 32-bit MurmurHash3 hash for the specified input string using the default seed.
     *
     * @param input the raw string input to hash (e.g., long original URL)
     * @return 32-bit integer hash code
     */
    int hash32(String input);

    /**
     * Computes a 32-bit MurmurHash3 hash for the specified input string using a custom seed value.
     * Custom seeds allow collision resolution and salt variations for identical URLs.
     *
     * @param input the raw string input to hash
     * @param seed  seed value used to vary hash output
     * @return 32-bit integer hash code
     */
    int hash32(String input, int seed);

    /**
     * Computes a 128-bit MurmurHash3 hash for the specified input string and returns the primary
     * 64-bit portion as a long value to lower collision probability in large-scale URL generation.
     *
     * @param input the raw string input to hash
     * @return 64-bit long hash code
     */
    long hash64(String input);

    /**
     * Computes a 128-bit MurmurHash3 hash for the specified input string using a custom seed value
     * and returns the primary 64-bit portion as a long value.
     *
     * @param input the raw string input to hash
     * @param seed  seed value used to resolve collisions or alter output
     * @return 64-bit long hash code
     */
    long hash64(String input, int seed);
}