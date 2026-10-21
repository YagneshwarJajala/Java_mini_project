package com.astraai.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;
import org.junit.jupiter.api.Test;

class SimulationEngineTest {
    @Test
    void rejectsIllegalCommandsAndRepeatedStartWithoutChangingState() throws Exception {
        SimulationConfig config = config(2, 20, 4, 7, 2, Duration.ofSeconds(2));
        SyntheticChallenge challenge = SyntheticChallenge.withoutMatch(config);
        try (SimulationEngine engine = new SimulationEngine(config, challenge)) {
            assertEquals(CommandDisposition.REJECTED, engine.pause().disposition());
            assertEquals(CommandDisposition.REJECTED, engine.resume().disposition());
            assertEquals(CommandDisposition.REJECTED, engine.stop().disposition());
            assertEquals(SimulationState.READY, engine.snapshot().state());

            EngineCommandResult start = engine.start();
            assertEquals(CommandDisposition.ACCEPTED, start.disposition());
            assertEquals(CommandDisposition.REJECTED, engine.start().disposition());
            SimulationEngineSnapshot terminal = start.completion().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(SimulationState.EXHAUSTED, terminal.state());
            assertEquals(CommandDisposition.REJECTED, engine.pause().disposition());
            assertEquals(CommandDisposition.REJECTED, engine.resume().disposition());
            assertEquals(CommandDisposition.REJECTED, engine.stop().disposition());
            assertEquals(SimulationState.EXHAUSTED, engine.snapshot().state());
            assertTrue(terminal.cleanupReport().orElseThrow().terminationConfirmed());
        }
    }

    @Test
    void pauseAcknowledgesSafeBoundariesAndResumeDoesNotRepeatCandidateWork() throws Exception {
        SimulationConfig config = config(2, 40, 4, 7, 2, Duration.ofSeconds(2));
        SyntheticChallenge challenge = SyntheticChallenge.withoutMatch(config);
        CountDownLatch firstBatchWorkers = new CountDownLatch(2);
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        ThreadLocal<Boolean> firstCandidateOnThread = ThreadLocal.withInitial(() -> true);
        AtomicIntegerArray evaluations = new AtomicIntegerArray(config.candidateCount());
        try (SimulationEngine engine = new SimulationEngine(config, challenge, index -> {
            if (firstCandidateOnThread.get()) {
                firstCandidateOnThread.set(false);
                firstBatchWorkers.countDown();
                await(releaseWorkers);
            }
            if (!evaluations.compareAndSet(index, 0, 1)) {
                throw new IllegalStateException("candidate was evaluated more than once: " + index);
            }
            return challenge.evaluate(index);
        })) {
            engine.start();
            assertTrue(firstBatchWorkers.await(2, TimeUnit.SECONDS));
            EngineCommandResult pause = engine.pause();
            assertEquals(SimulationState.PAUSING, pause.state());
            assertEquals(CommandDisposition.REJECTED, engine.pause().disposition());
            releaseWorkers.countDown();

            SimulationEngineSnapshot paused = pause.completion().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(SimulationState.PAUSED, paused.state());
            assertEquals(2, paused.coordination().orElseThrow().agents().stream()
                    .filter(agent -> agent.state() == AgentState.PAUSED).count());
            assertEquals(2, paused.coordination().orElseThrow().tasks().stream()
                    .filter(task -> task.state() == TaskState.PAUSED).count());
            assertEquals(2, paused.coordination().orElseThrow().tasks().stream()
                    .filter(task -> task.state() == TaskState.QUEUED).count());

            assertEquals(CommandDisposition.ACCEPTED, engine.resume().disposition());
            SimulationEngineSnapshot terminal = engine.terminalCompletion().toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertEquals(SimulationState.EXHAUSTED, terminal.state());
            for (int index = 0; index < evaluations.length(); index++) {
                assertEquals(1, evaluations.get(index), "candidate index " + index);
            }
        } finally {
            releaseWorkers.countDown();
        }
    }

    @Test
    void stopDuringPausingWinsAndLateMatchCannotReplaceStoppedOutcome() throws Exception {
        SimulationConfig config = config(1, 8, 4, 0, 1, Duration.ofSeconds(2));
        SyntheticChallenge challenge = new SyntheticChallenge(config);
        CountDownLatch evaluating = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (SimulationEngine engine = new SimulationEngine(config, challenge, index -> {
            if (index == 0) {
                evaluating.countDown();
                await(release);
            }
            return challenge.evaluate(index);
        })) {
            engine.start();
            assertTrue(evaluating.await(2, TimeUnit.SECONDS));
            EngineCommandResult pause = engine.pause();
            assertEquals(SimulationState.PAUSING, pause.state());
            assertEquals(CommandDisposition.ACCEPTED, engine.stop().disposition());
            release.countDown();

            SimulationEngineSnapshot terminal = engine.terminalCompletion().toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertEquals(SimulationState.STOPPED, terminal.state(), terminal.toString());
            assertEquals(ResultOutcome.STOPPED, terminal.executionReport().orElseThrow().result().outcome(),
                    terminal.executionReport().orElseThrow().result().diagnostic().toString());
            assertTrue(terminal.cleanupReport().orElseThrow().terminationConfirmed());
            assertEquals(CommandDisposition.ACCEPTED_NO_OP, engine.stop().disposition());
        } finally {
            release.countDown();
        }
    }

    @Test
    void stopWhilePausedCancelsSuspendedAndUnassignedWork() throws Exception {
        SimulationConfig config = config(1, 20, 4, 7, 2, Duration.ofSeconds(2));
        SyntheticChallenge challenge = SyntheticChallenge.withoutMatch(config);
        CountDownLatch evaluating = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (SimulationEngine engine = new SimulationEngine(config, challenge, index -> {
            if (index == 0) {
                evaluating.countDown();
                await(release);
            }
            return false;
        })) {
            engine.start();
            assertTrue(evaluating.await(2, TimeUnit.SECONDS));
            EngineCommandResult pause = engine.pause();
            release.countDown();
            assertEquals(SimulationState.PAUSED,
                    pause.completion().toCompletableFuture().get(5, TimeUnit.SECONDS).state());
            EngineCommandResult stop = engine.stop();
            assertEquals(CommandDisposition.ACCEPTED, stop.disposition());
            SimulationEngineSnapshot terminal = stop.completion().toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            assertEquals(SimulationState.STOPPED, terminal.state());
            assertEquals(ResultOutcome.STOPPED, terminal.executionReport().orElseThrow().result().outcome());
            assertTrue(terminal.coordination().orElseThrow().tasks().stream()
                    .allMatch(task -> task.state() == TaskState.CANCELLED));
        } finally {
            release.countDown();
        }
    }

    @Test
    void stopWhileRunningReturnsImmediatelyAndCompletesAsynchronously() throws Exception {
        SimulationConfig config = config(1, 20, 4, 7, 2, Duration.ofSeconds(2));
        SyntheticChallenge challenge = SyntheticChallenge.withoutMatch(config);
        CountDownLatch evaluating = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (SimulationEngine engine = new SimulationEngine(config, challenge, index -> {
            if (index == 0) {
                evaluating.countDown();
                await(release);
            }
            return false;
        })) {
            engine.start();
            assertTrue(evaluating.await(2, TimeUnit.SECONDS));
            EngineCommandResult stop = engine.stop();
            assertEquals(CommandDisposition.ACCEPTED, stop.disposition());
            assertEquals(SimulationState.STOPPING, stop.state());
            release.countDown();
            SimulationEngineSnapshot terminal = stop.completion().toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            assertEquals(SimulationState.STOPPED, terminal.state(), terminal.toString());
            assertEquals(ResultOutcome.STOPPED, terminal.executionReport().orElseThrow().result().outcome());
            assertTrue(terminal.cleanupReport().orElseThrow().terminationConfirmed());
        } finally {
            release.countDown();
        }
    }

    @Test
    void reportsSuccessfulMatchAndNoMatchExhaustionOnlyAfterValidationAndCleanup() throws Exception {
        SimulationConfig matchConfig = config(2, 20, 4, 7, 3, Duration.ofSeconds(2));
        try (SimulationEngine engine = new SimulationEngine(matchConfig, new SyntheticChallenge(matchConfig))) {
            SimulationEngineSnapshot terminal = engine.start().completion().toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertEquals(SimulationState.COMPLETED, terminal.state());
            assertTrue(terminal.executionReport().orElseThrow().validation().valid());
            assertEquals(ResultOutcome.MATCH, terminal.executionReport().orElseThrow().result().outcome());
            assertTrue(terminal.cleanupReport().orElseThrow().terminationConfirmed());
            assertEquals(CommandDisposition.REJECTED, engine.stop().disposition());
            assertTrue(engine.awaitCleanupExecutorTermination(Duration.ofSeconds(2)));
        }

        SimulationConfig noMatchConfig = config(2, 20, 4, 7, 3, Duration.ofSeconds(2));
        try (SimulationEngine engine = new SimulationEngine(noMatchConfig,
                SyntheticChallenge.withoutMatch(noMatchConfig))) {
            SimulationEngineSnapshot terminal = engine.start().completion().toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertEquals(SimulationState.EXHAUSTED, terminal.state());
            assertEquals(ResultOutcome.EXHAUSTED, terminal.executionReport().orElseThrow().result().outcome());
        }
    }

    @Test
    void workerFailureIsFailedAndNeverReportedAsExhaustion() throws Exception {
        SimulationConfig config = config(1, 20, 4, 7, 2, Duration.ofSeconds(2));
        SyntheticChallenge challenge = SyntheticChallenge.withoutMatch(config);
        try (SimulationEngine engine = new SimulationEngine(config, challenge, index -> {
            if (index == 1) {
                throw new IllegalArgumentException("controlled failure");
            }
            return false;
        })) {
            SimulationEngineSnapshot terminal = engine.start().completion().toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertEquals(SimulationState.FAILED, terminal.state());
            assertEquals(ResultOutcome.FAILED, terminal.executionReport().orElseThrow().result().outcome());
            assertTrue(terminal.diagnostic().orElseThrow().contains("controlled failure"));
            assertTrue(terminal.cleanupReport().orElseThrow().terminationConfirmed());
        }
    }

    @Test
    void cleanupTimeoutFailsEngineWithoutClaimingTermination() throws Exception {
        SimulationConfig config = config(1, 8, 4, 0, 1, Duration.ofMillis(30));
        SyntheticChallenge challenge = new SyntheticChallenge(config);
        CountDownLatch evaluating = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SimulationEngine engine = new SimulationEngine(config, challenge, index -> {
            if (index == 0) {
                evaluating.countDown();
                awaitIgnoringInterrupts(release);
            }
            return challenge.evaluate(index);
        });
        try {
            engine.start();
            assertTrue(evaluating.await(2, TimeUnit.SECONDS));
            assertEquals(CommandDisposition.ACCEPTED, engine.stop().disposition());
            SimulationEngineSnapshot terminal = engine.terminalCompletion().toCompletableFuture()
                    .get(3, TimeUnit.SECONDS);

            assertEquals(SimulationState.FAILED, terminal.state());
            assertFalse(terminal.cleanupReport().orElseThrow().terminationConfirmed());
            assertTrue(terminal.cleanupReport().orElseThrow().diagnostic().isPresent());
        } finally {
            release.countDown();
            engine.close();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("controlled test operation was not released");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("controlled test operation interrupted", interrupted);
        }
    }

    private static void awaitIgnoringInterrupts(CountDownLatch latch) {
        boolean released = false;
        while (!released) {
            try {
                released = latch.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                // Simulates an evaluator that cannot be terminated by interruption alone.
            }
            if (!released && latch.getCount() == 0) {
                released = true;
            }
        }
    }

    private static SimulationConfig config(int workers, int candidates, int tasks, int expected,
            int batchSize, Duration cleanupTimeout) {
        return SimulationConfig.forTesting(workers, candidates, tasks, expected, batchSize, cleanupTimeout);
    }
}
