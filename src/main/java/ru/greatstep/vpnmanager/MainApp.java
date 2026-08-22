package ru.greatstep.vpnmanager;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import ru.greatstep.vpnmanager.ui.MainController;

import java.io.InputStream;

public class MainApp extends Application {

    private MainController controller;

    // Определяем операционную систему
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final boolean IS_MAC = System.getProperty("os.name").toLowerCase().contains("mac");

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("VPN Manager for OpenWrt");
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(750);

        // ---- УСТАНАВЛИВАЕМ ИКОНКУ ДЛЯ ОКНА В ЗАВИСИМОСТИ ОТ ОС ----
        setWindowIcon(primaryStage);
        // ------------------------------------------------------------

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

    private void setWindowIcon(Stage stage) {
        try {
            // --- Windows: используем ICO ---
            if (IS_WINDOWS) {
                InputStream icoStream = getClass().getResourceAsStream("/icons/vpnmanager.ico");
                if (icoStream != null) {
                    Image icon = new Image(icoStream);
                    if (!icon.isError()) {
                        stage.getIcons().add(icon);
                        System.out.println("✅ Window icon loaded (ICO) for Windows");
                        return;
                    }
                }
                System.out.println("⚠️ ICO icon not found for Windows, trying PNG fallback...");
            }

            // --- macOS: используем ICNS или PNG ---
            if (IS_MAC) {
                // Пробуем ICNS (если есть)
                InputStream icnsStream = getClass().getResourceAsStream("/icons/vpnmanager.icns");
                if (icnsStream != null) {
                    Image icon = new Image(icnsStream);
                    if (!icon.isError()) {
                        stage.getIcons().add(icon);
                        System.out.println("✅ Window icon loaded (ICNS) for macOS");
                        return;
                    }
                }
                System.out.println("⚠️ ICNS icon not found for macOS, trying PNG...");
            }

            // --- Linux и общий fallback: используем PNG ---
            // Пробуем загрузить PNG 256x256
            InputStream png256 = getClass().getResourceAsStream("/icons/vpnmanager_256.png");
            if (png256 != null) {
                Image icon = new Image(png256);
                if (!icon.isError()) {
                    stage.getIcons().add(icon);
                    System.out.println("✅ Window icon loaded (PNG 256) for " + System.getProperty("os.name"));
                    return;
                }
            }

            // Пробуем загрузить PNG 128x128
            InputStream png128 = getClass().getResourceAsStream("/icons/vpnmanager_128.png");
            if (png128 != null) {
                Image icon = new Image(png128);
                if (!icon.isError()) {
                    stage.getIcons().add(icon);
                    System.out.println("✅ Window icon loaded (PNG 128) for " + System.getProperty("os.name"));
                    return;
                }
            }

            System.out.println("⚠️ No window icon found, using default Java icon");

        } catch (Exception e) {
            System.err.println("Error loading window icon: " + e.getMessage());
        }
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