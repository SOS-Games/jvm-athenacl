// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@code program} and {@code drumkit} from a kit file so a layer can name
 * {@code gm-lite:celesta} without opening the soundfont. {@code extends} is followed
 * once, the same way a kit file replaces a parent instrument.
 */
final class KitPrograms {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private KitPrograms() {
    }

    record Hit(int program, boolean percussion) {
    }

    static Hit lookup(String kitId, String name) {
        return lookup(kitId, name, new ArrayList<>());
    }

    private static Hit lookup(String kitId, String name, List<Path> seen) {
        Path file = find(kitId);
        if (file == null || !Files.isRegularFile(file) || seen.contains(file)) {
            return null;
        }
        seen.add(file);
        JsonNode root;
        try {
            root = YAML.readTree(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
        if (root == null || !root.path("kit").isTextual()) {
            return null;
        }
        Hit own = read(root.path("instruments").path(name));
        if (own != null) {
            return own;
        }
        String parent = root.path("extends").asText("");
        if (parent.isBlank()) {
            return null;
        }
        return lookup(parent, name, seen);
    }

    private static Hit read(JsonNode body) {
        if (body == null || !body.isObject()) {
            return null;
        }
        if (body.has("drumkit")) {
            return new Hit(body.get("drumkit").asInt(0), true);
        }
        if (body.has("program")) {
            return new Hit(body.get("program").asInt(0), false);
        }
        return null;
    }

    private static Path find(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        if (id.endsWith(".yaml") || id.endsWith(".yml")) {
            return Path.of(id);
        }
        Path bundled = Path.of("instruments").resolve(id + ".yaml");
        if (isKit(bundled)) {
            return bundled;
        }
        Path sample = Path.of("samples", "kits").resolve(id + ".yaml");
        if (Files.isRegularFile(sample)) {
            return sample;
        }
        return bundled;
    }

    private static boolean isKit(Path file) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                return trimmed.startsWith("kit:");
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }
}
