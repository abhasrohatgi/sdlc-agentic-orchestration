package com.urlshortner.service.impl;

import com.urlshortner.service.Base62Service;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * High-performance implementation of {@link Base62Service} providing bi-directional
 * encoding and decoding between 64-bit non-negative long integers and Base62 strings.
 * Uses character mapping array lookup for fast alphanumeric index resolution.
 */
@Service
public class Base62ServiceImpl implements Base62Service {

    private static final String BASE62_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = 62;
    private static final char[] DIGITS = BASE62_ALPHABET.toCharArray();
    private static final int[] CHAR_TO_INDEX = new int[256];

    static {
        Arrays.fill(CHAR_TO_INDEX, -1);
        for (int i = 0; i < DIGITS.length; i++) {
            CHAR_TO_INDEX[DIGITS[i]] = i;
        }
    }

    /**
     * Encodes a non-negative 64-bit long integer into a Base62 string representation.
     *
     * @param input non-negative long value to encode
     * @return Base62 encoded string representation
     * @throws IllegalArgumentException if input is negative
     */
    @Override
    public String encode(long input) {
        if (input < 0) {
            throw new IllegalArgumentException("Input must be a non-negative integer for Base62 encoding: " + input);
        }
        if (input == 0) {
            return "0";
        }

        StringBuilder sb = new StringBuilder(11);
        long value = input;
        while (value > 0) {
            int remainder = (int) (value % BASE);
            sb.append(DIGITS[remainder]);
            value /= BASE;
        }

        return sb.reverse().toString();
    }

    /**
     * Decodes a Base62 encoded string back into its original 64-bit long integer value.
     *
     * @param base62String valid Base62 string containing characters [0-9a-zA-Z]
     * @return original 64-bit long integer
     * @throws IllegalArgumentException if input is null, empty, contains invalid characters, or overflows long
     */
    @Override
    public long decode(String base62String) {
        if (base62String == null || base62String.isEmpty()) {
            throw new IllegalArgumentException("Input Base62 string must not be null or empty");
        }

        long result = 0;
        for (int i = 0; i < base62String.length(); i++) {
            char c = base62String.charAt(i);
            if (c >= 256 || CHAR_TO_INDEX[c] == -1) {
                throw new IllegalArgumentException(
                    String.format("Invalid Base62 character '%c' in string: %s", c, base62String)
                );
            }

            int digitValue = CHAR_TO_INDEX[c];
            if (result > (Long.MAX_VALUE - digitValue) / BASE) {
                throw new IllegalArgumentException("Base62 string overflows 64-bit long limits: " + base62String);
            }

            result = result * BASE + digitValue;
        }

        return result;
    }
}