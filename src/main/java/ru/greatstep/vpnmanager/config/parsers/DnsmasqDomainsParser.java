package ru.greatstep.vpnmanager.config.parsers;

import ru.greatstep.vpnmanager.config.models.VpnDomain;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DnsmasqDomainsParser {

    private static final Pattern DOMAIN_PATTERN = Pattern.compile("^nftset=/([^/]+)/");

    public static List<VpnDomain> parse(String content) {
        List<VpnDomain> domains = new ArrayList<>();

        if (content == null || content.isEmpty()) {
            return domains;
        }

        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            Matcher matcher = DOMAIN_PATTERN.matcher(line);
            if (matcher.find()) {
                String domain = matcher.group(1);
                domains.add(new VpnDomain(domain, false));
            }
        }

        return domains;
    }
}