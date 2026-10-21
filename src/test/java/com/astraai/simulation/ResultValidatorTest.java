package com.astraai.simulation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class ResultValidatorTest {
    private final SyntheticChallenge challenge = new SyntheticChallenge(
            SimulationConfig.forTesting(2, 20, 4, 7, 5, Duration.ofMillis(200)));
    private final ResultValidator validator = new ResultValidator();

    @Test
    void acceptsAValidMatch() {
        SimulationResult result = SimulationResult.matchClaim(7,
                challenge.candidateRepresentation(7), evidenceWith(7));

        assertTrue(validator.validate(result, challenge).valid());
    }

    @Test
    void rejectsOutOfRangeOrIncorrectCandidateClaims() {
        assertFalse(validator.validate(SimulationResult.matchClaim(20, "CANDIDATE-00020", evidenceWith(7)),
                challenge).valid());
        assertFalse(validator.validate(SimulationResult.matchClaim(6,
                challenge.candidateRepresentation(6), evidenceWith(6)), challenge).valid());
        assertFalse(validator.validate(SimulationResult.matchClaim(7, "CANDIDATE-00006", evidenceWith(7)),
                challenge).valid());
    }

    @Test
    void rejectsMissingOrInconsistentMatchEvidence() {
        SimulationResult missingEvidence = new SimulationResult(ResultOutcome.MATCH, OptionalInt.of(7),
                Optional.of("CANDIDATE-00007"), Optional.empty(), Optional.empty());
        SimulationResult notEvaluated = SimulationResult.matchClaim(7,
                "CANDIDATE-00007", emptyEvidence());
        SimulationResult wrongEvidenceSize = SimulationResult.matchClaim(7,
                "CANDIDATE-00007", new EvaluationEvidence(new int[19]));

        assertFalse(validator.validate(missingEvidence, challenge).valid());
        assertFalse(validator.validate(notEvaluated, challenge).valid());
        assertFalse(validator.validate(wrongEvidenceSize, challenge).valid());
    }

    @Test
    void acceptsExhaustionOnlyWithCompleteExactlyOnceCoverage() {
        SyntheticChallenge noMatchChallenge = SyntheticChallenge.withoutMatch(config());
        assertTrue(validator.validate(SimulationResult.exhaustedClaim(exactCoverage()), noMatchChallenge).valid());
        assertFalse(validator.validate(SimulationResult.exhaustedClaim(exactCoverage()), challenge).valid());
    }

    @Test
    void rejectsIncompleteOrDuplicateExhaustionEvidence() {
        int[] incomplete = new int[20];
        for (int i = 0; i < incomplete.length - 1; i++) {
            incomplete[i] = 1;
        }
        int[] duplicate = new int[20];
        java.util.Arrays.fill(duplicate, 1);
        duplicate[0] = 2;

        assertFalse(validator.validate(SimulationResult.exhaustedClaim(new EvaluationEvidence(incomplete)),
                challenge).valid());
        assertThrows(IllegalArgumentException.class, () -> new EvaluationEvidence(duplicate));
        assertFalse(validator.validate(SimulationResult.exhaustedClaim(emptyEvidence()), challenge).valid());
    }

    @Test
    void rejectsDuplicateEvidenceAtANonTargetIndexForMatch() {
        int[] counts = new int[20];
        counts[7] = 1;
        counts[12] = 2;

        assertThrows(IllegalArgumentException.class, () -> new EvaluationEvidence(counts));
    }

    @Test
    void stoppedAndFailedOutcomesRequireCompleteAccountingEvidenceForTheConfiguredSpace() {
        SimulationResult stoppedWithoutEvidence = new SimulationResult(ResultOutcome.STOPPED,
                OptionalInt.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        SimulationResult failedWithoutEvidence = new SimulationResult(ResultOutcome.FAILED,
                OptionalInt.empty(), Optional.empty(), Optional.empty(), Optional.of("worker failed"));

        assertFalse(validator.validate(stoppedWithoutEvidence, challenge).valid());
        assertFalse(validator.validate(failedWithoutEvidence, challenge).valid());
    }

    @Test
    void executionReportRejectsTotalsThatDisagreeWithEvidence() {
        SimulationResult result = SimulationResult.matchClaim(7,
                challenge.candidateRepresentation(7), evidenceWith(7));

        assertThrows(IllegalArgumentException.class,
                () -> new ExecutionReport(result, validator.validate(result, challenge), 0,
                        java.util.List.of(), java.util.List.of()));
    }

    @Test
    void rejectsExhaustionClaimsWithContradictoryMatchData() {
        SimulationResult inconsistent = new SimulationResult(ResultOutcome.EXHAUSTED, OptionalInt.of(7),
                Optional.of("CANDIDATE-00007"), Optional.of(exactCoverage()), Optional.empty());

        assertFalse(validator.validate(inconsistent, challenge).valid());
    }

    @Test
    void acceptsStructurallyConsistentStoppedAndFailedResults() {
        assertTrue(validator.validate(SimulationResult.stopped(evidenceWith(2)), challenge).valid());
        assertTrue(validator.validate(SimulationResult.failed("worker failed", evidenceWith(2)), challenge).valid());
    }

    @Test
    void rejectsStoppedOrFailedResultsWithInconsistentFields() {
        SimulationResult stoppedWithMatch = new SimulationResult(ResultOutcome.STOPPED, OptionalInt.of(7),
                Optional.of("CANDIDATE-00007"), Optional.of(evidenceWith(7)), Optional.empty());
        SimulationResult failedWithoutReason = new SimulationResult(ResultOutcome.FAILED, OptionalInt.empty(),
                Optional.empty(), Optional.of(evidenceWith(2)), Optional.of(" "));

        assertFalse(validator.validate(stoppedWithMatch, challenge).valid());
        assertFalse(validator.validate(failedWithoutReason, challenge).valid());
    }

    @Test
    void evidenceIsDefensivelyCopied() {
        int[] counts = new int[20];
        counts[7] = 1;
        EvaluationEvidence evidence = new EvaluationEvidence(counts);
        counts[7] = 0;

        assertTrue(validator.validate(SimulationResult.matchClaim(7,
                "CANDIDATE-00007", evidence), challenge).valid());
    }

    private static EvaluationEvidence evidenceWith(int index) {
        int[] counts = new int[20];
        counts[index] = 1;
        return new EvaluationEvidence(counts);
    }

    private static EvaluationEvidence emptyEvidence() {
        return new EvaluationEvidence(new int[20]);
    }

    private static EvaluationEvidence exactCoverage() {
        int[] counts = new int[20];
        java.util.Arrays.fill(counts, 1);
        return new EvaluationEvidence(counts);
    }

    private static SimulationConfig config() {
        return SimulationConfig.forTesting(2, 20, 4, 7, 5, Duration.ofMillis(200));
    }
}
