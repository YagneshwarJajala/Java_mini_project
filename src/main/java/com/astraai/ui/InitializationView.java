package com.astraai.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.animation.KeyFrame;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import com.astraai.simulation.SimulationEngineSnapshot;
import com.astraai.simulation.SimulationState;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.util.Duration;

/** Indeterminate, JavaFX-native transition shown while the local engine is constructed. */
final class InitializationView extends StackPane implements AutoCloseable {
    private static final int DOT_COUNT = 8;
    private static final String NORMAL_STATUS_STYLE = "-fx-background-color: rgba(255,255,255,0.55); "
            + "-fx-background-radius: 24px; -fx-border-color: rgba(255,255,255,0.9); "
            + "-fx-border-radius: 24px; -fx-text-fill: #27313d; -fx-font-size: 18px;";
    private final Label status = new Label("Initializing analysis environment");
    private final Button retry = new Button("Retry initialization");
    private final Button lifecycle = new Button("Pause");
    private final Button stop = new Button("Stop");
    private final ProgressBar progress = new ProgressBar(0);
    private final List<Circle> dots = new ArrayList<>(DOT_COUNT);
    private final Timeline dotAnimation;
    private final RotateTransition orbitAnimation;
    private int activeDot;
    private boolean closed;

    InitializationView(Runnable retryAction, Runnable lifecycleAction, Runnable stopAction) {
        setStyle("-fx-background-color: linear-gradient(to bottom right, #ffffff 0%, "
                + "#f8fafd 56%, #edf2f7 100%);");

        VBox content = new VBox(0);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(22, 28, 28, 28));
        content.setMaxWidth(1000);

        Group orbit = createOrbit();
        orbitAnimation = new RotateTransition(Duration.seconds(72), orbit);
        orbitAnimation.setByAngle(360);
        orbitAnimation.setCycleCount(Timeline.INDEFINITE);
        orbitAnimation.setInterpolator(javafx.animation.Interpolator.LINEAR);

        StackPane orb = createOrb(orbit);
        VBox.setMargin(orb, new Insets(4, 0, 12, 0));
        Label heading = new Label("Initializing PassScope");
        heading.setStyle("-fx-text-fill: #111315; -fx-font-size: 64px; -fx-font-weight: bold;");
        Label subtitle = new Label("Preparing 100 logical simulation agents");
        subtitle.setStyle("-fx-text-fill: #596579; -fx-font-size: 29px;");
        HBox indicators = createIndicators();
        VBox.setMargin(indicators, new Insets(34, 0, 0, 0));

        status.setAlignment(Pos.CENTER);
        status.setWrapText(true);
        status.setMaxWidth(620);
        status.setMinHeight(46);
        status.setPadding(new Insets(9, 30, 9, 30));
        status.setStyle(NORMAL_STATUS_STYLE);
        VBox.setMargin(status, new Insets(32, 0, 0, 0));

        retry.setVisible(false);
        retry.setManaged(false);
        retry.setOnAction(event -> retryAction.run());
        UiStyles.button(retry, "#202428", "#353a3e", "transparent", "white", 13,
                new Insets(10, 18, 10, 18), 18);
        VBox.setMargin(retry, new Insets(14, 0, 0, 0));

        progress.setMaxWidth(470);
        progress.setPrefHeight(8);
        progress.setStyle("-fx-accent: #202a35;");
        lifecycle.setOnAction(event -> lifecycleAction.run());
        stop.setOnAction(event -> stopAction.run());
        UiStyles.button(lifecycle, "#ffffff", "#eef2f6", "#d9e0e7", "#202832", 13,
                new Insets(8, 17, 8, 17), 18);
        UiStyles.button(stop, "#ffffff", "#fff1f1", "#f0d8d8", "#9c3636", 13,
                new Insets(8, 17, 8, 17), 18);
        HBox controls = new HBox(10, lifecycle, stop, retry);
        controls.setAlignment(Pos.CENTER);
        VBox.setMargin(controls, new Insets(14, 0, 0, 0));

        content.getChildren().addAll(orb, heading, subtitle, indicators, progress, status, controls);
        StackPane contentFrame = new StackPane(content);
        contentFrame.setAlignment(Pos.CENTER);
        ScrollPane scroll = new ScrollPane(contentFrame);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        getChildren().add(scroll);

        dotAnimation = new Timeline(new KeyFrame(Duration.millis(190), event -> advanceDot()));
        dotAnimation.setCycleCount(Timeline.INDEFINITE);
    }

    private static StackPane createOrb(Group orbit) {
        StackPane stage = new StackPane();
        stage.setMinSize(570, 480);
        stage.setPrefSize(570, 480);
        stage.setMaxSize(570, 480);
        Ellipse shadow = new Ellipse(198, 24);
        shadow.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.8, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(53, 68, 91, 0.25)), new Stop(1, Color.TRANSPARENT)));
        shadow.setTranslateY(199);

        Circle sphere = new Circle(167);
        sphere.setFill(new RadialGradient(0, 0, 0.34, 0.25, 0.82, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#c6ceda")), new Stop(0.12, Color.web("#59616c")),
                new Stop(0.31, Color.web("#252a31")), new Stop(0.67, Color.web("#101317")),
                new Stop(0.9, Color.web("#050608")), new Stop(1, Color.web("#020304"))));
        sphere.setStroke(Color.web("#e5ebf5", 0.88));
        sphere.setStrokeWidth(2.0);
        sphere.setEffect(new DropShadow(36, Color.rgb(74, 95, 130, 0.28)));

        Group reflectiveBands = new Group(
                band(135, 82, 130, 110, -28, "#eef3fb", 0.72, 8),
                band(142, 69, 18, 105, 31, "#aeb8c7", 0.52, 5),
                band(128, 78, 202, 94, -26, "#f5f7fb", 0.7, 6),
                band(117, 50, 18, 80, -26, "#8894a5", 0.45, 3));
        reflectiveBands.setClip(new Circle(0, 0, 166));
        Group sphereArtwork = new Group(sphere, reflectiveBands);

        stage.getChildren().addAll(shadow, orbit, sphereArtwork);
        return stage;
    }

    private static Group createOrbit() {
        Ellipse outer = new Ellipse(276, 213);
        outer.setFill(Color.TRANSPARENT);
        outer.setStroke(Color.web("#a9bee4", 0.38));
        outer.setStrokeWidth(1.0);
        outer.setRotate(-13);
        Ellipse inner = new Ellipse(230, 176);
        inner.setFill(Color.TRANSPARENT);
        inner.setStroke(Color.web("#b8c9e8", 0.27));
        inner.setStrokeWidth(0.9);
        inner.setRotate(14);
        Ellipse partial = new Ellipse(258, 195);
        partial.setFill(Color.TRANSPARENT);
        partial.setStroke(Color.web("#c4d2e9", 0.3));
        partial.setStrokeWidth(0.8);
        partial.getStrokeDashArray().setAll(340.0, 95.0, 310.0, 400.0);
        partial.setRotate(22);
        Group orbit = new Group(outer, inner, partial);
        addParticle(orbit, 0, -212, 9, "#d5e0f7", "#6982af");
        addParticle(orbit, 143, -150, 10, "#d9e0ed", "#687487");
        addParticle(orbit, 235, -61, 9, "#313b49", "#080a0e");
        addParticle(orbit, 225, 94, 8, "#d7e2f5", "#8493ad");
        addParticle(orbit, 154, 165, 7, "#aeb9c9", "#525e70");
        addParticle(orbit, -195, 156, 9, "#313a48", "#080a0d");
        addParticle(orbit, -226, 4, 10, "#414c5b", "#07090c");
        addParticle(orbit, -142, -145, 3, "#c4d4f1", "#8da5d0");
        return orbit;
    }

    private static Arc band(double radiusX, double radiusY, double start, double length,
            double rotation, String color, double opacity, double width) {
        Arc arc = new Arc(0, 0, radiusX, radiusY, start, length);
        arc.setType(ArcType.OPEN);
        arc.setFill(Color.TRANSPARENT);
        arc.setStroke(Color.web(color, opacity));
        arc.setStrokeWidth(width);
        arc.setRotate(rotation);
        return arc;
    }

    private static void addParticle(Group orbit, double x, double y, double radius,
            String highlight, String shade) {
        Circle particle = new Circle(radius);
        particle.setFill(new RadialGradient(0, 0, 0.3, 0.25, 0.8, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.WHITE), new Stop(0.22, Color.web(highlight)),
                new Stop(0.58, Color.web(shade)), new Stop(1, Color.web("#11151b"))));
        particle.setEffect(new DropShadow(5, Color.rgb(43, 56, 76, 0.24)));
        particle.setTranslateX(x);
        particle.setTranslateY(y);
        orbit.getChildren().add(particle);
    }

    private HBox createIndicators() {
        HBox row = new HBox(17);
        row.setAlignment(Pos.CENTER);
        for (int i = 0; i < DOT_COUNT; i++) {
            Circle dot = new Circle(10);
            dot.setAccessibleText("Indeterminate initialization indicator");
            dots.add(dot);
            row.getChildren().add(dot);
        }
        styleDots();
        return row;
    }

    private void advanceDot() {
        activeDot = (activeDot + 1) % DOT_COUNT;
        styleDots();
    }

    private void styleDots() {
        for (int i = 0; i < dots.size(); i++) {
            Circle dot = dots.get(i);
            dot.setFill(i == activeDot ? Color.web("#111315") : Color.web("#f7f9fc"));
            dot.setStroke(i == activeDot ? Color.web("#111315") : Color.web("#aeb7c4"));
            dot.setStrokeWidth(1.5);
        }
    }

    void start() {
        if (!closed) {
            status.setStyle(NORMAL_STATUS_STYLE);
            retry.setVisible(false);
            retry.setManaged(false);
            dotAnimation.playFromStart();
            orbitAnimation.playFromStart();
            progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            lifecycle.setVisible(false);
            lifecycle.setManaged(false);
            stop.setVisible(false);
            stop.setManaged(false);
        }
    }

    void update(SimulationEngineSnapshot snapshot) {
        String stateText = switch (snapshot.state()) {
            case READY -> "Preparing local simulation";
            case RUNNING -> "Evaluating bounded candidate batches";
            case PAUSING -> "Pausing at a safe batch boundary";
            case PAUSED -> "Paused · workers acknowledged safe boundaries";
            case STOPPING -> "Stopping · waiting for bounded cleanup";
            case COMPLETED -> "Simulation completed";
            case EXHAUSTED -> "Candidate space exhausted";
            case STOPPED -> "Simulation stopped";
            case FAILED -> snapshot.diagnostic().orElse("Simulation failed");
        };
        setStatus(stateText);
        if (snapshot.candidateSpaceSize() > 0) {
            progress.setProgress((double) snapshot.completedCandidateEvaluations() / snapshot.candidateSpaceSize());
        }
        boolean active = snapshot.state() == SimulationState.RUNNING;
        if (active) {
            dotAnimation.play();
            orbitAnimation.play();
        } else {
            dotAnimation.pause();
            orbitAnimation.pause();
        }
        lifecycle.setText(snapshot.state() == SimulationState.PAUSED ? "Resume" : "Pause");
        boolean canControl = snapshot.state() == SimulationState.RUNNING || snapshot.state() == SimulationState.PAUSED;
        lifecycle.setDisable(!canControl || snapshot.operationPending());
        lifecycle.setVisible(snapshot.state() != SimulationState.READY);
        lifecycle.setManaged(snapshot.state() != SimulationState.READY);
        stop.setDisable(!(snapshot.state() == SimulationState.RUNNING || snapshot.state() == SimulationState.PAUSED
                || snapshot.state() == SimulationState.PAUSING));
        stop.setVisible(snapshot.state() != SimulationState.READY);
        stop.setManaged(snapshot.state() != SimulationState.READY);
    }

    void setStatus(String message) {
        if (javafx.application.Platform.isFxApplicationThread()) {
            status.setText(message);
        } else {
            javafx.application.Platform.runLater(() -> {
                if (!closed) {
                    status.setText(message);
                }
            });
        }
    }

    void showFailure(String message) {
        showFailure(message, true);
    }

    void showFailure(String message, boolean retryAllowed) {
        dotAnimation.stop();
        orbitAnimation.stop();
        status.setText(message);
        status.setStyle("-fx-background-color: rgba(255,255,255,0.72); -fx-background-radius: 24px; "
                + "-fx-border-color: rgba(255,255,255,0.9); -fx-border-radius: 24px; "
                + "-fx-text-fill: #a9444d; -fx-font-size: 16px;");
        retry.setVisible(retryAllowed);
        retry.setManaged(retryAllowed);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            dotAnimation.stop();
            orbitAnimation.stop();
        }
    }
}
