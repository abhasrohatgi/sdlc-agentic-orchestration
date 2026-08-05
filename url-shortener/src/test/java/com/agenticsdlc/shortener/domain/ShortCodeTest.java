package com.agenticsdlc.shortener.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ShortCodeTest {

    @ParameterizedTest
    @ValueSource(strings = {"a", "aB3xK9p", "q3-2026-report", "with_underscore", "0"})
    @DisplayName("accepts URL-safe codes")
    void acceptsValidCodes(String value) {
        assertThat(ShortCode.of(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "has space",
            "has/slash",
            "has?query",
            "has#fragment",
            "has%20encoded",
            "has.dot",
            "café"
    })
    @DisplayName("rejects characters that would need encoding or change meaning in a URL")
    void rejectsUnsafeCharacters(String value) {
        assertThatThrownBy(() -> ShortCode.of(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only letters, digits");
    }

    @Test
    @DisplayName("rejects an empty code")
    void rejectsEmpty() {
        assertThatThrownBy(() -> ShortCode.of(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between");
    }

    @Test
    @DisplayName("rejects a code longer than the maximum")
    void rejectsTooLong() {
        String tooLong = "a".repeat(ShortCode.MAX_LENGTH + 1);
        assertThatThrownBy(() -> ShortCode.of(tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between");
    }

    @Test
    @DisplayName("rejects null")
    void rejectsNull() {
        assertThatThrownBy(() -> ShortCode.of(null)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"api", "API", "Actuator", "health", "metrics"})
    @DisplayName("rejects reserved words that would shadow an application route")
    void rejectsReservedWords(String reserved) {
        // Without this, a custom alias of "api" would make /api unreachable - a failure that
        // surfaces as a routing bug long after the link was created.
        assertThatThrownBy(() -> ShortCode.of(reserved))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
    }

    @Test
    @DisplayName("isValid reports validity without throwing")
    void isValidDoesNotThrow() {
        assertThat(ShortCode.isValid("aB3xK9p")).isTrue();
        assertThat(ShortCode.isValid("has space")).isFalse();
        assertThat(ShortCode.isValid("")).isFalse();
        assertThat(ShortCode.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("codes are case-sensitive, because base62 depends on it")
    void caseSensitive() {
        // 'aB3xK9p' and 'ab3xk9p' are different codes. Folding case would shrink the code
        // space by more than half and silently alias distinct generated codes together.
        assertThat(ShortCode.of("aB3xK9p")).isNotEqualTo(ShortCode.of("ab3xk9p"));
    }

    @Test
    @DisplayName("equality is by value")
    void valueEquality() {
        assertThat(ShortCode.of("abc")).isEqualTo(ShortCode.of("abc"))
                .hasSameHashCodeAs(ShortCode.of("abc"));
    }
}
