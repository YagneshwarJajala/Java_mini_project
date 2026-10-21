package com.astraai.simulation;

import java.util.Arrays;

/**
 * Immutable per-index evaluation accounting supplied by the engine/task layer.
 * Counts represent completed evaluations, not scheduled or attempted work.
 */
public final class EvaluationEvidence {
    private final int[] completedEvaluationCounts;

    EvaluationEvidence(int[] completedEvaluationCounts) {
        if (completedEvaluationCounts == null) {
            throw new NullPointerException("completedEvaluationCounts");
        }
        this.completedEvaluationCounts = completedEvaluationCounts.clone();
        for (int count : this.completedEvaluationCounts) {
            if (count < 0 || count > 1) {
                throw new IllegalArgumentException("completed evaluation counts must be zero or one per candidate");
            }
        }
    }

    public int candidateCount() {
        return completedEvaluationCounts.length;
    }

    public int completedEvaluationsAt(int index) {
        if (index < 0 || index >= completedEvaluationCounts.length) {
            throw new IndexOutOfBoundsException("candidate index is outside the evidence range: " + index);
        }
        return completedEvaluationCounts[index];
    }

    public long totalCompletedEvaluations() {
        return Arrays.stream(completedEvaluationCounts).asLongStream().sum();
    }

    public boolean coversEveryCandidateExactlyOnce() {
        if (completedEvaluationCounts.length == 0) {
            return false;
        }
        for (int count : completedEvaluationCounts) {
            if (count != 1) {
                return false;
            }
        }
        return true;
    }

    /** Every candidate is either unevaluated or evaluated once, with no duplicate work recorded. */
    public boolean hasNoDuplicateEvaluations() {
        for (int count : completedEvaluationCounts) {
            if (count > 1) {
                return false;
            }
        }
        return true;
    }
}
