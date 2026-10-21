package com.astraai.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.astraai.simulation.SimulationEngineSnapshot;
import com.astraai.simulation.SimulationConfig;
import com.astraai.simulation.SimulationEngine;
import com.astraai.simulation.SimulationState;
import com.astraai.simulation.SyntheticChallenge;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;

class PasswordSecurityReportViewTest {
    @Test
    void passwordAssessmentControlsRemainAttachedAndUpdateForEachAnalysis() throws Exception {
        runOnFxThread(() -> {
            PasswordSecurityReportView view = new PasswordSecurityReportView(() -> { });
            StackPane root = new StackPane(view);
            new Scene(root, 1280, 900);
            root.applyCss();
            root.resize(1280, 900);
            root.layout();

            PasswordAnalysis first = PasswordInputPolicy.analyzeAndErase("example-passphrase".toCharArray())
                    .orElseThrow();
            view.updatePasswordAnalysis(first);
            root.applyCss();
            root.layout();
            assertTrue(view.assessmentAttachedToActiveLayout(), "assessment must be a child of the active responsive layout");
            String firstAssessment = view.displayedAssessmentText();
            assertFalse(firstAssessment.isBlank());
            assertTrue(view.displayedClassification().equals(first.strength().label()));
            assertTrue(firstAssessment.contains("Strengths:"));
            assertTrue(firstAssessment.contains("Weaknesses and limits:"));
            assertTrue(firstAssessment.contains(first.explanation()));
            assertFalse(view.displayedRecommendation().isBlank());
            assertTrue(view.displayedGuessingText().contains("Brute-force guessing"));
            assertTrue(view.characteristicsColumnHasUsableWidth());

            PasswordAnalysis second = PasswordInputPolicy.analyzeAndErase("aaaaaaa".toCharArray()).orElseThrow();
            view.updatePasswordAnalysis(second);
            assertNotEquals(firstAssessment, view.displayedAssessmentText());
            assertTrue(view.displayedAssessmentText().contains(second.explanation()));
            assertEquals(second.strength().label(), view.displayedClassification());
            assertTrue(view.displayedGuessingText().contains("Pattern-based guessing"));

            root.resize(720, 900);
            root.layout();
            assertTrue(view.assessmentAttachedToActiveLayout(), "assessment must remain attached after responsive switch");
            assertTrue(view.characteristicsColumnHasUsableWidth(), "characteristics must not collapse at narrow widths");

            AlgorithmInfo argon = AlgorithmCatalog.all().get(0);
            AlgorithmInfoCard argonCard = new AlgorithmInfoCard(argon, 1);
            argonCard.setExpanded(true);
            assertTrue(argonCard.detailsVisible());
            assertTrue(argonCard.expandedDetailsText().contains("How it works:"));
            assertTrue(argonCard.expandedDetailsText().contains("Preferred starting point"));
            AlgorithmInfoCard sha3Card = new AlgorithmInfoCard(AlgorithmCatalog.all().get(4), 5);
            sha3Card.setExpanded(true);
            assertTrue(sha3Card.expandedDetailsText().contains("plain SHA-256"));
            return null;
        });
    }

    @Test
    void missingResultFormattingHasNoValuesFromAPreviousRun() throws Exception {
        SimulationConfig matchConfig = config();
        SimulationResultSummary priorRun;
        try (SimulationEngine engine = new SimulationEngine(matchConfig, new SyntheticChallenge(matchConfig))) {
            priorRun = SimulationResultSummary.from(engine.start().completion().toCompletableFuture()
                    .get(5, java.util.concurrent.TimeUnit.SECONDS));
        }
        SimulationEngineSnapshot missingEvidence = new SimulationEngineSnapshot(SimulationState.FAILED, 1,
                100, 2, 20, 0, Optional.of(Duration.ofSeconds(1)), OptionalDouble.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of("no execution report"), false);
        SimulationResultSummary summary = SimulationResultSummary.from(missingEvidence);

        String outcome = PasswordSecurityReportView.formatOutcome(summary);
        String metrics = PasswordSecurityReportView.formatMetrics(summary);
        String agentWork = PasswordSecurityReportView.formatAgentWork(summary);

        assertTrue(PasswordSecurityReportView.formatOutcome(priorRun).contains("CANDIDATE-00007"));
        assertTrue(outcome.contains("result unavailable"));
        assertTrue(metrics.contains("unavailable (missing evidence)"));
        assertTrue(metrics.contains("progress: unavailable"));
        assertTrue(metrics.contains("measured throughput: unavailable"));
        assertTrue(agentWork.contains("execution report missing"));
        assertFalse(outcome.contains("CANDIDATE-"));
    }

    @Test
    void exhaustionCopyDoesNotDescribeTheRunAsSuccessful() throws Exception {
        SimulationConfig config = config();
        SimulationResultSummary exhausted;
        try (SimulationEngine engine = new SimulationEngine(config, SyntheticChallenge.withoutMatch(config))) {
            exhausted = SimulationResultSummary.from(engine.start().completion().toCompletableFuture()
                    .get(5, java.util.concurrent.TimeUnit.SECONDS));
        }
        String text = PasswordSecurityReportView.formatOutcome(exhausted);
        assertTrue(text.contains("candidate space exhausted"));
        assertTrue(text.contains("target not found"));
        assertFalse(text.toLowerCase().contains("success"));
        assertTrue(text.contains("independent of the submitted password"));
        assertTrue(text.contains("cannot show that it was cracked"));
    }

    @Test
    void formattedMetricsAndAgentWorkMatchTheExecutionReport() throws Exception {
        SimulationConfig config = config();
        try (SimulationEngine engine = new SimulationEngine(config, new SyntheticChallenge(config))) {
            SimulationEngineSnapshot terminal = engine.start().completion().toCompletableFuture()
                    .get(5, java.util.concurrent.TimeUnit.SECONDS);
            SimulationResultSummary summary = SimulationResultSummary.from(terminal);
            long completed = terminal.executionReport().orElseThrow().completedEvaluations();
            String metrics = PasswordSecurityReportView.formatMetrics(summary);
            String work = PasswordSecurityReportView.formatAgentWork(summary);

            assertTrue(metrics.contains("Completed evaluations: " + completed + " / " + config.candidateCount()));
            assertTrue(metrics.contains("progress: " + String.format(java.util.Locale.ROOT, "%.1f%%",
                    (double) completed / config.candidateCount() * 100)));
            assertTrue(metrics.contains("measured throughput: " + String.format(java.util.Locale.ROOT,
                    "%.2f evaluations/s", summary.evaluationsPerSecond().orElseThrow())));
            String outcome = PasswordSecurityReportView.formatOutcome(summary);
            assertTrue(outcome.contains("independent of the submitted password"));
            assertFalse(outcome.contains("password was cracked"));
            assertTrue(work.contains("Logical-agent work (agents are not worker threads)"));
            assertTrue(work.contains(summary.logicalAgentCount() + " configured"));
            long assignedTasks = summary.agentWork().stream().mapToLong(agent -> agent.assignedTasks().size()).sum();
            long assignedAgents = summary.agentWork().stream().filter(agent -> !agent.assignedTasks().isEmpty()).count();
            assertTrue(work.contains(assignedTasks + " tasks assigned across " + assignedAgents + " agents"));
            assertFalse(work.contains("\n"));
        }
    }

    private static SimulationConfig config() {
        return SimulationConfig.forTesting(2, 20, 4, 7, 2, Duration.ofSeconds(2));
    }

    private static <T> T runOnFxThread(java.util.concurrent.Callable<T> action) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
            assertTrue(started.await(10, TimeUnit.SECONDS), "JavaFX toolkit should start");
        } catch (IllegalStateException alreadyStarted) {
            // Another test or application bootstrap already initialized JavaFX.
        }
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
