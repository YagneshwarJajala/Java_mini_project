package com.astraai.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.astraai.ui.SimulationResultSummary;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SimulationResultSummaryTest {
    @Test
    void summaryMatchesTheValidatedExecutionReportAndShowsTheDiscoveredCandidate() throws Exception {
        SimulationConfig config = config(20, 7, 2);
        try (SimulationEngine engine = new SimulationEngine(config, new SyntheticChallenge(config))) {
            SimulationEngineSnapshot terminal = engine.start().completion().toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            ExecutionReport report = terminal.executionReport().orElseThrow();
            SimulationResultSummary summary = SimulationResultSummary.from(terminal);

            assertTrue(report.validation().valid());
            assertEquals(Optional.of(ResultOutcome.MATCH), summary.outcome());
            assertEquals(Optional.of(true), summary.targetDiscovered());
            assertEquals(Optional.of(report.result().candidateRepresentation().orElseThrow()),
                    summary.discoveredCandidate());
            assertEquals(OptionalLong.of(report.result().evaluationEvidence().orElseThrow()
                    .totalCompletedEvaluations()), summary.completedEvaluations());
            assertEquals(report.result().evaluationEvidence().orElseThrow().candidateCount(), summary.searchSpaceSize());
            assertEquals(report.agents().stream().mapToLong(AgentSnapshot::completedEvaluations).sum(),
                    summary.agentWork().stream().mapToLong(SimulationResultSummary.AgentWork::completedEvaluations).sum());
            assertEquals(report.tasks().stream().filter(task -> task.owner().isPresent()).count(),
                    summary.agentWork().stream().mapToLong(agent -> agent.assignedTasks().size()).sum());
            assertEquals(report.agents().size(), summary.agentWork().size());
            assertTrue(summary.cleanupReport().orElseThrow().terminationConfirmed());
        }
    }

    @Test
    void exhaustionIsReportedAsNotFoundAndProgressUsesCompleteSearchSpace() throws Exception {
        SimulationConfig config = config(20, 7, 3);
        try (SimulationEngine engine = new SimulationEngine(config, SyntheticChallenge.withoutMatch(config))) {
            SimulationEngineSnapshot terminal = engine.start().completion().toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            SimulationResultSummary summary = SimulationResultSummary.from(terminal);

            assertEquals(Optional.of(ResultOutcome.EXHAUSTED), summary.outcome());
            assertEquals(Optional.of(false), summary.targetDiscovered());
            assertTrue(summary.discoveredCandidate().isEmpty());
            assertEquals(OptionalDouble.of(1.0), summary.progress());
            assertEquals(config.candidateCount(), summary.completedEvaluations().orElseThrow());
        }
    }

    @Test
    void stoppedRunKeepsItsPartialEvidenceAndDoesNotClaimDiscovery() throws Exception {
        SimulationConfig config = config(20, 7, 2);
        CountDownLatch evaluating = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        SyntheticChallenge challenge = SyntheticChallenge.withoutMatch(config);
        try (SimulationEngine engine = new SimulationEngine(config, challenge, index -> {
            if (index == 0 || index == 5) {
                evaluating.countDown();
                await(release);
            }
            return challenge.evaluate(index);
        })) {
            engine.start();
            assertTrue(evaluating.await(2, TimeUnit.SECONDS));
            assertEquals(CommandDisposition.ACCEPTED, engine.stop().disposition());
            release.countDown();
            SimulationEngineSnapshot terminal = engine.terminalCompletion().toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            SimulationResultSummary summary = SimulationResultSummary.from(terminal);

            assertEquals(Optional.of(ResultOutcome.STOPPED), summary.outcome());
            assertTrue(summary.targetDiscovered().isEmpty());
            assertEquals(OptionalLong.of(4), summary.completedEvaluations());
            assertEquals(OptionalDouble.of(0.2), summary.progress());
        } finally {
            release.countDown();
        }
    }

    @Test
    void failedRunRetainsTheCompletedPartOfTheFailingBatch() throws Exception {
        SimulationConfig config = SimulationConfig.forTesting(1, 20, 4, 7, 2, Duration.ofSeconds(2));
        SyntheticChallenge challenge = SyntheticChallenge.withoutMatch(config);
        try (SimulationEngine engine = new SimulationEngine(config, challenge, index -> {
            if (index == 1) {
                throw new IllegalStateException("controlled evaluation failure");
            }
            return false;
        })) {
            SimulationEngineSnapshot terminal = engine.start().completion().toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            SimulationResultSummary summary = SimulationResultSummary.from(terminal);

            assertEquals(Optional.of(ResultOutcome.FAILED), summary.outcome());
            assertTrue(summary.targetDiscovered().isEmpty());
            assertEquals(OptionalLong.of(1), summary.completedEvaluations());
            assertEquals(OptionalDouble.of(0.05), summary.progress());
            assertTrue(summary.diagnostic().orElseThrow().contains("controlled evaluation failure"));
        }
    }

    @Test
    void throughputUsesEvidenceCountAndMonotonicElapsedSeconds() throws Exception {
        SimulationConfig config = config(20, 7, 2);
        try (SimulationEngine engine = new SimulationEngine(config, new SyntheticChallenge(config))) {
            SimulationEngineSnapshot terminal = engine.start().completion().toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            SimulationResultSummary summary = SimulationResultSummary.from(terminal);
            long count = terminal.executionReport().orElseThrow().result().evaluationEvidence()
                    .orElseThrow().totalCompletedEvaluations();
            double seconds = terminal.elapsedTime().orElseThrow().toNanos() / 1_000_000_000.0;

            assertTrue(seconds > 0);
            assertEquals(count / seconds, summary.evaluationsPerSecond().orElseThrow(), 0.000001);
            assertEquals((double) count / config.candidateCount(), summary.progress().orElseThrow(), 0.000001);
        }
    }

    @Test
    void missingExecutionEvidenceLeavesOutcomeCountsProgressAndThroughputUnavailable() {
        SimulationEngineSnapshot snapshot = new SimulationEngineSnapshot(SimulationState.FAILED, 1,
                100, 2, 20, 0, Optional.of(Duration.ofSeconds(1)), OptionalDouble.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of("runner failed before producing a report"), false);
        SimulationResultSummary summary = SimulationResultSummary.from(snapshot);

        assertTrue(summary.outcome().isEmpty());
        assertTrue(summary.targetDiscovered().isEmpty());
        assertTrue(summary.discoveredCandidate().isEmpty());
        assertTrue(summary.completedEvaluations().isEmpty());
        assertTrue(summary.progress().isEmpty());
        assertTrue(summary.evaluationsPerSecond().isEmpty());
        assertTrue(summary.agentWork().isEmpty());
        assertEquals(20, summary.searchSpaceSize());
    }

    @Test
    void zeroDurationLeavesThroughputUnavailableWithoutDivisionByZero() {
        EvaluationEvidence evidence = new EvaluationEvidence(new int[] { 1 });
        SimulationResult result = SimulationResult.matchClaim(0, "CANDIDATE-00000", evidence);
        ResultValidator validator = new ResultValidator();
        List<AgentSnapshot> agents = List.of(new AgentSnapshot(AgentId.of(1), AgentState.COMPLETED,
                Optional.empty(), 1));
        ExecutionReport report = new ExecutionReport(result, validator.validate(result,
                new SyntheticChallenge(SimulationConfig.forTesting(1, 1, 1, 0, 1, Duration.ofSeconds(1)))),
                1, agents, List.of());
        SimulationEngineSnapshot snapshot = new SimulationEngineSnapshot(SimulationState.COMPLETED, 1,
                100, 1, 1, 1, Optional.of(Duration.ZERO), OptionalDouble.empty(),
                Optional.of(new CoordinatorSnapshot(agents, List.of())), Optional.of(report),
                Optional.of(new CleanupReport(true, false, Optional.empty())), Optional.empty(), false);

        SimulationResultSummary summary = SimulationResultSummary.from(snapshot);

        assertEquals(OptionalLong.of(1), summary.completedEvaluations());
        assertTrue(summary.progress().isPresent());
        assertTrue(summary.evaluationsPerSecond().isEmpty());
    }

    @Test
    void aLaterRunSummaryDoesNotCarryThePreviousRunsCandidateOrCounts() throws Exception {
        SimulationConfig matchConfig = config(20, 7, 2);
        SimulationResultSummary first;
        try (SimulationEngine engine = new SimulationEngine(matchConfig, new SyntheticChallenge(matchConfig))) {
            first = SimulationResultSummary.from(engine.start().completion().toCompletableFuture()
                    .get(5, TimeUnit.SECONDS));
        }
        SimulationEngineSnapshot secondSnapshot = new SimulationEngineSnapshot(SimulationState.FAILED, 2,
                100, 2, 12, 0, Optional.empty(), OptionalDouble.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of("second run has no execution report"), false);
        SimulationResultSummary second = SimulationResultSummary.from(secondSnapshot);

        assertTrue(first.discoveredCandidate().isPresent());
        assertTrue(first.completedEvaluations().isPresent());
        assertTrue(second.discoveredCandidate().isEmpty());
        assertTrue(second.completedEvaluations().isEmpty());
        assertEquals(12, second.searchSpaceSize());
    }

    private static SimulationConfig config(int candidates, int expected, int batchSize) {
        return SimulationConfig.forTesting(2, candidates, 4, expected, batchSize, Duration.ofSeconds(2));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("controlled evaluator was not released");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("controlled evaluator interrupted", interrupted);
        }
    }
}
