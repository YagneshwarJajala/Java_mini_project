package com.astraai.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Orientation;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Region;
import java.util.function.Consumer;
import java.util.Optional;

/** Expandable informational reference. It never displays fabricated measurements. */
public final class AlgorithmInfoCard extends VBox {
    private final AlgorithmInfo info;
    private final VBox details = new VBox(8);
    private final Label disclosure = new Label("+");
    private final Label modeledTimeTitle;
    private final Label modeledTime;
    private CrackTimeEstimate crackTimeEstimate;
    private boolean expanded;
    private Consumer<Boolean> expandedListener = ignored -> { };

    public AlgorithmInfoCard(AlgorithmInfo info, int number) {
        this.info = info;
        UiStyles.surface(this, "#ffffff", "#dce5e9", 12, null);
        setSpacing(0);
        setMaxHeight(Region.USE_PREF_SIZE);

        Label numberBadge = new Label("%02d".formatted(number));
        numberBadge.setAlignment(Pos.CENTER);
        numberBadge.setMinSize(28, 24);
        numberBadge.setPrefSize(28, 24);
        numberBadge.setStyle("-fx-background-color: #eaf2f0; -fx-background-radius: 6px; "
                + "-fx-text-fill: #416a60; -fx-font-size: 10px; -fx-font-weight: bold;");
        Label name = new Label(info.name());
        name.setWrapText(true);
        name.setMinWidth(0);
        UiStyles.label(name, "#24343e", 13, "bold");
        Label category = new Label(info.category());
        category.setWrapText(true);
        category.setMinWidth(0);
        UiStyles.label(category, "#526570", 11, "normal");
        VBox title = new VBox(4, name, category);
        title.setMinWidth(0);

        HBox titleLine = new HBox(12, numberBadge, title);
        titleLine.setAlignment(Pos.CENTER_LEFT);
        titleLine.setMinWidth(0);
        HBox.setHgrow(title, Priority.ALWAYS);
        Label suitability = new Label("Suitability · " + info.guidanceLabel());
        suitability.setWrapText(true);
        suitability.setMinWidth(0);
        suitability.setMaxWidth(Double.MAX_VALUE);
        UiStyles.label(suitability, "#315e51", 11, "bold");
        modeledTimeTitle = new Label("MODELED CRACK TIME");
        UiStyles.label(modeledTimeTitle, "#526a72", 9, "bold");
        ModeledCrackTimeEstimate estimate = CrackTimeEstimator.estimate(info);
        KdfBenchmarkResultState benchmarkState =
                CrackTimeEstimator.benchmarkState(info, null, Optional.empty(), null);
        modeledTime = new Label(estimate.status().displayText());
        modeledTime.setWrapText(true);
        modeledTime.setMinWidth(0);
        modeledTime.setMaxWidth(Double.MAX_VALUE);
        UiStyles.label(modeledTime, "#263d49", 17, "bold");
        modeledTime.setAccessibleHelp(estimate.reason() + " " + benchmarkState.status().displayText()
                + ": " + benchmarkState.reason());
        crackTimeEstimate = CrackTimeEstimator.estimate(info, null);
        VBox modeledTimePanel = new VBox(3, modeledTimeTitle, modeledTime);
        modeledTimePanel.setPadding(new Insets(8, 10, 8, 10));
        modeledTimePanel.setStyle("-fx-background-color: #edf4f2; -fx-background-radius: 7px; "
                + "-fx-border-color: #d9e8e3; -fx-border-radius: 7px;");
        UiStyles.label(disclosure, "#5b646a", 18, "normal");
        HBox titleAndDisclosure = new HBox(12, titleLine, disclosure);
        titleAndDisclosure.setAlignment(Pos.CENTER_LEFT);
        titleAndDisclosure.setMinWidth(0);
        HBox.setHgrow(titleLine, Priority.ALWAYS);
        VBox contents = new VBox(8, titleAndDisclosure, modeledTimePanel, suitability);
        contents.setPadding(new Insets(13, 16, 13, 16));
        contents.setMinWidth(0);
        contents.setMaxWidth(Double.MAX_VALUE);
        Button header = new Button();
        header.setGraphic(contents);
        header.setAccessibleText("%s — %s — Modeled crack time: %s — Suitability: %s".formatted(
                info.name(), info.category(), modeledTime.getText(), info.guidanceLabel()));
        header.setAccessibleHelp("Expand or collapse algorithm guidance. " + estimate.reason()
                + " " + benchmarkState.status().displayText() + ": " + benchmarkState.reason());
        UiStyles.button(header, "transparent", "#f3f7f6", "transparent", "#272d31", 12,
                new Insets(0), 11);
        header.setMaxWidth(Double.MAX_VALUE);
        header.setMinWidth(0);
        header.setOnAction(event -> setExpanded(!expanded));

        GridPane facts = new GridPane();
        facts.setHgap(14);
        facts.setVgap(7);
        facts.setMaxWidth(Double.MAX_VALUE);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(120);
        labelColumn.setPrefWidth(132);
        labelColumn.setMaxWidth(148);
        facts.getColumnConstraints().add(labelColumn);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setMinWidth(0);
        valueColumn.setHgrow(Priority.ALWAYS);
        valueColumn.setFillWidth(true);
        facts.getColumnConstraints().add(valueColumn);
        addDetail(facts, 0, "Purpose", info.howItWorks());
        addDetail(facts, 1, "Algorithm type", info.description());
        addDetail(facts, 2, "Advantages", info.resistance());
        addDetail(facts, 3, "Trade-offs", info.limitations());
        addDetail(facts, 4, "Use and deployment fit", info.recommendation());
        Label disclaimer = new Label("Educational reference only · this simulation does not run or compare these algorithms.");
        disclaimer.setWrapText(true);
        UiStyles.label(disclaimer, "#4e5d66", 11, "normal");
        details.getChildren().addAll(facts, disclaimer);
        details.setPadding(new Insets(12, 18, 15, 18));
        details.setMaxWidth(Double.MAX_VALUE);
        details.setStyle("-fx-background-color: #fafbfb; -fx-border-color: #f0f1f2 transparent transparent transparent; "
                + "-fx-border-width: 1px 0 0 0;");
        details.setVisible(false);
        details.setManaged(false);
        getChildren().addAll(header, details);
        setAccessibleText("%02d %s".formatted(number, info.name()));
    }

    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    @Override
    protected double computePrefHeight(double width) {
        Insets insets = getInsets();
        double contentWidth = width < 0 ? -1 : Math.max(0, width - insets.getLeft() - insets.getRight());
        double height = insets.getTop() + insets.getBottom();
        int managedChildren = 0;
        for (Node child : getChildrenUnmodifiable()) {
            if (!child.isManaged()) {
                continue;
            }
            Insets margin = VBox.getMargin(child);
            double top = margin == null ? 0 : margin.getTop();
            double bottom = margin == null ? 0 : margin.getBottom();
            double childWidth = contentWidth < 0 ? -1
                    : Math.max(0, contentWidth - (margin == null ? 0 : margin.getLeft() + margin.getRight()));
            if (managedChildren > 0) {
                height += getSpacing();
            }
            height += top + child.prefHeight(childWidth) + bottom;
            managedChildren++;
        }
        return snapSizeY(height);
    }

    public AlgorithmInfo info() {
        return info;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean value) {
        expanded = value;
        details.setVisible(value);
        details.setManaged(value);
        disclosure.setText(value ? "−" : "+");
        expandedListener.accept(value);
    }

    public void setExpandedListener(Consumer<Boolean> listener) {
        expandedListener = listener == null ? ignored -> { } : listener;
    }

    boolean detailsVisible() {
        return details.isVisible() && details.isManaged();
    }

    double expandedDetailsWidth() {
        return details.getWidth();
    }

    boolean headerContentFits() {
        if (getChildren().isEmpty() || !(getChildren().get(0) instanceof Button header)
                || header.getGraphic() == null) {
            return false;
        }
        return header.getGraphic().getBoundsInParent().getMaxX() <= header.getWidth() + 1;
    }

    boolean visibleContentFits() {
        return getChildrenUnmodifiable().stream()
                .filter(Node::isManaged)
                .allMatch(child -> child.getBoundsInParent().getMinY() >= -1
                        && child.getBoundsInParent().getMaxY() <= getHeight() + 1);
    }

    double detailValueColumnWidth() {
        return details.getChildren().stream()
                .filter(GridPane.class::isInstance)
                .map(GridPane.class::cast)
                .flatMap(grid -> grid.getChildren().stream())
                .filter(node -> GridPane.getColumnIndex(node) != null && GridPane.getColumnIndex(node) == 1)
                .filter(Label.class::isInstance)
                .map(Label.class::cast)
                .mapToDouble(Label::getWidth)
                .min()
                .orElse(0);
    }

    String expandedDetailsText() {
        return details.getChildren().stream()
                .filter(GridPane.class::isInstance)
                .map(GridPane.class::cast)
                .flatMap(grid -> grid.getChildren().stream())
                .filter(Label.class::isInstance)
                .map(Label.class::cast)
                .map(Label::getText)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    String modeledTimeText() {
        return modeledTimeTitle.getText() + " · " + modeledTime.getText();
    }

    ModeledCrackTimeEstimate modeledTimeEstimate() {
        return CrackTimeEstimator.estimate(info);
    }

    CrackTimeEstimate crackTimeEstimate() {
        return crackTimeEstimate;
    }

    void updateCrackTimeEstimate(PasswordAnalysis analysis) {
        crackTimeEstimate = CrackTimeEstimator.estimate(info, analysis);
        modeledTime.setText(crackTimeEstimate.displayText());
        modeledTime.setAccessibleHelp(crackTimeEstimate.explanation());
        Button header = (Button) getChildren().get(0);
        header.setAccessibleText("%s — %s — Modeled crack time: %s — Suitability: %s".formatted(
                info.name(), info.category(), modeledTime.getText(), info.guidanceLabel()));
    }

    String modeledTimeAccessibleHelp() {
        return modeledTime.getAccessibleHelp();
    }

    private static void addDetail(GridPane grid, int row, String title, String text) {
        Label heading = new Label(title + ":");
        heading.setWrapText(true);
        heading.setMinWidth(0);
        UiStyles.label(heading, "#34434b", 11, "bold");
        Label value = new Label(text);
        value.setMinWidth(0);
        value.setWrapText(true);
        UiStyles.label(value, "#3f4d55", 12, "normal");
        GridPane.setHgrow(value, Priority.ALWAYS);
        GridPane.setFillWidth(value, true);
        grid.add(heading, 0, row);
        grid.add(value, 1, row);
    }
}
