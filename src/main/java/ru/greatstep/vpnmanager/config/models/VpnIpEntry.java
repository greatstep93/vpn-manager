package ru.greatstep.vpnmanager.config.models;

public record VpnIpEntry(
        String ipOrNetwork
) {
    public VpnIpEntry {
        if (ipOrNetwork == null || ipOrNetwork.isBlank()) {
            throw new IllegalArgumentException("IP or network cannot be null or blank");
        }
    }

    @Override
    public String toString() {
        return ipOrNetwork;
    }
}