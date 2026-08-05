package com.agenticsdlc.orchestrator.core.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CanonicalJsonTest {

    @Nested
    @DisplayName("determinism (the property the audit chain depends on)")
    class Determinism {

        @Test
        @DisplayName("object key order in the input does not affect the output")
        void keyOrderIsIrrelevant() {
            // This is the single most important property in this class. If it ever fails,
            // every stored audit hash becomes unverifiable.
            Map<String, Object> insertedOneWay = new LinkedHashMap<>();
            insertedOneWay.put("zebra", 1);
            insertedOneWay.put("apple", 2);
            insertedOneWay.put("mango", 3);

            Map<String, Object> insertedAnother = new LinkedHashMap<>();
            insertedAnother.put("mango", 3);
            insertedAnother.put("zebra", 1);
            insertedAnother.put("apple", 2);

            assertThat(CanonicalJson.encode(insertedOneWay))
                    .isEqualTo(CanonicalJson.encode(insertedAnother))
                    .isEqualTo("{\"apple\":2,\"mango\":3,\"zebra\":1}");
        }

        @Test
        @DisplayName("keys sort by code point, not by case-insensitive or locale order")
        void keysSortByCodePoint() {
            assertThat(CanonicalJson.encode(Map.of("B", 1, "a", 2, "A", 3)))
                    .isEqualTo("{\"A\":3,\"B\":1,\"a\":2}");
        }

        @Test
        @DisplayName("array order is preserved because it carries meaning")
        void arrayOrderIsPreserved() {
            assertThat(CanonicalJson.encode(List.of("design", "implement", "test")))
                    .isEqualTo("[\"design\",\"implement\",\"test\"]");
        }

        @Test
        @DisplayName("instants are truncated to milliseconds so the hash is machine-independent")
        void instantsAreTruncatedToMillis() {
            Instant withNanos = Instant.parse("2026-08-05T10:15:30.123456789Z");
            Instant sameMillis = Instant.parse("2026-08-05T10:15:30.123000000Z");

            assertThat(CanonicalJson.encode(withNanos))
                    .isEqualTo(CanonicalJson.encode(sameMillis))
                    .isEqualTo("\"2026-08-05T10:15:30.123Z\"");
        }

        @Test
        @DisplayName("encoding is stable across repeated calls")
        void repeatedCallsAgree() {
            Object payload = Map.of(
                    "runId", "run-7f3a",
                    "stages", List.of("REQUIREMENTS", "DESIGN"),
                    "attempt", 2);

            String first = CanonicalJson.encode(payload);
            for (int i = 0; i < 50; i++) {
                assertThat(CanonicalJson.encode(payload)).isEqualTo(first);
            }
        }
    }

    @Nested
    @DisplayName("scalars")
    class Scalars {

        @Test
        void nullEncodesAsJsonNull() {
            assertThat(CanonicalJson.encode(null)).isEqualTo("null");
        }

        @Test
        void booleans() {
            assertThat(CanonicalJson.encode(true)).isEqualTo("true");
            assertThat(CanonicalJson.encode(false)).isEqualTo("false");
        }

        @Test
        @DisplayName("all integral widths encode identically to their long value")
        void integralNumbers() {
            assertThat(CanonicalJson.encode((byte) 7)).isEqualTo("7");
            assertThat(CanonicalJson.encode((short) 7)).isEqualTo("7");
            assertThat(CanonicalJson.encode(7)).isEqualTo("7");
            assertThat(CanonicalJson.encode(7L)).isEqualTo("7");
            assertThat(CanonicalJson.encode(java.math.BigInteger.valueOf(7))).isEqualTo("7");
        }

        @Test
        @DisplayName("BigDecimal uses plain notation with trailing zeros stripped")
        void bigDecimalIsPlainAndNormalised() {
            assertThat(CanonicalJson.encode(new BigDecimal("1.500"))).isEqualTo("1.5");
            assertThat(CanonicalJson.encode(new BigDecimal("1E+3"))).isEqualTo("1000");
        }

        @Test
        void enumsEncodeByName() {
            assertThat(CanonicalJson.encode(java.time.DayOfWeek.TUESDAY)).isEqualTo("\"TUESDAY\"");
        }
    }

    @Nested
    @DisplayName("string escaping")
    class StringEscaping {

        @Test
        @DisplayName("only the shortest legal escape is used for each character")
        void shortestEscapes() {
            assertThat(CanonicalJson.encode("quote\" backslash\\ tab\t newline\n"))
                    .isEqualTo("\"quote\\\" backslash\\\\ tab\\t newline\\n\"");
        }

        @Test
        @DisplayName("other control characters use lowercase four-digit \\u escapes")
        void controlCharacters() {
            assertThat(CanonicalJson.encode("")).isEqualTo("\"\\u0001\\u001f\"");
        }

        @Test
        @DisplayName("non-ASCII is emitted literally rather than escaped, and stays stable")
        void nonAsciiIsLiteral() {
            assertThat(CanonicalJson.encode("café → 日本")).isEqualTo("\"café → 日本\"");
        }
    }

    @Nested
    @DisplayName("rejections that protect hash stability")
    class Rejections {

        @Test
        @DisplayName("double is rejected: its text form is not stable across JDK versions")
        void doubleRejected() {
            assertThatThrownBy(() -> CanonicalJson.encode(1.5d))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Use BigDecimal");
        }

        @Test
        void floatRejected() {
            assertThatThrownBy(() -> CanonicalJson.encode(1.5f))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Use BigDecimal");
        }

        @Test
        @DisplayName("a double nested deep inside a payload is still rejected")
        void nestedDoubleRejected() {
            Object payload = Map.of("metrics", Map.of("p95Latency", 12.5d));
            assertThatThrownBy(() -> CanonicalJson.encode(payload))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("unsupported types fail loudly rather than falling back to toString()")
        void unsupportedTypeRejected() {
            assertThatThrownBy(() -> CanonicalJson.encode(new Object()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot canonically encode type");
        }

        @Test
        @DisplayName("keys that collide after stringification are rejected, not silently merged")
        void collidingKeysRejected() {
            Map<Object, Object> colliding = new LinkedHashMap<>();
            colliding.put("1", "string key");
            colliding.put(1, "integer key");

            assertThatThrownBy(() -> CanonicalJson.encode(colliding))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duplicate key");
        }
    }

    @Test
    @DisplayName("nested structures encode with no insignificant whitespace")
    void nestedStructure() {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("seq", 3);
        event.put("actor", "agent:implementer");
        event.put("artifacts", List.of(Map.of("name", "LinkCache.java", "hash", "abc")));

        assertThat(CanonicalJson.encode(event)).isEqualTo(
                "{\"actor\":\"agent:implementer\","
                        + "\"artifacts\":[{\"hash\":\"abc\",\"name\":\"LinkCache.java\"}],"
                        + "\"seq\":3}");
    }
}
