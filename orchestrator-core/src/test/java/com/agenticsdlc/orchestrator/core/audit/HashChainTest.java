package com.agenticsdlc.orchestrator.core.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HashChainTest {

    private static final List<Map<String, Object>> THREE_EVENTS = List.of(
            Map.of("seq", 0, "type", "RunStarted", "scenario", "brownfield"),
            Map.of("seq", 1, "type", "StageCompleted", "stage", "REQUIREMENTS"),
            Map.of("seq", 2, "type", "ApprovalGranted", "actor", "abhas"));

    private static List<String> chainOver(List<? extends Object> payloads) {
        List<String> hashes = new ArrayList<>();
        String previous = HashChain.GENESIS;
        for (Object p : payloads) {
            previous = HashChain.link(previous, p);
            hashes.add(previous);
        }
        return hashes;
    }

    @Test
    @DisplayName("a hash is 64 lowercase hex characters")
    void hashShape() {
        assertThat(HashChain.link(HashChain.GENESIS, Map.of("a", 1)))
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("the same predecessor and payload always produce the same hash")
    void deterministic() {
        String a = HashChain.link(HashChain.GENESIS, THREE_EVENTS.get(0));
        String b = HashChain.link(HashChain.GENESIS, THREE_EVENTS.get(0));
        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("the same payload under a different predecessor produces a different hash")
    void predecessorIsPartOfTheHash() {
        String underGenesis = HashChain.link(HashChain.GENESIS, THREE_EVENTS.get(1));
        String underOther = HashChain.link("f".repeat(64), THREE_EVENTS.get(1));
        assertThat(underGenesis).isNotEqualTo(underOther);
    }

    @Test
    @DisplayName("an untampered chain verifies end to end")
    void intactChainVerifies() {
        List<String> hashes = chainOver(THREE_EVENTS);

        assertThat(HashChain.findFirstBrokenLink(HashChain.GENESIS, THREE_EVENTS, hashes))
                .isEqualTo(-1);
    }

    @Test
    @DisplayName("editing an event's payload is detected at that event")
    void tamperedPayloadIsDetected() {
        List<String> hashes = chainOver(THREE_EVENTS);

        // Someone rewrites who approved the run, leaving the stored hashes untouched.
        List<Map<String, Object>> tampered = new ArrayList<>(THREE_EVENTS);
        tampered.set(2, Map.of("seq", 2, "type", "ApprovalGranted", "actor", "someone-else"));

        assertThat(HashChain.findFirstBrokenLink(HashChain.GENESIS, tampered, hashes))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("editing an early event invalidates it and everything after it")
    void tamperingEarlyBreaksTheRest() {
        List<String> hashes = chainOver(THREE_EVENTS);

        List<Map<String, Object>> tampered = new ArrayList<>(THREE_EVENTS);
        tampered.set(0, Map.of("seq", 0, "type", "RunStarted", "scenario", "greenfield"));

        // Detected at the first altered event...
        assertThat(HashChain.findFirstBrokenLink(HashChain.GENESIS, tampered, hashes)).isZero();

        // ...and recomputing forward from the edit yields an entirely different chain,
        // which is what makes a quiet single-event rewrite impossible.
        assertThat(chainOver(tampered)).isNotEqualTo(hashes);
    }

    @Test
    @DisplayName("deleting an event from the middle is detected")
    void deletionIsDetected() {
        List<String> hashes = chainOver(THREE_EVENTS);

        List<Map<String, Object>> withoutMiddle = List.of(THREE_EVENTS.get(0), THREE_EVENTS.get(2));
        List<String> hashesWithoutMiddle = List.of(hashes.get(0), hashes.get(2));

        assertThat(HashChain.findFirstBrokenLink(
                HashChain.GENESIS, withoutMiddle, hashesWithoutMiddle)).isEqualTo(1);
    }

    @Test
    @DisplayName("reordering two events is detected")
    void reorderingIsDetected() {
        List<String> hashes = chainOver(THREE_EVENTS);

        List<Map<String, Object>> swapped =
                List.of(THREE_EVENTS.get(1), THREE_EVENTS.get(0), THREE_EVENTS.get(2));

        assertThat(HashChain.findFirstBrokenLink(HashChain.GENESIS, swapped, hashes)).isZero();
    }

    @Test
    @DisplayName("the colon separator prevents payload bytes from shifting across the boundary")
    void separatorPreventsBoundaryConfusion() {
        // Without a separator, hashing (prev + payload) would let a crafted payload
        // reproduce the concatenation of a different (prev, payload) pair.
        String prevA = "a".repeat(64);
        String prevB = "a".repeat(63) + "b";

        assertThat(HashChain.link(prevA, "b:x")).isNotEqualTo(HashChain.link(prevB, ":x"));
    }

    @Test
    @DisplayName("a malformed predecessor hash is rejected rather than silently accepted")
    void malformedPredecessorRejected() {
        assertThatThrownBy(() -> HashChain.link("too-short", Map.of("a", 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64-character hex");

        assertThatThrownBy(() -> HashChain.link(null, Map.of("a", 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("mismatched payload and hash list lengths are rejected")
    void mismatchedLengthsRejected() {
        assertThatThrownBy(() -> HashChain.findFirstBrokenLink(
                HashChain.GENESIS, THREE_EVENTS, List.of("only-one")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same length");
    }
}
