// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import io.github.sosgames.athenacl.theory.Key;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges a playing state from genre defaults, the generic state, the genre-specific
 * state, then the game theme (its shared modules, then that one state).
 * Unknown module names are {@code THEME_UNKNOWN_KEY} with a near-name hint.
 */
public final class Resolver {
    static final List<String> MODULES = List.of(
            "scale", "harmony", "cadence", "drone", "voicing", "lead", "hooks",
            "instruments", "tempo", "density", "dynamics");
    private static final Set<String> STATE_KEYS = Set.of(
            "scale", "harmony", "cadence", "drone", "voicing", "lead", "hooks",
            "instruments", "tempo", "density", "dynamics", "layers", "programs",
            "key", "stinger", "meter");

    private Resolver() {
    }

    public static Composition genre(String id) throws IOException {
        JsonNode node = Composition.readResource("/presets/genres/" + id + ".yaml");
        if (node == null) {
            return null;
        }
        return Composition.empty().merge(node, "genre:" + id);
    }

    /**
     * @param themePath label used in provenance, or empty when there is no game file
     * @param theme     the game file, or null
     */
    public static Composition resolveTheme(String genreId, String stateId, Path theme) throws IOException {
        if (theme == null) {
            return resolve(genreId, stateId, "", null);
        }
        JsonNode file = new ObjectMapper(new YAMLFactory()).readTree(Files.readString(theme));
        String path = theme.toString().replace('\\', '/');
        String genre = genreId;
        if (genre == null || genre.isBlank()) {
            genre = file.path("genre").asText(file.path("preset").asText(""));
        }
        return resolve(genre, stateId, path, file);
    }

    public static Composition resolve(String genreId, String stateId, String themePath, JsonNode theme)
            throws IOException {
        JsonNode genreNode = Composition.readResource("/presets/genres/" + genreId + ".yaml");
        if (genreNode == null) {
            return null;
        }
        Composition composition = Composition.empty().merge(genreNode, "genre:" + genreId);
        JsonNode generic = Composition.readResource("/presets/states/" + stateId + ".yaml");
        if (generic != null) {
            composition = composition.merge(generic, "state:" + stateId);
        }
        JsonNode specific = Composition.readResource("/presets/states/" + genreId + "/" + stateId + ".yaml");
        if (specific != null) {
            composition = composition.merge(specific, "state:" + genreId + "/" + stateId);
        }
        if (theme != null && theme.isObject()) {
            String source = "theme:" + themePath;
            composition = composition.merge(modulesOf(theme), source);
            JsonNode states = theme.get("states");
            if (states != null && states.isObject() && states.get(stateId) != null) {
                composition = composition.merge(states.get(stateId), source);
            }
        }
        return composition;
    }

    public static Theme.StateDef state(String id, Composition composition, String meterFallback) {
        String tonic = composition.text("scale.tonic", "C");
        String mode = composition.text("scale.mode", "aeolian");
        Key key = Key.parse(tonic + " " + mode);
        int tempo = composition.number("tempo.bpm", 120);
        List<String> layers = new ArrayList<>(composition.list("layers"));
        if (layers.isEmpty()) {
            layers.add("pad");
        }
        Map<String, Integer> programs = new LinkedHashMap<>();
        Map<String, String> instruments = new LinkedHashMap<>();
        for (Map.Entry<String, Composition.Leaf> entry : composition.leaves().entrySet()) {
            if (entry.getKey().startsWith("programs.")) {
                programs.put(entry.getKey().substring("programs.".length()),
                        composition.number(entry.getKey(), 0));
            } else if (entry.getKey().startsWith("instruments.") && !entry.getKey().substring("instruments.".length()).contains(".")) {
                instruments.put(entry.getKey().substring("instruments.".length()), entry.getValue().value());
            }
        }
        if (!instruments.containsKey("taiko") && instruments.containsKey("perc")) {
            instruments.put("taiko", instruments.get("perc"));
        }
        String meter = composition.text("meter", meterFallback);
        return new Theme.StateDef(id, key, tempo, layers, null, programs, instruments,
                meter == null || meter.isBlank() ? null : meter, composition);
    }

    public static List<ThemeIssue> unknownKeys(String stateId, JsonNode body) {
        List<ThemeIssue> issues = new ArrayList<>();
        if (body == null || !body.isObject()) {
            return issues;
        }
        var fields = body.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (STATE_KEYS.contains(entry.getKey())) {
                continue;
            }
            issues.add(new ThemeIssue(
                    "THEME_UNKNOWN_KEY",
                    "/states/" + stateId + "/" + entry.getKey(),
                    "unknown key " + entry.getKey(),
                    hint(entry.getKey())));
        }
        return issues;
    }

    static String hint(String key) {
        String best = null;
        int bestDistance = 3;
        for (String module : MODULES) {
            int distance = distance(key, module);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = module;
            }
        }
        return best == null ? "Use a module such as harmony." : "did you mean " + best;
    }

    private static JsonNode modulesOf(JsonNode theme) {
        ObjectNode copy = (ObjectNode) theme.deepCopy();
        copy.remove("id");
        copy.remove("preset");
        copy.remove("genre");
        copy.remove("states");
        copy.remove("bars");
        copy.remove("bpm");
        copy.remove("meter");
        copy.remove("motif");
        copy.remove("hooks");
        copy.remove("critic");
        copy.remove("overlays");
        copy.remove("transitions");
        copy.remove("timeline");
        copy.remove("instruments");
        copy.remove("hold");
        copy.remove("clock");
        copy.remove("harmony");
        return copy;
    }

    private static int distance(String left, String right) {
        int[][] cells = new int[left.length() + 1][right.length() + 1];
        for (int i = 0; i <= left.length(); i++) {
            cells[i][0] = i;
        }
        for (int j = 0; j <= right.length(); j++) {
            cells[0][j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                cells[i][j] = Math.min(Math.min(cells[i - 1][j] + 1, cells[i][j - 1] + 1), cells[i - 1][j - 1] + cost);
            }
        }
        return cells[left.length()][right.length()];
    }
}
