package net.ch4rlesexe.whitelist;

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WhitelistConfig {
    public final String host;
    public final int port;
    public final String database;
    public final String username;
    public final String password;
    public final String tablePrefix;
    public final boolean enforceHub;
    public final Set<String> hubServers;
    public final Set<String> survivalServers;
    public final Set<String> recordingServers;

    public final List<String> allToggleTargets;

    private WhitelistConfig(String host, int port, String database,
                            String username, String password,
                            String tablePrefix, boolean enforceHub,
                            Set<String> hubServers, Set<String> survivalServers,
                            Set<String> recordingServers, List<String> allToggleTargets) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.tablePrefix = tablePrefix;
        this.enforceHub = enforceHub;
        this.hubServers = Set.copyOf(hubServers);
        this.survivalServers = Set.copyOf(survivalServers);
        this.recordingServers = Set.copyOf(recordingServers);
        this.allToggleTargets = List.copyOf(allToggleTargets);
    }

    public static WhitelistConfig load(Path dataDirectory, Logger logger) {
        try {
            Files.createDirectories(dataDirectory);
            Path file = dataDirectory.resolve("config.yml");
            if (!Files.exists(file)) {
                String defaults = """
                        mysql:
                          host: 127.0.0.1
                          port: 3306
                          database: CHANGE_ME
                          username: CHANGE_ME
                          password: CHANGE_ME
                          table-prefix: rwl_
                        settings:
                          enforce-hub: true
                        servers:
                          hub:
                            - lobby
                            - hub
                          survival:
                            - survival
                          recording:
                            - recording
                        toggle-targets:
                          - hub
                          - survival
                          - recording
                        """;
                Files.writeString(file, defaults);
                logger.warn("make a config.yml");
            }

            List<String> lines = Files.readAllLines(file);
            Map<String, String> flat = flattenYaml(lines);
            Set<String> hub = new HashSet<>(parseNamedList(lines, "hub"));
            Set<String> survival = new HashSet<>(parseNamedList(lines, "survival"));
            Set<String> recording = new HashSet<>(parseNamedList(lines, "recording"));
            List<String> toggles = parseList(lines, "toggle-targets");
            if (hub.isEmpty()) {
                hub.add("lobby");
                hub.add("hub");
            }
            if (survival.isEmpty()) {
                survival.add("survival");
            }
            if (recording.isEmpty()) {
                recording.add("recording");
            }
            if (toggles.isEmpty()) {
                toggles = List.of("hub", "survival", "recording");
            }

            return new WhitelistConfig(
                    flat.getOrDefault("mysql.host", "127.0.0.1"),
                    Integer.parseInt(flat.getOrDefault("mysql.port", "3306")),
                    flat.getOrDefault("mysql.database", "CHANGE_ME"),
                    flat.getOrDefault("mysql.username", "CHANGE_ME"),
                    flat.getOrDefault("mysql.password", "CHANGE_ME"),
                    flat.getOrDefault("mysql.table-prefix", "rwl_"),
                    Boolean.parseBoolean(flat.getOrDefault("settings.enforce-hub", "true")),
                    hub,
                    survival,
                    recording,
                    toggles
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to load rWhitelist config", e);
        }
    }


    private static List<String> parseNamedList(List<String> lines, String name) {
        List<String> out = new ArrayList<>();
        boolean inServers = false;
        boolean inName = false;
        for (String raw : lines) {
            String line = raw.replace("\t", "  ");
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int indent = indentOf(line);
            if (indent == 0 && trimmed.startsWith("servers:")) {
                inServers = true;
                inName = false;
                continue;
            }
            if (inServers && indent == 0 && trimmed.contains(":") && !trimmed.startsWith("servers:")) {
                break;
            }
            if (inServers && indent == 2 && trimmed.startsWith(name + ":")) {
                inName = true;
                continue;
            }
            if (inServers && indent == 2 && trimmed.contains(":") && !trimmed.startsWith(name + ":")) {
                inName = false;
                continue;
            }
            if (inName && trimmed.startsWith("- ")) {
                out.add(stripQuotes(trimmed.substring(2).trim()).toLowerCase());
            }
        }
        return out;
    }

    private static List<String> parseList(List<String> lines, String key) {
        List<String> out = new ArrayList<>();
        boolean inList = false;
        for (String raw : lines) {
            String line = raw.replace("\t", "  ");
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.startsWith(key + ":")) {
                inList = true;
                continue;
            }
            if (inList) {
                if (trimmed.startsWith("- ")) {
                    out.add(stripQuotes(trimmed.substring(2).trim()).toLowerCase());
                } else if (indentOf(line) == 0 && trimmed.contains(":")) {
                    break;
                }
            }
        }
        return out;
    }

    private static int indentOf(String line) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') {
            indent++;
        }
        return indent;
    }

    private static Map<String, String> flattenYaml(List<String> lines) {
        Map<String, String> out = new HashMap<>();
        String section = "";
        for (String raw : lines) {
            String line = raw.replace("\t", "  ");
            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                continue;
            }
            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ') {
                indent++;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("- ")) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = trimmed.substring(0, colon).trim();
            String value = trimmed.substring(colon + 1).trim();
            if (indent == 0) {
                section = key;
                if (!value.isEmpty()) {
                    out.put(key, stripQuotes(value));
                }
            } else if (indent == 2) {
                out.put(section + "." + key, stripQuotes(value));
            }
        }
        return out;
    }

    private static String stripQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
