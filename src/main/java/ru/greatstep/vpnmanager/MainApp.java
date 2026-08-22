package ru.greatstep.vpnmanager;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import ru.greatstep.vpnmanager.ui.MainController;

public class MainApp extends Application {

    private MainController controller;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("VPN Manager for OpenWrt");
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(750);

        controller = new MainController();

        BorderPane root = new BorderPane();
        root.setPadding(new javafx.geometry.Insets(10));

        root.setTop(controller.getConnectionPanel());
        root.setCenter(controller.getListsPanel());
        root.setBottom(controller.getBottomPanel());

        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.show();

        primaryStage.setOnCloseRequest(this::handleCloseRequest);
    }

    private void handleCloseRequest(WindowEvent event) {
        if (controller != null) {
            controller.shutdown();
        }
        Platform.exit();
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}