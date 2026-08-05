package com.agenticsdlc.shortener.adapter.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import com.agenticsdlc.shortener.domain.ShortCode;
import java.util.HashSet;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Base62CodeGeneratorTest {

    @Test
    @DisplayName("codes are always exactly the configured length")
    void fixedLength() {
        // Zero-padding matters: without it a small draw yields a short code, and codes stop
        // being visually uniform in a way users notice.
        Base62CodeGenerator generator = new Base62CodeGenerator();

        for (int i = 0; i < 500; i++) {
            assertThat(generator.generate().value())
                    .hasSize(Base62CodeGenerator.CODE_LENGTH);
        }
    }

    @Test
    @DisplayName("a draw of zero still produces a full-length code")
    void zeroIsPadded() {
        Base62CodeGenerator generator = new Base62CodeGenerator(fixedDraw(0L));

        assertThat(generator.generate().value()).isEqualTo("0000000");
    }

    @Test
    @DisplayName("the largest valid draw encodes without overflow")
    void topOfTheSpace() {
        Base62CodeGenerator generator = new Base62CodeGenerator(fixedDraw(Base62CodeGenerator.SPACE - 1));

        assertThat(generator.generate().value())
                .hasSize(Base62CodeGenerator.CODE_LENGTH)
                .isEqualTo("zzzzzzz");
    }

    @Test
    @DisplayName("codes only ever use the base62 alphabet, so they are always valid ShortCodes")
    void alphabetIsUrlSafe() {
        Base62CodeGenerator generator = new Base62CodeGenerator();

        for (int i = 0; i < 500; i++) {
            String value = generator.generate().value();
            assertThat(value).matches("[0-9A-Za-z]{7}");
            assertThat(ShortCode.isValid(value)).isTrue();
        }
    }

    @Test
    @DisplayName("encoding is a bijection: distinct draws give distinct codes")
    void distinctDrawsGiveDistinctCodes() {
        // The uniqueness guarantee ultimately comes from the repository's test-and-set, but
        // the encoder must not collapse two different draws onto one code - that would make
        // collisions far more likely than the space size suggests.
        Set<String> seen = new HashSet<>();
        for (long draw = 0; draw < 5_000; draw++) {
            String code = new Base62CodeGenerator(fixedDraw(draw)).generate().value();
            assertThat(seen.add(code)).as("draw %d produced a duplicate code %s", draw, code)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("a seeded generator is reproducible, which is what makes tests deterministic")
    void seededIsReproducible() {
        var first = new Base62CodeGenerator(RandomGenerator.of("L64X128MixRandom"));
        assertThat(first.generate().value()).hasSize(Base62CodeGenerator.CODE_LENGTH);

        var a = new Base62CodeGenerator(new java.util.Random(42));
        var b = new Base62CodeGenerator(new java.util.Random(42));

        assertThat(a.generate()).isEqualTo(b.generate());
        assertThat(a.generate()).isEqualTo(b.generate());
    }

    @Test
    @DisplayName("draws are spread across the space rather than clustered")
    void drawsAreSpread() {
        // A weak sanity check on the default source, not a statistical test: if every code
        // shared a first character, something is badly wrong with the encoding.
        Base62CodeGenerator generator = new Base62CodeGenerator();
        Set<Character> firstChars = new HashSet<>();

        for (int i = 0; i < 500; i++) {
            firstChars.add(generator.generate().value().charAt(0));
        }

        assertThat(firstChars).hasSizeGreaterThan(20);
    }

    /** A generator whose {@code nextLong(bound)} always returns the same value. */
    private static RandomGenerator fixedDraw(long value) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return value;
            }

            @Override
            public long nextLong(long bound) {
                return value;
            }
        };
    }
}
