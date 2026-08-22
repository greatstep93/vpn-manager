package ru.greatstep.vpnmanager.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import ru.greatstep.vpnmanager.config.models.VpnConfig;
import ru.greatstep.vpnmanager.config.models.VpnDomain;
import ru.greatstep.vpnmanager.config.models.VpnIpEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ListsPanel {

    // Observable lists
    private final ObservableList<String> autoDomainsData = FXCollections.observableArrayList();
    private final ObservableList<String> userDomainsData = FXCollections.observableArrayList();
    private final ObservableList<String> userIpsData = FXCollections.observableArrayList();

    // Filtered lists
    private FilteredList<String> filteredAutoDomains;
    private FilteredList<String> filteredUserDomains;
    private FilteredList<String> filteredUserIps;

    // UI Components
    private ListView<String> autoDomainsList;
    private ListView<String> userDomainsList;
    private ListView<String> userIpsList;
    private TextField autoDomainsSearchField;
    private TextField userDomainsSearchField;
    private TextField userIpsSearchField;
    private TextField globalSearchField;

    // Buttons
    private Button addDomainButton;
    private Button addDomainListButton;
    private Button removeDomainButton;
    private Button addIpButton;
    private Button addIpListButton;
    private Button removeIpButton;

    private Consumer<String> onAddDomain;
    private Runnable onAddDomainList;
    private Consumer<String> onRemoveDomain;
    private Consumer<String> onAddIp;
    private Runnable onAddIpList;
    private Consumer<String> onRemoveIp;

    public HBox create() {
        VBox mainPanel = new VBox(10);
        mainPanel.setPadding(new Insets(10));
        mainPanel.setFillWidth(true);

        // Global search
        HBox globalSearchBox = createGlobalSearch();

        HBox listsBox = new HBox(20);
        listsBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(listsBox, Priority.ALWAYS);

        // Auto domains panel
        VBox autoPanel = createAutoDomainsPanel();

        // User domains panel
        VBox userDomainsPanel = createUserDomainsPanel();

        // User IPs panel
        VBox userIpsPanel = createUserIpsPanel();

        listsBox.getChildren().addAll(autoPanel, userDomainsPanel, userIpsPanel);
        HBox.setHgrow(autoPanel, Priority.ALWAYS);
        HBox.setHgrow(userDomainsPanel, Priority.ALWAYS);
        HBox.setHgrow(userIpsPanel, Priority.ALWAYS);

        mainPanel.getChildren().addAll(globalSearchBox, listsBox);
        VBox.setVgrow(listsBox, Priority.ALWAYS);
        HBox.setHgrow(listsBox, Priority.ALWAYS);

        setupSelectionListeners();

        HBox container = new HBox(mainPanel);
        container.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(mainPanel, Priority.ALWAYS);
        return container;
    }

    private HBox createGlobalSearch() {
        HBox globalSearchBox = new HBox(5);
        globalSearchBox.setAlignment(Pos.CENTER_LEFT);
        globalSearchBox.setMaxWidth(Double.MAX_VALUE);

        Label globalSearchLabel = new Label("🔍 Global Search:");
        globalSearchLabel.setStyle("-fx-font-weight: bold;");

        globalSearchField = new TextField();
        globalSearchField.setPromptText("Search across all lists...");
        globalSearchField.setPrefWidth(300);
        globalSearchField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(globalSearchField, Priority.ALWAYS);
        globalSearchField.textProperty().addListener((obs, old, newVal) -> filterAllLists(newVal));

        globalSearchBox.getChildren().addAll(globalSearchLabel, globalSearchField);
        return globalSearchBox;
    }

    private VBox createAutoDomainsPanel() {
        VBox panel = createListPanel("Auto-loaded Domains", "from /tmp/dnsmasq.d/domains.lst");

        autoDomainsSearchField = new TextField();
        autoDomainsSearchField.setPromptText("🔍 Filter...");
        autoDomainsSearchField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(autoDomainsSearchField, Priority.ALWAYS);
        autoDomainsSearchField.textProperty().addListener((obs, old, newVal) -> filterAutoDomains(newVal));

        autoDomainsList = new ListView<>();
        autoDomainsList.setStyle("-fx-font-size: 12px;");
        autoDomainsList.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(autoDomainsList, Priority.ALWAYS);

        filteredAutoDomains = new FilteredList<>(autoDomainsData);
        autoDomainsList.setItems(filteredAutoDomains);

        panel.getChildren().addAll(autoDomainsSearchField, autoDomainsList);
        return panel;
    }

    private VBox createUserDomainsPanel() {
        VBox panel = createListPanel("User-added Domains", "from /etc/config/dhcp");

        userDomainsSearchField = new TextField();
        userDomainsSearchField.setPromptText("🔍 Filter...");
        userDomainsSearchField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(userDomainsSearchField, Priority.ALWAYS);
        userDomainsSearchField.textProperty().addListener((obs, old, newVal) -> filterUserDomains(newVal));

        userDomainsList = new ListView<>();
        userDomainsList.setStyle("-fx-font-size: 12px;");
        userDomainsList.setCellFactory(listView -> new PendingChangeCell());
        userDomainsList.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(userDomainsList, Priority.ALWAYS);

        filteredUserDomains = new FilteredList<>(userDomainsData);
        userDomainsList.setItems(filteredUserDomains);

        panel.getChildren().addAll(userDomainsSearchField, userDomainsList, createUserDomainButtons());
        return panel;
    }

    private VBox createUserIpsPanel() {
        VBox panel = createListPanel("User-added IPs", "from /etc/config/firewall");

        userIpsSearchField = new TextField();
        userIpsSearchField.setPromptText("🔍 Filter...");
        userIpsSearchField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(userIpsSearchField, Priority.ALWAYS);
        userIpsSearchField.textProperty().addListener((obs, old, newVal) -> filterUserIps(newVal));

        userIpsList = new ListView<>();
        userIpsList.setStyle("-fx-font-size: 12px;");
        userIpsList.setCellFactory(listView -> new PendingChangeCell());
        userIpsList.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(userIpsList, Priority.ALWAYS);

        filteredUserIps = new FilteredList<>(userIpsData);
        userIpsList.setItems(filteredUserIps);

        panel.getChildren().addAll(userIpsSearchField, userIpsList, createUserIpButtons());
        return panel;
    }

    private VBox createListPanel(String title, String subtitle) {
        VBox panel = new VBox(5);
        panel.setPadding(new Insets(10));
        panel.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1; -fx-border-radius: 5;");
        panel.setMaxWidth(Double.MAX_VALUE);
        panel.setMinWidth(200);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666;");

        panel.getChildren().addAll(titleLabel, subtitleLabel);
        return panel;
    }

    private HBox createUserDomainButtons() {
        VBox buttonContainer = new VBox(5);
        buttonContainer.setPadding(new Insets(5, 0, 0, 0));

        // Первая строка: Add Domain и Add Domain List
        HBox topRow = new HBox(5);
        addDomainButton = new Button("Add Domain");
        addDomainButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        addDomainButton.setOnAction(e -> {
            if (onAddDomain != null) onAddDomain.accept(null);
        });
        addDomainButton.setDisable(true);

        addDomainListButton = new Button("Add Domain List");
        addDomainListButton.setStyle("-fx-background-color: #43A047; -fx-text-fill: white; -fx-font-weight: bold;");
        addDomainListButton.setOnAction(e -> {
            if (onAddDomainList != null) onAddDomainList.run();
        });
        addDomainListButton.setDisable(true);
        addDomainListButton.setTooltip(new Tooltip("Add multiple domains at once"));

        topRow.getChildren().addAll(addDomainButton, addDomainListButton);

        // Вторая строка: Remove Domain
        HBox bottomRow = new HBox(5);
        removeDomainButton = new Button("Remove Domain");
        removeDomainButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        removeDomainButton.setOnAction(e -> {
            String selected = userDomainsList.getSelectionModel().getSelectedItem();
            if (onRemoveDomain != null && selected != null) {
                onRemoveDomain.accept(selected);
            } else if (selected == null) {
                Dialogs.showError("Selection Error", "Please select a domain to remove");
            }
        });
        removeDomainButton.setDisable(true);

        // Добавляем слушатель для обновления кнопки при изменении выделения
        userDomainsList.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            updateRemoveButtonState(userDomainsList, removeDomainButton, "Domain");
        });

        bottomRow.getChildren().add(removeDomainButton);

        buttonContainer.getChildren().addAll(topRow, bottomRow);
        return new HBox(buttonContainer);
    }

    private HBox createUserIpButtons() {
        VBox buttonContainer = new VBox(5);
        buttonContainer.setPadding(new Insets(5, 0, 0, 0));

        // Первая строка: Add IP и Add IP List
        HBox topRow = new HBox(5);
        addIpButton = new Button("Add IP");
        addIpButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        addIpButton.setOnAction(e -> {
            if (onAddIp != null) onAddIp.accept(null);
        });
        addIpButton.setDisable(true);

        addIpListButton = new Button("Add IP List");
        addIpListButton.setStyle("-fx-background-color: #43A047; -fx-text-fill: white; -fx-font-weight: bold;");
        addIpListButton.setOnAction(e -> {
            if (onAddIpList != null) onAddIpList.run();
        });
        addIpListButton.setDisable(true);
        addIpListButton.setTooltip(new Tooltip("Add multiple IPs/networks at once"));

        topRow.getChildren().addAll(addIpButton, addIpListButton);

        // Вторая строка: Remove IP
        HBox bottomRow = new HBox(5);
        removeIpButton = new Button("Remove IP");
        removeIpButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        removeIpButton.setOnAction(e -> {
            String selected = userIpsList.getSelectionModel().getSelectedItem();
            if (onRemoveIp != null && selected != null) {
                onRemoveIp.accept(selected);
            } else if (selected == null) {
                Dialogs.showError("Selection Error", "Please select an IP to remove");
            }
        });
        removeIpButton.setDisable(true);

        userIpsList.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            updateRemoveButtonState(userIpsList, removeIpButton, "IP");
        });

        bottomRow.getChildren().add(removeIpButton);

        buttonContainer.getChildren().addAll(topRow, bottomRow);
        return new HBox(buttonContainer);
    }

    private void updateRemoveButtonState(ListView<String> list, Button button, String type) {
        String selected = list.getSelectionModel().getSelectedItem();
        if (selected != null && selected.startsWith("❌ ")) {
            button.setText("Cancel Remove");
            button.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-weight: bold;");
            button.setTooltip(new Tooltip("Cancel removal of this " + type));
        } else {
            button.setText("Remove " + type);
            button.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
            button.setTooltip(null);
        }
    }

    private void setupSelectionListeners() {
        userDomainsList.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            userDomainsList.refresh();
        });

        userIpsList.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            userIpsList.refresh();
        });
    }

    // Filter methods
    private void filterAutoDomains(String filter) {
        if (filteredAutoDomains != null) {
            filteredAutoDomains.setPredicate(item -> {
                if (filter == null || filter.isEmpty()) return true;
                return item.toLowerCase().contains(filter.toLowerCase());
            });
        }
    }

    private void filterUserDomains(String filter) {
        if (filteredUserDomains != null) {
            filteredUserDomains.setPredicate(item -> {
                if (filter == null || filter.isEmpty()) return true;
                String cleanItem = item.replace("✅ ", "").replace("❌ ", "");
                return cleanItem.toLowerCase().contains(filter.toLowerCase());
            });
        }
    }

    private void filterUserIps(String filter) {
        if (filteredUserIps != null) {
            filteredUserIps.setPredicate(item -> {
                if (filter == null || filter.isEmpty()) return true;
                String cleanItem = item.replace("✅ ", "").replace("❌ ", "");
                return cleanItem.toLowerCase().contains(filter.toLowerCase());
            });
        }
    }

    private void filterAllLists(String filter) {
        filterAutoDomains(filter);
        filterUserDomains(filter);
        filterUserIps(filter);
    }

    // Update methods
    public void updateLists(VpnConfig config, List<String> pendingDomains, List<String> pendingIps) {
        if (config == null) return;

        autoDomainsData.clear();
        autoDomainsData.addAll(config.autoDomains().stream()
                .map(VpnDomain::domain)
                .toList());

        userDomainsData.clear();
        List<String> allUserDomains = new ArrayList<>(config.userDomains().stream()
                .map(VpnDomain::domain)
                .toList());

        for (String domain : pendingDomains) {
            if (domain.startsWith("!")) {
                String realDomain = domain.substring(1);
                if (!allUserDomains.contains(realDomain)) {
                    allUserDomains.add("❌ " + realDomain);
                } else {
                    allUserDomains.remove(realDomain);
                    allUserDomains.add("❌ " + realDomain);
                }
            } else {
                if (!allUserDomains.contains(domain)) {
                    allUserDomains.add("✅ " + domain);
                }
            }
        }
        userDomainsData.addAll(allUserDomains);

        userIpsData.clear();
        List<String> allUserIps = new ArrayList<>(config.userIpEntries().stream()
                .map(VpnIpEntry::ipOrNetwork)
                .toList());

        for (String ip : pendingIps) {
            if (ip.startsWith("!")) {
                String realIp = ip.substring(1);
                if (!allUserIps.contains(realIp)) {
                    allUserIps.add("❌ " + realIp);
                } else {
                    allUserIps.remove(realIp);
                    allUserIps.add("❌ " + realIp);
                }
            } else {
                if (!allUserIps.contains(ip)) {
                    allUserIps.add("✅ " + ip);
                }
            }
        }
        userIpsData.addAll(allUserIps);

        autoDomainsSearchField.clear();
        userDomainsSearchField.clear();
        userIpsSearchField.clear();
        globalSearchField.clear();

        resetButtonStates();
    }

    private void resetButtonStates() {
        if (removeDomainButton != null) {
            removeDomainButton.setText("Remove Domain");
            removeDomainButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        }
        if (removeIpButton != null) {
            removeIpButton.setText("Remove IP");
            removeIpButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        }
    }

    public void clearLists() {
        autoDomainsData.clear();
        userDomainsData.clear();
        userIpsData.clear();
        resetButtonStates();
    }

    // Getters
    public ListView<String> getAutoDomainsList() { return autoDomainsList; }
    public ListView<String> getUserDomainsList() { return userDomainsList; }
    public ListView<String> getUserIpsList() { return userIpsList; }

    // Enable/disable controls
    public void setControlsEnabled(boolean enabled) {
        if (addDomainButton != null) {
            addDomainButton.setDisable(!enabled);
            addDomainListButton.setDisable(!enabled);
            removeDomainButton.setDisable(!enabled);
            addIpButton.setDisable(!enabled);
            addIpListButton.setDisable(!enabled);
            removeIpButton.setDisable(!enabled);
        }
        if (!enabled) {
            resetButtonStates();
        }
    }

    public void refreshCellStyles() {
        if (userDomainsList != null) {
            userDomainsList.refresh();
        }
        if (userIpsList != null) {
            userIpsList.refresh();
        }
    }

    // Callback setters
    public void setOnAddDomain(Consumer<String> onAddDomain) { this.onAddDomain = onAddDomain; }
    public void setOnAddDomainList(Runnable onAddDomainList) { this.onAddDomainList = onAddDomainList; }
    public void setOnRemoveDomain(Consumer<String> onRemoveDomain) { this.onRemoveDomain = onRemoveDomain; }
    public void setOnAddIp(Consumer<String> onAddIp) { this.onAddIp = onAddIp; }
    public void setOnAddIpList(Runnable onAddIpList) { this.onAddIpList = onAddIpList; }
    public void setOnRemoveIp(Consumer<String> onRemoveIp) { this.onRemoveIp = onRemoveIp; }
}