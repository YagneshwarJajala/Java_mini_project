package com.astraai.simulation;

/** Internal deterministic seam for candidate evaluation and controlled tests. */
@FunctionalInterface
interface CandidateEvaluator {
    boolean evaluate(int candidateIndex);
}
