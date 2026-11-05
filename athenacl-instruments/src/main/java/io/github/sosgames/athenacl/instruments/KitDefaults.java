// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which kit a command uses when the theme does not name one.
 * The built-in kit is {@code gm-full} (MuseScore_General). {@code gm-lite} (GeneralUser GS)
 * stays the per-instrument fallback when that bank is missing.
 * Precedence is the {@code --kit} flag, then {@code ATHENACL_KIT}, then {@code kit.default}
 * from {@code -Dkit.default}, {@code ~/.athenaclrc}, or {@code local.properties}.
 * {@code ATHENACL_RC} (or {@code -Dathenacl.rc}) points at a different rc file.
 * A theme that lists {@code instruments.kits} keeps that list.
 */
public final class KitDefaults {
    public static final String BUILTIN = "gm-full";
    public static final String FALLBACK = "gm-lite";

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private KitDefaults() {
    }

    /** {@code source} is {@code flag}, {@code env}, {@code rc}, or {@code builtin}. */
    public record Choice(String id, String source) {
    }

    public static Choice resolve(String flag) {
        if (present(flag)) {
            return new Choice(flag.trim(), "flag");
        }
        String env = System.getenv("ATHENACL_KIT");
        if (present(env)) {
            return new Choice(env.trim(), "env");
        }
        String property = System.getProperty("kit.default");
        if (present(property)) {
            return new Choice(property.trim(), "rc");
        }
        String rc = readKey(rcFile(), "kit.default");
        if (present(rc)) {
            return new Choice(rc.trim(), "rc");
        }
        String local = readKey(Path.of("local.properties"), "kit.default");
        if (present(local)) {
            return new Choice(local.trim(), "rc");
        }
        return new Choice(BUILTIN, "builtin");
    }

    public static String id(String flag) {
        return resolve(flag).id();
    }

    /**
     * One {@code kit=} {@code source=} line. A theme file that lists {@code instruments.kits}
     * is {@code source=pin} and beats the environment and the rc file. {@code --kit} beats the pin.
     */
    public static String playbackLine(String flag, Path theme) throws IOException {
        if (present(flag)) {
            return "kit=" + flag.trim() + " source=flag";
        }
        List<String> pinned = pins(theme);
        if (!pinned.isEmpty()) {
            return "kit=" + String.join(",", pinned) + " source=pin";
        }
        Choice choice = resolve(null);
        return "kit=" + choice.id() + " source=" + choice.source();
    }

    /** Kits named under {@code instruments.kits} in a theme file. Empty when the file does not pin any. */
    public static List<String> pins(Path theme) throws IOException {
        if (theme == null || !Files.isRegularFile(theme)) {
            return List.of();
        }
        JsonNode node = YAML.readTree(Files.readString(theme, StandardCharsets.UTF_8))
                .path("instruments").path("kits");
        if (!node.isArray()) {
            return List.of();
        }
        List<String> kits = new ArrayList<>();
        for (JsonNode item : node) {
            String name = item.asText("").trim();
            if (!name.isEmpty()) {
                kits.add(name);
            }
        }
        return kits;
    }

    private static Path rcFile() {
        String override = System.getenv("ATHENACL_RC");
        if (!present(override)) {
            override = System.getProperty("athenacl.rc");
        }
        if (present(override)) {
            return Path.of(override.trim());
        }
        String home = System.getProperty("user.home");
        if (!present(home)) {
            return null;
        }
        return Path.of(home.trim(), ".athenaclrc");
    }

    private static String readKey(Path file, String key) {
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        Map<String, String> parsed = new LinkedHashMap<>();
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
        } catch (IOException e) {
            return null;
        }
        return parsed.get(key);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
