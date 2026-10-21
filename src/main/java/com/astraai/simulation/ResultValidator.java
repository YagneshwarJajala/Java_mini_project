package com.astraai.simulation;

import java.util.Objects;

/**
 * Checks result claims against the challenge and supplied completed-work accounting.
 * MATCH requires the configured target to be recorded exactly once; all evidence counts must be at most one.
 * EXHAUSTED requires a no-match challenge and exactly-once coverage of the whole candidate space.
 * STOPPED and FAILED require correctly sized, duplicate-free partial-or-complete evidence; neither may claim a
 * candidate match. FAILED additionally requires a non-blank diagnostic. Evidence records completed evaluations,
 * not invocation attempts that did not return successfully.
 */
public final class ResultValidator {
    public ValidationReport validate(SimulationResult result, SyntheticChallenge challenge) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(challenge, "challenge");

        return switch (result.outcome()) {
            case MATCH -> validateMatch(result, challenge);
            case EXHAUSTED -> validateExhaustion(result, challenge);
            case STOPPED -> validateStopped(result, challenge);
            case FAILED -> validateFailure(result, challenge);
        };
    }

    private ValidationReport validateMatch(SimulationResult result, SyntheticChallenge challenge) {
        if (result.matchedIndex().isEmpty() || result.candidateRepresentation().isEmpty()
                || result.evaluationEvidence().isEmpty() || result.diagnostic().isPresent()) {
            return ValidationReport.rejected("match result is incomplete or contains unrelated failure data");
        }
        int index = result.matchedIndex().getAsInt();
        if (!isInRange(index, challenge)) {
            return ValidationReport.rejected("match index is outside the configured candidate range");
        }
        if (!challenge.candidateRepresentation(index).equals(result.candidateRepresentation().orElseThrow())) {
            return ValidationReport.rejected("candidate representation does not match the canonical index representation");
        }
        EvaluationEvidence evidence = result.evaluationEvidence().orElseThrow();
        if (!hasExpectedEvidenceSize(evidence, challenge)) {
            return ValidationReport.rejected("evaluation evidence does not match the configured candidate range");
        }
        if (!evidence.hasNoDuplicateEvaluations()) {
            return ValidationReport.rejected("match evidence contains duplicate candidate evaluations");
        }
        if (evidence.completedEvaluationsAt(index) != 1) {
            return ValidationReport.rejected("matched index was not completed exactly once");
        }
        if (!challenge.evaluate(index)) {
            return ValidationReport.rejected("candidate does not match the configured synthetic challenge");
        }
        return ValidationReport.accepted("candidate match independently validated");
    }

    private ValidationReport validateExhaustion(SimulationResult result, SyntheticChallenge challenge) {
        if (result.matchedIndex().isPresent() || result.candidateRepresentation().isPresent()
                || result.diagnostic().isPresent() || result.evaluationEvidence().isEmpty()) {
            return ValidationReport.rejected("exhaustion result is incomplete or contains contradictory data");
        }
        EvaluationEvidence evidence = result.evaluationEvidence().orElseThrow();
        if (!hasExpectedEvidenceSize(evidence, challenge)) {
            return ValidationReport.rejected("evaluation evidence does not match the configured candidate range");
        }
        if (!evidence.hasNoDuplicateEvaluations()) {
            return ValidationReport.rejected("exhaustion evidence contains duplicate candidate evaluations");
        }
        if (!evidence.coversEveryCandidateExactlyOnce()) {
            return ValidationReport.rejected("exhaustion requires every candidate to be completed exactly once");
        }
        if (challenge.hasExpectedMatch()) {
            return ValidationReport.rejected("complete coverage includes the configured matching candidate");
        }
        return ValidationReport.accepted("complete candidate coverage independently validated");
    }

    private ValidationReport validateStopped(SimulationResult result, SyntheticChallenge challenge) {
        if (result.matchedIndex().isPresent() || result.candidateRepresentation().isPresent()
                || result.diagnostic().isPresent() || result.evaluationEvidence().isEmpty()) {
            return ValidationReport.rejected("stopped result contains contradictory match or failure data");
        }
        EvaluationEvidence evidence = result.evaluationEvidence().orElseThrow();
        if (!hasExpectedEvidenceSize(evidence, challenge)) {
            return ValidationReport.rejected("evaluation evidence does not match the configured candidate range");
        }
        if (!evidence.hasNoDuplicateEvaluations()) {
            return ValidationReport.rejected("stopped evidence contains duplicate candidate evaluations");
        }
        return ValidationReport.accepted("stopped outcome is structurally consistent");
    }

    private ValidationReport validateFailure(SimulationResult result, SyntheticChallenge challenge) {
        if (result.matchedIndex().isPresent() || result.candidateRepresentation().isPresent()
                || result.diagnostic().isEmpty() || result.diagnostic().orElseThrow().isBlank()
                || result.evaluationEvidence().isEmpty()) {
            return ValidationReport.rejected("failed result requires a diagnostic and cannot claim a match");
        }
        EvaluationEvidence evidence = result.evaluationEvidence().orElseThrow();
        if (!hasExpectedEvidenceSize(evidence, challenge)) {
            return ValidationReport.rejected("evaluation evidence does not match the configured candidate range");
        }
        if (!evidence.hasNoDuplicateEvaluations()) {
            return ValidationReport.rejected("failed evidence contains duplicate candidate evaluations");
        }
        return ValidationReport.accepted("failure outcome is structurally consistent");
    }

    private boolean hasExpectedEvidenceSize(EvaluationEvidence evidence, SyntheticChallenge challenge) {
        return evidence.candidateCount() == challenge.candidateCount();
    }

    private boolean isInRange(int index, SyntheticChallenge challenge) {
        return index >= 0 && index < challenge.candidateCount();
    }
}
