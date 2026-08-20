package ru.greatstep.vpnmanager;

import ru.greatstep.vpnmanager.config.models.VpnConfig;
import ru.greatstep.vpnmanager.service.VpnConfigService;
import ru.greatstep.vpnmanager.ssh.SSHClient;

import java.util.Scanner;

public class Main {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_WHITE = "\u001B[37m";
    private static final String ANSI_RED = "\u001B[31m";

    private static VpnConfigService service;
    private static SSHClient sshClient;

    public static void main(String[] args) {
        System.out.println(ANSI_CYAN + "╔═══════════════════════════════════════╗" + ANSI_RESET);
        System.out.println(ANSI_CYAN + "║     VPN Manager for OpenWrt v1.0     ║" + ANSI_RESET);
        System.out.println(ANSI_CYAN + "╚═══════════════════════════════════════╝" + ANSI_RESET);
        System.out.println();

        try (var scanner = new Scanner(System.in)) {
            System.out.print(ANSI_WHITE + "Enter router IP [192.168.2.1]: " + ANSI_RESET);
            var host = scanner.nextLine().trim();
            if (host.isEmpty()) host = "192.168.2.1";

            System.out.print(ANSI_WHITE + "Enter SSH port [22]: " + ANSI_RESET);
            var portStr = scanner.nextLine().trim();
            var port = portStr.isEmpty() ? 22 : Integer.parseInt(portStr);

            System.out.print(ANSI_WHITE + "Enter username [root]: " + ANSI_RESET);
            var username = scanner.nextLine().trim();
            if (username.isEmpty()) username = "root";

            System.out.print(ANSI_WHITE + "Enter password: " + ANSI_RESET);
            var password = scanner.nextLine();
            if (password.isEmpty()) {
                System.out.println(ANSI_YELLOW + "No password entered, using default" + ANSI_RESET);
                password = "djljgfL!@50";
            }

            System.out.println();
            System.out.print(ANSI_YELLOW + "Connecting to " + host + "..." + ANSI_RESET);

            sshClient = new SSHClient(host, port, username, password);
            sshClient.connect();
            System.out.println(ANSI_GREEN + " Connected!" + ANSI_RESET);

            service = new VpnConfigService(sshClient);

            showMainMenu(scanner);

        } catch (Exception e) {
            System.err.println(ANSI_RED + "\nError: " + e.getMessage() + ANSI_RESET);
            e.printStackTrace();
        } finally {
            if (sshClient != null) {
                sshClient.close();
            }
        }
    }

    private static void showMainMenu(Scanner scanner) throws Exception {
        while (true) {
            System.out.println();
            System.out.println(ANSI_CYAN + "═══════════════════════════════════════════════════════" + ANSI_RESET);
            System.out.println(ANSI_CYAN + "  Main Menu" + ANSI_RESET);
            System.out.println(ANSI_CYAN + "═══════════════════════════════════════════════════════" + ANSI_RESET);
            System.out.println("  1) View current configuration");
            System.out.println("  2) Add user domain");
            System.out.println("  3) Remove user domain");
            System.out.println("  4) Add user IP");
            System.out.println("  5) Remove user IP");
            System.out.println("  6) Reload configuration");
            System.out.println("  7) Exit");
            System.out.println(ANSI_CYAN + "─────────────────────────────────────────────────────" + ANSI_RESET);
            System.out.print(ANSI_WHITE + "Select option: " + ANSI_RESET);

            var choice = scanner.nextLine().trim();

            switch (choice) {
                case "1" -> viewConfig();
                case "2" -> addDomain(scanner);
                case "3" -> removeDomain(scanner);
                case "4" -> addIp(scanner);
                case "5" -> removeIp(scanner);
                case "6" -> reloadConfig();
                case "7" -> {
                    System.out.println(ANSI_GREEN + "Goodbye!" + ANSI_RESET);
                    return;
                }
                default -> System.out.println(ANSI_RED + "Invalid option. Please try again." + ANSI_RESET);
            }
        }
    }

    private static void viewConfig() throws Exception {
        System.out.print(ANSI_YELLOW + "Loading configuration..." + ANSI_RESET);
        var config = service.loadConfig();
        System.out.println(ANSI_GREEN + " Done!" + ANSI_RESET);
        printConfiguration(config);
    }

    private static void addDomain(Scanner scanner) throws Exception {
        System.out.print(ANSI_WHITE + "Enter domain to add (e.g., example.com): " + ANSI_RESET);
        var domain = scanner.nextLine().trim();

        if (domain.isEmpty()) {
            System.out.println(ANSI_RED + "Domain cannot be empty" + ANSI_RESET);
            return;
        }

        try {
            service.addDomain(domain);
            System.out.println(ANSI_GREEN + "Domain added successfully!" + ANSI_RESET);
        } catch (Exception e) {
            System.out.println(ANSI_RED + "Error: " + e.getMessage() + ANSI_RESET);
        }
    }

    private static void removeDomain(Scanner scanner) throws Exception {
        var config = service.loadConfig();
        var userDomains = config.userDomains();

        if (userDomains.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No user domains to remove" + ANSI_RESET);
            return;
        }

        System.out.println(ANSI_WHITE + "Select domain to remove:" + ANSI_RESET);
        for (int i = 0; i < userDomains.size(); i++) {
            System.out.println("  " + (i + 1) + ") " + userDomains.get(i).domain());
        }
        System.out.print(ANSI_WHITE + "Enter number: " + ANSI_RESET);

        var choice = scanner.nextLine().trim();
        try {
            int index = Integer.parseInt(choice) - 1;
            if (index >= 0 && index < userDomains.size()) {
                var domain = userDomains.get(index).domain();
                service.removeDomain(domain);
                System.out.println(ANSI_GREEN + "Domain removed successfully!" + ANSI_RESET);
            } else {
                System.out.println(ANSI_RED + "Invalid selection" + ANSI_RESET);
            }
        } catch (NumberFormatException e) {
            System.out.println(ANSI_RED + "Invalid input" + ANSI_RESET);
        }
    }

    private static void addIp(Scanner scanner) throws Exception {
        System.out.print(ANSI_WHITE + "Enter IP or network to add (e.g., 192.168.1.0/24): " + ANSI_RESET);
        var ip = scanner.nextLine().trim();

        if (ip.isEmpty()) {
            System.out.println(ANSI_RED + "IP cannot be empty" + ANSI_RESET);
            return;
        }

        try {
            service.addIpEntry(ip);
            System.out.println(ANSI_GREEN + "IP added successfully!" + ANSI_RESET);
        } catch (Exception e) {
            System.out.println(ANSI_RED + "Error: " + e.getMessage() + ANSI_RESET);
        }
    }

    private static void removeIp(Scanner scanner) throws Exception {
        var config = service.loadConfig();
        var userIps = config.userIpEntries();

        if (userIps.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No user IPs to remove" + ANSI_RESET);
            return;
        }

        System.out.println(ANSI_WHITE + "Select IP to remove:" + ANSI_RESET);
        for (int i = 0; i < userIps.size(); i++) {
            System.out.println("  " + (i + 1) + ") " + userIps.get(i).ipOrNetwork());
        }
        System.out.print(ANSI_WHITE + "Enter number: " + ANSI_RESET);

        var choice = scanner.nextLine().trim();
        try {
            int index = Integer.parseInt(choice) - 1;
            if (index >= 0 && index < userIps.size()) {
                var ip = userIps.get(index).ipOrNetwork();
                service.removeIpEntry(ip);
                System.out.println(ANSI_GREEN + "IP removed successfully!" + ANSI_RESET);
            } else {
                System.out.println(ANSI_RED + "Invalid selection" + ANSI_RESET);
            }
        } catch (NumberFormatException e) {
            System.out.println(ANSI_RED + "Invalid input" + ANSI_RESET);
        }
    }

    private static void reloadConfig() throws Exception {
        System.out.print(ANSI_YELLOW + "Reloading configuration..." + ANSI_RESET);
        var config = service.loadConfig();
        System.out.println(ANSI_GREEN + " Done!" + ANSI_RESET);
        printConfiguration(config);
    }

    private static void printConfiguration(VpnConfig config) {
        System.out.println();
        System.out.println(ANSI_CYAN + "═══════════════════════════════════════════════════════" + ANSI_RESET);
        System.out.println(ANSI_CYAN + "  Configuration Summary" + ANSI_RESET);
        System.out.println(ANSI_CYAN + "═══════════════════════════════════════════════════════" + ANSI_RESET);
        System.out.println("  Total domains:   " + ANSI_YELLOW + config.totalDomains() + ANSI_RESET);
        System.out.println("  Total IPs:       " + ANSI_YELLOW + config.userIpEntries().size() + ANSI_RESET);
        System.out.println("  Total entries:   " + ANSI_YELLOW + config.totalEntries() + ANSI_RESET);
        System.out.println();

        // Автоматические домены
        System.out.println(ANSI_BLUE + "┌─ Auto-loaded domains (from /tmp/dnsmasq.d/domains.lst)" + ANSI_RESET);
        System.out.println(ANSI_BLUE + "│  Total: " + config.autoDomains().size() + ANSI_RESET);
        if (!config.autoDomains().isEmpty()) {
            config.autoDomains().stream()
                    .limit(20)
                    .forEach(domain -> System.out.println("│  • " + domain.domain()));
            if (config.autoDomains().size() > 20) {
                System.out.println("│  ... and " + (config.autoDomains().size() - 20) + " more");
            }
        } else {
            System.out.println("│  " + ANSI_YELLOW + "(empty)" + ANSI_RESET);
        }
        System.out.println(ANSI_BLUE + "└─────────────────────────────────────────────────────" + ANSI_RESET);
        System.out.println();

        // Пользовательские домены
        System.out.println(ANSI_GREEN + "┌─ User-added domains (from /etc/config/dhcp)" + ANSI_RESET);
        System.out.println(ANSI_GREEN + "│  Total: " + config.userDomains().size() + ANSI_RESET);
        if (!config.userDomains().isEmpty()) {
            config.userDomains().forEach(domain ->
                    System.out.println("│  • " + domain.domain())
            );
        } else {
            System.out.println("│  " + ANSI_YELLOW + "(empty)" + ANSI_RESET);
        }
        System.out.println(ANSI_GREEN + "└─────────────────────────────────────────────────────" + ANSI_RESET);
        System.out.println();

        // Пользовательские IP
        System.out.println(ANSI_YELLOW + "┌─ User-added IPs (from /etc/config/firewall)" + ANSI_RESET);
        System.out.println(ANSI_YELLOW + "│  Total: " + config.userIpEntries().size() + ANSI_RESET);
        if (!config.userIpEntries().isEmpty()) {
            config.userIpEntries().forEach(ip ->
                    System.out.println("│  • " + ip.ipOrNetwork())
            );
        } else {
            System.out.println("│  " + ANSI_YELLOW + "(empty)" + ANSI_RESET);
        }
        System.out.println(ANSI_YELLOW + "└─────────────────────────────────────────────────────" + ANSI_RESET);
        System.out.println();
    }
}