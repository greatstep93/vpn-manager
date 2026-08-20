package ru.greatstep.vpnmanager.config.models;

import java.util.List;

public record VpnConfig(
        List<VpnDomain> autoDomains,
        List<VpnDomain> userDomains,
        List<VpnIpEntry> userIpEntries
) {
    public VpnConfig {
        autoDomains = autoDomains != null ? List.copyOf(autoDomains) : List.of();
        userDomains = userDomains != null ? List.copyOf(userDomains) : List.of();
        userIpEntries = userIpEntries != null ? List.copyOf(userIpEntries) : List.of();
    }

    public int totalDomains() {
        return autoDomains.size() + userDomains.size();
    }

    public int totalEntries() {
        return totalDomains() + userIpEntries.size();
    }
}