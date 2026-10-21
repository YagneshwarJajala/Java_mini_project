package com.astraai;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.astraai.ui.AstraApplicationView;

/** Starts the JavaFX application and releases its local simulation resources on shutdown. */
public final class App extends Application {
    private AstraApplicationView applicationView;

    @Override
    public void start(Stage stage) {
        applicationView = new AstraApplicationView();

        Scene scene = new Scene(applicationView, 1280, 900);
        stage.setTitle(AppMetadata.WINDOW_TITLE);
        stage.setMinWidth(640);
        stage.setMinHeight(500);
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (applicationView != null) {
            applicationView.close();
        }
    }
}
