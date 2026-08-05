package com.agenticsdlc.shortener.adapter.codegen;

import com.agenticsdlc.shortener.domain.ShortCode;
import com.agenticsdlc.shortener.port.CodeGenerator;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Generates fixed-length base62 codes by drawing uniformly from the 7-character code space.
 *
 * <h2>Why random rather than a counter</h2>
 *
 * <p>A monotonic sequence is collision-free by construction, which is genuinely attractive.
 * It was the first design here and was rejected for two reasons:
 *
 * <ol>
 *   <li><strong>Sequence state has to be durable and coordinated.</strong> The counter must
 *       survive restart and must not be issued twice across instances. That means either a
 *       database sequence - a round trip on the create path and a hard coupling between the
 *       generator and whichever storage adapter is active - or an in-memory counter seeded
 *       on startup, which is only correct until two instances run at once. Neither cost is
 *       worth paying for a property we can get another way.</li>
 *   <li><strong>Sequential codes leak.</strong> Emitting {@code base62(n)} makes every link
 *       enumerable from any other, and makes the total number of links ever created readable
 *       off a single code. A multiplicative permutation hides the adjacency but is trivially
 *       invertible by anyone who reads the source, so it obscures rather than protects.</li>
 * </ol>
 *
 * <p>Drawing at random removes the durable state entirely, and uniqueness is enforced where
 * it can actually be enforced atomically: {@code LinkRepository.saveIfAbsent} is a
 * test-and-set, and {@code LinkService} retries with a fresh code when a write loses. So
 * uniqueness is <em>guaranteed by the write path</em> rather than assumed from the
 * generator - which is a stronger position, because it also covers a generated code
 * colliding with a custom alias, something no counter scheme handles.
 *
 * <h2>Collision probability</h2>
 *
 * <p>The space is 62^7, about 3.5 trillion. At one million stored links the chance that any
 * single draw collides is roughly 1 in 3.5 million, and the bounded retry absorbs it. If the
 * table ever approached a meaningful fraction of the space, {@link #CODE_LENGTH} should be
 * raised rather than the retry budget.
 *
 * <h2>Unguessability</h2>
 *
 * <p>The default source is {@link SecureRandom}, so codes are not predictable from one
 * another. This still is not an access control - a short link is a bearer token roughly as
 * strong as its 41 bits of entropy, and anything genuinely sensitive needs authentication on
 * the redirect rather than a longer code.
 *
 * <p>Thread-safe when the supplied {@link RandomGenerator} is; {@link SecureRandom} is.
 */
public final class Base62CodeGenerator implements CodeGenerator {

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private static final int RADIX = 62;

    /** Every generated code is exactly this long, so codes are visually uniform. */
    public static final int CODE_LENGTH = 7;

    /** 62^7 - the size of the code space. */
    static final long SPACE = 3_521_614_606_208L;

    private final RandomGenerator random;

    /** Uses {@link SecureRandom}. This is the production constructor. */
    public Base62CodeGenerator() {
        this(new SecureRandom());
    }

    /**
     * @param random source of randomness; pass a seeded generator to make tests deterministic
     */
    public Base62CodeGenerator(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    @Override
    public ShortCode generate() {
        return new ShortCode(encode(random.nextLong(SPACE)));
    }

    /** Fixed-width base62 encoding, most significant digit first, zero-padded. */
    private static String encode(long value) {
        char[] out = new char[CODE_LENGTH];
        long remaining = value;
        for (int i = CODE_LENGTH - 1; i >= 0; i--) {
            out[i] = ALPHABET.charAt((int) (remaining % RADIX));
            remaining /= RADIX;
        }
        return new String(out);
    }
}
