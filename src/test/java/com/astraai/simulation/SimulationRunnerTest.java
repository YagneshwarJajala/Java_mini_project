package com.astraai.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;
import org.junit.jupiter.api.Test;

class SimulationRunnerTest {
    @Test
    void createsExactlyOneHundredLogicalAgentsIndependentOfWorkers() {
        SimulationConfig defaults = SimulationConfig.defaults();
        try (SimulationRunner runner = new SimulationRunner(defaults, new SyntheticChallenge(defaults))) {
            assertEquals(8, runner.workerCount());
            assertEquals(100, runner.coordinatorSnapshot().agents().size());
        }

        SimulationConfig smaller = config(2, 20, 4, 7, 5);
        try (SimulationRunner runner = new SimulationRunner(smaller, new SyntheticChallenge(smaller))) {
            assertEquals(2, runner.workerCount());
            assertEquals(100, runner.coordinatorSnapshot().agents().size());
        }
    }

    @Test
    void executesAssignedTasksAndIndependentlyValidatesTheSyntheticMatch() throws Exception {
        SimulationConfig config = config(2, 20, 4, 7, 2);
        SyntheticChallenge challenge = new SyntheticChallenge(config);
        try (SimulationRunner runner = new SimulationRunner(config, challenge)) {
            ExecutionReport report = runner.start().get(5, TimeUnit.SECONDS);

            assertEquals(ResultOutcome.MATCH, report.result().outcome());
            assertTrue(report.validation().valid());
            assertEquals(7, report.result().matchedIndex().getAsInt());
            assertEquals("CANDIDATE-00007", report.result().candidateRepresentation().orElseThrow());
            assertTrue(report.completedEvaluations() >= 4);
            assertEquals(100, report.agents().size());
            assertEquals(4, report.tasks().size());
            assertTrue(report.tasks().stream().allMatch(task -> task.state() == TaskState.COMPLETED
                    || task.state() == TaskState.CANCELLED));
            assertTrue(runner.shutdownAndAwait().terminationConfirmed());
        }
    }

    @Test
    void findsTargetAtCandidateIndexZero() throws Exception {
        SimulationConfig config = config(1, 8, 4, 0, 2);
        SyntheticChallenge challenge = new SyntheticChallenge(config);
        try (SimulationRunner runner = new SimulationRunner(config, challenge)) {
            ExecutionReport report = runner.start().get(5, TimeUnit.SECONDS);

            assertEquals(ResultOutcome.MATCH, report.result().outcome());
            assertEquals(0, report.result().matchedIndex().orElseThrow());
            assertEquals("CANDIDATE-00000", report.result().candidateRepresentation().orElseThrow());
            assertEquals(2, report.completedEvaluations(), "the active batch completes after finding a match");
            assertTrue(report.validation().valid());
        }
    }

    @Test
    void findsTargetAtFinalCandidateIndex() throws Exception {
        SimulationConfig config = config(2, 8, 4, 7, 2);
        SyntheticChallenge challenge = new SyntheticChallenge(config);
        try (SimulationRunner runner = new SimulationRunner(config, challenge)) {
            ExecutionReport report = runner.start().get(5, TimeUnit.SECONDS);

            assertEquals(ResultOutcome.MATCH, report.result().outcome());
            assertEquals(7, report.result().matchedIndex().orElseThrow());
            assertEquals("CANDIDATE-00007", report.result().candidateRepresentation().orElseThrow());
            assertTrue(report.validation().valid());
        }
    }

    @Test
    void findsOnlyCandidateInSingleCandidateSearchSpace() throws Exception {
        SimulationConfig config = config(1, 1, 1, 0, 1);
        SyntheticChallenge challenge = new SyntheticChallenge(config);
        try (SimulationRunner runner = new SimulationRunner(config, challenge)) {
            ExecutionReport report = runner.start().get(5, TimeUnit.SECONDS);

            assertEquals(ResultOutcome.MATCH, report.result().outcome());
            assertEquals(0, report.result().matchedIndex().orElseThrow());
            assertEquals(1, report.completedEvaluations());
            assertTrue(report.validation().valid());
        }
    }

    @Test
    void cancellingOneConcurrentSimulationDoesNotAffectTheOther() throws Exception {
        SimulationConfig cancelledConfig = config(1, 20, 4, 7, 2);
        SimulationConfig successfulConfig = config(1, 8, 4, 7, 2);
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        SimulationRunner cancelled = new SimulationRunner(cancelledConfig,
                SyntheticChallenge.withoutMatch(cancelledConfig), index -> {
                    if (index == 0) {
                        bothStarted.countDown();
                        await(release);
                    }
                    return false;
                });
        SimulationRunner successful = new SimulationRunner(successfulConfig,
                new SyntheticChallenge(successfulConfig), index -> {
                    if (index == 0) {
                        bothStarted.countDown();
                        await(release);
                    }
                    return index == successfulConfig.expectedIndex();
                });
        try {
            CompletableFuture<ExecutionReport> cancelledCompletion = cancelled.start();
            CompletableFuture<ExecutionReport> successfulCompletion = successful.start();
            assertTrue(bothStarted.await(2, TimeUnit.SECONDS));
            assertTrue(cancelled.requestCancellation());
            release.countDown();

            ExecutionReport cancelledReport = cancelledCompletion.get(5, TimeUnit.SECONDS);
            ExecutionReport successfulReport = successfulCompletion.get(5, TimeUnit.SECONDS);
            assertEquals(ResultOutcome.STOPPED, cancelledReport.result().outcome());
            assertEquals(ResultOutcome.MATCH, successfulReport.result().outcome());
            assertEquals(successfulConfig.expectedIndex(), successfulReport.result().matchedIndex().orElseThrow());
            assertTrue(successfulReport.validation().valid());
        } finally {
            release.countDown();
            cancelled.close();
            successful.close();
        }
    }

    @Test
    void evaluatesOnlyInRangeAndAccountsEveryCandidateExactlyOnceWhenNoMatchExists() throws Exception {
        SimulationConfig config = config(3, 20, 4, 7, 3);
        SyntheticChallenge challenge = SyntheticChallenge.withoutMatch(config);
        AtomicIntegerArray observed = new AtomicIntegerArray(config.candidateCount());
        try (SimulationRunner runner = new SimulationRunner(config, challenge, index -> {
            if (index < 0 || index >= config.candidateCount()) {
                throw new AssertionError("candidate outside configured range: " + index);
            }
            if (!observed.compareAndSet(index, 0, 1)) {
                throw new AssertionError("duplicate evaluation: " + index);
            }
            return challenge.evaluate(index);
        })) {
            ExecutionReport report = runner.start().get(5, TimeUnit.SECONDS);

            assertEquals(ResultOutcome.EXHAUSTED, report.result().outcome());
            assertTrue(report.validation().valid());
            assertEquals(20, report.completedEvaluations());
            assertTrue(report.result().evaluationEvidence().orElseThrow().coversEveryCandidateExactlyOnce());
            for (int index = 0; index < observed.length(); index++) {
                assertEquals(1, observed.get(index));
            }
            assertUniqueTaskOwners(report);
        }
    }

    @Test
    void workerExceptionProducesFailureAndNeverExhaustion() throws Exception {
        SimulationConfig config = config(1, 20, 4, 7, 2);
        SyntheticChallenge challenge = SyntheticChallenge.withoutMatch(config);
        try (SimulationRunner runner = new SimulationRunner(config, challenge, index -> {
            if (index == 1) {
                throw new IllegalStateException("controlled worker failure");
            }
            return false;
        })) {
            ExecutionReport report = runner.start().get(5, TimeUnit.SECONDS);

            assertEquals(ResultOutcome.FAILED, report.result().outcome());
            assertTrue(report.result().diagnostic().orElseThrow().contains("controlled worker failure"));
            assertTrue(report.validation().valid());
            assertNotEquals(ResultOutcome.EXHAUSTED, report.result().outcome());
            assertTrue(report.tasks().stream().anyMatch(task -> task.state() == TaskState.FAILED));
            assertTrue(runner.shutdownAndAwait().terminationConfirmed());
        }
    }

    @Test
    void cancellationIsObservedBetweenBatchesAndIncompleteWorkIsNotExhaustion() throws Exception {
        SimulationConfig config = config(1, 20, 4, 7, 5);
        SyntheticChallenge challenge = SyntheticChallenge.withoutMatch(config);
        CountDownLatch candidateStarted = new CountDownLatch(1);
        CountDownLatch releaseCandidate = new CountDownLatch(1);
        SimulationRunner runner = new SimulationRunner(config, challenge, index -> {
            if (index == 0) {
                candidateStarted.countDown();
                try {
                    if (!releaseCandidate.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test did not release controlled candidate");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("controlled candidate interrupted", interrupted);
                }
            }
            return false;
        });
        try {
            CompletableFuture<ExecutionReport> completion = runner.start();
            assertTrue(candidateStarted.await(2, TimeUnit.SECONDS));
            assertTrue(runner.requestCancellation());
            releaseCandidate.countDown();
            ExecutionReport report = completion.get(5, TimeUnit.SECONDS);

            assertEquals(ResultOutcome.STOPPED, report.result().outcome());
            assertTrue(report.validation().valid());
            assertTrue(report.completedEvaluations() > 0);
            assertTrue(report.completedEvaluations() < config.candidateCount());
            assertFalse(report.result().evaluationEvidence().orElseThrow()
                    .coversEveryCandidateExactlyOnce());
            CleanupReport cleanup = runner.shutdownAndAwait();
            assertTrue(cleanup.terminationConfirmed());
            assertFalse(cleanup.interruptionRequested());
        } finally {
            releaseCandidate.countDown();
            runner.close();
        }
    }

    @Test
    void executionCompletionConfirmsBoundedExecutorShutdown() throws Exception {
        SimulationConfig config = config(2, 20, 4, 7, 4);
        SyntheticChallenge challenge = SyntheticChallenge.withoutMatch(config);
        try (SimulationRunner runner = new SimulationRunner(config, challenge)) {
            runner.start().get(5, TimeUnit.SECONDS);
            CleanupReport cleanup = runner.shutdownAndAwait();

            assertTrue(cleanup.terminationConfirmed());
            assertFalse(cleanup.interruptionRequested());
            assertTrue(runner.cleanupReport().orElseThrow().terminationConfirmed());
        }
    }

    private static void assertUniqueTaskOwners(ExecutionReport report) {
        Set<AgentId> owners = new HashSet<>();
        for (TaskSnapshot task : report.tasks()) {
            assertTrue(task.owner().isPresent());
            assertTrue(owners.add(task.owner().orElseThrow()), "logical agent owns multiple tasks");
        }
    }

    private static SimulationConfig config(int workers, int candidates, int tasks, int expected, int batchSize) {
        return SimulationConfig.forTesting(workers, candidates, tasks, expected, batchSize,
                Duration.ofSeconds(2));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("controlled concurrent simulation was not released");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("controlled concurrent simulation interrupted", interrupted);
        }
    }
}
