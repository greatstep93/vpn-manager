package ru.greatstep.vpnmanager.config.models;

public record VpnDomain(
        String domain,
        boolean isUserAdded
) {
    public VpnDomain {
        if (domain == null || domain.isBlank()) {
            throw new IllegalArgumentException("Domain cannot be null or blank");
        }
    }

    @Override
    public String toString() {
        return domain + (isUserAdded ? " (user)" : " (auto)");
    }
}