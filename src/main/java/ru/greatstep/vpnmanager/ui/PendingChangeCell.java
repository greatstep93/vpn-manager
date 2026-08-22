package ru.greatstep.vpnmanager.ui;

import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;

public class PendingChangeCell extends ListCell<String> {

    private static final String STYLE_ADDED = "-fx-text-fill: #1b5e20; -fx-font-weight: bold; -fx-background-color: #e8f5e9;";
    private static final String STYLE_ADDED_SELECTED = "-fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-background-color: #388e3c;";

    private static final String STYLE_REMOVED = "-fx-text-fill: #b71c1c; -fx-font-weight: bold; -fx-background-color: #ffebee;";
    private static final String STYLE_REMOVED_SELECTED = "-fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-background-color: #c62828;";

    private static final String STYLE_NORMAL = "-fx-text-fill: #212121; -fx-background-color: transparent;";
    private static final String STYLE_NORMAL_SELECTED = "-fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-background-color: #1976d2;";

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            setStyle("");
            return;
        }

        boolean isSelected = isSelected();

        if (item.startsWith("✅ ")) {
            setText(item.substring(2));
            setStyle(isSelected ? STYLE_ADDED_SELECTED : STYLE_ADDED);
        } else if (item.startsWith("❌ ")) {
            setText(item.substring(2));
            setStyle(isSelected ? STYLE_REMOVED_SELECTED : STYLE_REMOVED);
            // Добавляем подсказку, что можно отменить удаление
            if (!isSelected) {
                setTooltip(new Tooltip("Click 'Remove Domain' again to cancel deletion"));
            } else {
                setTooltip(new Tooltip("Click 'Remove Domain' to cancel deletion"));
            }
        } else {
            setText(item);
            setStyle(isSelected ? STYLE_NORMAL_SELECTED : STYLE_NORMAL);
        }
    }
}