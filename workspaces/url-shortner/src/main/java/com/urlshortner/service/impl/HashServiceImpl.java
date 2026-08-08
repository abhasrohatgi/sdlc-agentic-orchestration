package com.urlshortner.service.impl;

import com.google.common.hash.Hashing;
import com.urlshortner.service.HashService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Implementation of {@link HashService} using the MurmurHash3 algorithm provided by Guava.
 * Generates non-cryptographic, high-throughput, low-collision 32-bit and 64-bit integer hashes
 * for original URLs to support deterministic short code derivation.
 */
@Service
public class HashServiceImpl implements HashService {

    private static final Logger log = LoggerFactory.getLogger(HashServiceImpl.class);
    private static final int DEFAULT_SEED = 0;

    /**
     * Computes a 32-bit MurmurHash3 hash for the specified input string using the default seed.
     *
     * @param input the raw string input to hash
     * @return 32-bit integer hash code
     */
    @Override
    public int hash32(String input) {
        return hash32(input, DEFAULT_SEED);
    }

    /**
     * Computes a 32-bit MurmurHash3 hash for the specified input string using a custom seed value.
     *
     * @param input the raw string input to hash
     * @param seed  seed value used to vary hash output
     * @return 32-bit integer hash code
     * @throws IllegalArgumentException if input is null
     */
    @Override
    public int hash32(String input, int seed) {
        if (input == null) {
            throw new IllegalArgumentException("Input string for hashing cannot be null");
        }
        return Hashing.murmur3_32_fixed(seed)
                .hashString(input, StandardCharsets.UTF_8)
                .asInt();
    }

    /**
     * Computes a 128-bit MurmurHash3 hash for the specified input string using the default seed
     * and returns the primary 64-bit portion as a long value.
     *
     * @param input the raw string input to hash
     * @return 64-bit long hash code
     */
    @Override
    public long hash64(String input) {
        return hash64(input, DEFAULT_SEED);
    }

    /**
     * Computes a 128-bit MurmurHash3 hash for the specified input string using a custom seed value
     * and returns the primary 64-bit portion as a long value.
     *
     * @param input the raw string input to hash
     * @param seed  seed value used to resolve collisions or alter output
     * @return 64-bit long hash code
     * @throws IllegalArgumentException if input is null
     */
    @Override
    public long hash64(String input, int seed) {
        if (input == null) {
            throw new IllegalArgumentException("Input string for hashing cannot be null");
        }
        return Hashing.murmur3_128(seed)
                .hashString(input, StandardCharsets.UTF_8)
                .asLong();
    }
}