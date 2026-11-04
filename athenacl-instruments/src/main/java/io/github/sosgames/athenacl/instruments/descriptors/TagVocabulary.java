// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.descriptors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.sosgames.athenacl.instruments.KitException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The tag, role, and class names in {@code presets/tags.yaml}. Adding a tag is a data
 * change. A misspelled tag fails with the closest name, the same way an unknown command does.
 */
public final class TagVocabulary {
    private static TagVocabulary loaded;
    private final Set<String> tags;
    private final Set<String> roles;
    private final Set<String> classes;

    private TagVocabulary(Set<String> tags, Set<String> roles, Set<String> classes) {
        this.tags = tags;
        this.roles = roles;
        this.classes = classes;
    }

    public static TagVocabulary get() throws IOException, KitException {
        if (loaded != null) {
            return loaded;
        }
        Path file = Path.of("presets", "tags.yaml");
        if (!Files.isRegularFile(file)) {
            throw new KitException(new KitException.Issue(
                    "NOT_FOUND", "presets/tags.yaml", "tag vocabulary not found",
                    "Keep presets/tags.yaml next to the genres."));
        }
        JsonNode root = new ObjectMapper(new YAMLFactory()).readTree(Files.readString(file, StandardCharsets.UTF_8));
        loaded = new TagVocabulary(set(root.get("tags")), set(root.get("roles")), set(root.get("classes")));
        return loaded;
    }

    public Set<String> tags() {
        return tags;
    }

    public Set<String> roles() {
        return roles;
    }

    public Set<String> classes() {
        return classes;
    }

    public void checkTag(String tag, String path) throws KitException {
        if (!tags.contains(tag)) {
            throw unknown("unknown tag " + tag, path, hint(tag, tags));
        }
    }

    public void checkRole(String role, String path) throws KitException {
        if (!roles.contains(role)) {
            throw unknown("unknown role " + role, path, hint(role, roles));
        }
    }

    public String canonicalClass(String name, String path) throws KitException {
        String folded = foldClass(name);
        if (classes.contains(folded)) {
            return folded;
        }
        throw unknown("unknown class " + name, path, hint(folded, classes));
    }

    public static String hint(String token, Collection<String> names) {
        String near = nearest(token, names, 3);
        return near == null ? "Use a name from presets/tags.yaml." : "Did you mean " + near + "?";
    }

    public static String nearest(String token, Collection<String> names, int maxDistance) {
        if (token == null || names == null) {
            return null;
        }
        String best = null;
        int bestDistance = maxDistance + 1;
        for (String name : names) {
            int distance = distance(token.toLowerCase(Locale.ROOT), name.toLowerCase(Locale.ROOT));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = name;
            }
        }
        return bestDistance <= maxDistance ? best : null;
    }

    private static String foldClass(String name) {
        String text = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return switch (text) {
            case "unpitched-percussion", "unpitched" -> "unpitched-perc";
            case "tuned-percussion" -> "tuned-perc";
            case "synth-raw" -> "synth";
            default -> text;
        };
    }

    private static KitException unknown(String message, String path, String fix) {
        return new KitException(new KitException.Issue("INVALID_INPUT", path, message, fix));
    }

    private static Set<String> set(JsonNode node) {
        Set<String> values = new LinkedHashSet<>();
        if (node == null || !node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            String text = item.asText("").trim().toLowerCase(Locale.ROOT);
            if (!text.isBlank()) {
                values.add(text);
            }
        }
        return values;
    }

    private static int distance(String left, String right) {
        int[] prev = new int[right.length() + 1];
        int[] curr = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }
        return prev[right.length()];
    }

    static List<String> copy(Collection<String> values) {
        return List.copyOf(new ArrayList<>(values));
    }
}
