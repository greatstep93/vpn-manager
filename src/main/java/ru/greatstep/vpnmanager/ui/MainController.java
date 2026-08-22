package ru.greatstep.vpnmanager.ui;

import javafx.application.Platform;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import ru.greatstep.vpnmanager.config.models.VpnConfig;
import ru.greatstep.vpnmanager.config.models.VpnDomain;
import ru.greatstep.vpnmanager.config.models.VpnIpEntry;
import ru.greatstep.vpnmanager.service.VpnConfigService;
import ru.greatstep.vpnmanager.ssh.SSHClient;
import ru.greatstep.vpnmanager.utils.PreferencesManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainController {

    private final PreferencesManager prefsManager = new PreferencesManager();

    private SSHClient sshClient;
    private VpnConfigService service;
    private VpnConfig currentConfig;

    private final ConnectionPanel connectionPanel;
    private final ListsPanel listsPanel;
    private final BottomPanel bottomPanel;

    private final List<String> pendingDomains = new ArrayList<>();
    private final List<String> pendingIps = new ArrayList<>();

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private boolean isConnected = false;

    public MainController() {
        this.connectionPanel = new ConnectionPanel(prefsManager);
        this.listsPanel = new ListsPanel();
        this.bottomPanel = new BottomPanel();

        setupCallbacks();
    }

    private void setupCallbacks() {
        connectionPanel.setOnConnect(this::connect);
        connectionPanel.setOnDisconnect(this::disconnect);

        listsPanel.setOnAddDomain(domain -> addDomain());
        listsPanel.setOnAddDomainList(this::addDomainList);
        listsPanel.setOnRemoveDomain(this::removeDomain);
        listsPanel.setOnAddIp(ip -> addIp());
        listsPanel.setOnAddIpList(this::addIpList);
        listsPanel.setOnRemoveIp(this::removeIp);

        bottomPanel.setOnApply(this::applyChanges);
        bottomPanel.setOnRefresh(this::loadConfig);
    }

    private void addDomainList() {
        Dialogs.showListInputDialog(
                "Add Domain List",
                "Add multiple domains to VPN routing",
                "Enter domains (one per line):",
                "example.com\nyoutube.com\nfacebook.com"
        ).ifPresent(input -> {
            if (input.trim().isEmpty()) {
                Dialogs.showError("Input Error", "No domains entered");
                return;
            }

            String[] lines = input.split("\n");
            int addedCount = 0;
            int duplicateCount = 0;
            int invalidCount = 0;

            for (String line : lines) {
                String domain = line.trim().toLowerCase();
                if (domain.isEmpty()) continue;

                // Проверяем, не содержит ли строка пробелов или спецсимволов (кроме точки и дефиса)
                if (!domain.matches("^[a-z0-9.-]+$")) {
                    invalidCount++;
                    continue;
                }

                if (pendingDomains.contains(domain)) {
                    duplicateCount++;
                    continue;
                }

                if (currentConfig != null && currentConfig.userDomains().stream()
                        .anyMatch(d -> d.domain().equalsIgnoreCase(domain))) {
                    duplicateCount++;
                    continue;
                }

                pendingDomains.add(domain);
                addedCount++;
            }

            if (addedCount > 0) {
                bottomPanel.addLog("Added " + addedCount + " domains to pending");
                if (duplicateCount > 0) {
                    bottomPanel.addLog("Skipped " + duplicateCount + " duplicates");
                }
                if (invalidCount > 0) {
                    bottomPanel.addLog("Skipped " + invalidCount + " invalid entries");
                }
                updatePendingStatus();
                listsPanel.updateLists(currentConfig, pendingDomains, pendingIps);
            } else {
                String message = "No domains were added";
                if (duplicateCount > 0) message += " (" + duplicateCount + " duplicates skipped)";
                if (invalidCount > 0) message += " (" + invalidCount + " invalid entries skipped)";
                Dialogs.showError("Nothing Added", message);
            }
        });
    }

    private void addIpList() {
        Dialogs.showListInputDialog(
                "Add IP List",
                "Add multiple IPs/networks to VPN routing",
                "Enter IPs/networks (one per line):",
                "192.168.1.0/24\n10.0.0.1\n8.8.8.8/32"
        ).ifPresent(input -> {
            if (input.trim().isEmpty()) {
                Dialogs.showError("Input Error", "No IPs entered");
                return;
            }

            String[] lines = input.split("\n");
            int addedCount = 0;
            int duplicateCount = 0;
            int invalidCount = 0;

            for (String line : lines) {
                String ip = line.trim();
                if (ip.isEmpty()) continue;

                // Простая проверка на IP/сеть
                if (!ip.matches("^[0-9a-fA-F:./]+$")) {
                    invalidCount++;
                    continue;
                }

                if (pendingIps.contains(ip)) {
                    duplicateCount++;
                    continue;
                }

                if (currentConfig != null && currentConfig.userIpEntries().stream()
                        .anyMatch(entry -> entry.ipOrNetwork().equals(ip))) {
                    duplicateCount++;
                    continue;
                }

                pendingIps.add(ip);
                addedCount++;
            }

            if (addedCount > 0) {
                bottomPanel.addLog("Added " + addedCount + " IPs to pending");
                if (duplicateCount > 0) {
                    bottomPanel.addLog("Skipped " + duplicateCount + " duplicates");
                }
                if (invalidCount > 0) {
                    bottomPanel.addLog("Skipped " + invalidCount + " invalid entries");
                }
                updatePendingStatus();
                listsPanel.updateLists(currentConfig, pendingDomains, pendingIps);
            } else {
                String message = "No IPs were added";
                if (duplicateCount > 0) message += " (" + duplicateCount + " duplicates skipped)";
                if (invalidCount > 0) message += " (" + invalidCount + " invalid entries skipped)";
                Dialogs.showError("Nothing Added", message);
            }
        });
    }

    public VBox getConnectionPanel() {
        VBox panel = connectionPanel.create();
        connectionPanel.loadCredentials();
        return panel;
    }

    public javafx.scene.layout.HBox getListsPanel() {
        return listsPanel.create();
    }

    public VBox getBottomPanel() {
        return bottomPanel.create();
    }

    private void connect() {
        try {
            String host = connectionPanel.getHost();
            if (host.isEmpty()) {
                Dialogs.showError("Input Error", "Router IP is required");
                return;
            }

            int port = 22;
            if (!connectionPanel.getPort().isEmpty()) {
                port = Integer.parseInt(connectionPanel.getPort());
            }

            String username = connectionPanel.getUsername();
            if (username.isEmpty()) {
                username = "root";
            }

            String password = connectionPanel.getPassword();
            if (password.isEmpty()) {
                Dialogs.showError("Input Error", "Password is required");
                return;
            }

            connectionPanel.saveCredentials();
            connectionPanel.setConnecting();
            bottomPanel.addLog("Connecting to " + host + ":" + port);

            int finalPort = port;
            String finalUsername = username;
            executor.submit(() -> {
                try {
                    sshClient = new SSHClient(host, finalPort, finalUsername, password);
                    sshClient.connect();
                    service = new VpnConfigService(sshClient);

                    Platform.runLater(() -> {
                        isConnected = true;
                        connectionPanel.setConnectedTo(host);
                        listsPanel.setControlsEnabled(true);
                        bottomPanel.setRefreshEnabled(true);
                        bottomPanel.setApplyEnabled(false);
                        loadConfig();
                        bottomPanel.addLog("Connected successfully");
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        connectionPanel.setConnectionFailed();
                        bottomPanel.addLog("Connection failed: " + ex.getMessage());
                        Dialogs.showError("Connection Error", ex.getMessage());
                    });
                }
            });

        } catch (Exception ex) {
            Dialogs.showError("Input Error", "Invalid port number");
            connectionPanel.setConnectEnabled(true);
        }
    }

    private void disconnect() {
        bottomPanel.addLog("Disconnecting...");
        connectionPanel.setDisconnecting();

        executor.submit(() -> {
            try {
                if (sshClient != null) {
                    sshClient.close();
                    bottomPanel.addLog("SSH disconnected");
                }
            } catch (Exception e) {
                bottomPanel.addLog("Error during disconnect: " + e.getMessage());
            } finally {
                Platform.runLater(() -> {
                    isConnected = false;
                    connectionPanel.setConnected(false);
                    listsPanel.setControlsEnabled(false);
                    listsPanel.clearLists();
                    pendingDomains.clear();
                    pendingIps.clear();
                    bottomPanel.setApplyEnabled(false);
                    bottomPanel.setRefreshEnabled(false);
                    bottomPanel.setStatus("Disconnected", Color.GRAY);
                    bottomPanel.addLog("Disconnected");
                });
            }
        });
    }

    private void loadConfig() {
        if (service == null) {
            return;
        }

        bottomPanel.setStatus("⏳ Loading configuration...", Color.ORANGE);
        bottomPanel.addLog("Loading configuration...");

        executor.submit(() -> {
            try {
                currentConfig = service.loadConfig();

                pendingDomains.clear();
                pendingIps.clear();

                Platform.runLater(() -> {
                    listsPanel.updateLists(currentConfig, pendingDomains, pendingIps);
                    bottomPanel.setStatus("✅ Configuration loaded - " +
                            currentConfig.totalDomains() + " domains, " +
                            currentConfig.userIpEntries().size() + " IPs", Color.GREEN);
                    bottomPanel.setApplyEnabled(false);
                    bottomPanel.hidePendingHint();
                    bottomPanel.addLog("Configuration loaded successfully");
                    // Обновляем статус pending (должно быть пусто)
                    updatePendingStatus();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    bottomPanel.setStatus("❌ Failed to load configuration: " + e.getMessage(), Color.RED);
                    bottomPanel.addLog("Failed to load configuration: " + e.getMessage());
                    Dialogs.showError("Load Error", e.getMessage());
                });
            }
        });
    }

    private void addDomain() {
        Dialogs.showInputDialog("Add Domain", "Add domain to VPN routing", "Enter domain (e.g., example.com):")
                .ifPresent(domain -> {
                    if (domain.trim().isEmpty()) {
                        Dialogs.showError("Input Error", "Domain cannot be empty");
                        return;
                    }

                    String cleanDomain = domain.trim().toLowerCase();

                    if (pendingDomains.contains(cleanDomain)) {
                        Dialogs.showError("Duplicate", "Domain already added to pending list");
                        return;
                    }

                    if (currentConfig != null && currentConfig.userDomains().stream()
                            .anyMatch(d -> d.domain().equalsIgnoreCase(cleanDomain))) {
                        Dialogs.showError("Duplicate", "Domain already exists in configuration");
                        return;
                    }

                    pendingDomains.add(cleanDomain);
                    bottomPanel.addLog("Domain added to pending: " + cleanDomain);
                    bottomPanel.showPendingHint(); // Показываем подсказку
                    updatePendingStatus();
                    listsPanel.updateLists(currentConfig, pendingDomains, pendingIps);
                });
    }

    private void removeDomain(String selected) {
        if (selected == null) {
            Dialogs.showError("Selection Error", "Please select a domain to remove");
            return;
        }

        // Очищаем от маркеров для получения чистого имени
        String cleanSelected = selected.replace("✅ ", "").replace("❌ ", "");

        // Проверяем, не помечен ли уже этот домен на удаление
        String markForRemoval = "!" + cleanSelected;
        if (pendingDomains.contains(markForRemoval)) {
            // Если уже помечен на удаление - отменяем пометку
            pendingDomains.remove(markForRemoval);
            bottomPanel.addLog("Domain unmarked for removal: " + cleanSelected);
            listsPanel.updateLists(currentConfig, pendingDomains, pendingIps);
            updatePendingStatus();
            return;
        }

        // Проверяем, не находится ли домен в списке на добавление
        if (pendingDomains.contains(cleanSelected)) {
            // Если домен был добавлен в pending - удаляем его оттуда
            pendingDomains.remove(cleanSelected);
            bottomPanel.addLog("Domain removed from pending: " + cleanSelected);
            listsPanel.updateLists(currentConfig, pendingDomains, pendingIps);
            updatePendingStatus();
            return;
        }

        // Проверяем, существует ли домен в текущей конфигурации
        if (currentConfig != null && currentConfig.userDomains().stream()
                .noneMatch(d -> d.domain().equalsIgnoreCase(cleanSelected))) {
            Dialogs.showError("Error", "Domain not found in configuration");
            return;
        }

        // Убираем подтверждение - сразу помечаем на удаление
        pendingDomains.add("!" + cleanSelected);
        bottomPanel.addLog("Domain marked for removal: " + cleanSelected);
        updatePendingStatus();
        listsPanel.updateLists(currentConfig, pendingDomains, pendingIps);
    }

    private void addIp() {
        Dialogs.showInputDialog("Add IP", "Add IP or network to VPN routing", "Enter IP/network (e.g., 192.168.1.0/24):")
                .ifPresent(ip -> {
                    if (ip.trim().isEmpty()) {
                        Dialogs.showError("Input Error", "IP cannot be empty");
                        return;
                    }

                    String cleanIp = ip.trim();

                    if (pendingIps.contains(cleanIp)) {
                        Dialogs.showError("Duplicate", "IP already added to pending list");
                        return;
                    }

                    if (currentConfig != null && currentConfig.userIpEntries().stream()
                            .anyMatch(entry -> entry.ipOrNetwork().equals(cleanIp))) {
                        Dialogs.showError("Duplicate", "IP already exists in configuration");
                        return;
                    }

                    pendingIps.add(cleanIp);
                    bottomPanel.addLog("IP added to pending: " + cleanIp);
                    bottomPanel.showPendingHint(); // Показываем подсказку
                    updatePendingStatus();
                    listsPanel.updateLists(currentConfig, pendingDomains, pendingIps);
                });
    }

    private void removeIp(String selected) {
        if (selected == null) {
            Dialogs.showError("Selection Error", "Please select an IP to remove");
            return;
        }

        // Очищаем от маркеров для получения чистого имени
        String cleanSelected = selected.replace("✅ ", "").replace("❌ ", "");

        // Проверяем, не помечен ли уже этот IP на удаление
        String markForRemoval = "!" + cleanSelected;
        if (pendingIps.contains(markForRemoval)) {
            // Если уже помечен на удаление - отменяем пометку
            pendingIps.remove(markForRemoval);
            bottomPanel.addLog("IP unmarked for removal: " + cleanSelected);
            listsPanel.updateLists(currentConfig, pendingDomains, pendingIps);
            updatePendingStatus();
            return;
        }

        // Проверяем, не находится ли IP в списке на добавление
        if (pendingIps.contains(cleanSelected)) {
            // Если IP был добавлен в pending - удаляем его оттуда
            pendingIps.remove(cleanSelected);
            bottomPanel.addLog("IP removed from pending: " + cleanSelected);
            listsPanel.updateLists(currentConfig, pendingDomains, pendingIps);
            updatePendingStatus();
            return;
        }

        // Проверяем, существует ли IP в текущей конфигурации
        if (currentConfig != null && currentConfig.userIpEntries().stream()
                .noneMatch(entry -> entry.ipOrNetwork().equals(cleanSelected))) {
            Dialogs.showError("Error", "IP not found in configuration");
            return;
        }

        // Убираем подтверждение - сразу помечаем на удаление
        pendingIps.add("!" + cleanSelected);
        bottomPanel.addLog("IP marked for removal: " + cleanSelected);
        updatePendingStatus();
        listsPanel.updateLists(currentConfig, pendingDomains, pendingIps);
    }

    private void updatePendingStatus() {
        // Подсчет реальных изменений
        long addDomains = pendingDomains.stream().filter(d -> !d.startsWith("!")).count();
        long removeDomains = pendingDomains.stream().filter(d -> d.startsWith("!")).count();
        long addIps = pendingIps.stream().filter(ip -> !ip.startsWith("!")).count();
        long removeIps = pendingIps.stream().filter(ip -> ip.startsWith("!")).count();

        boolean hasPending = addDomains > 0 || removeDomains > 0 || addIps > 0 || removeIps > 0;

        bottomPanel.setApplyEnabled(hasPending);

        if (hasPending) {
            // Формируем компактное сообщение
            StringBuilder statusMsg = new StringBuilder("⏳ Pending:");
            if (addDomains > 0) statusMsg.append(" +").append(addDomains).append(" Domain(s)");
            if (removeDomains > 0) statusMsg.append(" -").append(removeDomains).append(" Domain(s)");
            if (addIps > 0) statusMsg.append(" +").append(addIps).append(" IP");
            if (removeIps > 0) statusMsg.append(" -").append(removeIps).append(" IP");

            bottomPanel.setStatus(statusMsg.toString(), Color.ORANGE);
            bottomPanel.showPendingHint();
        } else {
            bottomPanel.hidePendingHint();
            // Восстанавливаем нормальный статус
            if (isConnected) {
                // Показываем количество доменов/IP в конфигурации
                if (currentConfig != null) {
                    bottomPanel.setStatus("Connected - " +
                            currentConfig.totalDomains() + " domains, " +
                            currentConfig.userIpEntries().size() + " IPs", Color.GREEN);
                } else {
                    bottomPanel.setStatus("Connected", Color.GREEN);
                }
            } else {
                bottomPanel.setStatus("Ready", Color.GRAY);
            }
        }
    }

    private void applyChanges() {
        boolean hasPending = pendingDomains.stream().anyMatch(d -> !d.startsWith("!")) ||
                pendingDomains.stream().anyMatch(d -> d.startsWith("!")) ||
                pendingIps.stream().anyMatch(ip -> !ip.startsWith("!")) ||
                pendingIps.stream().anyMatch(ip -> ip.startsWith("!"));

        if (!hasPending) {
            bottomPanel.setStatus("No pending changes to apply", Color.YELLOW);
            return;
        }

        long addDomains = pendingDomains.stream().filter(d -> !d.startsWith("!")).count();
        long removeDomains = pendingDomains.stream().filter(d -> d.startsWith("!")).count();
        long addIps = pendingIps.stream().filter(ip -> !ip.startsWith("!")).count();
        long removeIps = pendingIps.stream().filter(ip -> ip.startsWith("!")).count();

        String message = "Pending changes:\n" +
                "  Add domains: " + addDomains + "\n" +
                "  Remove domains: " + removeDomains + "\n" +
                "  Add IPs: " + addIps + "\n" +
                "  Remove IPs: " + removeIps + "\n\n" +
                "This will restart dnsmasq and firewall services.";

        if (!Dialogs.showConfirmation("Apply Changes", message)) {
            return;
        }

        bottomPanel.setStatus("⏳ Applying changes...", Color.ORANGE);
        bottomPanel.setApplyEnabled(false);
        bottomPanel.hidePendingHint(); // Скрываем подсказку во время применения
        bottomPanel.addLog("Applying changes...");

        executor.submit(() -> {
            try {
                VpnConfig config = service.loadConfig();

                List<String> domains = new ArrayList<>(config.userDomains().stream()
                        .map(VpnDomain::domain)
                        .toList());

                List<String> ips = new ArrayList<>(config.userIpEntries().stream()
                        .map(VpnIpEntry::ipOrNetwork)
                        .toList());

                for (String item : pendingDomains) {
                    if (item.startsWith("!")) {
                        String domain = item.substring(1);
                        domains.removeIf(d -> d.equalsIgnoreCase(domain));
                        bottomPanel.addLog("Removed domain: " + domain);
                    } else {
                        if (!domains.contains(item)) {
                            domains.add(item);
                            bottomPanel.addLog("Added domain: " + item);
                        }
                    }
                }

                for (String item : pendingIps) {
                    if (item.startsWith("!")) {
                        String ip = item.substring(1);
                        ips.removeIf(i -> i.equals(ip));
                        bottomPanel.addLog("Removed IP: " + ip);
                    } else {
                        if (!ips.contains(item)) {
                            ips.add(item);
                            bottomPanel.addLog("Added IP: " + item);
                        }
                    }
                }

                service.applyFullConfig(new VpnConfig(
                        config.autoDomains(),
                        domains.stream().map(d -> new VpnDomain(d, true)).toList(),
                        ips.stream().map(VpnIpEntry::new).toList()
                ));

                pendingDomains.clear();
                pendingIps.clear();

                Platform.runLater(() -> {
                    bottomPanel.setStatus("✅ Changes applied successfully!", Color.GREEN);
                    bottomPanel.addLog("Changes applied successfully");
                    bottomPanel.setApplyEnabled(false);
                    bottomPanel.hidePendingHint();
                    // Обновляем статус (должно быть пусто)
                    updatePendingStatus();
                    loadConfig();
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    bottomPanel.setStatus("❌ Failed to apply changes: " + e.getMessage(), Color.RED);
                    bottomPanel.setApplyEnabled(false);
                    bottomPanel.addLog("Failed to apply changes: " + e.getMessage());
                    // Обновляем статус (должно быть пусто)
                    updatePendingStatus();
                    Dialogs.showError("Apply Error", e.getMessage());
                });
            }

        });
    }

    public void shutdown() {
        bottomPanel.addLog("Shutting down...");
        if (sshClient != null && sshClient.isConnected()) {
            try {
                sshClient.close();
                bottomPanel.addLog("SSH disconnected");
            } catch (Exception e) {
                bottomPanel.addLog("Error disconnecting SSH: " + e.getMessage());
            }
        }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        bottomPanel.addLog("Application closed");
    }
}