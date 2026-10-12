// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One resolved musical document. Maps merge field by field and lists replace.
 * Each leaf remembers the layer that set it ({@code genre:fantasy}, {@code state:sneaking},
 * {@code theme:themes/my-dungeon.yaml}). {@code tempo.relative} multiplies the bpm already
 * resolved and is stored as {@code tempo.bpm}.
 */
public final class Composition {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    public record Leaf(String value, String source) {
    }

    private final Map<String, Leaf> leaves;
    private final List<String> chain;

    private Composition(Map<String, Leaf> leaves, List<String> chain) {
        this.leaves = leaves;
        this.chain = chain;
    }

    public static Composition empty() {
        return new Composition(new LinkedHashMap<>(), List.of());
    }

    public static Composition parse(String source, String yaml) throws IOException {
        JsonNode node = YAML.readTree(yaml);
        if (node == null || !node.isObject()) {
            return empty();
        }
        return empty().merge(node, source);
    }

    public static JsonNode readResource(String path) throws IOException {
        try (InputStream in = Composition.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return YAML.readTree(in);
        }
    }

    public List<String> chain() {
        return chain;
    }

    public String chainText() {
        return String.join("|", chain);
    }

    public boolean has(String path) {
        return leaves.containsKey(path);
    }

    public String text(String path, String fallback) {
        Leaf leaf = leaves.get(path);
        return leaf == null ? fallback : leaf.value;
    }

    public int number(String path, int fallback) {
        Leaf leaf = leaves.get(path);
        if (leaf == null) {
            return fallback;
        }
        return (int) Math.round(Double.parseDouble(leaf.value));
    }

    public double decimal(String path, double fallback) {
        Leaf leaf = leaves.get(path);
        if (leaf == null) {
            return fallback;
        }
        return Double.parseDouble(leaf.value);
    }

    public boolean flag(String path, boolean fallback) {
        Leaf leaf = leaves.get(path);
        if (leaf == null) {
            return fallback;
        }
        return Boolean.parseBoolean(leaf.value);
    }

    public List<Integer> ints(String path) {
        Leaf leaf = leaves.get(path);
        if (leaf == null || leaf.value.isBlank()) {
            return List.of();
        }
        List<Integer> values = new ArrayList<>();
        for (String part : leaf.value.split(",")) {
            if (!part.isBlank()) {
                values.add((int) Math.round(Double.parseDouble(part.trim())));
            }
        }
        return values;
    }

    public List<String> list(String path) {
        Leaf leaf = leaves.get(path);
        if (leaf == null || leaf.value.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String part : leaf.value.split(",")) {
            if (!part.isBlank()) {
                values.add(part.trim());
            }
        }
        return values;
    }

    public String source(String path) {
        Leaf leaf = leaves.get(path);
        return leaf == null ? "" : leaf.source;
    }

    public Map<String, Leaf> leaves() {
        return leaves;
    }

    /** Later layer wins. A list at {@code path} replaces the previous list. */
    public Composition merge(JsonNode node, String source) {
        if (node == null || !node.isObject()) {
            return this;
        }
        int bpm = number("tempo.bpm", 120);
        Map<String, Leaf> next = new LinkedHashMap<>(leaves);
        apply(next, "", node, source);
        JsonNode tempo = node.get("tempo");
        if (tempo != null && tempo.isObject() && tempo.has("relative") && !tempo.has("bpm")) {
            double relative = tempo.get("relative").asDouble(1);
            int resolved = (int) Math.round(bpm * relative);
            next.put("tempo.bpm", new Leaf(Integer.toString(resolved), source));
            next.remove("tempo.relative");
        }
        List<String> layers = new ArrayList<>(chain);
        if (!layers.contains(source)) {
            layers.add(source);
        }
        return new Composition(next, List.copyOf(layers));
    }

    public Map<String, Object> tree() {
        Map<String, Object> root = new LinkedHashMap<>();
        for (Map.Entry<String, Leaf> entry : leaves.entrySet()) {
            if (skip(entry.getKey())) {
                continue;
            }
            put(root, entry.getKey(), typed(entry.getValue().value));
        }
        return root;
    }

    /** One {@code key=value} line per leaf. {@code explain} appends the merge chain. */
    public String lines(boolean explain) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Leaf> entry : leaves.entrySet()) {
            if (skip(entry.getKey())) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(entry.getKey()).append('=').append(show(entry.getValue().value));
            if (explain) {
                text.append(" from=").append(chainText());
            } else {
                text.append(" from=").append(entry.getValue().source);
            }
        }
        return text.toString();
    }

    private static boolean skip(String path) {
        return path.equals("genre") || path.equals("state") || path.equals("character")
                || path.equals("palette") || path.equals("limits") || path.equals("meter")
                || path.equals("id") || path.startsWith("loudness");
    }

    private static String show(String value) {
        if (value.chars().anyMatch(ch -> ch == ' ' || ch == '"')) {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }
        return value;
    }

    private static Object typed(String value) {
        if ("true".equals(value) || "false".equals(value)) {
            return Boolean.parseBoolean(value);
        }
        try {
            if (value.indexOf('.') >= 0) {
                return Double.parseDouble(value);
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            if (value.indexOf(',') >= 0) {
                List<String> parts = new ArrayList<>();
                for (String part : value.split(",")) {
                    parts.add(part.trim());
                }
                return parts;
            }
            return value;
        }
    }

    @SuppressWarnings("unchecked")
    private static void put(Map<String, Object> root, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> cursor = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object child = cursor.get(parts[i]);
            if (!(child instanceof Map<?, ?>)) {
                child = new LinkedHashMap<String, Object>();
                cursor.put(parts[i], child);
            }
            cursor = (Map<String, Object>) child;
        }
        cursor.put(parts[parts.length - 1], value);
    }

    private static void apply(Map<String, Leaf> leaves, String prefix, JsonNode node, String source) {
        var fields = node.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            JsonNode value = entry.getValue();
            if (value.isObject()) {
                apply(leaves, path, value, source);
            } else if (value.isArray()) {
                List<String> items = new ArrayList<>();
                for (JsonNode item : value) {
                    items.add(item.asText());
                }
                leaves.put(path, new Leaf(String.join(",", items), source));
            } else if (value.isBoolean()) {
                leaves.put(path, new Leaf(value.asBoolean() ? "true" : "false", source));
            } else if (value.isNumber()) {
                leaves.put(path, new Leaf(value.asText(), source));
            } else {
                leaves.put(path, new Leaf(value.asText(""), source));
            }
        }
    }
}
