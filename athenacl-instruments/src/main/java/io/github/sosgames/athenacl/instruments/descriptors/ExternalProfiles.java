// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.descriptors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sosgames.athenacl.instruments.EngineSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A future describer drops a JSON file of instrument fields. Nothing is read unless
 * {@code descriptors.external.enabled} is true. The key is the soundfont hash, the bank,
 * and the program. A missing file is an empty catalog, not an error.
 */
public final class ExternalProfiles {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ExternalProfiles() {
    }

    public static Map<String, JsonNode> load() throws IOException {
        if (!EngineSettings.externalDescriptors()) {
            return Map.of();
        }
        Path file = Path.of(EngineSettings.externalDescriptorPath());
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        JsonNode root = JSON.readTree(Files.readString(file));
        JsonNode rows = root.path("profiles");
        Map<String, JsonNode> index = new LinkedHashMap<>();
        if (!rows.isArray()) {
            return index;
        }
        for (JsonNode row : rows) {
            String hash = row.path("soundfont").asText("");
            int bank = row.path("bank").asInt(0);
            int program = row.path("program").asInt(-1);
            if (program < 0) {
                continue;
            }
            index.put(key(hash, bank, program), row);
            if (!hash.isBlank()) {
                index.putIfAbsent(key("", bank, program), row);
            }
        }
        return index;
    }

    public static JsonNode find(Map<String, JsonNode> index, String hash, int bank, int program) {
        if (index == null || index.isEmpty()) {
            return null;
        }
        JsonNode exact = hash == null || hash.isBlank() ? null : index.get(key(hash, bank, program));
        if (exact != null) {
            return exact;
        }
        return index.get(key("", bank, program));
    }

    private static String key(String hash, int bank, int program) {
        return (hash == null ? "" : hash) + "/" + bank + "/" + program;
    }
}
