package ru.greatstep.vpnmanager.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.greatstep.vpnmanager.utils.PreferencesManager;

import java.util.prefs.BackingStoreException;

public class ConnectionPanel {

    private static final Logger log = LoggerFactory.getLogger(ConnectionPanel.class);
    private final PreferencesManager prefsManager;

    // Connection fields
    private TextField hostField;
    private TextField portField;
    private TextField usernameField;
    private PasswordField passwordField;
    private TextField passwordVisibleField;
    private Button togglePasswordButton;
    private CheckBox saveCredentialsCheckBox;

    // Status
    private Label connectionStatus;
    private Button connectButton;

    private Runnable onConnect;
    private Runnable onDisconnect;

    public ConnectionPanel(PreferencesManager prefsManager) {
        this.prefsManager = prefsManager;
    }

    public VBox create() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1; -fx-border-radius: 5;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        hostField = new TextField();
        hostField.setPrefWidth(200);
        hostField.setPromptText("e.g., 192.168.2.1");

        portField = new TextField();
        portField.setPrefWidth(80);
        portField.setPromptText("22");

        usernameField = new TextField();
        usernameField.setPrefWidth(150);
        usernameField.setPromptText("root");

        HBox passwordBox = new HBox(5);
        passwordBox.setAlignment(Pos.CENTER_LEFT);

        passwordField = new PasswordField();
        passwordField.setPrefWidth(165);
        passwordField.setPromptText("Enter password");

        passwordVisibleField = new TextField();
        passwordVisibleField.setPrefWidth(165);
        passwordVisibleField.setPromptText("Enter password");
        passwordVisibleField.setManaged(false);
        passwordVisibleField.setVisible(false);

        passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());

        togglePasswordButton = new Button("SHOW");
        togglePasswordButton.setStyle("-fx-font-size: 10px; -fx-background-color: transparent; -fx-padding: 2 5 2 5; -fx-font-weight: bold; -fx-text-fill: #2196F3;");
        togglePasswordButton.setTooltip(new Tooltip("Show/Hide password"));
        togglePasswordButton.setOnAction(e -> togglePasswordVisibility());

        passwordBox.getChildren().addAll(passwordField, passwordVisibleField, togglePasswordButton);

        saveCredentialsCheckBox = new CheckBox("Save credentials");
        saveCredentialsCheckBox.setStyle("-fx-font-size: 11px;");

        connectionStatus = new Label("⏹ Disconnected");
        connectionStatus.setTextFill(Color.RED);

        connectButton = new Button("Connect");
        connectButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        connectButton.setOnAction(e -> {
            if (onConnect != null) {
                onConnect.run();
            }
        });

        Button clearCredentialsButton = new Button("Clear Saved");
        clearCredentialsButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-size: 11px;");
        clearCredentialsButton.setOnAction(e -> clearCredentials());

        grid.add(new Label("Router IP:"), 0, 0);
        grid.add(hostField, 1, 0);
        grid.add(new Label("Port:"), 2, 0);
        grid.add(portField, 3, 0);
        grid.add(new Label("Username:"), 0, 1);
        grid.add(usernameField, 1, 1);
        grid.add(new Label("Password:"), 2, 1);
        grid.add(passwordBox, 3, 1);
        grid.add(saveCredentialsCheckBox, 1, 2);
        grid.add(clearCredentialsButton, 3, 2);

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        buttonBox.getChildren().addAll(connectButton, connectionStatus);

        panel.getChildren().addAll(grid, buttonBox);
        return panel;
    }

    private void togglePasswordVisibility() {
        boolean showing = passwordVisibleField.isVisible();
        passwordVisibleField.setVisible(!showing);
        passwordVisibleField.setManaged(!showing);
        passwordField.setVisible(showing);
        passwordField.setManaged(showing);
        togglePasswordButton.setText(showing ? "SHOW" : "HIDE");
        togglePasswordButton.setStyle(showing ?
                "-fx-font-size: 10px; -fx-background-color: transparent; -fx-padding: 2 5 2 5; -fx-font-weight: bold; -fx-text-fill: #4CAF50;" :
                "-fx-font-size: 10px; -fx-background-color: transparent; -fx-padding: 2 5 2 5; -fx-font-weight: bold; -fx-text-fill: #f44336;");
        togglePasswordButton.setTooltip(new Tooltip(showing ? "Show password" : "Hide password"));
    }

    private void clearCredentials() {
        try {
            prefsManager.clear();
        } catch (BackingStoreException e) {
            log.error(e.getMessage(), e);
        }
        saveCredentialsCheckBox.setSelected(false);
        hostField.setText("");
        portField.setText("");
        usernameField.setText("");
        passwordField.setText("");
        passwordVisibleField.setText("");
    }

    public void setOnConnect(Runnable onConnect) {
        this.onConnect = onConnect;
    }

    public void setOnDisconnect(Runnable onDisconnect) {
        this.onDisconnect = onDisconnect;
    }

    public String getHost() {
        return hostField.getText().trim();
    }

    public String getPort() {
        return portField.getText().trim();
    }

    public String getUsername() {
        return usernameField.getText().trim();
    }

    public String getPassword() {
        return passwordField.getText();
    }

    public boolean isSaveCredentials() {
        return saveCredentialsCheckBox.isSelected();
    }

    public void setHost(String host) {
        hostField.setText(host);
    }

    public void setPort(String port) {
        portField.setText(port);
    }

    public void setUsername(String username) {
        usernameField.setText(username);
    }

    public void setPassword(String password) {
        passwordField.setText(password);
        passwordVisibleField.setText(password);
    }

    public void setSaveCredentials(boolean save) {
        saveCredentialsCheckBox.setSelected(save);
    }

    public void setConnected(boolean connected) {
        if (connected) {
            connectionStatus.setText("✅ Connected");
            connectionStatus.setTextFill(Color.GREEN);

            // Меняем кнопку на Disconnect и обновляем обработчик
            connectButton.setText("Disconnect");
            connectButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-weight: bold;");
            connectButton.setOnAction(e -> {
                if (onDisconnect != null) {
                    onDisconnect.run();
                }
            });
        } else {
            connectionStatus.setText("⏹ Disconnected");
            connectionStatus.setTextFill(Color.RED);

            // Меняем кнопку на Connect и обновляем обработчик
            connectButton.setText("Connect");
            connectButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
            connectButton.setOnAction(e -> {
                if (onConnect != null) {
                    onConnect.run();
                }
            });
        }
        connectButton.setDisable(false);
    }

    public void setConnecting() {
        connectionStatus.setText("⏳ Connecting...");
        connectionStatus.setTextFill(Color.ORANGE);
        connectButton.setDisable(true);
        // Сохраняем обработчик на случай если кнопка была Disconnect
        connectButton.setOnAction(e -> {});
    }

    public void setDisconnecting() {
        connectionStatus.setText("⏳ Disconnecting...");
        connectionStatus.setTextFill(Color.ORANGE);
        connectButton.setDisable(true);
        connectButton.setOnAction(e -> {});
    }

    public void setConnectEnabled(boolean enabled) {
        connectButton.setDisable(!enabled);
    }

    public void setConnectedTo(String host) {
        connectionStatus.setText("✅ Connected to " + host);
        connectionStatus.setTextFill(Color.GREEN);

        // Меняем кнопку на Disconnect
        connectButton.setText("Disconnect");
        connectButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-weight: bold;");
        connectButton.setOnAction(e -> {
            if (onDisconnect != null) {
                onDisconnect.run();
            }
        });
        connectButton.setDisable(false);
    }

    public void setConnectionFailed() {
        connectionStatus.setText("❌ Connection failed");
        connectionStatus.setTextFill(Color.RED);

        // Меняем кнопку на Connect
        connectButton.setText("Connect");
        connectButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        connectButton.setOnAction(e -> {
            if (onConnect != null) {
                onConnect.run();
            }
        });
        connectButton.setDisable(false);
    }

    public void setConnectionStatusText(String text, Color color) {
        connectionStatus.setText(text);
        connectionStatus.setTextFill(color);
    }

    public void loadCredentials() {
        if (prefsManager.isSaveCredentials()) {
            PreferencesManager.Credentials creds = prefsManager.loadCredentials();
            setHost(creds.host);
            setPort(creds.port);
            setUsername(creds.username);
            setPassword(creds.password);
            setSaveCredentials(true);
        } else {
            setHost(prefsManager.getHost());
            setPort(prefsManager.getPort());
            setUsername(prefsManager.getUsername());
            setPassword("");
            setSaveCredentials(false);
        }
    }

    public void saveCredentials() {
        if (isSaveCredentials()) {
            prefsManager.saveCredentials(getHost(), getPort(), getUsername(), getPassword());
        }
    }
}