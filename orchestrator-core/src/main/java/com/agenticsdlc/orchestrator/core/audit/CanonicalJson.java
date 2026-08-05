package com.agenticsdlc.orchestrator.core.audit;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * Deterministic JSON encoder used to compute audit-log hashes.
 *
 * <p>This exists instead of reaching for Jackson, and that is a deliberate decision worth
 * stating plainly: the hash chain in {@link HashChain} is only meaningful if the byte
 * sequence being hashed is stable <em>forever</em>. A general-purpose serializer's output is
 * not a stable contract - key order can depend on reflection order, number formatting can
 * change between versions, and a dependency upgrade would silently invalidate every hash
 * already stored in the event log. That failure would be invisible until someone tried to
 * verify an old run, which is exactly when an audit log needs to work.
 *
 * <p>The canonical form is defined by four rules:
 * <ol>
 *   <li>Object keys are sorted by Unicode code point, ascending.</li>
 *   <li>No insignificant whitespace.</li>
 *   <li>Strings use the shortest legal escape for each character.</li>
 *   <li>Numbers are exact. Binary floating point is rejected outright (see below).</li>
 * </ol>
 *
 * <p><strong>On rejecting {@code double} and {@code float}:</strong> there is no formatting
 * of a binary floating point value that is both round-trippable and stable across JDK
 * versions, so allowing them would reintroduce precisely the drift this class exists to
 * prevent. Callers that need fractional values use {@link BigDecimal}. In practice the
 * orchestrator's payloads are identifiers, timestamps, counts and durations, so this
 * constraint has cost us nothing.
 *
 * <p>{@link Instant} is truncated to milliseconds before encoding. Nanosecond precision
 * varies by platform and clock source, so preserving it would make a hash depend on which
 * machine produced it.
 *
 * <p>This class is stateless and thread-safe.
 */
public final class CanonicalJson {

    private CanonicalJson() {
    }

    /**
     * Encodes a value to its canonical JSON representation.
     *
     * <p>Supported types: {@code null}, {@link Boolean}, {@link String}, {@link Instant},
     * {@link Enum}, integral numbers ({@link Byte}, {@link Short}, {@link Integer},
     * {@link Long}, {@link BigInteger}), {@link BigDecimal}, {@link Map} with string-like
     * keys, and {@link Collection}.
     *
     * @throws IllegalArgumentException if the value contains an unsupported type
     */
    public static String encode(Object value) {
        StringBuilder out = new StringBuilder(256);
        write(value, out);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out) {
        switch (value) {
            case null -> out.append("null");
            case Boolean b -> out.append(b ? "true" : "false");
            case String s -> writeString(s, out);
            case Character c -> writeString(c.toString(), out);
            case Enum<?> e -> writeString(e.name(), out);
            case Instant i -> writeString(i.truncatedTo(ChronoUnit.MILLIS).toString(), out);
            case Double d -> throw floatingPointRejected(d);
            case Float f -> throw floatingPointRejected(f);
            case BigDecimal d -> out.append(d.stripTrailingZeros().toPlainString());
            case Byte n -> out.append(n.longValue());
            case Short n -> out.append(n.longValue());
            case Integer n -> out.append(n.longValue());
            case Long n -> out.append(n.longValue());
            case BigInteger n -> out.append(n.toString());
            case Map<?, ?> m -> writeObject(m, out);
            case Collection<?> c -> writeArray(c, out);
            default -> throw new IllegalArgumentException(
                    "Cannot canonically encode type " + value.getClass().getName()
                            + ". Supported: null, Boolean, String, Character, Enum, Instant, "
                            + "integral numbers, BigDecimal, Map, Collection.");
        }
    }

    private static IllegalArgumentException floatingPointRejected(Number n) {
        return new IllegalArgumentException(
                "Binary floating point (" + n.getClass().getSimpleName() + ": " + n + ") is not "
                        + "canonically encodable - its text form is not stable across JDK versions, "
                        + "which would silently invalidate stored audit hashes. Use BigDecimal.");
    }

    private static void writeObject(Map<?, ?> map, StringBuilder out) {
        // TreeMap gives us code-point ordering on the keys, which is the whole point.
        TreeMap<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            Object rawKey = e.getKey();
            if (rawKey == null) {
                throw new IllegalArgumentException("Null keys cannot be canonically ordered.");
            }
            String key = rawKey instanceof Enum<?> en ? en.name() : rawKey.toString();
            if (sorted.putIfAbsent(key, e.getValue()) != null) {
                // Two distinct keys stringifying to the same thing would make the encoding
                // depend on iteration order. Fail rather than pick a winner.
                throw new IllegalArgumentException("Duplicate key after stringification: " + key);
            }
        }

        out.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : sorted.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(e.getKey(), out);
            out.append(':');
            write(e.getValue(), out);
        }
        out.append('}');
    }

    private static void writeArray(Collection<?> values, StringBuilder out) {
        // Array order is significant and is preserved as given - it carries meaning
        // (execution order, dependency order) that sorting would destroy.
        out.append('[');
        boolean first = true;
        for (Object v : values) {
            if (!first) {
                out.append(',');
            }
            first = false;
            write(v, out);
        }
        out.append(']');
    }

    private static void writeString(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        // Lowercase hex, always four digits - one spelling, not two.
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
