package com.astraai.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SyntheticChallengeTest {
    @Test
    void defaultChallengeHasTheApprovedExpectedIndex() {
        SyntheticChallenge challenge = new SyntheticChallenge(SimulationConfig.defaults());

        assertEquals(4_271, challenge.expectedIndex());
        assertEquals(100_000, challenge.candidateCount());
        assertTrue(challenge.evaluate(4_271));
    }

    @Test
    void candidateRepresentationIsCanonicalAtRangeBoundaries() {
        SyntheticChallenge challenge = new SyntheticChallenge(SimulationConfig.defaults());

        assertEquals("CANDIDATE-00000", challenge.candidateRepresentation(0));
        assertEquals("CANDIDATE-99999", challenge.candidateRepresentation(99_999));
    }

    @Test
    void rejectsOutOfRangeIndicesForEvaluationAndRepresentation() {
        SyntheticChallenge challenge = new SyntheticChallenge(SimulationConfig.defaults());

        assertThrows(IndexOutOfBoundsException.class, () -> challenge.evaluate(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> challenge.evaluate(100_000));
        assertThrows(IndexOutOfBoundsException.class, () -> challenge.candidateRepresentation(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> challenge.candidateRepresentation(100_000));
    }

    @Test
    void evaluatesOnlyTheConfiguredSyntheticIndex() {
        SyntheticChallenge challenge = new SyntheticChallenge(testConfig());

        assertTrue(challenge.evaluate(7));
        assertFalse(challenge.evaluate(6));
    }

    @Test
    void noMatchVariantHasNoExpectedCandidate() {
        SyntheticChallenge challenge = SyntheticChallenge.withoutMatch(testConfig());

        assertFalse(challenge.hasExpectedMatch());
        assertFalse(challenge.evaluate(7));
        assertThrows(IllegalStateException.class, challenge::expectedIndex);
    }

    private static SimulationConfig testConfig() {
        return SimulationConfig.forTesting(2, 20, 4, 7, 5, Duration.ofMillis(200));
    }
}
