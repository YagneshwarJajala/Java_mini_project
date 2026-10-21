package com.astraai.simulation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Asynchronous lifecycle controller around the bounded simulation runner. */
public final class SimulationEngine implements AutoCloseable {
    private static final int MAX_SUBSCRIBERS = 8;

    private final Object lock = new Object();
    private final SimulationConfig config;
    private final SimulationRunner runner;
    private final ThreadPoolExecutor cleanupExecutor;
    private final CompletableFuture<SimulationEngineSnapshot> terminalCompletion = new CompletableFuture<>();
    private final List<EngineEventSubscription> subscriptions = new ArrayList<>();

    private SimulationState state = SimulationState.READY;
    private PendingOperation pendingOperation = PendingOperation.NONE;
    private CompletableFuture<SimulationEngineSnapshot> pendingOperationCompletion;
    private ExecutionReport executionReport;
    private CleanupReport cleanupReport;
    private String diagnostic;
    private TerminalDecision terminalDecision;
    private boolean cleanupScheduled;
    private boolean closed;
    private boolean runStarted;
    private boolean runEnded;
    private long startedAtNanos;
    private long endedAtNanos;
    private long lastProgressEventCount;
    private long eventSequence;
    private int nextSubscriptionNumber;

    public SimulationEngine(SimulationConfig config, SyntheticChallenge challenge) {
        this.config = Objects.requireNonNull(config, "config");
        runner = new SimulationRunner(config, challenge, Objects.requireNonNull(challenge, "challenge")::evaluate,
                this::runnerProgress);
        cleanupExecutor = createCleanupExecutor();
    }

    SimulationEngine(SimulationConfig config, SyntheticChallenge challenge, CandidateEvaluator evaluator) {
        this.config = Objects.requireNonNull(config, "config");
        runner = new SimulationRunner(config, challenge, evaluator, this::runnerProgress);
        cleanupExecutor = createCleanupExecutor();
    }

    public EngineCommandResult start() {
        synchronized (lock) {
            if (state != SimulationState.READY || closed) {
                return response(CommandDisposition.REJECTED, "engine can start only once from READY", snapshotLocked());
            }
            runStarted = true;
            startedAtNanos = System.nanoTime();
            transitionLocked(SimulationState.RUNNING);
            pendingOperation = PendingOperation.NONE;
            try {
                runner.start().whenComplete(this::runnerCompleted);
            } catch (RuntimeException failure) {
                terminalDecision = TerminalDecision.FAILED;
                diagnostic = describe(failure);
                scheduleCleanupLocked();
            }
            return response(CommandDisposition.ACCEPTED, "run accepted", snapshotLocked(), terminalCompletion);
        }
    }

    public EngineCommandResult pause() {
        synchronized (lock) {
            if (state == SimulationState.PAUSED && terminalDecision == null) {
                return response(CommandDisposition.ACCEPTED_NO_OP, "engine is already paused", snapshotLocked());
            }
            if (state != SimulationState.RUNNING || pendingOperation != PendingOperation.NONE
                    || terminalDecision != null) {
                return response(CommandDisposition.REJECTED, "pause is not legal in the current state",
                        snapshotLocked());
            }
            transitionLocked(SimulationState.PAUSING);
            pendingOperation = PendingOperation.PAUSE;
            pendingOperationCompletion = new CompletableFuture<>();
            CompletableFuture<Void> acknowledgement = runner.requestPause();
            acknowledgement.whenCompleteAsync(this::pauseAcknowledged, cleanupExecutor);
            return response(CommandDisposition.ACCEPTED, "pause requested", snapshotLocked(),
                    pendingOperationCompletion);
        }
    }

    public EngineCommandResult resume() {
        synchronized (lock) {
            if (state == SimulationState.RUNNING && pendingOperation == PendingOperation.NONE
                    && terminalDecision == null) {
                return response(CommandDisposition.ACCEPTED_NO_OP, "engine is already running", snapshotLocked());
            }
            if (state != SimulationState.PAUSED || pendingOperation != PendingOperation.NONE
                    || terminalDecision != null) {
                return response(CommandDisposition.REJECTED, "resume is legal only from PAUSED",
                        snapshotLocked());
            }
            try {
                runner.resume();
                transitionLocked(SimulationState.RUNNING);
                return response(CommandDisposition.ACCEPTED, "resume accepted", snapshotLocked());
            } catch (RuntimeException failure) {
                terminalDecision = TerminalDecision.FAILED;
                diagnostic = describe(failure);
                scheduleCleanupLocked();
                return response(CommandDisposition.ACCEPTED, "resume failed; cleanup started", snapshotLocked(),
                        terminalCompletion);
            }
        }
    }

    public EngineCommandResult stop() {
        CompletableFuture<SimulationEngineSnapshot> supersededPause = null;
        SimulationEngineSnapshot stopSnapshot;
        synchronized (lock) {
            if (state == SimulationState.STOPPED) {
                return response(CommandDisposition.ACCEPTED_NO_OP, "run is already stopped", snapshotLocked());
            }
            if (state == SimulationState.STOPPING && terminalDecision == TerminalDecision.STOPPED) {
                return response(CommandDisposition.ACCEPTED_NO_OP, "stop is already pending", snapshotLocked(),
                        terminalCompletion);
            }
            if ((state != SimulationState.RUNNING && state != SimulationState.PAUSING
                    && state != SimulationState.PAUSED) || terminalDecision != null) {
                return response(CommandDisposition.REJECTED, "stop is not legal in the current state",
                        snapshotLocked());
            }
            if (pendingOperation == PendingOperation.PAUSE) {
                supersededPause = pendingOperationCompletion;
            }
            transitionLocked(SimulationState.STOPPING);
            pendingOperation = PendingOperation.STOP;
            pendingOperationCompletion = terminalCompletion;
            terminalDecision = TerminalDecision.STOPPED;
            runner.requestCancellation();
            scheduleCleanupLocked();
            stopSnapshot = snapshotLocked();
        }
        if (supersededPause != null) {
            supersededPause.complete(stopSnapshot);
        }
        return response(CommandDisposition.ACCEPTED, "stop accepted; cleanup is asynchronous",
                stopSnapshot, terminalCompletion);
    }

    public SimulationEngineSnapshot snapshot() {
        synchronized (lock) {
            return snapshotLocked();
        }
    }

    /**
     * Registers a bounded asynchronous listener and captures its initial snapshot
     * under the same lock as registration. Callbacks run on a dedicated daemon
     * delivery thread, never on a simulation worker or the JavaFX Application Thread.
     */
    public SimulationSubscription subscribe(Consumer<SimulationEvent> listener) {
        Objects.requireNonNull(listener, "listener");
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("engine is closed");
            }
            if (subscriptions.size() >= MAX_SUBSCRIBERS) {
                throw new IllegalStateException("at most " + MAX_SUBSCRIBERS + " active subscribers are allowed");
            }
            SimulationEngineSnapshot initial = snapshotLocked();
            SimulationEvent initialEvent = new SimulationEvent(eventSequence,
                    EngineEventType.INITIAL_SNAPSHOT, initial, false);
            int number = ++nextSubscriptionNumber;
            EngineEventSubscription subscription = new EngineEventSubscription(listener, initial, initialEvent,
                    number, this::removeSubscription);
            subscriptions.add(subscription);
            subscription.start();
            return subscription;
        }
    }

    public CompletionStage<SimulationEngineSnapshot> terminalCompletion() {
        return terminalCompletion.minimalCompletionStage();
    }

    private void runnerCompleted(ExecutionReport report, Throwable failure) {
        CompletableFuture<SimulationEngineSnapshot> operation = null;
        SimulationEngineSnapshot settled = null;
        synchronized (lock) {
            if (report != null) {
                executionReport = report;
                if (runStarted && !runEnded) {
                    endedAtNanos = System.nanoTime();
                    runEnded = true;
                }
                publishLocked(EngineEventType.RESULT_VALIDATED);
                if (terminalDecision == null) {
                    terminalDecision = decisionFor(report);
                    if (terminalDecision == TerminalDecision.FAILED) {
                        diagnostic = report.result().diagnostic().orElse("worker execution failed");
                        publishLocked(EngineEventType.WORKER_FAILURE);
                    }
                }
            }
            if (failure != null && terminalDecision == null) {
                terminalDecision = TerminalDecision.FAILED;
                diagnostic = describe(failure);
                if (runStarted && !runEnded) {
                    endedAtNanos = System.nanoTime();
                    runEnded = true;
                }
                publishLocked(EngineEventType.WORKER_FAILURE);
            }
            if (pendingOperation == PendingOperation.PAUSE) {
                pendingOperation = PendingOperation.NONE;
                operation = pendingOperationCompletion;
                pendingOperationCompletion = null;
                settled = snapshotLocked();
            }
            scheduleCleanupLocked();
        }
        if (operation != null) {
            operation.complete(settled);
        }
    }

    private void pauseAcknowledged(Void ignored, Throwable failure) {
        CompletableFuture<SimulationEngineSnapshot> operation = null;
        SimulationEngineSnapshot settled = null;
        synchronized (lock) {
            if (pendingOperation != PendingOperation.PAUSE || state != SimulationState.PAUSING) {
                return;
            }
            operation = pendingOperationCompletion;
            pendingOperationCompletion = null;
            pendingOperation = PendingOperation.NONE;
            if (failure == null && terminalDecision == null) {
                transitionLocked(SimulationState.PAUSED);
            } else if (failure != null && terminalDecision == null) {
                terminalDecision = TerminalDecision.FAILED;
                diagnostic = describe(failure);
                publishLocked(EngineEventType.WORKER_FAILURE);
                scheduleCleanupLocked();
            }
            settled = snapshotLocked();
        }
        if (operation != null) {
            operation.complete(settled);
        }
    }

    private void scheduleCleanupLocked() {
        if (cleanupScheduled || terminalDecision == null || isTerminal(state)) {
            return;
        }
        cleanupScheduled = true;
        try {
            cleanupExecutor.execute(this::performCleanup);
        } catch (RuntimeException failure) {
            cleanupScheduled = false;
            terminalDecision = TerminalDecision.FAILED;
            diagnostic = "cleanup executor rejected cleanup: " + describe(failure);
            cleanupReport = new CleanupReport(false, false, Optional.of(diagnostic));
            transitionLocked(SimulationState.FAILED);
            publishLocked(EngineEventType.CLEANUP_COMPLETED);
            terminalCompletion.complete(snapshotLocked());
        }
    }

    private void performCleanup() {
        CleanupReport report;
        try {
            report = runner.shutdownAndAwait();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            report = new CleanupReport(false, true,
                    Optional.of("cleanup thread was interrupted before termination was confirmed"));
        }

        SimulationEngineSnapshot terminalSnapshot;
        synchronized (lock) {
            cleanupReport = report;
            cleanupScheduled = false;
            if (runStarted && !runEnded) {
                endedAtNanos = System.nanoTime();
                runEnded = true;
            }
            if (!report.terminationConfirmed()) {
                diagnostic = report.diagnostic().orElse("worker termination was not confirmed");
                terminalDecision = TerminalDecision.FAILED;
                transitionLocked(SimulationState.FAILED);
            } else {
                transitionLocked(stateFor(terminalDecision));
            }
            pendingOperation = PendingOperation.NONE;
            pendingOperationCompletion = null;
            publishLocked(EngineEventType.CLEANUP_COMPLETED);
            terminalSnapshot = snapshotLocked();
        }
        terminalCompletion.complete(terminalSnapshot);
        cleanupExecutor.shutdown();
    }

    private SimulationEngineSnapshot snapshotLocked() {
        CoordinatorSnapshot coordination = runner.coordinatorSnapshot();
        long completed = coordination.agents().stream().mapToLong(AgentSnapshot::completedEvaluations).sum();
        Optional<Duration> elapsed = Optional.empty();
        OptionalDouble throughput = OptionalDouble.empty();
        if (runStarted) {
            long end = runEnded ? endedAtNanos : System.nanoTime();
            Duration measured = Duration.ofNanos(Math.max(0, end - startedAtNanos));
            elapsed = Optional.of(measured);
            if (!measured.isZero()) {
                throughput = OptionalDouble.of(completed / (measured.toNanos() / 1_000_000_000.0));
            }
        }
        return new SimulationEngineSnapshot(state, eventSequence, config.logicalAgentCount(), config.workerCount(),
                config.candidateCount(), completed, elapsed, throughput, Optional.of(coordination),
                Optional.ofNullable(executionReport), Optional.ofNullable(cleanupReport),
                Optional.ofNullable(diagnostic), pendingOperation != PendingOperation.NONE || cleanupScheduled);
    }

    private void runnerProgress(long completed) {
        synchronized (lock) {
            if (completed <= lastProgressEventCount) {
                return;
            }
            lastProgressEventCount = completed;
            if (terminalDecision == null && !isTerminal(state) && state != SimulationState.STOPPING) {
                publishLocked(EngineEventType.PROGRESS);
            }
        }
    }

    private void transitionLocked(SimulationState next) {
        if (state != next) {
            state = next;
            publishLocked(EngineEventType.STATE_CHANGED);
        }
    }

    private void publishLocked(EngineEventType type) {
        SimulationEvent event = new SimulationEvent(++eventSequence, type, snapshotLocked(), false);
        for (EngineEventSubscription subscription : List.copyOf(subscriptions)) {
            subscription.offer(event);
        }
    }

    private void removeSubscription(EngineEventSubscription target) {
        synchronized (lock) {
            subscriptions.remove(target);
        }
    }

    private EngineCommandResult response(CommandDisposition disposition, String message,
            SimulationEngineSnapshot snapshot) {
        return response(disposition, message, snapshot,
                CompletableFuture.completedFuture(snapshot));
    }

    private EngineCommandResult response(CommandDisposition disposition, String message,
            SimulationEngineSnapshot snapshot, CompletableFuture<SimulationEngineSnapshot> completion) {
        return new EngineCommandResult(disposition, snapshot.state(), message, completion.minimalCompletionStage());
    }

    private static TerminalDecision decisionFor(ExecutionReport report) {
        if (!report.validation().valid()) {
            return TerminalDecision.FAILED;
        }
        return switch (report.result().outcome()) {
            case MATCH -> TerminalDecision.COMPLETED;
            case EXHAUSTED -> TerminalDecision.EXHAUSTED;
            case STOPPED -> TerminalDecision.STOPPED;
            case FAILED -> TerminalDecision.FAILED;
        };
    }

    private static SimulationState stateFor(TerminalDecision decision) {
        if (decision == null) {
            return SimulationState.FAILED;
        }
        return switch (decision) {
            case COMPLETED -> SimulationState.COMPLETED;
            case EXHAUSTED -> SimulationState.EXHAUSTED;
            case STOPPED -> SimulationState.STOPPED;
            case FAILED -> SimulationState.FAILED;
        };
    }

    private static boolean isTerminal(SimulationState state) {
        return state == SimulationState.COMPLETED || state == SimulationState.EXHAUSTED
                || state == SimulationState.STOPPED || state == SimulationState.FAILED;
    }

    private static String describe(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return root.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static ThreadPoolExecutor createCleanupExecutor() {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "astra-simulation-cleanup-" + counter.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(2), factory, new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public void close() {
        List<EngineEventSubscription> toClose;
        boolean stopActiveRun;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            toClose = List.copyOf(subscriptions);
            subscriptions.clear();
            if (state == SimulationState.READY) {
                terminalDecision = TerminalDecision.STOPPED;
                pendingOperation = PendingOperation.STOP;
                transitionLocked(SimulationState.STOPPING);
                scheduleCleanupLocked();
                stopActiveRun = false;
            } else {
                stopActiveRun = !isTerminal(state);
            }
        }
        toClose.forEach(SimulationSubscription::close);
        if (stopActiveRun) {
            stop();
        }
    }

    boolean awaitCleanupExecutorTermination(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        return cleanupExecutor.awaitTermination(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    private enum PendingOperation {
        NONE,
        PAUSE,
        STOP
    }

    private enum TerminalDecision {
        COMPLETED,
        EXHAUSTED,
        STOPPED,
        FAILED
    }
}
