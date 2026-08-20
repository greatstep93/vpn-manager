package ru.greatstep.vpnmanager.config.parsers;

import ru.greatstep.vpnmanager.config.models.VpnDomain;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DhcpConfigParser {

    private static final Pattern DOMAINS_PATTERN = Pattern.compile(
            "dhcp\\.@ipset\\[\\d+\\]\\.domain=(.*)"
    );

    public static List<VpnDomain> parse(String content) {
        List<VpnDomain> domains = new ArrayList<>();

        if (content == null || content.isEmpty()) {
            return domains;
        }

        Matcher matcher = DOMAINS_PATTERN.matcher(content);

        // Ищем все совпадения
        while (matcher.find()) {
            String domainsLine = matcher.group(1);

            // Разбиваем строку по разделителю ' '
            // Но: между кавычками может быть пробел
            String[] domainArray = domainsLine.split("' '");

            for (String domain : domainArray) {
                domain = domain.trim();
                // Убираем кавычки если они есть в начале
                if (domain.startsWith("'")) {
                    domain = domain.substring(1);
                }
                // Убираем кавычки если они есть в конце
                if (domain.endsWith("'")) {
                    domain = domain.substring(0,domain.length() - 1);
                }
                if (!domain.isEmpty()) {
                    domains.add(new VpnDomain(domain, true));
                }
            }
        }

        return domains;
    }
}