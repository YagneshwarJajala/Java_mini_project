package com.astraai.simulation;

/** Immutable half-open range of candidate indices: [startInclusive, endExclusive). */
public record CandidateRange(int startInclusive, int endExclusive) {
    public CandidateRange {
        if (startInclusive < 0 || endExclusive <= startInclusive) {
            throw new IllegalArgumentException("candidate range must be non-empty and non-negative");
        }
    }

    public int size() {
        return Math.subtractExact(endExclusive, startInclusive);
    }

    public boolean contains(int candidateIndex) {
        return candidateIndex >= startInclusive && candidateIndex < endExclusive;
    }
}
