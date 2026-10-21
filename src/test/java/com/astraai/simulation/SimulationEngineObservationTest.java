package com.astraai.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SimulationEngineObservationTest {
    @Test
    void snapshotIsImmutableAndContainsMeasuredConfigurationAndWork() throws Exception {
        SimulationConfig config = config(1, 8, 2, 3, Duration.ofSeconds(2));
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
            SimulationEngineSnapshot ready = engine.snapshot();
            assertEquals(100, ready.logicalAgentCount());
            assertEquals(1, ready.executionWorkerCount());
            assertEquals(8, ready.candidateSpaceSize());
            assertEquals(0, ready.completedCandidateEvaluations());
            assertTrue(ready.elapsedTime().isEmpty());
            assertTrue(ready.evaluationsPerSecond().isEmpty());
            assertThrows(UnsupportedOperationException.class,
                    () -> ready.coordination().orElseThrow().agents().clear());

            engine.start();
            assertTrue(evaluating.await(2, TimeUnit.SECONDS));
            SimulationEngineSnapshot active = engine.snapshot();
            assertTrue(active.elapsedTime().isPresent());
            assertEquals(active.completedCandidateEvaluations(), active.coordination().orElseThrow().agents()
                    .stream().mapToLong(AgentSnapshot::completedEvaluations).sum());
            release.countDown();
            SimulationEngineSnapshot terminal = engine.terminalCompletion().toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertEquals(8, terminal.completedCandidateEvaluations());
            assertTrue(terminal.evaluationsPerSecond().isPresent());
            assertTrue(terminal.evaluationsPerSecond().getAsDouble() > 0);
            assertEquals(ready.state(), SimulationState.READY);
            assertEquals(0, ready.completedCandidateEvaluations());
        } finally {
            release.countDown();
        }
    }

    @Test
    void subscriptionRegistrationAndInitialSnapshotCannotMissFirstTransition() throws Exception {
        SimulationConfig config = config(1, 8, 2, 3, Duration.ofSeconds(2));
        try (SimulationEngine engine = new SimulationEngine(config, SyntheticChallenge.withoutMatch(config))) {
            ArrayBlockingQueue<SimulationEvent> received = new ArrayBlockingQueue<>(8);
            SimulationSubscription subscription = engine.subscribe(received::offer);
            assertEquals(SimulationState.READY, subscription.initialSnapshot().state());
            engine.start();
            assertEquals(EngineEventType.INITIAL_SNAPSHOT,
                    received.poll(2, TimeUnit.SECONDS).type());
            SimulationEvent transition = received.poll(2, TimeUnit.SECONDS);
            assertNotNull(transition);
            assertEquals(EngineEventType.STATE_CHANGED, transition.type());
            assertEquals(SimulationState.RUNNING, transition.snapshot().state());
            assertTrue(transition.sequence() > 0);
            SimulationEngineSnapshot terminal = engine.terminalCompletion().toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertEquals(SimulationState.EXHAUSTED, terminal.state());
            subscription.close();
            assertTrue(subscription.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    void concurrentSubscribersReceiveTheirOwnInitialSnapshotsAndTerminalState() throws Exception {
        SimulationConfig config = config(2, 16, 4, 6, Duration.ofSeconds(2));
        try (SimulationEngine engine = new SimulationEngine(config, SyntheticChallenge.withoutMatch(config))) {
            CountDownLatch terminalEvents = new CountDownLatch(4);
            List<SimulationSubscription> subscriptions = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                subscriptions.add(engine.subscribe(event -> {
                    if (event.type() == EngineEventType.CLEANUP_COMPLETED) {
                        terminalEvents.countDown();
                    }
                }));
            }
            engine.start();
            assertEquals(SimulationState.EXHAUSTED, engine.terminalCompletion().toCompletableFuture()
                    .get(5, TimeUnit.SECONDS).state());
            assertTrue(terminalEvents.await(2, TimeUnit.SECONDS));
            for (SimulationSubscription subscription : subscriptions) {
                assertEquals(SimulationState.READY, subscription.initialSnapshot().state());
                subscription.close();
                assertTrue(subscription.awaitTermination(Duration.ofSeconds(2)));
            }
        }
    }

    @Test
    void slowSubscriberHasBoundedMailboxAndCanRecoverFromCriticalEventOverflow() throws Exception {
        SimulationEngineSnapshot snapshot = emptySnapshot();
        CountDownLatch listenerEntered = new CountDownLatch(1);
        CountDownLatch releaseListener = new CountDownLatch(1);
        ArrayBlockingQueue<SimulationEvent> received = new ArrayBlockingQueue<>(EngineEventSubscription.MAILBOX_CAPACITY);
        SimulationEvent initial = new SimulationEvent(0, EngineEventType.INITIAL_SNAPSHOT, snapshot, false);
        EngineEventSubscription subscription = new EngineEventSubscription(event -> {
            if (event.type() == EngineEventType.INITIAL_SNAPSHOT) {
                listenerEntered.countDown();
                await(releaseListener);
                return;
            }
            received.offer(event);
        }, snapshot, initial, 1, ignored -> { });
        subscription.start();
        try {
            assertTrue(listenerEntered.await(2, TimeUnit.SECONDS));
            for (int sequence = 1; sequence <= EngineEventSubscription.MAILBOX_CAPACITY + 3; sequence++) {
                subscription.offer(new SimulationEvent(sequence, EngineEventType.STATE_CHANGED, snapshot, false));
            }
            assertTrue(subscription.mailboxSize() <= EngineEventSubscription.MAILBOX_CAPACITY);
            releaseListener.countDown();
            SimulationEvent recovered = received.poll(2, TimeUnit.SECONDS);
            assertNotNull(recovered);
            assertEquals(EngineEventType.RESYNC_REQUIRED, recovered.type());
            assertTrue(recovered.resyncRequired());
            assertEquals(EngineEventSubscription.MAILBOX_CAPACITY + 1, recovered.sequence());
        } finally {
            releaseListener.countDown();
            subscription.close();
        }
        assertTrue(subscription.awaitTermination(Duration.ofSeconds(2)));
    }

    @Test
    void listenerFailureIsIsolatedAndDoesNotPreventAnotherListener() throws Exception {
        SimulationConfig config = config(1, 8, 2, 3, Duration.ofSeconds(2));
        try (SimulationEngine engine = new SimulationEngine(config, SyntheticChallenge.withoutMatch(config))) {
            CountDownLatch healthyListenerSawTerminal = new CountDownLatch(1);
            SimulationSubscription broken = engine.subscribe(event -> {
                throw new IllegalStateException("controlled listener failure");
            });
            SimulationSubscription healthy = engine.subscribe(event -> {
                if (event.type() == EngineEventType.CLEANUP_COMPLETED) {
                    healthyListenerSawTerminal.countDown();
                }
            });
            engine.start();
            SimulationEngineSnapshot terminal = engine.terminalCompletion().toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertTrue(terminal.validatedResult().isPresent());
            assertTrue(terminal.validation().orElseThrow().valid());
            assertTrue(terminal.cleanupReport().orElseThrow().terminationConfirmed());
            assertTrue(healthyListenerSawTerminal.await(2, TimeUnit.SECONDS));
            broken.close();
            healthy.close();
            assertTrue(broken.awaitTermination(Duration.ofSeconds(2)));
            assertTrue(healthy.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    void eventSequencesAreMonotonicAndTerminalStateCannotBeOverwritten() throws Exception {
        SimulationConfig config = config(1, 8, 2, 3, Duration.ofSeconds(2));
        try (SimulationEngine engine = new SimulationEngine(config, SyntheticChallenge.withoutMatch(config))) {
            ArrayBlockingQueue<SimulationEvent> events = new ArrayBlockingQueue<>(128);
            SimulationSubscription subscription = engine.subscribe(events::offer);
            engine.start();
            SimulationEngineSnapshot terminal = engine.terminalCompletion().toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertEquals(SimulationState.EXHAUSTED, terminal.state());
            List<SimulationEvent> observed = new ArrayList<>();
            events.drainTo(observed);
            long previous = -1;
            for (SimulationEvent event : observed) {
                assertTrue(event.sequence() > previous);
                previous = event.sequence();
            }
            assertTrue(observed.stream().anyMatch(event -> event.type() == EngineEventType.RESULT_VALIDATED));
            assertTrue(observed.stream().anyMatch(event -> event.type() == EngineEventType.CLEANUP_COMPLETED
                    && event.snapshot().state() == SimulationState.EXHAUSTED));
            assertFalse(observed.stream().anyMatch(event -> event.type() == EngineEventType.PROGRESS
                    && event.snapshot().state() == SimulationState.EXHAUSTED));
            subscription.close();
            assertTrue(subscription.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    void progressCoalescingSignalsGapsAndFreshSnapshotProvidesRecovery() throws Exception {
        SimulationEngineSnapshot snapshot = emptySnapshot();
        ArrayBlockingQueue<SimulationEvent> received = new ArrayBlockingQueue<>(8);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        EngineEventSubscription subscription = new EngineEventSubscription(event -> {
            if (event.type() == EngineEventType.INITIAL_SNAPSHOT) {
                entered.countDown();
                await(release);
                return;
            }
            received.offer(event);
        }, snapshot, new SimulationEvent(0, EngineEventType.INITIAL_SNAPSHOT, snapshot, false), 2,
                ignored -> { });
        subscription.start();
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            for (int sequence = 1; sequence <= EngineEventSubscription.MAILBOX_CAPACITY + 10; sequence++) {
                subscription.offer(new SimulationEvent(sequence, EngineEventType.PROGRESS, snapshot, false));
            }
            assertTrue(subscription.mailboxSize() <= EngineEventSubscription.MAILBOX_CAPACITY);
            release.countDown();
            SimulationEvent coalesced = received.poll(2, TimeUnit.SECONDS);
            assertNotNull(coalesced);
            assertEquals(EngineEventType.PROGRESS, coalesced.type());
            assertTrue(coalesced.resyncRequired());
            assertEquals(EngineEventSubscription.MAILBOX_CAPACITY + 10, coalesced.sequence());
            // A fresh snapshot is the authoritative recovery path after coalescing.
            assertEquals(snapshot.completedCandidateEvaluations(), snapshot.coordination().orElseThrow().agents()
                    .stream().mapToLong(AgentSnapshot::completedEvaluations).sum());
        } finally {
            release.countDown();
            subscription.close();
        }
        assertTrue(subscription.awaitTermination(Duration.ofSeconds(2)));
    }

    @Test
    void stopTerminalStateIsNotOverwrittenByTheLastCompletedBatchProgress() throws Exception {
        SimulationConfig config = config(1, 8, 2, 3, Duration.ofSeconds(2));
        SyntheticChallenge challenge = SyntheticChallenge.withoutMatch(config);
        CountDownLatch evaluating = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch cleanupDelivered = new CountDownLatch(1);
        ArrayBlockingQueue<SimulationEvent> events = new ArrayBlockingQueue<>(32);
        try (SimulationEngine engine = new SimulationEngine(config, challenge, index -> {
            if (index == 0) {
                evaluating.countDown();
                await(release);
            }
            return false;
        })) {
            SimulationSubscription subscription = engine.subscribe(event -> {
                events.offer(event);
                if (event.type() == EngineEventType.CLEANUP_COMPLETED) {
                    cleanupDelivered.countDown();
                }
            });
            engine.start();
            assertTrue(evaluating.await(2, TimeUnit.SECONDS));
            assertEquals(CommandDisposition.ACCEPTED, engine.stop().disposition());
            release.countDown();
            SimulationEngineSnapshot terminal = engine.terminalCompletion().toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            assertEquals(SimulationState.STOPPED, terminal.state());
            assertTrue(cleanupDelivered.await(2, TimeUnit.SECONDS));
            // Stop is cooperative at the configured batch boundary, so this batch completes fully.
            assertEquals(2, terminal.completedCandidateEvaluations());

            List<SimulationEvent> observed = new ArrayList<>();
            events.drainTo(observed);
            assertFalse(observed.stream().anyMatch(event -> event.type() == EngineEventType.PROGRESS
                    && event.snapshot().state() == SimulationState.STOPPING));
            assertTrue(observed.stream().anyMatch(event -> event.type() == EngineEventType.CLEANUP_COMPLETED
                    && event.snapshot().state() == SimulationState.STOPPED));
            subscription.close();
            assertTrue(subscription.awaitTermination(Duration.ofSeconds(2)));
        } finally {
            release.countDown();
        }
    }

    private static SimulationEngineSnapshot emptySnapshot() {
        return new SimulationEngineSnapshot(SimulationState.READY, 0, 100, 1, 0, 0,
                Optional.empty(), OptionalDouble.empty(), Optional.of(new CoordinatorSnapshot(List.of(), List.of())),
                Optional.empty(), Optional.empty(), Optional.empty(), false);
    }

    private static SimulationConfig config(int workers, int candidates, int tasks, int expected,
            Duration timeout) {
        return SimulationConfig.forTesting(workers, candidates, tasks, expected, 2, timeout);
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
}
