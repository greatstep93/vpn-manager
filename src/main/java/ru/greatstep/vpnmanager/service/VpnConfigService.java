package ru.greatstep.vpnmanager.service;

import ru.greatstep.vpnmanager.config.models.VpnConfig;
import ru.greatstep.vpnmanager.config.models.VpnDomain;
import ru.greatstep.vpnmanager.config.models.VpnIpEntry;
import ru.greatstep.vpnmanager.config.parsers.DhcpConfigParser;
import ru.greatstep.vpnmanager.config.parsers.DnsmasqDomainsParser;
import ru.greatstep.vpnmanager.config.parsers.FirewallConfigParser;
import ru.greatstep.vpnmanager.ssh.SSHClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

public class VpnConfigService {
    private final SSHClient sshClient;

    public VpnConfigService(SSHClient sshClient) {
        this.sshClient = sshClient;
    }

    public VpnConfig loadConfig() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var autoDomainsFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    var content = sshClient.executeCommand("cat /tmp/dnsmasq.d/domains.lst 2>/dev/null || echo ''");
                    return DnsmasqDomainsParser.parse(content);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to load auto domains", e);
                }
            }, executor);

            var userDomainsFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    var content = sshClient.executeCommand("uci show dhcp | grep -A 20 'vpn_domains' 2>/dev/null || echo ''");
                    return DhcpConfigParser.parse(content);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to load user domains", e);
                }
            }, executor);

            var userIpsFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    var content = sshClient.executeCommand("uci show firewall | grep -A 30 'vpn_domains' 2>/dev/null || echo ''");
                    return FirewallConfigParser.parse(content);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to load user IPs", e);
                }
            }, executor);

            CompletableFuture.allOf(autoDomainsFuture, userDomainsFuture, userIpsFuture).join();

            return new VpnConfig(
                    autoDomainsFuture.get(),
                    userDomainsFuture.get(),
                    userIpsFuture.get()
            );
        }
    }

    public void addDomain(String domain) throws Exception {
        var currentConfig = loadConfig();
        boolean exists = currentConfig.userDomains().stream()
                .anyMatch(d -> d.domain().equalsIgnoreCase(domain));

        if (exists) {
            throw new IllegalArgumentException("Domain already exists: " + domain);
        }

        var currentDomains = new ArrayList<>(currentConfig.userDomains().stream()
                .map(VpnDomain::domain)
                .toList());
        currentDomains.add(domain);

        updateDhcpDomains(currentDomains);
        applyChanges();
    }

    public void removeDomain(String domain) throws Exception {
        var currentConfig = loadConfig();
        var currentDomains = new ArrayList<>(currentConfig.userDomains().stream()
                .map(VpnDomain::domain)
                .filter(d -> !d.equalsIgnoreCase(domain))
                .toList());

        updateDhcpDomains(currentDomains);
        applyChanges();
    }

    public void addIpEntry(String ipEntry) throws Exception {
        var currentConfig = loadConfig();
        boolean exists = currentConfig.userIpEntries().stream()
                .anyMatch(ip -> ip.ipOrNetwork().equals(ipEntry));

        if (exists) {
            throw new IllegalArgumentException("IP entry already exists: " + ipEntry);
        }

        var currentIps = new ArrayList<>(currentConfig.userIpEntries().stream()
                .map(VpnIpEntry::ipOrNetwork)
                .toList());
        currentIps.add(ipEntry);

        updateFirewallIps(currentIps);
        applyChanges();
    }

    public void removeIpEntry(String ipEntry) throws Exception {
        var currentConfig = loadConfig();
        var currentIps = new ArrayList<>(currentConfig.userIpEntries().stream()
                .map(VpnIpEntry::ipOrNetwork)
                .filter(ip -> !ip.equals(ipEntry))
                .toList());

        updateFirewallIps(currentIps);
        applyChanges();
    }

    private void updateDhcpDomains(List<String> domains) throws Exception {
        String configId = getDhcpConfigId();

        if (configId == null) {
            sshClient.executeCommand("uci add dhcp ipset");
            sshClient.executeCommand("uci set dhcp.@ipset[-1].name='vpn_domains'");
            for (String domain : domains) {
                sshClient.executeCommand("uci add_list dhcp.@ipset[-1].domain='" + domain + "'");
            }
        } else {
            sshClient.executeCommand("uci -q delete dhcp." + configId + ".domain");
            for (String domain : domains) {
                sshClient.executeCommand("uci add_list dhcp." + configId + ".domain='" + domain + "'");
            }
        }

        sshClient.executeCommand("uci commit dhcp");
    }

    private void updateFirewallIps(List<String> ips) throws Exception {
        String configId = getFirewallConfigId();

        if (configId == null) {
            sshClient.executeCommand("uci add firewall ipset");
            sshClient.executeCommand("uci set firewall.@ipset[-1].name='vpn_domains'");
            sshClient.executeCommand("uci set firewall.@ipset[-1].match='dst_net'");
            for (String ip : ips) {
                sshClient.executeCommand("uci add_list firewall.@ipset[-1].entry='" + ip + "'");
            }
        } else {
            sshClient.executeCommand("uci -q delete firewall." + configId + ".entry");
            for (String ip : ips) {
                sshClient.executeCommand("uci add_list firewall." + configId + ".entry='" + ip + "'");
            }
        }

        sshClient.executeCommand("uci commit firewall");
    }

    private void applyChanges() throws Exception {
        // Перезапускаем dnsmasq чтобы он заново зарезолвил все домены
        sshClient.executeCommand("/etc/init.d/dnsmasq restart");
        Thread.sleep(2000);

        // Перезапускаем firewall чтобы очистить все nftables set и применить новые правила
        sshClient.executeCommand("/etc/init.d/firewall restart");
        Thread.sleep(1000);

        // Еще раз перезапускаем dnsmasq чтобы убедиться что все домены зарезолвлены
        sshClient.executeCommand("/etc/init.d/dnsmasq restart");
        Thread.sleep(1000);
    }

    private String getDhcpConfigId() throws Exception {
        String output = sshClient.executeCommand("uci show dhcp | grep '@ipset' | grep 'name=' 2>/dev/null || echo ''");
        String[] lines = output.split("\n");

        for (String line : lines) {
            if (line.contains("name='vpn_domains'")) {
                String[] parts = line.split("\\.");
                if (parts.length > 1) {
                    String configPart = parts[1];
                    String configId = configPart.split("=")[0];
                    return configId;
                }
            }
        }
        return null;
    }

    private String getFirewallConfigId() throws Exception {
        String output = sshClient.executeCommand("uci show firewall | grep '@ipset' | grep 'name=' 2>/dev/null || echo ''");
        String[] lines = output.split("\n");

        for (String line : lines) {
            if (line.contains("name='vpn_domains'")) {
                String[] parts = line.split("\\.");
                if (parts.length > 1) {
                    String configPart = parts[1];
                    String configId = configPart.split("=")[0];
                    return configId;
                }
            }
        }
        return null;
    }

    public void applyFullConfig(VpnConfig config) throws Exception {
        var domains = config.userDomains().stream()
                .map(VpnDomain::domain)
                .toList();
        updateDhcpDomains(domains);

        var ips = config.userIpEntries().stream()
                .map(VpnIpEntry::ipOrNetwork)
                .toList();
        updateFirewallIps(ips);

        applyChanges();
    }
}