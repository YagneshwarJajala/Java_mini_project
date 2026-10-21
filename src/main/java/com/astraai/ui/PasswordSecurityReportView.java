package com.astraai.ui;

import com.astraai.simulation.SimulationEngineSnapshot;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.effect.DropShadow;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Final report for the local synthetic run; it makes no claims about discarded user input. */
public final class PasswordSecurityReportView extends javafx.scene.layout.BorderPane {
    private final List<AlgorithmInfoCard> algorithmCards = new ArrayList<>();
    private final Label subtitle = new Label("Local password heuristic · independent synthetic search");
    private final Label classification = new Label("Not assessed");
    private final Label lengthValue = body("Unavailable");
    private final Label uppercaseValue = body("Unavailable");
    private final Label lowercaseValue = body("Unavailable");
    private final Label digitsValue = body("Unavailable");
    private final Label symbolsValue = body("Unavailable");
    private final Label assessmentText = body("Password characteristics have not been calculated.");
    private final Label guessingText = body("Guessing approaches have not been assessed.");
    private final Label passwordRecommendation = body("Analyze a password to see recommendations.");
    private final List<Rectangle> strengthSegments = new ArrayList<>();
    private final Button anotherPassword = new Button("Analyze Another Password");
    private final Label syntheticOutcome = body("Synthetic search result unavailable until execution completes.");
    private final Label simulationMetrics = body("Simulation metrics unavailable.");
    private final Label agentWork = body("Logical-agent work unavailable.");
    private ResponsiveAssessmentLayout responsiveAssessmentLayout;

    public PasswordSecurityReportView(Runnable onAnotherPassword) {
        setStyle("-fx-background-color: linear-gradient(to bottom right, #ffffff 0%, #f8fafd 60%, #edf2f7 100%);");
        setTop(createHeader());

        VBox content = new VBox(11);
        content.setPadding(new Insets(12, 28, 20, 28));
        content.setMaxWidth(1160);
        content.setFillWidth(true);
        content.setAlignment(Pos.TOP_CENTER);
        content.getChildren().add(createStrengthPanel());
        syntheticOutcome.setWrapText(true);
        syntheticOutcome.setMaxWidth(Double.MAX_VALUE);
        syntheticOutcome.setPadding(new Insets(8, 12, 8, 12));
        syntheticOutcome.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 9px; "
                + "-fx-border-color: #e2e8f0; -fx-border-radius: 9px;");
        content.getChildren().add(syntheticOutcome);
        simulationMetrics.setWrapText(true);
        simulationMetrics.setMaxWidth(Double.MAX_VALUE);
        content.getChildren().add(simulationMetrics);
        agentWork.setWrapText(true);
        agentWork.setMaxWidth(Double.MAX_VALUE);
        content.getChildren().add(agentWork);

        Label algorithmsHeading = new Label("Hashing Algorithm Analysis");
        UiStyles.label(algorithmsHeading, "#151a24", 18, "bold");
        content.getChildren().add(algorithmsHeading);
        List<AlgorithmInfo> algorithms = AlgorithmCatalog.all();
        for (int index = 0; index < algorithms.size(); index++) {
            final int cardIndex = index;
            AlgorithmInfoCard card = new AlgorithmInfoCard(algorithms.get(index), index + 1);
            algorithmCards.add(card);
            card.setExpandedListener(expanded -> {
                if (expanded) {
                    for (int other = 0; other < algorithmCards.size(); other++) {
                        if (other != cardIndex) {
                            algorithmCards.get(other).setExpanded(false);
                        }
                    }
                }
            });
            content.getChildren().add(card);
        }

        UiStyles.button(anotherPassword, "linear-gradient(to bottom, #252b33, #11151b)",
                "#39424e", "#20252b", "white", 12, new Insets(11, 22, 11, 22), 24);
        anotherPassword.setOnAction(event -> onAnotherPassword.run());
        anotherPassword.setAccessibleHelp("Return to the entry screen after the previous engine is cleaned up.");
        HBox actionRow = new HBox(anotherPassword);
        actionRow.setAlignment(Pos.CENTER);
        VBox.setMargin(actionRow, new Insets(2, 0, 0, 0));
        content.getChildren().add(actionRow);

        VBox page = new VBox(content);
        page.setAlignment(Pos.TOP_CENTER);
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scroll.setPannable(true);
        StackPane centered = new StackPane(scroll);
        centered.setAlignment(Pos.TOP_CENTER);
        centered.setStyle("-fx-background-color: transparent;");
        setCenter(centered);
    }

    public void update(SimulationEngineSnapshot snapshot, PasswordAnalysis analysis) {
        SimulationResultSummary summary = SimulationResultSummary.from(snapshot);
        subtitle.setText("Local character heuristic · independent synthetic search · "
                + summary.logicalAgentCount() + " logical agents configured");
        updatePasswordAnalysis(analysis);
        syntheticOutcome.setText(formatOutcome(summary));
        simulationMetrics.setText(formatMetrics(summary));
        agentWork.setText(formatAgentWork(summary));
    }

    static String formatOutcome(SimulationResultSummary summary) {
        if (summary.outcome().isEmpty()) {
            return "Synthetic search · terminal state " + summary.terminalState().name().toLowerCase(Locale.ROOT)
                    + " · result unavailable · validated execution evidence is missing"
                    + " · this independent synthetic search cannot establish whether the submitted password was cracked or would resist guessing";
        }
        String outcome = switch (summary.outcome().orElseThrow()) {
            case MATCH -> "Synthetic search · target discovered · "
                    + summary.discoveredCandidate().orElse("candidate evidence unavailable");
            case EXHAUSTED -> "Synthetic search · candidate space exhausted · target not found";
            case STOPPED -> "Synthetic search · stopped · target discovery is unconfirmed";
            case FAILED -> "Synthetic search · failed · target discovery is unconfirmed";
        };
        return outcome + " · terminal state " + summary.terminalState().name().toLowerCase(Locale.ROOT)
                + " · this synthetic candidate search is independent of the submitted password and cannot show that it was cracked, resisted an attack, or would take a particular time to guess";
    }

    static String formatMetrics(SimulationResultSummary summary) {
        String completed = summary.completedEvaluations().isPresent()
                ? Long.toString(summary.completedEvaluations().getAsLong()) : "unavailable (missing evidence)";
        String progress = summary.progress().isPresent()
                ? String.format(Locale.ROOT, "%.1f%%", summary.progress().getAsDouble() * 100)
                : "unavailable";
        String elapsed = summary.elapsedTime().map(UiFormatting::formatDuration).orElse("unavailable");
        String throughput = summary.evaluationsPerSecond().isPresent()
                ? String.format(Locale.ROOT, "%.2f evaluations/s", summary.evaluationsPerSecond().getAsDouble())
                : "unavailable";
        String cleanup = summary.cleanupReport().map(report -> report.terminationConfirmed()
                ? "confirmed" : "not confirmed").orElse("unavailable");
        StringBuilder metrics = new StringBuilder()
                .append("Completed evaluations: ").append(completed).append(" / ").append(summary.searchSpaceSize())
                .append(" · progress: ").append(progress)
                .append(" · elapsed: ").append(elapsed)
                .append(" · measured throughput: ").append(throughput)
                .append(" · worker termination: ").append(cleanup);
        summary.diagnostic().ifPresent(diagnostic -> metrics.append(" · diagnostic: ").append(diagnostic));
        return metrics.toString();
    }

    static String formatAgentWork(SimulationResultSummary summary) {
        if (summary.outcome().isEmpty()) {
            return "Logical-agent work unavailable (execution report missing).";
        }
        long assignedAgents = summary.agentWork().stream().filter(agent -> !agent.assignedTasks().isEmpty()).count();
        long assignedTasks = summary.agentWork().stream().mapToLong(agent -> agent.assignedTasks().size()).sum();
        List<Long> contributingCounts = summary.agentWork().stream()
                .map(SimulationResultSummary.AgentWork::completedEvaluations)
                .filter(count -> count > 0).sorted().toList();
        long totalAgentEvaluations = contributingCounts.stream().mapToLong(Long::longValue).sum();
        String contributions = contributingCounts.isEmpty() ? "no completed evaluations recorded by agents"
                : contributingCounts.size() + " agents recorded evaluations (" + totalAgentEvaluations
                        + " total; " + contributingCounts.get(0) + "–"
                        + contributingCounts.get(contributingCounts.size() - 1) + " per contributing agent)";
        return "Logical-agent work (agents are not worker threads) · " + summary.logicalAgentCount()
                + " configured · " + assignedTasks + " tasks assigned across " + assignedAgents + " agents · "
                + contributions;
    }
    public Button anotherPasswordButton() {
        return anotherPassword;
    }

    public void setAnotherPasswordEnabled(boolean enabled) {
        anotherPassword.setDisable(!enabled);
    }

    public void showCleanupWarning(String message) {
        syntheticOutcome.setText(syntheticOutcome.getText() + " · " + message);
        anotherPassword.setDisable(true);
    }

    private Node createHeader() {
        HBox bar = new HBox(9, createBrandMark(), new Label("Astra AI"));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 22, 8, 22));
        bar.setStyle("-fx-background-color: rgba(255,255,255,0.78); "
                + "-fx-border-color: transparent transparent #e7ebef transparent; -fx-border-width: 0 0 1px 0;");
        UiStyles.label((Label) bar.getChildren().get(1), "#171b20", 14, "bold");

        Label title = new Label("Password Security Report");
        UiStyles.label(title, "#111621", 24, "bold");
        UiStyles.label(subtitle, "#52627a", 13, "normal");
        VBox heading = new VBox(2, title, subtitle);
        heading.setAlignment(Pos.CENTER);
        VBox header = new VBox(8, bar, heading);
        VBox.setMargin(heading, new Insets(7, 10, 0, 10));
        return header;
    }

    private Node createStrengthPanel() {
        Label title = new Label("Password Strength Analysis");
        UiStyles.label(title, "#171b20", 16, "bold");

        Label classificationTitle = smallHeading("Overall Strength");
        classification.setText("Not assessed");
        UiStyles.label(classification, "#5a6674", 20, "bold");
        HBox strengthMeter = new HBox(4);
        for (int index = 0; index < 6; index++) {
            Rectangle segment = new Rectangle(44, 9, Color.web("#dce2e8"));
            segment.setArcWidth(8);
            segment.setArcHeight(8);
            strengthSegments.add(segment);
            strengthMeter.getChildren().add(segment);
        }
        VBox strength = new VBox(9, classificationTitle, classification, strengthMeter);
        strength.setMinWidth(205);

        VBox characteristics = new VBox(6,
                smallHeading("Detected characteristics"),
                characteristic("Length", lengthValue),
                characteristic("Uppercase letters", uppercaseValue),
                characteristic("Lowercase letters", lowercaseValue),
                characteristic("Digits", digitsValue),
                characteristic("Symbols", symbolsValue));
        VBox assessment = new VBox(8,
                smallHeading("Why this password received this assessment"), assessmentText,
                smallHeading("How it could be guessed"), guessingText);
        characteristics.setMinWidth(230);
        characteristics.setFillWidth(true);
        assessment.setMinWidth(260);
        assessment.setMaxWidth(Double.MAX_VALUE);
        assessmentText.setMinWidth(0);
        assessmentText.setMaxWidth(Double.MAX_VALUE);
        assessmentText.setWrapText(true);
        assessmentText.setMinHeight(Region.USE_PREF_SIZE);
        guessingText.setMinWidth(0);
        guessingText.setMaxWidth(Double.MAX_VALUE);
        guessingText.setWrapText(true);
        guessingText.setMinHeight(Region.USE_PREF_SIZE);
        Region firstDivider = verticalDivider();
        Region secondDivider = verticalDivider();
        HBox columns = new HBox(20, strength, firstDivider, characteristics, secondDivider, assessment);
        columns.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(assessment, Priority.ALWAYS);
        VBox.setVgrow(assessment, Priority.NEVER);
        Region horizontalDivider = new Region();
        horizontalDivider.setMinHeight(1);
        horizontalDivider.setPrefHeight(1);
        horizontalDivider.setStyle("-fx-background-color: #dde4ec;");
        // JavaFX nodes can have only one parent. Keep the alternate container
        // empty until the responsive switch, otherwise constructing it removes
        // these controls from the initially active horizontal layout.
        VBox compactColumns = new VBox(11);
        compactColumns.setFillWidth(true);

        Label recommendationTitle = smallHeading("Recommendation");
        HBox recommendationStrip = new HBox(12, infoIcon(), new VBox(3, recommendationTitle, passwordRecommendation));
        recommendationStrip.setAlignment(Pos.CENTER_LEFT);
        recommendationStrip.setPadding(new Insets(10, 13, 10, 13));
        recommendationStrip.setStyle("-fx-background-color: #eef5fd; -fx-background-radius: 11px;");

        VBox panel = new VBox(12, title, columns, recommendationStrip);
        panel.widthProperty().addListener((observable, oldWidth, newWidth) -> {
            boolean useCompact = newWidth.doubleValue() < 960;
            Node current = panel.getChildren().get(1);
            if (useCompact && current != compactColumns) {
                columns.getChildren().clear();
                compactColumns.getChildren().setAll(strength, horizontalDivider, characteristics, assessment);
                panel.getChildren().set(1, compactColumns);
            } else if (!useCompact && current != columns) {
                compactColumns.getChildren().clear();
                columns.getChildren().setAll(strength, firstDivider, characteristics, secondDivider, assessment);
                panel.getChildren().set(1, columns);
            }
        });
        panel.setPadding(new Insets(13, 15, 13, 15));
        panel.setStyle("-fx-background-color: rgba(255,255,255,0.88); -fx-background-radius: 14px; "
                + "-fx-border-color: #e8edf2; -fx-border-radius: 14px;");
        panel.setEffect(new DropShadow(12, 0, 4, Color.rgb(42, 62, 85, 0.08)));
        responsiveAssessmentLayout = new ResponsiveAssessmentLayout(columns, compactColumns, characteristics, assessment);
        return panel;
    }

    boolean assessmentAttachedToActiveLayout() {
        if (responsiveAssessmentLayout == null) return false;
        ResponsiveAssessmentLayout layout = responsiveAssessmentLayout;
        Node container = layout.assessment().getParent();
        return container == layout.horizontal() && layout.horizontal().getChildren().contains(layout.assessment())
                || container == layout.compact() && layout.compact().getChildren().contains(layout.assessment());
    }

    boolean characteristicsColumnHasUsableWidth() {
        return responsiveAssessmentLayout != null && responsiveAssessmentLayout.characteristics().getWidth() >= 200;
    }

    private record ResponsiveAssessmentLayout(HBox horizontal, VBox compact, VBox characteristics, VBox assessment) { }

    private static HBox characteristic(String name, Label result) {
        Label label = body(name);
        label.setMinWidth(145);
        label.setWrapText(false);
        result.setMinWidth(72);
        result.setWrapText(false);
        HBox row = new HBox(12, label, result);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(label, Priority.ALWAYS);
        return row;
    }

    void updatePasswordAnalysis(PasswordAnalysis analysis) {
        if (analysis == null) {
            classification.setText("Not assessed");
            for (Label label : List.of(lengthValue, uppercaseValue, lowercaseValue, digitsValue, symbolsValue)) {
                label.setText("Unavailable");
            }
            assessmentText.setText("No local password characteristics are available. The synthetic challenge result is separate.");
            guessingText.setText("No password-specific guessing observations are available.");
            passwordRecommendation.setText("No password-specific recommendation is available.");
            classification.setStyle("-fx-text-fill: #5a6674; -fx-font-size: 20px; -fx-font-weight: bold;");
            strengthSegments.forEach(segment -> segment.setFill(Color.web("#dce2e8")));
            return;
        }
        PasswordAnalysis.Strength strength = analysis.strength();
        classification.setText(strength.label());
        classification.setStyle("-fx-text-fill: " + strength.color() + "; -fx-font-size: 20px; -fx-font-weight: bold;");
        lengthValue.setText("%d characters".formatted(analysis.length()));
        uppercaseValue.setText("%s (%d)".formatted(yesNo(analysis.uppercaseCount()), analysis.uppercaseCount()));
        lowercaseValue.setText("%s (%d)".formatted(yesNo(analysis.lowercaseCount()), analysis.lowercaseCount()));
        digitsValue.setText("%s (%d)".formatted(yesNo(analysis.digitCount()), analysis.digitCount()));
        symbolsValue.setText("%s (%d)".formatted(yesNo(analysis.symbolCount()), analysis.symbolCount()));
        PasswordSecurityGuidance guidance = PasswordSecurityGuidance.from(analysis);
        assessmentText.setText(analysis.explanation() + "\n" + guidance.assessmentText());
        guessingText.setText(guidance.guessingText());
        passwordRecommendation.setText(guidance.recommendationText());
        for (int index = 0; index < strengthSegments.size(); index++) {
            strengthSegments.get(index).setFill(index < strength.filledSegments()
                    ? Color.web(strength.color()) : Color.web("#dce2e8"));
        }
    }

    String displayedAssessmentText() {
        return assessmentText.getText();
    }

    String displayedClassification() {
        return classification.getText();
    }

    String displayedRecommendation() {
        return passwordRecommendation.getText();
    }

    String displayedGuessingText() {
        return guessingText.getText();
    }

    private static String yesNo(int count) {
        return count > 0 ? "Yes" : "No";
    }

    private static Label smallHeading(String text) {
        Label label = new Label(text);
        UiStyles.label(label, "#232a33", 11, "bold");
        return label;
    }

    private static Label body(String text) {
        Label label = new Label(text);
        UiStyles.label(label, "#53627a", 11, "normal");
        label.setWrapText(true);
        return label;
    }

    private static Region verticalDivider() {
        Region divider = new Region();
        divider.setMinWidth(1);
        divider.setPrefWidth(1);
        divider.setMaxWidth(1);
        divider.setMinHeight(68);
        divider.setStyle("-fx-background-color: #dde4ec;");
        return divider;
    }

    private static Node infoIcon() {
        Label icon = new Label("i");
        icon.setAlignment(Pos.CENTER);
        icon.setMinSize(25, 25);
        icon.setPrefSize(25, 25);
        icon.setStyle("-fx-border-color: #2581e8; -fx-border-radius: 50%; -fx-border-width: 1.5px; "
                + "-fx-text-fill: #2581e8; -fx-font-size: 15px; -fx-font-weight: bold;");
        return icon;
    }

    private static Node createBrandMark() {
        Polygon mark = new Polygon(9.0, 24.0, 17.0, 7.0, 25.0, 24.0, 20.0, 24.0, 17.0, 17.0, 14.0, 24.0);
        mark.setFill(Color.web("#20252c"));
        StackPane holder = new StackPane(mark);
        holder.setMinSize(26, 27);
        holder.setPrefSize(26, 27);
        holder.setMaxSize(26, 27);
        return holder;
    }
}
