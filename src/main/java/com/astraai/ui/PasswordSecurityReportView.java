package com.astraai.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
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

/** Final password assessment and educational KDF reference. */
public final class PasswordSecurityReportView extends javafx.scene.layout.BorderPane {
    private final List<AlgorithmInfoCard> algorithmCards = new ArrayList<>();
    private final Label subtitle = new Label("Local password heuristic · educational hashing guidance");
    private final Label classification = new Label("Not assessed");
    private final Label lengthValue = body("Unavailable");
    private final Label uppercaseValue = body("Unavailable");
    private final Label lowercaseValue = body("Unavailable");
    private final Label digitsValue = body("Unavailable");
    private final Label symbolsValue = body("Unavailable");
    private final Label analysisExplanation = body("Password characteristics have not been calculated.");
    private final Label keyFindings = body("Observed strengths and weaknesses are unavailable.");
    private final VBox strengthFindings = new VBox(3);
    private final VBox weaknessFindings = new VBox(3);
    private final Label heuristicLimitations = body("The local heuristic has not been run.");
    private final VBox attackFindings = new VBox(4);
    private final Label passwordRecommendation = body("Analyze a password to see recommendations.");
    private final Label interpretation = body("Recommendation: Argon2id is a strong modern starting point when a maintained implementation and its memory/latency requirements fit. Use a unique random salt and parameters calibrated for the deployment. bcrypt, scrypt, or PBKDF2 may better fit compatibility or compliance constraints. The SHA-3 entry is not specified enough to recommend for password storage. PassScope did not store or hash the submitted password.");
    private String displayedAssessment = "Password characteristics have not been calculated.";
    private String displayedGuessing = "Guessing approaches have not been assessed.";
    private final List<Rectangle> strengthSegments = new ArrayList<>();
    private final Button anotherPassword = new Button("Analyze Another Password");
    private final ScrollPane reportScroll = new ScrollPane();
    private ResponsiveAssessmentLayout responsiveAssessmentLayout;

    public PasswordSecurityReportView(Runnable onAnotherPassword) {
        setStyle("-fx-background-color: linear-gradient(to bottom right, #ffffff 0%, #f8fafd 60%, #edf2f7 100%);");
        setTop(createHeader());

        VBox content = new VBox(16);
        content.setPadding(new Insets(14, 26, 22, 26));
        content.setMinWidth(0);
        content.setPrefWidth(0);
        content.setMaxWidth(1160);
        content.setFillWidth(true);
        content.setAlignment(Pos.TOP_LEFT);
        content.getChildren().add(createStrengthPanel());

        Label algorithmsHeading = new Label("Hashing Algorithm Analysis");
        UiStyles.label(algorithmsHeading, "#202b36", 23, "bold");
        Label algorithmIntroduction = body("Approximate times use an explicitly illustrative offline rate and a simplified candidate-space model from local character counts and detected patterns. "
                + "They are not predictions, true entropy, or actual attacker ordering. The 100 logical agents and synthetic throughput are unrelated and are not used. "
                + "PassScope does not hash, guess, or benchmark the submitted password. SHA-3 remains not estimable because no concrete password KDF is specified.");
        algorithmIntroduction.setMinWidth(0);
        algorithmIntroduction.setMaxWidth(Double.MAX_VALUE);
        VBox algorithmSection = new VBox(12,
                sectionHeader("ALGORITHM ANALYSIS", algorithmsHeading),
                algorithmIntroduction);
        algorithmSection.setMinWidth(0);
        algorithmSection.setPadding(new Insets(17, 18, 18, 18));
        algorithmSection.setStyle("-fx-background-color: #eef3f5; -fx-background-radius: 14px; "
                + "-fx-border-color: #dce5e9; -fx-border-radius: 14px;");
        content.getChildren().add(algorithmSection);
        List<AlgorithmInfo> algorithms = AlgorithmCatalog.all();
        VBox algorithmDetails = new VBox(8);
        algorithmDetails.setMinWidth(0);
        algorithmDetails.setMaxWidth(Double.MAX_VALUE);
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
            algorithmDetails.getChildren().add(card);
        }
        algorithmSection.getChildren().add(algorithmDetails);

        UiStyles.button(anotherPassword, "linear-gradient(to bottom, #252b33, #11151b)",
                "#39424e", "#20252b", "white", 12, new Insets(11, 22, 11, 22), 24);
        anotherPassword.setOnAction(event -> onAnotherPassword.run());
        anotherPassword.setAccessibleHelp("Return to the entry screen after the previous engine is cleaned up.");
        interpretation.setMaxWidth(Double.MAX_VALUE);
        interpretation.setPadding(new Insets(12, 15, 12, 15));
        interpretation.setStyle("-fx-text-fill: #33454f; -fx-font-size: 13px; "
                + "-fx-background-color: #eaf0f3; -fx-background-radius: 10px; "
                + "-fx-border-color: #d9e3e8; -fx-border-radius: 10px;");
        content.getChildren().add(interpretation);
        HBox actionRow = new HBox(anotherPassword);
        actionRow.setAlignment(Pos.CENTER);
        VBox.setMargin(actionRow, new Insets(8, 0, 8, 0));
        content.getChildren().add(actionRow);

        VBox page = new VBox(content);
        page.setMinWidth(0);
        page.setPrefWidth(0);
        page.setMaxWidth(Double.MAX_VALUE);
        page.setAlignment(Pos.TOP_CENTER);
        reportScroll.setContent(page);
        reportScroll.setMinWidth(0);
        reportScroll.setMaxWidth(Double.MAX_VALUE);
        reportScroll.setFitToWidth(true);
        reportScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        reportScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        reportScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        reportScroll.setPannable(true);
        reportScroll.setFocusTraversable(true);
        reportScroll.setAccessibleText("Password security report. Use Tab to navigate the report.");
        StackPane centered = new StackPane(reportScroll);
        centered.setMinWidth(0);
        centered.setMaxWidth(Double.MAX_VALUE);
        centered.setAlignment(Pos.TOP_CENTER);
        centered.setStyle("-fx-background-color: transparent;");
        setCenter(centered);
    }

    private static HBox sectionHeader(String eyebrow, Label title) {
        Label overline = new Label(eyebrow);
        UiStyles.label(overline, "#607480", 10, "bold");
        VBox titles = title == null ? new VBox(overline) : new VBox(2, overline, title);
        HBox header = new HBox(titles);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    public void update(PasswordAnalysis analysis) {
        subtitle.setText("Local password heuristic · educational hashing guidance");
        updatePasswordAnalysis(analysis);
        reportScroll.setVvalue(0);
        Platform.runLater(() -> {
            reportScroll.setVvalue(reportScroll.getVmin());
            reportScroll.requestFocus();
        });
    }

    public Button anotherPasswordButton() {
        return anotherPassword;
    }

    public void setAnotherPasswordEnabled(boolean enabled) {
        anotherPassword.setDisable(!enabled);
    }

    public void showCleanupWarning(String message) {
        anotherPassword.setDisable(true);
        anotherPassword.setAccessibleHelp("Restart is unavailable: " + message);
        anotherPassword.setTooltip(new Tooltip(message));
    }

    private Node createHeader() {
        HBox bar = new HBox(9, createBrandMark(), new Label("PassScope"));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 24, 8, 24));
        bar.setStyle("-fx-background-color: rgba(255,255,255,0.78); "
                + "-fx-border-color: transparent transparent #e7ebef transparent; -fx-border-width: 0 0 1px 0;");
        UiStyles.label((Label) bar.getChildren().get(1), "#25343f", 14, "bold");

        Label title = new Label("Password Security Report");
        UiStyles.label(title, "#202b36", 23, "bold");
        UiStyles.label(subtitle, "#4e606d", 12, "normal");
        VBox heading = new VBox(2, title, subtitle);
        heading.setAlignment(Pos.CENTER_LEFT);
        VBox header = new VBox(8, bar, heading);
        VBox.setMargin(heading, new Insets(10, 26, 0, 26));
        header.setStyle("-fx-background-color: #f5f8fa;");
        return header;
    }

    private Node createStrengthPanel() {
        Label title = new Label("Password Strength Analysis");
        UiStyles.label(title, "#202b36", 18, "bold");

        Label classificationTitle = smallHeading("Overall Strength");
        classification.setText("Not assessed");
        UiStyles.label(classification, "#f5f8fa", 23, "bold");
        HBox strengthMeter = new HBox(4);
        for (int index = 0; index < 6; index++) {
            Rectangle segment = new Rectangle(28, 7, Color.web("#dce2e8"));
            segment.setArcWidth(8);
            segment.setArcHeight(8);
            strengthSegments.add(segment);
            strengthMeter.getChildren().add(segment);
        }
        VBox strength = new VBox(10, classificationTitle, classification, strengthMeter);
        strength.setAlignment(Pos.TOP_LEFT);
        strength.setMinWidth(195);
        strength.setMaxWidth(225);
        strength.setPadding(new Insets(14, 15, 14, 15));
        strength.setStyle("-fx-background-color: #203746; -fx-background-radius: 11px; "
                + "-fx-border-color: #3c5c6b; -fx-border-radius: 11px;");
        classification.setPadding(new Insets(2, 0, 2, 0));
        classification.setStyle("-fx-text-fill: #f5f8fa; -fx-font-size: 23px; -fx-font-weight: bold;");

        VBox characteristics = new VBox(6,
                smallHeading("Detected characteristics"),
                characteristic("Length", lengthValue),
                characteristic("Uppercase letters", uppercaseValue),
                characteristic("Lowercase letters", lowercaseValue),
                characteristic("Digits", digitsValue),
                characteristic("Symbols", symbolsValue));
        TitledPane details = new TitledPane("Strengths, weaknesses and heuristic limits",
                new VBox(8,
                        smallHeading("Observed strengths"), strengthFindings,
                        smallHeading("Observed weaknesses"), weaknessFindings,
                        smallHeading("Limits of this local heuristic"), heuristicLimitations,
                        smallHeading("Attack concepts · not performed"), attackFindings));
        details.setExpanded(false);
        details.setAnimated(false);
        details.setMaxWidth(Double.MAX_VALUE);
        details.setStyle("-fx-text-fill: #263844; -fx-font-size: 11px;");
        VBox assessment = new VBox(8,
                smallHeading("Why this assessment"),
                analysisExplanation,
                keyFindings,
                details);
        characteristics.setMinWidth(230);
        characteristics.setPrefWidth(280);
        characteristics.setMaxWidth(Double.MAX_VALUE);
        characteristics.setFillWidth(true);
        assessment.setMinWidth(260);
        assessment.setPrefWidth(420);
        assessment.setMaxWidth(Double.MAX_VALUE);
        assessment.setPadding(new Insets(2, 0, 0, 0));
        analysisExplanation.setMinWidth(0);
        analysisExplanation.setMaxWidth(Double.MAX_VALUE);
        keyFindings.setMinWidth(0);
        keyFindings.setMaxWidth(Double.MAX_VALUE);
        keyFindings.setStyle("-fx-text-fill: #344955; -fx-font-size: 11px; -fx-font-weight: bold;");
        heuristicLimitations.setMinWidth(0);
        heuristicLimitations.setMaxWidth(Double.MAX_VALUE);
        strengthFindings.setFillWidth(true);
        weaknessFindings.setFillWidth(true);
        attackFindings.setFillWidth(true);
        Region firstDivider = verticalDivider();
        Region secondDivider = verticalDivider();
        HBox columns = new HBox(18, strength, firstDivider, characteristics, secondDivider, assessment);
        columns.setAlignment(Pos.CENTER_LEFT);
        columns.setFillHeight(true);
        columns.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(strength, Priority.NEVER);
        HBox.setHgrow(characteristics, Priority.ALWAYS);
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
        compactColumns.setMaxWidth(Double.MAX_VALUE);

        Label recommendationTitle = smallHeading("Recommended next step");
        HBox recommendationStrip = new HBox(12, infoIcon(), new VBox(3, recommendationTitle, passwordRecommendation));
        recommendationStrip.setAlignment(Pos.CENTER_LEFT);
        recommendationStrip.setPadding(new Insets(11, 14, 11, 14));
        recommendationStrip.setStyle("-fx-background-color: #edf4f2; -fx-background-radius: 10px; "
                + "-fx-border-color: #d9e8e3; -fx-border-radius: 10px;");

        VBox panel = new VBox(13, sectionHeader("PASSWORD ANALYSIS", title), columns, recommendationStrip);
        panel.setMinWidth(0);
        panel.setFillWidth(true);
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
            if (!useCompact) {
                HBox.setHgrow(characteristics, Priority.ALWAYS);
                HBox.setHgrow(assessment, Priority.ALWAYS);
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

    double characteristicsColumnWidth() {
        return responsiveAssessmentLayout == null ? 0 : responsiveAssessmentLayout.characteristics().getWidth();
    }

    double assessmentColumnWidth() {
        return responsiveAssessmentLayout == null ? 0 : responsiveAssessmentLayout.assessment().getWidth();
    }

    boolean reportContentFitsViewport() {
        Node content = reportScroll.getContent();
        if (!(content instanceof VBox page) || page.getChildren().isEmpty()) {
            return false;
        }
        Node pageContent = page.getChildren().get(0);
        double viewportWidth = reportScroll.getViewportBounds().getWidth();
        return page.getWidth() <= viewportWidth + 1 && pageContent.getBoundsInParent().getWidth() <= page.getWidth() + 1;
    }

    boolean analysisColumnsFitTheirLayout() {
        if (responsiveAssessmentLayout == null) {
            return false;
        }
        Node parent = responsiveAssessmentLayout.assessment().getParent();
        if (parent instanceof HBox row) {
            return row.getChildren().stream().filter(Node::isManaged)
                    .allMatch(child -> child.getBoundsInParent().getMaxX() <= row.getWidth() + 1);
        }
        if (parent instanceof VBox column) {
            return column.getChildren().stream().filter(Node::isManaged)
                    .allMatch(child -> child.getBoundsInParent().getMaxX() <= column.getWidth() + 1);
        }
        return false;
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
        algorithmCards.forEach(card -> card.updateCrackTimeEstimate(analysis));
        if (analysis == null) {
            classification.setText("Not assessed");
            for (Label label : List.of(lengthValue, uppercaseValue, lowercaseValue, digitsValue, symbolsValue)) {
                label.setText("Unavailable");
            }
            analysisExplanation.setText("No local password characteristics are available. The synthetic challenge result is separate.");
            strengthFindings.getChildren().setAll(findingLabel("No observed strengths are available."));
            weaknessFindings.getChildren().setAll(findingLabel("No observed weaknesses are available."));
            heuristicLimitations.setText("No analysis is available; no password-specific claim can be made.");
            keyFindings.setText("Observed strengths and weaknesses are unavailable.");
            attackFindings.getChildren().setAll(findingLabel("No password-specific observations are available; the educational concepts below were not tested against a password."));
            displayedAssessment = analysisExplanation.getText();
            displayedGuessing = "No password-specific guessing observations are available.";
            passwordRecommendation.setText("No password-specific recommendation is available.");
            setClassificationStyle("#5a6674");
            strengthSegments.forEach(segment -> segment.setFill(Color.web("#dce2e8")));
            return;
        }
        PasswordAnalysis.Strength strength = analysis.strength();
        classification.setText(strength.label());
        setClassificationStyle(strength.color());
        lengthValue.setText("%d characters".formatted(analysis.length()));
        uppercaseValue.setText("%s (%d)".formatted(yesNo(analysis.uppercaseCount()), analysis.uppercaseCount()));
        lowercaseValue.setText("%s (%d)".formatted(yesNo(analysis.lowercaseCount()), analysis.lowercaseCount()));
        digitsValue.setText("%s (%d)".formatted(yesNo(analysis.digitCount()), analysis.digitCount()));
        symbolsValue.setText("%s (%d)".formatted(yesNo(analysis.symbolCount()), analysis.symbolCount()));
        PasswordSecurityGuidance guidance = PasswordSecurityGuidance.from(analysis);
        analysisExplanation.setText(analysis.explanation());
        strengthFindings.getChildren().setAll(guidance.strengths().stream().map(PasswordSecurityReportView::findingLabel).toList());
        weaknessFindings.getChildren().setAll(guidance.weaknesses().stream().map(PasswordSecurityReportView::findingLabel).toList());
        String leadingStrength = guidance.strengths().stream().findFirst().orElse("No clear strength was identified.");
        String weakness = guidance.weaknesses().stream().findFirst().orElse("No specific weakness was identified.");
        keyFindings.setText("Strength: " + leadingStrength + "  ·  Consider: " + weakness);
        heuristicLimitations.setText(guidance.limitationsText());
        attackFindings.getChildren().setAll(guidance.attackExplanations().stream()
                .map(PasswordSecurityReportView::findingLabel).toList());
        displayedAssessment = analysis.explanation() + "\n" + guidance.assessmentText();
        displayedGuessing = guidance.guessingText();
        passwordRecommendation.setText(guidance.recommendationText());
        for (int index = 0; index < strengthSegments.size(); index++) {
            strengthSegments.get(index).setFill(index < strength.filledSegments()
                    ? Color.web(strength.color()) : Color.web("#dce2e8"));
        }
    }

    String displayedAssessmentText() {
        return displayedAssessment;
    }

    String displayedClassification() {
        return classification.getText();
    }

    String displayedRecommendation() {
        return passwordRecommendation.getText();
    }

    String displayedGuessingText() {
        return displayedGuessing;
    }

    String displayedReportText() {
        return collectText(reportScroll.getContent());
    }

    private static String collectText(Node node) {
        String text = node instanceof Label label ? label.getText() : "";
        if (node instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                text += " " + collectText(child);
            }
        }
        return text;
    }

    List<AlgorithmInfoCard> algorithmCards() {
        return List.copyOf(algorithmCards);
    }

    private static Label findingLabel(String text) {
        Label label = body("- " + text);
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private void setClassificationStyle(String color) {
        classification.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 23px; -fx-font-weight: bold;");
    }

    private static String yesNo(int count) {
        return count > 0 ? "Yes" : "No";
    }

    private static Label smallHeading(String text) {
        Label label = new Label(text);
        UiStyles.label(label, "#354955", 10, "bold");
        return label;
    }

    private static Label body(String text) {
        Label label = new Label(text);
        UiStyles.label(label, "#40535f", 11, "normal");
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
        icon.setStyle("-fx-border-color: #3c776a; -fx-border-radius: 50%; -fx-border-width: 1.5px; "
                + "-fx-text-fill: #3c776a; -fx-font-size: 15px; -fx-font-weight: bold;");
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
