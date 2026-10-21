package com.astraai.ui;

import java.util.Optional;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.effect.DropShadow;

/** Minimal entry screen. Only immutable derived analysis leaves this view; raw text is erased on submit. */
final class PasswordEntryView extends StackPane {
    private final PasswordField password = new PasswordField();
    private final TextField visibleText = new TextField();
    private final Label message = new Label("Please enter the password to start the analysis.");
    private final Button toggle = new Button("◉");

    PasswordEntryView(Consumer<PasswordAnalysis> onAccepted) {
        setStyle("-fx-background-color: linear-gradient(to bottom right, #ffffff, #f7fafd 58%, #edf2f7);");
        VBox content = new VBox(0);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(28, 30, 38, 30));
        content.setMaxWidth(1080);

        StackPane mark = createMark(116);
        Label title = new Label("Astra AI");
        title.setStyle("-fx-text-fill: #111315; -fx-font-size: 58px; -fx-font-weight: bold;");
        Label subtitle = new Label("Multi-Agent Password Analysis");
        subtitle.setStyle("-fx-text-fill: #596579; -fx-font-size: 28px;");
        VBox heading = new VBox(7, mark, title, subtitle);
        heading.setAlignment(Pos.CENTER);
        HBox passwordBar = createPasswordBar(onAccepted);
        VBox.setMargin(passwordBar, new Insets(48, 0, 0, 0));

        Label infoIcon = new Label("i");
        infoIcon.setAlignment(Pos.CENTER);
        infoIcon.setMinSize(25, 25);
        infoIcon.setStyle("-fx-border-color: #283440; -fx-border-radius: 50%; -fx-text-fill: #202b36; "
                + "-fx-border-width: 1.5px; -fx-font-size: 15px; -fx-font-weight: bold;");
        message.setWrapText(true);
        UiStyles.label(message, "#263442", 15, "normal");
        Label disclosure = new Label("A local character heuristic is computed, then the input is erased. "
                + "The synthetic challenge is unrelated to it.");
        disclosure.setWrapText(true);
        UiStyles.label(disclosure, "#596579", 12, "normal");
        VBox messageText = new VBox(4, message, disclosure);
        HBox information = new HBox(13, infoIcon, messageText);
        information.setAlignment(Pos.CENTER_LEFT);
        information.setPadding(new Insets(13, 18, 13, 18));
        information.setMaxWidth(690);
        information.setStyle("-fx-background-color: rgba(255,255,255,0.58); -fx-background-radius: 17px; "
                + "-fx-border-color: rgba(255,255,255,0.95); -fx-border-radius: 17px;");
        information.setEffect(new DropShadow(12, 0, 3, Color.rgb(51, 66, 82, 0.05)));
        VBox.setMargin(information, new Insets(30, 0, 0, 0));

        content.getChildren().addAll(heading, passwordBar, information);
        getChildren().add(content);
    }

    void clear() {
        password.clear();
        visibleText.clear();
        password.setVisible(true);
        password.setManaged(true);
        visibleText.setVisible(false);
        visibleText.setManaged(false);
        message.setText("Please enter the password to start the analysis.");
        UiStyles.label(message, "#263442", 15, "normal");
    }

    private HBox createPasswordBar(Consumer<PasswordAnalysis> onAccepted) {
        HBox bar = new HBox(11);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(11, 13, 11, 27));
        bar.setMinHeight(101);
        bar.setMaxWidth(1070);
        bar.setStyle("-fx-background-color: linear-gradient(to bottom right, #171a1d, #101316); "
                + "-fx-background-radius: 22px; -fx-border-color: #363b40; -fx-border-radius: 22px;");
        bar.setEffect(new DropShadow(22, 0, 10, Color.rgb(24, 34, 46, 0.20)));

        Label lock = new Label("▣");
        lock.setStyle("-fx-text-fill: white; -fx-font-size: 24px;");
        String fieldStyle = "-fx-background-color: #1b1f23; -fx-background-radius: 15px; "
                + "-fx-text-fill: #f5f6f7; -fx-prompt-text-fill: #828c99; -fx-font-size: 23px; "
                + "-fx-border-color: transparent; -fx-padding: 13px 19px;";
        password.setPromptText("Enter your password");
        visibleText.setPromptText("Enter your password");
        password.setStyle(fieldStyle);
        visibleText.setStyle(fieldStyle);
        password.textProperty().bindBidirectional(visibleText.textProperty());
        StackPane fields = new StackPane(password, visibleText);
        fields.setMinWidth(100);
        HBox.setHgrow(fields, Priority.ALWAYS);

        UiStyles.button(toggle, "transparent", "#2a3036", "transparent", "#f5f6f7", 17,
                new Insets(9), 8);
        toggle.setMinSize(48, 56);
        toggle.setAccessibleText("Show password");
        toggle.setOnAction(event -> toggleVisibility());
        Button enter = new Button("Enter");
        UiStyles.button(enter, "linear-gradient(to bottom, #ffffff, #f1f3f5)", "#e9edf1",
                "#e2e5e8", "#141619", 20, new Insets(16, 40, 16, 40), 15);
        enter.setMinWidth(132);
        enter.setDefaultButton(true);
        enter.setOnAction(event -> submit(onAccepted));
        bar.getChildren().addAll(lock, fields, toggle, enter);
        return bar;
    }

    private void submit(Consumer<PasswordAnalysis> onAccepted) {
        char[] chars = (password.isVisible() ? password : visibleText).getText().toCharArray();
        int submittedLength = chars.length;
        Optional<PasswordAnalysis> result = PasswordInputPolicy.analyzeAndErase(chars);
        clearFieldsOnly();
        if (result.isEmpty()) {
            message.setText(submittedLength > PasswordInputPolicy.MAX_INPUT_LENGTH
                    ? "Please use 256 characters or fewer."
                    : "Please enter a password to continue.");
            UiStyles.label(message, "#a9444d", 15, "normal");
            return;
        }
        onAccepted.accept(result.orElseThrow());
    }

    private void clearFieldsOnly() {
        password.clear();
        visibleText.clear();
        password.setVisible(true);
        password.setManaged(true);
        visibleText.setVisible(false);
        visibleText.setManaged(false);
        toggle.setAccessibleText("Show password");
    }

    private void toggleVisibility() {
        boolean show = !visibleText.isVisible();
        visibleText.setVisible(show);
        visibleText.setManaged(show);
        password.setVisible(!show);
        password.setManaged(!show);
        toggle.setText(show ? "⊘" : "◉");
        toggle.setAccessibleText(show ? "Hide password" : "Show password");
        (show ? visibleText : password).requestFocus();
    }

    private static StackPane createMark(double size) {
        Polygon logo = new Polygon(0.0, size, size * 0.48, 0.0, size * 0.94, size, size * 0.67, size,
                size * 0.52, size * 0.58, size * 0.34, size);
        logo.setFill(Color.web("#252a32"));
        logo.setEffect(new DropShadow(5, Color.rgb(41, 53, 71, 0.20)));
        StackPane holder = new StackPane(logo);
        holder.setMinSize(size, size + 5);
        holder.setPrefSize(size, size + 5);
        holder.setMaxSize(size, size + 5);
        return holder;
    }
}
