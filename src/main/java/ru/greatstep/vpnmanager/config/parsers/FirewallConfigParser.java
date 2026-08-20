package ru.greatstep.vpnmanager.config.parsers;

import ru.greatstep.vpnmanager.config.models.VpnIpEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FirewallConfigParser {

    private static final Pattern ENTRIES_PATTERN = Pattern.compile(
            "firewall\\.@ipset\\[\\d+\\]\\.entry=(.*)"
    );

    public static List<VpnIpEntry> parse(String content) {
        List<VpnIpEntry> entries = new ArrayList<>();

        if (content == null || content.isEmpty()) {
            return entries;
        }

        Matcher matcher = ENTRIES_PATTERN.matcher(content);

        // Ищем все совпадения
        while (matcher.find()) {
            String entriesLine = matcher.group(1);

            // Разбиваем строку по разделителю ' '
            String[] entryArray = entriesLine.split("' '");

            for (String entry : entryArray) {
                entry = entry.trim();
                // Убираем кавычки если они есть в начале
                if (entry.startsWith("'")) {
                    entry = entry.substring(1);
                }
                // Убираем кавычки если они есть в конце
                if (entry.endsWith("'")) {
                    entry = entry.substring(0,entry.length() - 1);
                }

                if (!entry.isEmpty()) {
                    entries.add(new VpnIpEntry(entry));
                }
            }
        }

        return entries;
    }
}