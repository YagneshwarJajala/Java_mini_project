package com.astraai.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;

class InitializationViewTest {
    @Test
    void describesLogicalSimulationAgentsWithoutClaimingAiModels() throws Exception {
        runOnFxThread(() -> {
            InitializationView view = new InitializationView(() -> { }, () -> { }, () -> { });
            StackPane root = new StackPane(view);
            new Scene(root, 1280, 900);
            root.applyCss();

            boolean hasAccurateSubtitle = view.lookupAll(".label").stream()
                    .filter(Label.class::isInstance)
                    .map(Label.class::cast)
                    .anyMatch(label -> "Preparing 100 logical simulation agents".equals(label.getText()));
            boolean hasMisleadingSubtitle = view.lookupAll(".label").stream()
                    .filter(Label.class::isInstance)
                    .map(Label.class::cast)
                    .anyMatch(label -> "Preparing 100 simulated AI agents".equals(label.getText()));
            view.close();

            assertTrue(hasAccurateSubtitle);
            assertFalse(hasMisleadingSubtitle);
            return null;
        });
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
        return task.get(60, TimeUnit.SECONDS);
    }
}
