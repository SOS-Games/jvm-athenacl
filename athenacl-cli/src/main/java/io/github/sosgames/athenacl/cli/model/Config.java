// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One lookup for a setting: {@code -D} property, then environment variable, then
 * {@code local.properties} in the working directory. {@code athenacl.soundbank} is
 * {@code ATHENACL_SOUNDBANK}.
 */
public final class Config {
    private static Map<String, String> local;

    private Config() {
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
