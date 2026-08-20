package ru.greatstep.vpnmanager;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import ru.greatstep.vpnmanager.config.models.VpnConfig;
import ru.greatstep.vpnmanager.config.models.VpnDomain;
import ru.greatstep.vpnmanager.config.models.VpnIpEntry;
import ru.greatstep.vpnmanager.service.VpnConfigService;
import ru.greatstep.vpnmanager.ssh.SSHClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;

public class MainApp extends Application {

    private static final String PREFS_NODE = "vpnmanager";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_SAVE_CREDENTIALS = "saveCredentials";

    private SSHClient sshClient;
    private VpnConfigService service;
    private VpnConfig currentConfig;

    // UI Components
    private ListView<String> autoDomainsList;
    private ListView<String> userDomainsList;
    private ListView<String> userIpsList;
    private TextArea logArea;
    private Label statusLabel;
    private Label connectionStatus;
    private Button connectButton;
    private Button addDomainButton;
    private Button removeDomainButton;
    private Button addIpButton;
    private Button removeIpButton;
    private Button refreshButton;
    private Button applyButton;
    private Button toggleLogsButton;

    // Connection fields
    private TextField hostField;
    private TextField portField;
    private TextField usernameField;
    private PasswordField passwordField;
    private TextField passwordVisibleField;
    private Button togglePasswordButton;
    private CheckBox saveCredentialsCheckBox;

    // Lists for pending changes
    private List<String> pendingDomains = new ArrayList<>();
    private List<String> pendingIps = new ArrayList<>();

    private boolean logsVisible = false;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("VPN Manager for OpenWrt");
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(700);

        // Main layout
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // Сначала создаем все компоненты
        initializeComponents();

        // Top: Connection panel
        root.setTop(createConnectionPanel());

        // Center: Three lists
        root.setCenter(createListsPanel());

        // Bottom: Status and logs
        root.setBottom(createBottomPanel());

        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.show();

        addLog("Application started");

        // Загружаем сохраненные данные
        loadSavedCredentials();
    }

    private void initializeComponents() {
        // Initialize all buttons to avoid NullPointerException
        addDomainButton = new Button("Add Domain");
        removeDomainButton = new Button("Remove Domain");
        addIpButton = new Button("Add IP");
        removeIpButton = new Button("Remove IP");
        refreshButton = new Button("Refresh");
        applyButton = new Button("Apply Changes");
        toggleLogsButton = new Button("▶ Show Logs");

        // Initially disabled
        addDomainButton.setDisable(true);
        removeDomainButton.setDisable(true);
        addIpButton.setDisable(true);
        removeIpButton.setDisable(true);
        refreshButton.setDisable(true);
        applyButton.setDisable(true);
    }

    private void loadSavedCredentials() {
        try {
            Preferences prefs = Preferences.userRoot().node(PREFS_NODE);

            boolean saveCredentials = prefs.getBoolean(KEY_SAVE_CREDENTIALS, false);
            saveCredentialsCheckBox.setSelected(saveCredentials);

            if (saveCredentials) {
                String host = prefs.get(KEY_HOST, "");
                String port = prefs.get(KEY_PORT, "22");
                String username = prefs.get(KEY_USERNAME, "root");
                String password = prefs.get(KEY_PASSWORD, "");

                hostField.setText(host);
                portField.setText(port);
                usernameField.setText(username);
                passwordField.setText(password);
                if (passwordVisibleField != null) {
                    passwordVisibleField.setText(password);
                }

                addLog("Saved credentials loaded");
            } else {
                // Загружаем только хост и порт если пароль не сохранен
                String host = prefs.get(KEY_HOST, "");
                String port = prefs.get(KEY_PORT, "22");
                String username = prefs.get(KEY_USERNAME, "root");

                hostField.setText(host);
                portField.setText(port);
                usernameField.setText(username);
                passwordField.setText("");
                if (passwordVisibleField != null) {
                    passwordVisibleField.setText("");
                }
            }
        } catch (Exception e) {
            addLog("Failed to load saved credentials: " + e.getMessage());
        }
    }

    private void saveCredentials() {
        try {
            Preferences prefs = Preferences.userRoot().node(PREFS_NODE);

            boolean save = saveCredentialsCheckBox.isSelected();
            prefs.putBoolean(KEY_SAVE_CREDENTIALS, save);

            if (save) {
                prefs.put(KEY_HOST, hostField.getText().trim());
                prefs.put(KEY_PORT, portField.getText().trim());
                prefs.put(KEY_USERNAME, usernameField.getText().trim());
                prefs.put(KEY_PASSWORD, passwordField.getText());
                addLog("Credentials saved");
            } else {
                // Очищаем сохраненные данные
                prefs.remove(KEY_HOST);
                prefs.remove(KEY_PORT);
                prefs.remove(KEY_USERNAME);
                prefs.remove(KEY_PASSWORD);
                addLog("Credentials cleared");
            }
        } catch (Exception e) {
            addLog("Failed to save credentials: " + e.getMessage());
        }
    }

    private VBox createConnectionPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1; -fx-border-radius: 5;");

        // Connection fields
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

        // Password field with show/hide button
        HBox passwordBox = new HBox(5);
        passwordBox.setAlignment(Pos.CENTER_LEFT);

        passwordField = new PasswordField();
        passwordField.setPrefWidth(165);
        passwordField.setPromptText("Enter password");

        // TextField for showing password (hidden by default)
        passwordVisibleField = new TextField();
        passwordVisibleField.setPrefWidth(165);
        passwordVisibleField.setPromptText("Enter password");
        passwordVisibleField.setManaged(false);
        passwordVisibleField.setVisible(false);

        // Bind text between fields
        passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());

        // Toggle password visibility button (text version)
        togglePasswordButton = new Button("SHOW");
        togglePasswordButton.setStyle("-fx-font-size: 10px; -fx-background-color: transparent; -fx-padding: 2 5 2 5; -fx-font-weight: bold; -fx-text-fill: #2196F3;");
        togglePasswordButton.setTooltip(new Tooltip("Show/Hide password"));
        togglePasswordButton.setOnAction(e -> {
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
        });

        passwordBox.getChildren().addAll(passwordField, passwordVisibleField, togglePasswordButton);

        saveCredentialsCheckBox = new CheckBox("Save credentials");
        saveCredentialsCheckBox.setStyle("-fx-font-size: 11px;");

        connectionStatus = new Label("⏹ Disconnected");
        connectionStatus.setTextFill(Color.RED);

        connectButton = new Button("Connect");
        connectButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        connectButton.setOnAction(e -> {
            try {
                String host = hostField.getText().trim();
                if (host.isEmpty()) {
                    showError("Input Error", "Router IP is required");
                    return;
                }

                int port = 22;
                if (!portField.getText().trim().isEmpty()) {
                    port = Integer.parseInt(portField.getText().trim());
                }

                String username = usernameField.getText().trim();
                if (username.isEmpty()) {
                    username = "root";
                }

                String password = passwordField.getText();
                if (password.isEmpty()) {
                    showError("Input Error", "Password is required");
                    return;
                }

                // Сохраняем данные для входа
                saveCredentials();

                connectButton.setDisable(true);
                connectionStatus.setText("⏳ Connecting...");
                connectionStatus.setTextFill(Color.ORANGE);
                addLog("Connecting to " + host + ":" + port);

                int finalPort = port;
                String finalUsername = username;
                new Thread(() -> {
                    try {
                        sshClient = new SSHClient(host, finalPort, finalUsername, password);
                        sshClient.connect();
                        service = new VpnConfigService(sshClient);

                        Platform.runLater(() -> {
                            connectionStatus.setText("✅ Connected to " + host);
                            connectionStatus.setTextFill(Color.GREEN);
                            connectButton.setText("Disconnect");
                            connectButton.setOnAction(disconnectEvent -> disconnect());

                            // Enable controls
                            enableControls(true);

                            // Load initial config
                            loadConfig();
                            addLog("Connected successfully");
                        });
                    } catch (Exception ex) {
                        Platform.runLater(() -> {
                            connectionStatus.setText("❌ Connection failed");
                            connectionStatus.setTextFill(Color.RED);
                            connectButton.setDisable(false);
                            addLog("Connection failed: " + ex.getMessage());
                            showError("Connection Error", ex.getMessage());
                        });
                    }
                }).start();

            } catch (Exception ex) {
                showError("Input Error", "Invalid port number");
                connectButton.setDisable(false);
            }
        });

        // Add clear credentials button
        Button clearCredentialsButton = new Button("Clear Saved");
        clearCredentialsButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-size: 11px;");
        clearCredentialsButton.setOnAction(e -> {
            try {
                Preferences prefs = Preferences.userRoot().node(PREFS_NODE);
                prefs.clear();
                saveCredentialsCheckBox.setSelected(false);
                hostField.setText("");
                portField.setText("");
                usernameField.setText("");
                passwordField.setText("");
                passwordVisibleField.setText("");
                addLog("Saved credentials cleared");
            } catch (Exception ex) {
                addLog("Failed to clear credentials: " + ex.getMessage());
            }
        });

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

    private HBox createListsPanel() {
        HBox panel = new HBox(20);
        panel.setPadding(new Insets(10));

        // Auto domains panel
        VBox autoPanel = createListPanel("Auto-loaded Domains", "from /tmp/dnsmasq.d/domains.lst", true);
        autoDomainsList = new ListView<>();
        autoDomainsList.setPrefHeight(300);
        autoDomainsList.setStyle("-fx-font-size: 12px;");
        autoPanel.getChildren().add(autoDomainsList);
        VBox.setVgrow(autoDomainsList, Priority.ALWAYS);

        // User domains panel
        VBox userDomainsPanel = createListPanel("User-added Domains", "from /etc/config/dhcp", false);
        userDomainsList = new ListView<>();
        userDomainsList.setPrefHeight(300);
        userDomainsList.setStyle("-fx-font-size: 12px;");
        userDomainsPanel.getChildren().add(userDomainsList);
        VBox.setVgrow(userDomainsList, Priority.ALWAYS);
        userDomainsPanel.getChildren().add(createUserDomainButtons());

        // User IPs panel
        VBox userIpsPanel = createListPanel("User-added IPs", "from /etc/config/firewall", false);
        userIpsList = new ListView<>();
        userIpsList.setPrefHeight(300);
        userIpsList.setStyle("-fx-font-size: 12px;");
        userIpsPanel.getChildren().add(userIpsList);
        VBox.setVgrow(userIpsList, Priority.ALWAYS);
        userIpsPanel.getChildren().add(createUserIpButtons());

        panel.getChildren().addAll(autoPanel, userDomainsPanel, userIpsPanel);
        HBox.setHgrow(autoPanel, Priority.ALWAYS);
        HBox.setHgrow(userDomainsPanel, Priority.ALWAYS);
        HBox.setHgrow(userIpsPanel, Priority.ALWAYS);

        return panel;
    }

    private VBox createListPanel(String title, String subtitle, boolean readOnly) {
        VBox panel = new VBox(5);
        panel.setPadding(new Insets(10));
        panel.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1; -fx-border-radius: 5;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666;");

        panel.getChildren().addAll(titleLabel, subtitleLabel);
        return panel;
    }

    private HBox createUserDomainButtons() {
        HBox box = new HBox(5);
        box.setPadding(new Insets(5, 0, 0, 0));

        addDomainButton = new Button("Add Domain");
        addDomainButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        addDomainButton.setOnAction(e -> addDomain());
        addDomainButton.setDisable(true);

        removeDomainButton = new Button("Remove Domain");
        removeDomainButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        removeDomainButton.setOnAction(e -> removeDomain());
        removeDomainButton.setDisable(true);

        box.getChildren().addAll(addDomainButton, removeDomainButton);
        return box;
    }

    private HBox createUserIpButtons() {
        HBox box = new HBox(5);
        box.setPadding(new Insets(5, 0, 0, 0));

        addIpButton = new Button("Add IP");
        addIpButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        addIpButton.setOnAction(e -> addIp());
        addIpButton.setDisable(true);

        removeIpButton = new Button("Remove IP");
        removeIpButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        removeIpButton.setOnAction(e -> removeIp());
        removeIpButton.setDisable(true);

        refreshButton = new Button("Refresh");
        refreshButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
        refreshButton.setOnAction(e -> loadConfig());
        refreshButton.setDisable(true);

        box.getChildren().addAll(addIpButton, removeIpButton, refreshButton);
        return box;
    }

    private VBox createBottomPanel() {
        VBox panel = new VBox(5);

        // Status bar
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(10));
        statusBar.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1; -fx-border-radius: 5;");

        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-font-size: 12px;");

        // Apply button
        applyButton = new Button("Apply Changes");
        applyButton.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-weight: bold;");
        applyButton.setOnAction(e -> applyChanges());
        applyButton.setDisable(true);

        // Toggle logs button
        toggleLogsButton = new Button("▶ Show Logs");
        toggleLogsButton.setStyle("-fx-background-color: #607D8B; -fx-text-fill: white;");
        toggleLogsButton.setOnAction(e -> toggleLogs());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusBar.getChildren().addAll(statusLabel, spacer, applyButton, toggleLogsButton);

        // Log area (hidden by default)
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        logArea.setPrefHeight(150);
        logArea.setVisible(false);
        logArea.setManaged(false);

        panel.getChildren().addAll(statusBar, logArea);
        return panel;
    }

    private void toggleLogs() {
        logsVisible = !logsVisible;
        logArea.setVisible(logsVisible);
        logArea.setManaged(logsVisible);
        toggleLogsButton.setText(logsVisible ? "▼ Hide Logs" : "▶ Show Logs");
    }

    private void addLog(String message) {
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        Platform.runLater(() -> {
            logArea.appendText("[" + timestamp + "] " + message + "\n");
            // Auto-scroll to bottom
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    private void enableControls(boolean enabled) {
        if (addDomainButton != null) {
            addDomainButton.setDisable(!enabled);
            removeDomainButton.setDisable(!enabled);
            addIpButton.setDisable(!enabled);
            removeIpButton.setDisable(!enabled);
            refreshButton.setDisable(!enabled);
            applyButton.setDisable(!enabled);
        }
    }

    private void disconnect() {
        if (sshClient != null) {
            sshClient.close();
        }
        connectionStatus.setText("⏹ Disconnected");
        connectionStatus.setTextFill(Color.RED);
        connectButton.setText("Connect");
        connectButton.setDisable(false);
        connectButton.setOnAction(e -> {
            // Reconnect logic will be handled by the button action
        });
        enableControls(false);
        clearLists();
        addLog("Disconnected");
    }

    private void loadConfig() {
        if (service == null) {
            return;
        }

        statusLabel.setText("⏳ Loading configuration...");
        statusLabel.setTextFill(Color.ORANGE);
        addLog("Loading configuration...");

        new Thread(() -> {
            try {
                currentConfig = service.loadConfig();

                // Clear pending changes
                pendingDomains.clear();
                pendingIps.clear();

                Platform.runLater(() -> {
                    updateLists();
                    statusLabel.setText("✅ Configuration loaded - " +
                            currentConfig.totalDomains() + " domains, " +
                            currentConfig.userIpEntries().size() + " IPs");
                    statusLabel.setTextFill(Color.GREEN);
                    applyButton.setDisable(true);
                    addLog("Configuration loaded successfully");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("❌ Failed to load configuration: " + e.getMessage());
                    statusLabel.setTextFill(Color.RED);
                    addLog("Failed to load configuration: " + e.getMessage());
                    showError("Load Error", e.getMessage());
                });
            }
        }).start();
    }

    private void updateLists() {
        if (currentConfig == null) return;

        // Auto domains
        autoDomainsList.getItems().clear();
        currentConfig.autoDomains().stream()
                .map(VpnDomain::domain)
                .forEach(autoDomainsList.getItems()::add);

        // User domains - combine current config with pending changes
        List<String> allDomains = new ArrayList<>(currentConfig.userDomains().stream()
                .map(VpnDomain::domain)
                .toList());

        // Apply pending changes visually
        for (String domain : pendingDomains) {
            if (!domain.startsWith("!") && !allDomains.contains(domain)) {
                allDomains.add(domain);
            }
        }

        userDomainsList.getItems().clear();
        allDomains.forEach(userDomainsList.getItems()::add);

        // User IPs - combine current config with pending changes
        List<String> allIps = new ArrayList<>(currentConfig.userIpEntries().stream()
                .map(VpnIpEntry::ipOrNetwork)
                .toList());

        for (String ip : pendingIps) {
            if (!ip.startsWith("!") && !allIps.contains(ip)) {
                allIps.add(ip);
            }
        }

        userIpsList.getItems().clear();
        allIps.forEach(userIpsList.getItems()::add);

        // Enable apply button if there are pending changes
        boolean hasPending = pendingDomains.stream().anyMatch(d -> !d.startsWith("!")) ||
                pendingDomains.stream().anyMatch(d -> d.startsWith("!")) ||
                pendingIps.stream().anyMatch(ip -> !ip.startsWith("!")) ||
                pendingIps.stream().anyMatch(ip -> ip.startsWith("!"));

        applyButton.setDisable(!hasPending);
        if (hasPending) {
            statusLabel.setText("⏳ Pending changes: " +
                    pendingDomains.size() + " domains, " +
                    pendingIps.size() + " IPs");
            statusLabel.setTextFill(Color.ORANGE);
        }
    }

    private void clearLists() {
        autoDomainsList.getItems().clear();
        userDomainsList.getItems().clear();
        userIpsList.getItems().clear();
        pendingDomains.clear();
        pendingIps.clear();
        applyButton.setDisable(true);
    }

    private void addDomain() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Domain");
        dialog.setHeaderText("Add domain to VPN routing");
        dialog.setContentText("Enter domain (e.g., example.com):");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(domain -> {
            if (domain.trim().isEmpty()) {
                showError("Input Error", "Domain cannot be empty");
                return;
            }

            String cleanDomain = domain.trim().toLowerCase();

            // Check if already in pending list
            if (pendingDomains.contains(cleanDomain)) {
                showError("Duplicate", "Domain already added to pending list");
                return;
            }

            // Check if already in current config
            if (currentConfig != null && currentConfig.userDomains().stream()
                    .anyMatch(d -> d.domain().equalsIgnoreCase(cleanDomain))) {
                showError("Duplicate", "Domain already exists in configuration");
                return;
            }

            pendingDomains.add(cleanDomain);
            addLog("Domain added to pending: " + cleanDomain);
            statusLabel.setText("⏳ Pending: " + pendingDomains.size() + " domains, " + pendingIps.size() + " IPs");
            statusLabel.setTextFill(Color.ORANGE);
            updateLists();
        });
    }

    private void removeDomain() {
        String selected = userDomainsList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Selection Error", "Please select a domain to remove");
            return;
        }

        // Check if in pending list (added but not applied)
        if (pendingDomains.contains(selected)) {
            pendingDomains.remove(selected);
            addLog("Domain removed from pending: " + selected);
            updateLists();
            return;
        }

        // Check if marked for removal already
        String markForRemoval = "!" + selected;
        if (pendingDomains.contains(markForRemoval)) {
            pendingDomains.remove(markForRemoval);
            addLog("Domain unmarked for removal: " + selected);
            updateLists();
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Remove Domain");
        alert.setHeaderText("Remove domain from VPN routing");
        alert.setContentText("Are you sure you want to remove: " + selected + "?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            pendingDomains.add("!" + selected); // Mark for removal
            addLog("Domain marked for removal: " + selected);
            statusLabel.setText("⏳ Pending: " + pendingDomains.size() + " domains, " + pendingIps.size() + " IPs");
            statusLabel.setTextFill(Color.ORANGE);
            updateLists();
        }
    }

    private void addIp() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add IP");
        dialog.setHeaderText("Add IP or network to VPN routing");
        dialog.setContentText("Enter IP/network (e.g., 192.168.1.0/24):");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(ip -> {
            if (ip.trim().isEmpty()) {
                showError("Input Error", "IP cannot be empty");
                return;
            }

            String cleanIp = ip.trim();

            if (pendingIps.contains(cleanIp)) {
                showError("Duplicate", "IP already added to pending list");
                return;
            }

            if (currentConfig != null && currentConfig.userIpEntries().stream()
                    .anyMatch(entry -> entry.ipOrNetwork().equals(cleanIp))) {
                showError("Duplicate", "IP already exists in configuration");
                return;
            }

            pendingIps.add(cleanIp);
            addLog("IP added to pending: " + cleanIp);
            statusLabel.setText("⏳ Pending: " + pendingDomains.size() + " domains, " + pendingIps.size() + " IPs");
            statusLabel.setTextFill(Color.ORANGE);
            updateLists();
        });
    }

    private void removeIp() {
        String selected = userIpsList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Selection Error", "Please select an IP to remove");
            return;
        }

        if (pendingIps.contains(selected)) {
            pendingIps.remove(selected);
            addLog("IP removed from pending: " + selected);
            updateLists();
            return;
        }

        String markForRemoval = "!" + selected;
        if (pendingIps.contains(markForRemoval)) {
            pendingIps.remove(markForRemoval);
            addLog("IP unmarked for removal: " + selected);
            updateLists();
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Remove IP");
        alert.setHeaderText("Remove IP from VPN routing");
        alert.setContentText("Are you sure you want to remove: " + selected + "?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            pendingIps.add("!" + selected);
            addLog("IP marked for removal: " + selected);
            statusLabel.setText("⏳ Pending: " + pendingDomains.size() + " domains, " + pendingIps.size() + " IPs");
            statusLabel.setTextFill(Color.ORANGE);
            updateLists();
        }
    }

    private void applyChanges() {
        boolean hasPending = pendingDomains.stream().anyMatch(d -> !d.startsWith("!")) ||
                pendingDomains.stream().anyMatch(d -> d.startsWith("!")) ||
                pendingIps.stream().anyMatch(ip -> !ip.startsWith("!")) ||
                pendingIps.stream().anyMatch(ip -> ip.startsWith("!"));

        if (!hasPending) {
            statusLabel.setText("No pending changes to apply");
            statusLabel.setTextFill(Color.YELLOW);
            return;
        }

        // Count actual changes
        long addDomains = pendingDomains.stream().filter(d -> !d.startsWith("!")).count();
        long removeDomains = pendingDomains.stream().filter(d -> d.startsWith("!")).count();
        long addIps = pendingIps.stream().filter(ip -> !ip.startsWith("!")).count();
        long removeIps = pendingIps.stream().filter(ip -> ip.startsWith("!")).count();

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Apply Changes");
        alert.setHeaderText("Apply pending changes to router");
        alert.setContentText(
                "Pending changes:\n" +
                        "  Add domains: " + addDomains + "\n" +
                        "  Remove domains: " + removeDomains + "\n" +
                        "  Add IPs: " + addIps + "\n" +
                        "  Remove IPs: " + removeIps + "\n\n" +
                        "This will restart dnsmasq and firewall services."
        );

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            statusLabel.setText("⏳ Applying changes...");
            statusLabel.setTextFill(Color.ORANGE);
            applyButton.setDisable(true);
            addLog("Applying " + pendingDomains.size() + " domain and " + pendingIps.size() + " IP changes...");

            new Thread(() -> {
                try {
                    // Get current config
                    VpnConfig config = service.loadConfig();

                    // Process pending changes
                    List<String> domains = new ArrayList<>(config.userDomains().stream()
                            .map(VpnDomain::domain)
                            .toList());

                    List<String> ips = new ArrayList<>(config.userIpEntries().stream()
                            .map(VpnIpEntry::ipOrNetwork)
                            .toList());

                    // Apply domain changes
                    for (String item : pendingDomains) {
                        if (item.startsWith("!")) {
                            // Remove domain
                            String domain = item.substring(1);
                            domains.removeIf(d -> d.equalsIgnoreCase(domain));
                            addLog("Removed domain: " + domain);
                        } else {
                            // Add domain
                            if (!domains.contains(item)) {
                                domains.add(item);
                                addLog("Added domain: " + item);
                            }
                        }
                    }

                    // Apply IP changes
                    for (String item : pendingIps) {
                        if (item.startsWith("!")) {
                            // Remove IP
                            String ip = item.substring(1);
                            ips.removeIf(i -> i.equals(ip));
                            addLog("Removed IP: " + ip);
                        } else {
                            // Add IP
                            if (!ips.contains(item)) {
                                ips.add(item);
                                addLog("Added IP: " + item);
                            }
                        }
                    }

                    // Apply to router
                    service.applyFullConfig(new VpnConfig(
                            config.autoDomains(),
                            domains.stream().map(d -> new VpnDomain(d, true)).toList(),
                            ips.stream().map(VpnIpEntry::new).toList()
                    ));

                    // Clear pending changes
                    pendingDomains.clear();
                    pendingIps.clear();

                    Platform.runLater(() -> {
                        statusLabel.setText("✅ Changes applied successfully!");
                        statusLabel.setTextFill(Color.GREEN);
                        addLog("Changes applied successfully");
                        loadConfig(); // Reload to show final state
                    });

                } catch (Exception e) {
                    Platform.runLater(() -> {
                        statusLabel.setText("❌ Failed to apply changes: " + e.getMessage());
                        statusLabel.setTextFill(Color.RED);
                        applyButton.setDisable(false);
                        addLog("Failed to apply changes: " + e.getMessage());
                        showError("Apply Error", e.getMessage());
                    });
                }
            }).start();
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}