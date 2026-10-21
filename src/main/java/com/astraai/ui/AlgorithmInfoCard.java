package com.astraai.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import java.util.function.Consumer;

/** Expandable informational reference. It never displays fabricated measurements. */
public final class AlgorithmInfoCard extends VBox {
    private final AlgorithmInfo info;
    private final VBox details = new VBox(8);
    private final Label disclosure = new Label("+");
    private boolean expanded;
    private Consumer<Boolean> expandedListener = ignored -> { };

    public AlgorithmInfoCard(AlgorithmInfo info, int number) {
        this.info = info;
        UiStyles.surface(this, "#ffffff", "#e8eaec", 9, null);

        Label icon = createIcon();
        Label name = new Label(info.name());
        UiStyles.label(name, "#272d31", 12, "bold");
        Label category = new Label(info.category());
        UiStyles.label(category, "#59646b", 11, "normal");
        VBox title = new VBox(4, name, category);

        Label status = new Label(info.guidanceLabel());
        UiStyles.label(status, "#606a71", 10, "bold");
        VBox strength = new VBox(3, new Label("Storage guidance"), status);
        strength.setAlignment(Pos.CENTER_LEFT);
        UiStyles.label((Label) strength.getChildren().get(0), "#69727a", 9, "normal");
        HBox indicators = new HBox(3);
        for (int index = 0; index < 6; index++) {
            javafx.scene.shape.Rectangle segment = new javafx.scene.shape.Rectangle(16, 6, Color.web("#dce1e6"));
            segment.setArcWidth(6);
            segment.setArcHeight(6);
            indicators.getChildren().add(segment);
        }
        VBox.setMargin(indicators, new Insets(0, 0, 0, 0));
        UiStyles.label(disclosure, "#5b646a", 18, "normal");
        HBox.setHgrow(title, Priority.ALWAYS);
        VBox assessment = new VBox(3, strength, indicators);
        Region divider = new Region();
        divider.setMinWidth(1);
        divider.setPrefWidth(1);
        divider.setMaxWidth(1);
        divider.setMinHeight(30);
        divider.setStyle("-fx-background-color: #e3e7eb;");
        HBox contents = new HBox(14, icon, title, divider, assessment, disclosure);
        contents.setAlignment(Pos.CENTER_LEFT);
        contents.setPadding(new Insets(14, 16, 14, 16));
        Button header = new Button();
        header.setGraphic(contents);
        header.setAccessibleText("%s — %s — %s".formatted(info.name(), info.category(), info.statusLabel()));
        header.setAccessibleHelp("Expand or collapse informational algorithm details.");
        UiStyles.button(header, "transparent", "#fafbfb", "transparent", "#272d31", 12,
                new Insets(0), 9);
        header.setMaxWidth(Double.MAX_VALUE);
        header.setOnAction(event -> setExpanded(!expanded));

        GridPane facts = new GridPane();
        facts.setHgap(14);
        facts.setVgap(7);
        facts.setMaxWidth(Double.MAX_VALUE);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(148);
        labelColumn.setPrefWidth(148);
        labelColumn.setMaxWidth(160);
        facts.getColumnConstraints().add(labelColumn);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        valueColumn.setFillWidth(true);
        facts.getColumnConstraints().add(valueColumn);
        addDetail(facts, 0, "How it works", info.howItWorks());
        addDetail(facts, 1, "Role", info.description());
        addDetail(facts, 2, "Why it is resistant", info.resistance());
        addDetail(facts, 3, "Limitations", info.limitations());
        addDetail(facts, 4, "Recommendation", info.recommendation());
        Label disclaimer = new Label("Educational reference only · this simulation does not run or compare these algorithms.");
        disclaimer.setWrapText(true);
        UiStyles.label(disclaimer, "#606a71", 10, "normal");
        details.getChildren().addAll(facts, disclaimer);
        details.setPadding(new Insets(11, 16, 14, 60));
        details.setStyle("-fx-background-color: #fafbfb; -fx-border-color: #f0f1f2 transparent transparent transparent; "
                + "-fx-border-width: 1px 0 0 0;");
        details.setVisible(false);
        details.setManaged(false);
        getChildren().addAll(header, details);
        setAccessibleText("%02d %s".formatted(number, info.name()));
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

    private static Label createIcon() {
        Circle outline = new Circle(12, Color.TRANSPARENT);
        outline.setStroke(Color.web("#66717e"));
        outline.setStrokeWidth(1.2);
        Arc segment = new Arc(0, 0, 6.5, 6.5, 35, 285);
        segment.setType(ArcType.OPEN);
        segment.setFill(Color.TRANSPARENT);
        segment.setStroke(Color.web("#232a32"));
        segment.setStrokeWidth(1.8);
        javafx.scene.layout.StackPane icon = new javafx.scene.layout.StackPane(outline, segment);
        icon.setMinSize(26, 26);
        icon.setPrefSize(26, 26);
        icon.setMaxSize(26, 26);
        Label holder = new Label();
        holder.setGraphic(icon);
        holder.setStyle("-fx-padding: 0;");
        return holder;
    }

    private static void addDetail(GridPane grid, int row, String title, String text) {
        Label heading = new Label(title + ":");
        heading.setWrapText(false);
        UiStyles.label(heading, "#272d31", 10, "bold");
        Label value = new Label(text);
        value.setWrapText(true);
        UiStyles.label(value, "#59646b", 10, "normal");
        GridPane.setHgrow(value, Priority.ALWAYS);
        GridPane.setFillWidth(value, true);
        grid.add(heading, 0, row);
        grid.add(value, 1, row);
    }
}
