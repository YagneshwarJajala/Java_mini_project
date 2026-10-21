package com.astraai.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;

/** Small JavaFX styling helpers shared by the desktop views. */
final class UiStyles {
    private UiStyles() {
    }

    static void label(Labeled control, String color, int size, String weight) {
        control.setStyle("-fx-text-fill: " + color + "; -fx-font-size: " + size
                + "px; -fx-font-weight: " + weight + ";");
    }

    static void surface(Region region, String fill, String border, int radius, Insets padding) {
        region.setStyle("-fx-background-color: " + fill + "; -fx-background-radius: " + radius
                + "px; -fx-border-color: " + border + "; -fx-border-radius: " + radius
                + "px; -fx-border-width: 1px;");
        if (padding != null) {
            region.setPadding(padding);
        }
    }

    static void button(Button button, String fill, String hover, String border, String text,
            int size, Insets padding, int radius) {
        button.setStyle(buttonStyle(fill, border, text, size, padding, radius));
        button.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_ENTERED, event -> {
            if (!button.isDisabled()) {
                button.setStyle(buttonStyle(hover, border, text, size, padding, radius));
            }
        });
        button.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, event ->
                button.setStyle(buttonStyle(fill, border, text, size, padding, radius)));
        button.disabledProperty().addListener((observable, oldValue, disabled) ->
                button.setOpacity(disabled ? 0.46 : 1.0));
    }

    private static String buttonStyle(String fill, String border, String text, int size,
            Insets padding, int radius) {
        return "-fx-background-color: " + fill + "; -fx-background-radius: " + radius
                + "px; -fx-border-color: " + border + "; -fx-border-radius: " + radius
                + "px; -fx-text-fill: " + text + "; -fx-font-size: " + size
                + "px; -fx-font-weight: bold; -fx-padding: " + padding.getTop() + "px "
                + padding.getRight() + "px " + padding.getBottom() + "px " + padding.getLeft()
                + "px; -fx-cursor: hand;";
    }

    static Tooltip tooltip(String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setStyle("-fx-background-color: #2b3034; -fx-text-fill: white; "
                + "-fx-background-radius: 5px; -fx-font-size: 10px;");
        return tooltip;
    }
}
