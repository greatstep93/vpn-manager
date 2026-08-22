package ru.greatstep.vpnmanager.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class BottomPanel {

    private Label statusLabel;
    private Label pendingHintLabel;
    private Button applyButton;
    private Button refreshButton;
    private Button toggleLogsButton;
    private TextArea logArea;
    private boolean logsVisible = false;

    private Runnable onApply;
    private Runnable onRefresh;

    public VBox create() {
        VBox panel = new VBox(5);
        panel.setMaxWidth(Double.MAX_VALUE);
        panel.setFillWidth(true);

        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(10));
        statusBar.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1; -fx-border-radius: 5;");
        statusBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(statusBar, Priority.ALWAYS);

        // Статус (слева)
        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-font-size: 12px;");

        // Растягивающийся разделитель
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Группа кнопок справа
        HBox rightGroup = new HBox(8);
        rightGroup.setAlignment(Pos.CENTER_RIGHT);

        // Подсказка о pending изменениях (по умолчанию скрыта)
        pendingHintLabel = new Label("⚡ Click Apply Changes for save your config!");
        pendingHintLabel.setStyle(
                "-fx-font-size: 11px; " +
                        "-fx-text-fill: #e65100; " +
                        "-fx-font-weight: bold; " +
                        "-fx-background-color: #fff3e0; " +
                        "-fx-padding: 3 10 3 10; " +
                        "-fx-border-color: #ff9800; " +
                        "-fx-border-radius: 3; " +
                        "-fx-background-radius: 3;"
        );
        pendingHintLabel.setVisible(false);
        pendingHintLabel.setManaged(false);
        pendingHintLabel.setTooltip(new Tooltip("You have unsaved changes. Click 'Apply Changes' to save them."));

        // Apply Changes button
        applyButton = new Button("Apply Changes");
        applyButton.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-weight: bold;");
        applyButton.setOnAction(e -> {
            if (onApply != null) onApply.run();
        });
        applyButton.setDisable(true);

        // Refresh button
        refreshButton = new Button("Refresh");
        refreshButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        refreshButton.setOnAction(e -> {
            if (onRefresh != null) onRefresh.run();
        });
        refreshButton.setDisable(true);

        // Toggle logs button
        toggleLogsButton = new Button("▶ Show Logs");
        toggleLogsButton.setStyle("-fx-background-color: #607D8B; -fx-text-fill: white;");
        toggleLogsButton.setOnAction(e -> toggleLogs());

        rightGroup.getChildren().addAll(
                pendingHintLabel,
                applyButton,
                refreshButton,
                toggleLogsButton
        );

        statusBar.getChildren().addAll(
                statusLabel,
                spacer,
                rightGroup
        );

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        logArea.setPrefHeight(150);
        logArea.setMaxWidth(Double.MAX_VALUE);
        logArea.setVisible(false);
        logArea.setManaged(false);

        panel.getChildren().addAll(statusBar, logArea);
        VBox.setVgrow(logArea, Priority.ALWAYS);
        return panel;
    }

    private void toggleLogs() {
        logsVisible = !logsVisible;
        logArea.setVisible(logsVisible);
        logArea.setManaged(logsVisible);
        toggleLogsButton.setText(logsVisible ? "▼ Hide Logs" : "▶ Show Logs");
    }

    public void setOnApply(Runnable onApply) {
        this.onApply = onApply;
    }

    public void setOnRefresh(Runnable onRefresh) {
        this.onRefresh = onRefresh;
    }

    public void setStatus(String status, Color color) {
        statusLabel.setText(status);
        statusLabel.setTextFill(color);
    }

    public void setStatus(String status) {
        statusLabel.setText(status);
    }

    public void setApplyEnabled(boolean enabled) {
        applyButton.setDisable(!enabled);
        setPendingHintVisible(enabled);
    }

    public void setRefreshEnabled(boolean enabled) {
        refreshButton.setDisable(!enabled);
    }

    public void setPendingHintVisible(boolean visible) {
        pendingHintLabel.setVisible(visible);
        pendingHintLabel.setManaged(visible);
    }

    public void showPendingHint() {
        pendingHintLabel.setVisible(true);
        pendingHintLabel.setManaged(true);
    }

    public void hidePendingHint() {
        pendingHintLabel.setVisible(false);
        pendingHintLabel.setManaged(false);
    }

    public void addLog(String message) {
        String timestamp = java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        logArea.appendText("[" + timestamp + "] " + message + "\n");
        logArea.setScrollTop(Double.MAX_VALUE);
    }

    public void clearLogs() {
        logArea.clear();
    }

    public void resetState() {
        setApplyEnabled(false);
        setRefreshEnabled(false);
        setPendingHintVisible(false);
        setStatus("Ready", Color.GRAY);
    }
}