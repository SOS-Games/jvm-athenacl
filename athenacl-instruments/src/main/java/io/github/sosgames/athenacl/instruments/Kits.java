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
 * Reads {@code instruments/*.yaml}. {@code extends} copies the parent kit, then the child
 * replaces sources and instruments that share a name. Lookup prefers a file beside the child,
 * then {@code instruments/<id>.yaml} in the working directory.
 */
public final class Kits {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private Kits() {
    }

    public static Kit load(Path file) throws IOException, KitException {
        return load(file, new ArrayList<>());
    }

    public static Path find(String id) {
        if (id.endsWith(".yaml") || id.endsWith(".yml")) {
            return Path.of(id);
        }
        Path bundled = Path.of("instruments").resolve(id + ".yaml");
        Path sample = Path.of("samples", "kits").resolve(id + ".yaml");
        if (isKitFile(bundled)) {
            return bundled;
        }
        if (Files.isRegularFile(sample)) {
            return sample;
        }
        return bundled;
    }

    /**
     * {@code instruments/analysis.yaml} holds probe thresholds, not a kit.
     * A file counts as a kit when its first lines set {@code kit:}.
     */
    private static boolean isKitFile(Path file) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            for (String line : text.split("\n", 40)) {
                String trimmed = line.strip();
                if (trimmed.startsWith("kit:")) {
                    return true;
                }
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    return false;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    private static Kit load(Path file, List<String> stack) throws IOException, KitException {
        if (!Files.isRegularFile(file)) {
            throw new KitException(new KitException.Issue(
                    "KIT_NOT_FOUND", "argv[1]", "kit not found",
                    "Pass a kit file such as instruments/gm-lite.yaml."));
        }
        String id = file.getFileName().toString();
        if (stack.contains(id)) {
            throw new KitException(new KitException.Issue(
                    "KIT_EXTENDS_CYCLE", "/extends", "kit extends itself",
                    "Remove the cycle in extends."));
        }
        stack.add(id);
        JsonNode root = YAML.readTree(Files.readString(file, StandardCharsets.UTF_8));
        String extendsId = root.path("extends").asText("");
        Kit parent = null;
        if (!extendsId.isBlank()) {
            Path beside = file.getParent().resolve(extendsId + ".yaml");
            Path parentFile = Files.isRegularFile(beside) ? beside : find(extendsId);
            parent = load(parentFile, stack);
        }
        Map<String, Kit.Source> sources = new LinkedHashMap<>();
        Map<String, Kit.Instrument> instruments = new LinkedHashMap<>();
        if (parent != null) {
            for (Kit.Source source : parent.sources()) {
                sources.put(source.key(), source);
            }
            for (Kit.Instrument instrument : parent.instruments()) {
                instruments.put(instrument.name(), instrument);
            }
        }
        JsonNode sourceNode = root.path("sources");
        if (sourceNode.isObject()) {
            sourceNode.fields().forEachRemaining(entry ->
                    sources.put(entry.getKey(), source(entry.getKey(), entry.getValue())));
        }
        JsonNode instrumentNode = root.path("instruments");
        if (instrumentNode.isObject()) {
            instrumentNode.fields().forEachRemaining(entry ->
                    instruments.put(entry.getKey(), instrument(entry.getKey(), entry.getValue())));
        }
        String kitId = root.path("kit").asText(strip(file));
        stack.remove(stack.size() - 1);
        return new Kit(kitId, new ArrayList<>(sources.values()), new ArrayList<>(instruments.values()));
    }

    private static Kit.Source source(String key, JsonNode body) {
        JsonNode license = body.path("license");
        String path = body.path("path").asText("");
        return new Kit.Source(
                key,
                text(body, "id"),
                path.isBlank() ? null : Path.of(path),
                body.path("format").asText("sf2"),
                text(body, "sha256"),
                new Kit.License(
                        license.path("spdx").asText(""),
                        license.path("url").asText(""),
                        license.path("attribution").asText(""),
                        license.path("redistribute").asBoolean(false),
                        license.path("commercial").asBoolean(false),
                        license.path("file").asText("")));
    }

    private static Kit.Instrument instrument(String name, JsonNode body) {
        JsonNode range = body.path("keyRange");
        String low = null;
        String high = null;
        if (range.isArray() && range.size() >= 2) {
            low = range.get(0).asText();
            high = range.get(1).asText();
        }
        List<String> fallbacks = new ArrayList<>();
        JsonNode fallback = body.path("fallback");
        if (fallback.isArray()) {
            for (JsonNode item : fallback) {
                fallbacks.add(item.asText());
            }
        }
        return new Kit.Instrument(
                name,
                body.path("source").asText(""),
                body.has("bank") ? body.path("bank").asInt() : 0,
                body.has("program") ? body.path("program").asInt() : -1,
                text(body, "preset"),
                body.has("drumkit") ? body.path("drumkit").asInt() : -1,
                body.path("gainDb").asDouble(0),
                body.path("transpose").asInt(0),
                low,
                high,
                body.path("outOfRange").asText(low == null ? "" : "fold"),
                fallbacks,
                text(body, "class"),
                text(body.path("analysis"), "verdict"),
                text(body.path("analysis"), "note"));
    }

    private static String text(JsonNode body, String field) {
        if (body == null || body.isMissingNode() || body.isNull()) {
            return null;
        }
        String value = body.path(field).asText("");
        return value.isBlank() ? null : value;
    }

    private static String strip(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
