package com.astraai.ui;

import com.astraai.simulation.EngineCommandResult;
import com.astraai.simulation.CommandDisposition;
import com.astraai.simulation.EngineEventType;
import com.astraai.simulation.SimulationConfig;
import com.astraai.simulation.SimulationEngine;
import com.astraai.simulation.SimulationEngineSnapshot;
import com.astraai.simulation.SimulationEvent;
import com.astraai.simulation.SimulationState;
import com.astraai.simulation.SimulationSubscription;
import com.astraai.simulation.SyntheticChallenge;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.layout.StackPane;

/** Coordinates the application's three primary screens without owning simulation logic. */
public final class AstraApplicationView extends StackPane implements AutoCloseable {
    private final ExecutorService commands = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "astra-ui-commands");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<PendingSnapshot> pendingSnapshot = new AtomicReference<>();
    private final AtomicBoolean renderQueued = new AtomicBoolean();
    private final PasswordEntryView entry = new PasswordEntryView(this::submitPassword);
    private final PasswordSecurityReportView report = new PasswordSecurityReportView(this::analyzeAnother);
    private InitializationView processing;
    private volatile SimulationEngine engine;
    private volatile SimulationSubscription subscription;
    private PasswordAnalysis passwordAnalysis;
    private volatile long displayedSequence = -1;
    private volatile boolean closed;

    public AstraApplicationView() {
        show(PresentationState.PASSWORD_ENTRY);
    }

    public PresentationState presentationState() {
        if (getChildren().contains(entry)) return PresentationState.PASSWORD_ENTRY;
        if (getChildren().contains(report)) return PresentationState.FINAL_RESULT;
        return PresentationState.PROCESSING;
    }

    private void submitPassword(PasswordAnalysis analysis) {
        if (closed) return;
        passwordAnalysis = analysis;
        long run = generation.incrementAndGet();
        displayedSequence = -1;
        processing = new InitializationView(this::retryInitialization,
                this::pauseOrResume, this::stopSimulation);
        show(PresentationState.PROCESSING);
        processing.start();
        initializeEngine(run);
    }

    private void retryInitialization() {
        if (closed || engine != null) return;
        long run = generation.incrementAndGet();
        processing.start();
        initializeEngine(run);
    }

    private void initializeEngine(long run) {
        commands.execute(() -> {
            if (closed || generation.get() != run) return;
            SimulationEngine created = null;
            SimulationSubscription createdSubscription = null;
            try {
                SimulationConfig config = SimulationConfig.defaults();
                created = new SimulationEngine(config, new SyntheticChallenge(config));
                SimulationEngine target = created;
                createdSubscription = created.subscribe(event -> receive(run, target, event));
                engine = created;
                subscription = createdSubscription;
                if (!closed && generation.get() == run) {
                    Platform.runLater(() -> {
                        if (!closed && generation.get() == run && processing != null) {
                            processing.setStatus("Engine ready · starting bounded local search");
                        }
                    });
                }
                EngineCommandResult start = created.start();
                if (start.disposition() == CommandDisposition.REJECTED) {
                    throw new IllegalStateException(start.message());
                }
            } catch (Throwable failure) {
                if (engine == created) engine = null;
                if (subscription == createdSubscription) subscription = null;
                if (createdSubscription != null) createdSubscription.close();
                boolean retryAllowed = created == null;
                if (created != null) {
                    created.close();
                    try {
                        SimulationEngineSnapshot terminal = created.terminalCompletion().toCompletableFuture()
                                .get(configuredCleanupWaitMillis(), TimeUnit.MILLISECONDS);
                        retryAllowed = terminal.cleanupReport().map(cleanup -> cleanup.terminationConfirmed())
                                .orElse(false);
                    } catch (Exception ignored) {
                        retryAllowed = false;
                    }
                }
                boolean canRetry = retryAllowed;
                if (generation.get() == run && !closed) {
                    Platform.runLater(() -> {
                        if (processing != null) processing.showFailure("Initialization failed: "
                                + safeMessage(failure) + (canRetry ? "" : " · cleanup is incomplete; restart unavailable"),
                                canRetry);
                    });
                }
            }
        });
    }

    private void receive(long run, SimulationEngine source, SimulationEvent event) {
        if (closed || generation.get() != run) return;
        SimulationEngineSnapshot snapshot = event.snapshot();
        if (event.resyncRequired() || event.type() == EngineEventType.RESYNC_REQUIRED
                || (displayedSequence >= 0 && event.sequence() > displayedSequence + 1)) {
            snapshot = source.snapshot();
        }
        PendingSnapshot latest = new PendingSnapshot(run, snapshot);
        pendingSnapshot.accumulateAndGet(latest,
                (oldValue, newValue) -> oldValue == null || newValue.snapshot.sequence() >= oldValue.snapshot.sequence()
                        ? newValue : oldValue);
        queueRender();
    }

    private void queueRender() {
        if (renderQueued.compareAndSet(false, true)) {
            Platform.runLater(() -> {
                PendingSnapshot latest = pendingSnapshot.getAndSet(null);
                renderQueued.set(false);
                if (latest != null) applySnapshot(latest.generation, latest.snapshot);
                if (pendingSnapshot.get() != null) queueRender();
            });
        }
    }

    private void applySnapshot(long run, SimulationEngineSnapshot snapshot) {
        if (closed || generation.get() != run || snapshot.sequence() < displayedSequence) return;
        displayedSequence = snapshot.sequence();
        if (PresentationStateMapper.map(snapshot.state(), true) == PresentationState.FINAL_RESULT) {
            if (processing != null) processing.close();
            report.update(passwordAnalysis);
            show(PresentationState.FINAL_RESULT);
            return;
        }
        if (processing != null) processing.update(snapshot);
    }

    private void pauseOrResume() {
        SimulationEngine target = engine;
        if (target == null || closed) return;
        commands.execute(() -> {
            SimulationState current = target.snapshot().state();
            if (current == SimulationState.PAUSED) target.resume();
            else if (current == SimulationState.RUNNING) target.pause();
        });
    }

    private void stopSimulation() {
        SimulationEngine target = engine;
        if (target != null && !closed) commands.execute(target::stop);
    }

    private void analyzeAnother() {
        if (closed) return;
        SimulationEngine oldEngine = engine;
        SimulationSubscription oldSubscription = subscription;
        report.setAnotherPasswordEnabled(false);
        commands.execute(() -> {
            if (oldSubscription != null) oldSubscription.close();
            if (oldEngine != null) oldEngine.close();
            boolean clean = oldEngine == null || oldEngine.snapshot().cleanupReport()
                    .map(report -> report.terminationConfirmed()).orElse(false);
            Platform.runLater(() -> {
                if (closed) return;
                report.setAnotherPasswordEnabled(true);
                if (!clean) {
                    report.showCleanupWarning("Previous simulation worker termination was not confirmed. Restart is unavailable.");
                    return;
                }
                engine = null;
                subscription = null;
                passwordAnalysis = null;
                entry.clear();
                show(PresentationState.PASSWORD_ENTRY);
            });
        });
    }

    private void show(PresentationState state) {
        getChildren().setAll(switch (state) {
            case PASSWORD_ENTRY -> entry;
            case PROCESSING -> processing;
            case FINAL_RESULT -> report;
        });
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static long configuredCleanupWaitMillis() {
        return SimulationConfig.DEFAULT_CLEANUP_TIMEOUT.toMillis() + 1_000;
    }

    private record PendingSnapshot(long generation, SimulationEngineSnapshot snapshot) { }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        generation.incrementAndGet();
        if (processing != null) processing.close();
        commands.execute(() -> {
            SimulationSubscription currentSubscription = subscription;
            if (currentSubscription != null) currentSubscription.close();
            SimulationEngine currentEngine = engine;
            if (currentEngine != null) currentEngine.close();
        });
        commands.shutdown();
    }
}
