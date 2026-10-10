// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.sosgames.athenacl.theory.Key;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads a theme YAML file and checks that every state, layer, and transition target exists.
 * A bad {@code to} is {@code THEME_UNKNOWN_STATE} at a JSON pointer, so an agent can patch that field.
 */
public final class Themes {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final Set<String> LAYERS = Set.of("pad", "bass", "lead", "drums");

    private Themes() {
    }

    public static boolean looksLike(Path path) {
        try {
            JsonNode root = YAML.readTree(Files.readString(path, StandardCharsets.UTF_8));
            return root != null && root.path("states").isObject();
        } catch (IOException e) {
            return false;
        }
    }

    public static Theme load(Path path) throws IOException, ThemeException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        return parse(path.getFileName().toString(), text);
    }

    public static Theme parse(String name, String yaml) throws ThemeException {
        JsonNode root;
        try {
            root = YAML.readTree(yaml);
        } catch (IOException e) {
            throw new ThemeException(List.of(new ThemeIssue(
                    "THEME_SYNTAX", "", "theme is not YAML",
                    "Use states, transitions, and an optional timeline. See samples/themes/poc.yaml.")));
        }
        if (root == null || !root.isObject()) {
            throw new ThemeException(List.of(new ThemeIssue(
                    "THEME_SYNTAX", "", "theme is empty",
                    "Add a states map. See samples/themes/poc.yaml.")));
        }
        List<ThemeIssue> issues = new ArrayList<>();
        String id = text(root, "id", name);
        int bars = root.path("bars").asInt(16);
        if (bars < 1) {
            issues.add(issue("THEME_BARS", "/bars", "bars must be 1 or more", "Set bars to 16 or more."));
            bars = 1;
        }
        int bpm = root.path("bpm").asInt(120);
        int numerator = 4;
        int denominator = 4;
        String meter = root.path("meter").asText("4/4");
        String[] meterParts = meter.split("/");
        if (meterParts.length == 2) {
            try {
                numerator = Integer.parseInt(meterParts[0].trim());
                denominator = Integer.parseInt(meterParts[1].trim());
            } catch (NumberFormatException e) {
                issues.add(issue("THEME_METER", "/meter", "meter " + meter, "Use a meter like 4/4."));
            }
        } else {
            issues.add(issue("THEME_METER", "/meter", "meter " + meter, "Use a meter like 4/4."));
        }
        Map<String, Theme.StateDef> states = new LinkedHashMap<>();
        JsonNode stateNode = root.get("states");
        if (stateNode == null || !stateNode.isObject() || stateNode.isEmpty()) {
            issues.add(issue("THEME_STATES", "/states", "theme has no states",
                    "Add at least one state, such as peaceful."));
        } else {
            var fields = stateNode.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                states.put(entry.getKey(), stateDef(entry.getKey(), entry.getValue(), bpm, issues));
            }
        }
        Map<String, Theme.StingerDef> stingers = new LinkedHashMap<>();
        JsonNode stingerNode = root.get("stingers");
        if (stingerNode != null && stingerNode.isObject()) {
            var fields = stingerNode.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                JsonNode body = entry.getValue();
                String pitch = body.path("pitch").asText("C5");
                String dur = body.path("dur").asText("1/8");
                stingers.put(entry.getKey(), new Theme.StingerDef(entry.getKey(), pitch, dur));
            }
        }
        for (Theme.StateDef state : states.values()) {
            if (state.stinger() != null && !stingers.containsKey(state.stinger())) {
                issues.add(issue("THEME_UNKNOWN_STINGER", "/states/" + state.id() + "/stinger",
                        "unknown stinger " + state.stinger(),
                        "Declare " + state.stinger() + " under stingers."));
            }
        }
        Quantize quantize = Quantize.BAR;
        JsonNode defaults = root.path("transitions").path("defaults").path("quantize");
        if (defaults.isTextual()) {
            try {
                quantize = Quantize.parse(defaults.asText());
            } catch (IllegalArgumentException e) {
                issues.add(issue("THEME_QUANTIZE", "/transitions/defaults/quantize", e.getMessage(),
                        "Use bar, beat, or immediate."));
            }
        }
        List<Theme.Rule> rules = new ArrayList<>();
        JsonNode ruleNode = root.path("transitions").path("rules");
        if (ruleNode.isArray()) {
            for (int i = 0; i < ruleNode.size(); i++) {
                rules.add(rule(ruleNode.get(i), i, states, issues));
            }
        }
        List<Theme.Control> timeline = new ArrayList<>();
        JsonNode timelineNode = root.get("timeline");
        if (timelineNode != null && timelineNode.isArray()) {
            for (int i = 0; i < timelineNode.size(); i++) {
                timeline.add(control(timelineNode.get(i), "/timeline/" + i, states, issues));
            }
        }
        if (!issues.isEmpty()) {
            throw new ThemeException(issues);
        }
        return new Theme(id, bars, bpm, numerator, denominator, states, stingers, quantize, rules, timeline);
    }

    /** Four decimal places, so a YAML float and the control log agree. */
    public static double intensity(double raw) {
        return Math.round(raw * 10000.0) / 10000.0;
    }

    public static String number(double value) {
        return BigDecimal.valueOf(intensity(value)).stripTrailingZeros().toPlainString();
    }

    private static Theme.StateDef stateDef(String id, JsonNode body, int bpm, List<ThemeIssue> issues) {
        String keyText = body.path("key").asText("C major");
        Key key;
        try {
            key = Key.parse(keyText);
        } catch (IllegalArgumentException e) {
            issues.add(issue("THEME_KEY", "/states/" + id + "/key", e.getMessage(),
                    "Use a key like C major or D-dorian."));
            key = Key.parse("C major");
        }
        int tempo = body.path("tempo").asInt(bpm);
        List<String> layers = new ArrayList<>();
        JsonNode layerNode = body.get("layers");
        if (layerNode != null && layerNode.isArray()) {
            for (int i = 0; i < layerNode.size(); i++) {
                String layer = layerNode.get(i).asText("");
                if (!LAYERS.contains(layer)) {
                    issues.add(issue("THEME_UNKNOWN_LAYER", "/states/" + id + "/layers/" + i,
                            "unknown layer " + layer,
                            "Use pad, bass, lead, or drums."));
                }
                layers.add(layer);
            }
        }
        if (layers.isEmpty()) {
            layers.add("pad");
        }
        String stinger = body.hasNonNull("stinger") ? body.get("stinger").asText() : null;
        return new Theme.StateDef(id, key, tempo, layers, stinger);
    }

    private static Theme.Rule rule(JsonNode body, int index, Map<String, Theme.StateDef> states, List<ThemeIssue> issues) {
        String path = "/transitions/rules/" + index;
        String from = body.path("from").asText("*");
        if (!from.equals("*") && !states.containsKey(from)) {
            issues.add(issue("THEME_UNKNOWN_STATE", path + "/from", "unknown state " + from,
                    "Use a state declared under states, such as " + example(states) + "."));
        }
        List<String> to = new ArrayList<>();
        JsonNode toNode = body.get("to");
        if (toNode == null || toNode.isNull()) {
            issues.add(issue("THEME_UNKNOWN_STATE", path + "/to", "rule has no to",
                    "Set to to a state declared under states."));
        } else if (toNode.isArray()) {
            for (int j = 0; j < toNode.size(); j++) {
                String name = toNode.get(j).asText("");
                to.add(name);
                if (!states.containsKey(name)) {
                    issues.add(issue("THEME_UNKNOWN_STATE", path + "/to/" + j, "unknown state " + name,
                            "Use a state declared under states, such as " + example(states) + "."));
                }
            }
        } else {
            String name = toNode.asText("");
            to.add(name);
            if (!states.containsKey(name)) {
                issues.add(issue("THEME_UNKNOWN_STATE", path + "/to", "unknown state " + name,
                        "Use a state declared under states, such as " + example(states) + "."));
            }
        }
        Quantize quantize = Quantize.BAR;
        if (body.hasNonNull("quantize")) {
            try {
                quantize = Quantize.parse(body.get("quantize").asText());
            } catch (IllegalArgumentException e) {
                issues.add(issue("THEME_QUANTIZE", path + "/quantize", e.getMessage(),
                        "Use bar, beat, or immediate."));
            }
        }
        return new Theme.Rule(from, to, quantize);
    }

    static Theme.Control control(JsonNode body, String path, Map<String, Theme.StateDef> states, List<ThemeIssue> issues) {
        int bar = body.path("bar").asInt(1);
        int beat = body.path("beat").asInt(1);
        String state = body.path("state").asText("");
        if (state.isBlank()) {
            issues.add(issue("THEME_UNKNOWN_STATE", path + "/state", "control has no state",
                    "Set state to one declared under states."));
            state = states.isEmpty() ? "peaceful" : example(states);
        } else if (!states.containsKey(state)) {
            issues.add(issue("THEME_UNKNOWN_STATE", path + "/state", "unknown state " + state,
                    "Use a state declared under states, such as " + example(states) + "."));
        }
        double intensity = body.has("intensity") ? intensity(body.get("intensity").asDouble()) : 0.3;
        if (intensity < 0 || intensity > 1) {
            issues.add(issue("THEME_INTENSITY", path + "/intensity", "intensity " + number(intensity),
                    "Use a value from 0 to 1."));
            intensity = 0.3;
        }
        if (bar < 1 || beat < 1) {
            issues.add(issue("THEME_TIME", path, "bar and beat start at 1", "Use bar 1 beat 1 for the downbeat."));
        }
        return new Theme.Control(bar, beat, state, intensity);
    }

    private static ThemeIssue issue(String code, String path, String message, String fix) {
        return new ThemeIssue(code, path, message, fix);
    }

    private static String text(JsonNode root, String field, String fallback) {
        JsonNode node = root.get(field);
        if (node == null || node.asText().isBlank()) {
            return fallback;
        }
        return node.asText();
    }

    private static String example(Map<String, Theme.StateDef> states) {
        if (states.isEmpty()) {
            return "peaceful";
        }
        return states.keySet().iterator().next();
    }

    static String layerList(List<String> layers) {
        return String.join(",", layers).toLowerCase(Locale.ROOT);
    }
}
