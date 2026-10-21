package com.astraai.simulation;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongConsumer;

/** Bounded executor for one deterministic local synthetic simulation run. */
public final class SimulationRunner implements AutoCloseable {
    private final SimulationConfig config;
    private final SyntheticChallenge challenge;
    private final CandidateEvaluator evaluator;
    private final LongConsumer progressListener;
    private final ResultValidator resultValidator = new ResultValidator();
    private final AgentCoordinator coordinator;
    private final ThreadPoolExecutor executor;
    private final AtomicIntegerArray completedCounts;
    private final AtomicInteger matchedIndex = new AtomicInteger(-1);
    private final AtomicReference<Throwable> workerFailure = new AtomicReference<>();
    private final AtomicBoolean cancellationRequested = new AtomicBoolean();
    private final Object controlLock = new Object();
    private final ArrayDeque<Continuation> suspendedTasks = new ArrayDeque<>();
    private CompletableFuture<ExecutionReport> completion;
    private CompletableFuture<Void> pauseAcknowledgement;
    private boolean started;
    private boolean pauseRequested;
    private boolean pauseReached;
    private boolean finalizerScheduled;
    private int activeTaskWorkers;
    private int waveWorkersRemaining;
    private volatile CleanupReport cleanupReport;

    public SimulationRunner(SimulationConfig config, SyntheticChallenge challenge) {
        this(config, challenge, Objects.requireNonNull(challenge, "challenge")::evaluate, ignored -> { });
    }

    SimulationRunner(SimulationConfig config, SyntheticChallenge challenge, CandidateEvaluator evaluator) {
        this(config, challenge, evaluator, ignored -> { });
    }

    SimulationRunner(SimulationConfig config, SyntheticChallenge challenge, CandidateEvaluator evaluator,
            LongConsumer progressListener) {
        this.config = Objects.requireNonNull(config, "config");
        this.challenge = Objects.requireNonNull(challenge, "challenge");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.progressListener = Objects.requireNonNull(progressListener, "progressListener");
        if (config.candidateCount() != challenge.candidateCount()) {
            throw new IllegalArgumentException("challenge candidate count must match the simulation config");
        }
        coordinator = new AgentCoordinator(config);
        completedCounts = new AtomicIntegerArray(config.candidateCount());
        executor = new ThreadPoolExecutor(config.workerCount(), config.workerCount(), 0L,
                TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(config.workerCount()),
                workerThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
    }

    public int workerCount() {
        return config.workerCount();
    }

    public CoordinatorSnapshot coordinatorSnapshot() {
        return coordinator.snapshot();
    }

    /** Starts execution and returns immediately with a future completed after terminal work is reported. */
    public CompletableFuture<ExecutionReport> start() {
        synchronized (controlLock) {
            if (started) {
                throw new IllegalStateException("this runner can execute only one run");
            }
            started = true;
            completion = new CompletableFuture<>();
            launchWorkerWaveLocked();
            return completion;
        }
    }

    /** Requests a cooperative pause. Completion means all worker loops left at safe batch boundaries. */
    public CompletableFuture<Void> requestPause() {
        CompletableFuture<Void> acknowledgement;
        boolean acknowledgeImmediately = false;
        synchronized (controlLock) {
            if (!started || completion.isDone() || cancellationRequested.get()
                    || matchedIndex.get() >= 0 || workerFailure.get() != null) {
                return CompletableFuture.failedFuture(new IllegalStateException("run cannot be paused now"));
            }
            if (pauseRequested) {
                return pauseAcknowledgement;
            }
            pauseRequested = true;
            pauseReached = false;
            pauseAcknowledgement = new CompletableFuture<>();
            if (waveWorkersRemaining == 0) {
                if (hasUnfinishedWorkLocked()) {
                    pauseReached = true;
                    acknowledgeImmediately = true;
                } else {
                    scheduleFinalizerLocked();
                }
            }
            acknowledgement = pauseAcknowledgement;
        }
        if (acknowledgeImmediately) {
            acknowledgement.complete(null);
        }
        return acknowledgement;
    }

    /** Resumes suspended task cursors without reassigning tasks or recounting completed candidates. */
    public void resume() {
        synchronized (controlLock) {
            if (!started || completion.isDone() || !pauseRequested || !pauseReached
                    || cancellationRequested.get() || matchedIndex.get() >= 0
                    || workerFailure.get() != null) {
                throw new IllegalStateException("run is not safely paused");
            }
            pauseRequested = false;
            pauseReached = false;
            launchWorkerWaveLocked();
        }
    }

    /** Requests cooperative cancellation; workers stop at batch boundaries. */
    public boolean requestCancellation() {
        synchronized (controlLock) {
            if (!started || completion.isDone() || cancellationRequested.get()) {
                return false;
            }
            cancellationRequested.set(true);
            pauseRequested = false;
            pauseReached = false;
            cancelSuspendedAndUnassignedLocked();
            if (waveWorkersRemaining == 0 && activeTaskWorkers == 0) {
                scheduleFinalizerLocked();
            }
            return true;
        }
    }

    private void launchWorkerWaveLocked() {
        waveWorkersRemaining = config.workerCount();
        for (int worker = 0; worker < config.workerCount(); worker++) {
            try {
                executor.execute(this::workerLoop);
            } catch (RuntimeException failure) {
                workerFailure.compareAndSet(null, failure);
                cancellationRequested.set(true);
                waveWorkersRemaining -= config.workerCount() - worker;
                cancelSuspendedAndUnassignedLocked();
                if (waveWorkersRemaining == 0) {
                    scheduleFinalizerLocked();
                }
                break;
            }
        }
    }

    private void workerLoop() {
        try {
            while (true) {
                TaskAssignment assignment;
                Continuation continuation;
                synchronized (controlLock) {
                    if (shouldStopLocked() || pauseRequested) {
                        return;
                    }
                    continuation = suspendedTasks.pollFirst();
                    if (continuation != null) {
                        assignment = continuation.assignment();
                        if (!coordinator.resume(assignment)) {
                            failAssignmentLocked(assignment,
                                    new IllegalStateException("paused task could not resume"));
                            return;
                        }
                    } else {
                        Optional<TaskAssignment> next = coordinator.claimNextAssignment();
                        if (next.isEmpty()) {
                            return;
                        }
                        assignment = next.orElseThrow();
                        if (!coordinator.begin(assignment)) {
                            failAssignmentLocked(assignment,
                                    new IllegalStateException("task assignment could not enter execution"));
                            return;
                        }
                        continuation = new Continuation(assignment,
                                assignment.task().candidateRange().startInclusive());
                    }
                    activeTaskWorkers++;
                }
                executeTask(continuation);
            }
        } catch (Throwable failure) {
            workerFailure.compareAndSet(null, failure);
            cancellationRequested.set(true);
        } finally {
            workerWaveFinished();
        }
    }

    private void executeTask(Continuation initial) {
        TaskAssignment assignment = initial.assignment();
        CandidateRange range = assignment.task().candidateRange();
        int cursor = initial.nextCandidate();
        while (cursor < range.endExclusive()) {
            BoundaryDecision decision = boundaryDecision(assignment, cursor);
            if (decision != BoundaryDecision.CONTINUE) {
                return;
            }

            int batchEnd = (int) Math.min((long) range.endExclusive(),
                    (long) cursor + config.candidateBatchSize());
            long batchCompleted = 0;
            try {
                for (int index = cursor; index < batchEnd; index++) {
                    boolean isMatch = evaluator.evaluate(index);
                    if (!completedCounts.compareAndSet(index, 0, 1)) {
                        throw new IllegalStateException("candidate index evaluated more than once: " + index);
                    }
                    batchCompleted++;
                    if (isMatch) {
                        synchronized (controlLock) {
                            if (!cancellationRequested.get()) {
                                int existing = matchedIndex.get();
                                if (existing == -1) {
                                    matchedIndex.compareAndSet(-1, index);
                                } else if (existing != index) {
                                    throw new IllegalStateException("multiple distinct candidate indices matched");
                                }
                            }
                        }
                    }
                }
            } catch (Throwable failure) {
                recordCompletedBatch(assignment, batchCompleted);
                finishFailed(assignment, failure);
                return;
            }
            recordCompletedBatch(assignment, batchCompleted);
            cursor = batchEnd;
        }

        if (!coordinator.complete(assignment)) {
            finishFailed(assignment, new IllegalStateException("completed task state was rejected"));
        } else {
            finishActiveTask();
        }
    }

    private void recordCompletedBatch(TaskAssignment assignment, long batchCompleted) {
        long total;
        synchronized (controlLock) {
            coordinator.recordCompletedEvaluations(assignment, batchCompleted);
            total = coordinator.snapshot().agents().stream()
                    .mapToLong(AgentSnapshot::completedEvaluations).sum();
        }
        try {
            progressListener.accept(total);
        } catch (Throwable ignored) {
            // Observability must never change candidate execution or its result.
        }
    }

    private BoundaryDecision boundaryDecision(TaskAssignment assignment, int cursor) {
        synchronized (controlLock) {
            if (shouldStopLocked()) {
                coordinator.cancel(assignment);
                activeTaskWorkers--;
                return BoundaryDecision.STOP;
            }
            if (pauseRequested) {
                if (!coordinator.pause(assignment)) {
                    failAssignmentLocked(assignment, new IllegalStateException("task could not pause safely"));
                    activeTaskWorkers--;
                    return BoundaryDecision.STOP;
                }
                suspendedTasks.addLast(new Continuation(assignment, cursor));
                activeTaskWorkers--;
                return BoundaryDecision.SUSPEND;
            }
            return BoundaryDecision.CONTINUE;
        }
    }

    private void finishFailed(TaskAssignment assignment, Throwable failure) {
        synchronized (controlLock) {
            failAssignmentLocked(assignment, failure);
            activeTaskWorkers--;
        }
    }

    private void finishActiveTask() {
        synchronized (controlLock) {
            activeTaskWorkers--;
        }
    }

    private void failAssignmentLocked(TaskAssignment assignment, Throwable failure) {
        workerFailure.compareAndSet(null, failure);
        cancellationRequested.set(true);
        coordinator.fail(assignment);
        cancelSuspendedAndUnassignedLocked();
    }

    private boolean shouldStopLocked() {
        return cancellationRequested.get() || matchedIndex.get() >= 0 || workerFailure.get() != null;
    }

    private void workerWaveFinished() {
        CompletableFuture<Void> acknowledge = null;
        synchronized (controlLock) {
            waveWorkersRemaining--;
            if (waveWorkersRemaining != 0) {
                return;
            }
            if (activeTaskWorkers != 0) {
                workerFailure.compareAndSet(null,
                        new IllegalStateException("worker wave ended while task work remained active"));
                cancellationRequested.set(true);
                cancelSuspendedAndUnassignedLocked();
            }
            if (shouldStopLocked()) {
                cancelSuspendedAndUnassignedLocked();
                scheduleFinalizerLocked();
            } else if (pauseRequested && hasUnfinishedWorkLocked()) {
                pauseReached = true;
                acknowledge = pauseAcknowledgement;
            } else {
                scheduleFinalizerLocked();
            }
        }
        if (acknowledge != null) {
            acknowledge.complete(null);
        }
    }

    private boolean hasUnfinishedWorkLocked() {
        return coordinator.snapshot().tasks().stream().anyMatch(task -> task.state() == TaskState.QUEUED
                || task.state() == TaskState.ASSIGNED || task.state() == TaskState.RUNNING
                || task.state() == TaskState.PAUSED);
    }

    private void cancelSuspendedAndUnassignedLocked() {
        while (!suspendedTasks.isEmpty()) {
            coordinator.cancel(suspendedTasks.removeFirst().assignment());
        }
        coordinator.cancelUnassignedTasks();
    }

    private void scheduleFinalizerLocked() {
        if (finalizerScheduled || completion == null || completion.isDone()) {
            return;
        }
        finalizerScheduled = true;
        try {
            executor.execute(this::finalizeRun);
        } catch (RuntimeException failure) {
            // The engine may begin bounded shutdown while the final worker is
            // leaving its loop. In that case the executor rejects this task;
            // shutdownAndAwait completes the report after worker termination
            // has been observed, avoiding a false worker failure.
        }
    }

    private void finalizeRun() {
        try {
            synchronized (controlLock) {
                cancelSuspendedAndUnassignedLocked();
            }
            completion.complete(buildReport());
        } catch (Throwable failure) {
            workerFailure.compareAndSet(null, failure);
            completion.complete(buildReport());
        } finally {
            executor.shutdown();
        }
    }

    private ExecutionReport buildReport() {
        EvaluationEvidence evidence = new EvaluationEvidence(copyCompletedCounts());
        SimulationResult result;
        Throwable failure = workerFailure.get();
        int match = matchedIndex.get();
        if (failure != null) {
            result = SimulationResult.failed(diagnostic(failure), evidence);
        } else if (cancellationRequested.get()) {
            result = SimulationResult.stopped(evidence);
        } else if (match >= 0) {
            result = SimulationResult.matchClaim(match, challenge.candidateRepresentation(match), evidence);
        } else {
            result = SimulationResult.exhaustedClaim(evidence);
        }

        ValidationReport validation = resultValidator.validate(result, challenge);
        if (!validation.valid() && result.outcome() != ResultOutcome.FAILED) {
            result = SimulationResult.failed("execution result rejected: " + validation.explanation(), evidence);
            validation = resultValidator.validate(result, challenge);
        }
        CoordinatorSnapshot snapshot = coordinator.snapshot();
        return new ExecutionReport(result, validation, evidence.totalCompletedEvaluations(),
                snapshot.agents(), snapshot.tasks());
    }

    private int[] copyCompletedCounts() {
        int[] copy = new int[completedCounts.length()];
        for (int index = 0; index < copy.length; index++) {
            copy[index] = completedCounts.get(index);
        }
        return copy;
    }

    private static String diagnostic(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private ThreadFactory workerThreadFactory() {
        AtomicInteger number = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, "astra-simulation-worker-" + number.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
    }

    /** Performs bounded graceful shutdown, then one bounded interrupt-assisted attempt. */
    public synchronized CleanupReport shutdownAndAwait() throws InterruptedException {
        if (cleanupReport != null) {
            return cleanupReport;
        }
        requestCancellation();
        executor.shutdown();
        Duration timeout = config.cleanupTimeout();
        boolean terminated = executor.awaitTermination(timeout.toNanos(), TimeUnit.NANOSECONDS);
        boolean interrupted = false;
        if (!terminated) {
            interrupted = true;
            executor.shutdownNow();
            terminated = executor.awaitTermination(timeout.toNanos(), TimeUnit.NANOSECONDS);
        }
        cleanupReport = terminated
                ? new CleanupReport(true, interrupted, Optional.empty())
                : new CleanupReport(false, interrupted,
                        Optional.of("worker executor did not terminate within the bounded cleanup attempts"));
        if (terminated && completion != null && !completion.isDone()) {
            // A finalizer can be rejected if shutdown races with the last
            // worker-wave callback. The cleanup thread is then the safe,
            // bounded fallback that publishes the result after termination.
            completion.complete(buildReport());
        }
        return cleanupReport;
    }

    public Optional<CleanupReport> cleanupReport() {
        return Optional.ofNullable(cleanupReport);
    }

    @Override
    public void close() {
        try {
            shutdownAndAwait();
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            cleanupReport = new CleanupReport(executor.isTerminated(), true,
                    executor.isTerminated() ? Optional.empty()
                            : Optional.of("shutdown wait was interrupted before worker termination was confirmed"));
        }
    }

    private enum BoundaryDecision {
        CONTINUE,
        SUSPEND,
        STOP
    }

    private record Continuation(TaskAssignment assignment, int nextCandidate) {
    }
}
