package com.astraai.simulation;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Immutable claim and engine accounting submitted to {@link ResultValidator}. */
public record SimulationResult(
        ResultOutcome outcome,
        OptionalInt matchedIndex,
        Optional<String> candidateRepresentation,
        Optional<EvaluationEvidence> evaluationEvidence,
        Optional<String> diagnostic) {

    public SimulationResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(matchedIndex, "matchedIndex");
        Objects.requireNonNull(candidateRepresentation, "candidateRepresentation");
        Objects.requireNonNull(evaluationEvidence, "evaluationEvidence");
        Objects.requireNonNull(diagnostic, "diagnostic");
    }

    public static SimulationResult matchClaim(int index, String representation, EvaluationEvidence evidence) {
        return new SimulationResult(ResultOutcome.MATCH, OptionalInt.of(index),
                Optional.ofNullable(representation), Optional.ofNullable(evidence), Optional.empty());
    }

    public static SimulationResult exhaustedClaim(EvaluationEvidence evidence) {
        return new SimulationResult(ResultOutcome.EXHAUSTED, OptionalInt.empty(), Optional.empty(),
                Optional.ofNullable(evidence), Optional.empty());
    }

    public static SimulationResult stopped(EvaluationEvidence evidence) {
        return new SimulationResult(ResultOutcome.STOPPED, OptionalInt.empty(), Optional.empty(),
                Optional.ofNullable(evidence), Optional.empty());
    }

    public static SimulationResult failed(String reason, EvaluationEvidence evidence) {
        return new SimulationResult(ResultOutcome.FAILED, OptionalInt.empty(), Optional.empty(),
                Optional.ofNullable(evidence), Optional.ofNullable(reason));
    }
}
