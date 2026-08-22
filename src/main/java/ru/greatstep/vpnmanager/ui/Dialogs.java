package ru.greatstep.vpnmanager.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Optional;

public class Dialogs {

    public static void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static boolean showConfirmation(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    public static Optional<String> showInputDialog(String title, String header, String content) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(content);
        return dialog.showAndWait();
    }

    public static Optional<String> showListInputDialog(String title, String header, String content, String placeholder) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);

        ButtonType okButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));

        Label label = new Label(content);
        label.setStyle("-fx-font-weight: bold;");

        TextArea textArea = new TextArea();
        textArea.setPromptText(placeholder);
        textArea.setPrefHeight(200);
        textArea.setPrefWidth(400);
        VBox.setVgrow(textArea, Priority.ALWAYS);

        // Подсказка о формате
        Label hintLabel = new Label("Enter one item per line. Empty lines will be ignored.");
        hintLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666;");

        vbox.getChildren().addAll(label, textArea, hintLabel);

        dialog.getDialogPane().setContent(vbox);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == okButtonType) {
                return textArea.getText();
            }
            return null;
        });

        return dialog.showAndWait();
    }
}