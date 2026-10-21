package com.astraai.simulation;

import java.util.Locale;
import java.util.Objects;
import java.util.OptionalInt;

/** Deterministic local indexed challenge; it does not process passwords or credentials. */
public final class SyntheticChallenge {
    private static final String CANDIDATE_PREFIX = "CANDIDATE-";

    private final int candidateCount;
    private final OptionalInt expectedIndex;

    public SyntheticChallenge(SimulationConfig config) {
        Objects.requireNonNull(config, "config");
        this.candidateCount = config.candidateCount();
        this.expectedIndex = OptionalInt.of(config.expectedIndex());
    }

    private SyntheticChallenge(int candidateCount) {
        this.candidateCount = candidateCount;
        this.expectedIndex = OptionalInt.empty();
    }

    /** Creates a deterministic challenge variant that has no matching candidate. */
    public static SyntheticChallenge withoutMatch(SimulationConfig config) {
        Objects.requireNonNull(config, "config");
        return new SyntheticChallenge(config.candidateCount());
    }

    public int candidateCount() {
        return candidateCount;
    }

    public int expectedIndex() {
        if (expectedIndex.isEmpty()) {
            throw new IllegalStateException("this challenge has no expected match");
        }
        return expectedIndex.getAsInt();
    }

    public boolean hasExpectedMatch() {
        return expectedIndex.isPresent();
    }

    /** Returns the canonical display representation for an in-range candidate index. */
    public String candidateRepresentation(int index) {
        requireInRange(index);
        return CANDIDATE_PREFIX + String.format(Locale.ROOT, "%05d", index);
    }

    /** Evaluates a candidate index against the configured synthetic target. */
    public boolean evaluate(int index) {
        requireInRange(index);
        return expectedIndex.isPresent() && index == expectedIndex.getAsInt();
    }

    private void requireInRange(int index) {
        if (index < 0 || index >= candidateCount) {
            throw new IndexOutOfBoundsException("candidate index must be in [0, " + candidateCount + "): " + index);
        }
    }
}
