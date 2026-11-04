// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Settings shared by the CLI and the window. Lookup is the same triple as the CLI:
 * a {@code -D} property, then the environment variable, then {@code local.properties}.
 * {@code analysis.enabled} is off unless one of those says true, so a render does not
 * probe a soundfont. {@code descriptors.external.enabled} is the switch for a future
 * describer file.
 */
public final class EngineSettings {
    private static Map<String, String> local;

    private EngineSettings() {
    }

    public static boolean analysisEnabled() {
        return truthy(get("analysis.enabled"));
    }

    public static boolean externalDescriptors() {
        return truthy(get("descriptors.external.enabled"));
    }

    public static String externalDescriptorPath() {
        String path = get("descriptors.external.path");
        return path == null ? "instruments/external-profiles.json" : path;
    }

    public static KitException disabled() {
        return new KitException(new KitException.Issue(
                "ANALYSIS_DISABLED",
                "analysis.enabled",
                "analysis disabled; set analysis.enabled=true (experimental)",
                "Set -Danalysis.enabled=true or analysis.enabled=true in local.properties."));
    }

    public static String get(String key) {
        String prop = System.getProperty(key);
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv(key.toUpperCase(Locale.ROOT).replace('.', '_'));
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String file = local().get(key);
        return file == null || file.isBlank() ? null : file.trim();
    }

    private static boolean truthy(String value) {
        if (value == null) {
            return false;
        }
        String text = value.trim().toLowerCase(Locale.ROOT);
        return text.equals("true") || text.equals("1") || text.equals("yes") || text.equals("on");
    }

    private static Map<String, String> local() {
        if (local != null) {
            return local;
        }
        Map<String, String> parsed = new LinkedHashMap<>();
        Path file = Path.of("local.properties");
        if (Files.isRegularFile(file)) {
            try {
                for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String line = raw.trim();
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                        continue;
                    }
                    int eq = line.indexOf('=');
                    if (eq < 0) {
                        continue;
                    }
                    parsed.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            } catch (IOException ignored) {
                parsed.clear();
            }
        }
        local = parsed;
        return local;
    }
}
