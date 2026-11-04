// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.descriptors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.sosgames.athenacl.instruments.KitException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Writes declared descriptor fields into a kit YAML. {@code --out} keeps the original file
 * untouched, which is how a check can tag an instrument without editing {@code gm-lite}.
 */
public final class KitTagger {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final Set<String> FIELDS = Set.of(
            "roles", "tags", "avoid", "blendWith", "clashWith", "genres", "notes", "class",
            "loudnessTrimDb", "register.low", "register.high", "register.sweetLow", "register.sweetHigh");

    private KitTagger() {
    }

    public static String write(Path kitFile, String name, String[] assignments, Path out)
            throws IOException, KitException {
        if (!Files.isRegularFile(kitFile)) {
            throw new KitException(new KitException.Issue(
                    "KIT_NOT_FOUND", "--kit", "kit not found", "Pass a kit file such as instruments/gm-lite.yaml."));
        }
        if (assignments == null || assignments.length == 0) {
            throw new KitException(new KitException.Issue(
                    "INVALID_INPUT", "argv[3]", "usage: kit tag <instrument> key=value",
                    "Try kit tag soft_flute avoid=true."));
        }
        ObjectNode root = (ObjectNode) YAML.readTree(Files.readString(kitFile, StandardCharsets.UTF_8));
        ObjectNode instruments = root.withObject("instruments");
        ObjectNode body = instruments.withObject(name);
        TagVocabulary vocabulary = TagVocabulary.get();
        StringBuilder summary = new StringBuilder();
        summary.append("instrument=").append(name);
        for (String assignment : assignments) {
            int eq = assignment.indexOf('=');
            if (eq <= 0) {
                throw new KitException(new KitException.Issue(
                        "INVALID_INPUT", "argv[3]", "expected key=value", "Try avoid=true or tags=soft,airy."));
            }
            String key = assignment.substring(0, eq).trim();
            String value = assignment.substring(eq + 1).trim();
            if (!FIELDS.contains(key)) {
                throw new KitException(new KitException.Issue(
                        "INVALID_INPUT", "/instruments/" + name + "/" + key,
                        "unknown key " + key, TagVocabulary.hint(key, FIELDS)));
            }
            apply(body, name, key, value, vocabulary);
            summary.append(' ').append(key).append('=').append(value);
        }
        Path dest = out == null ? kitFile : out;
        if (dest.getParent() != null) {
            Files.createDirectories(dest.getParent());
        }
        Files.writeString(dest, YAML.writeValueAsString(root), StandardCharsets.UTF_8);
        summary.append(" wrote=").append(dest.toString().replace('\\', '/'));
        return summary.toString();
    }

    private static void apply(ObjectNode body, String name, String key, String value, TagVocabulary vocabulary)
            throws KitException {
        if (key.startsWith("register.")) {
            ObjectNode register = body.withObject("register");
            register.put(key.substring("register.".length()), Integer.parseInt(value));
            return;
        }
        switch (key) {
            case "avoid" -> body.put("avoid", Boolean.parseBoolean(value));
            case "notes" -> body.put("notes", value);
            case "class" -> body.put("class", vocabulary.canonicalClass(value, "/instruments/" + name + "/class"));
            case "loudnessTrimDb" -> body.put("loudnessTrimDb", Double.parseDouble(value));
            case "roles" -> body.set("roles", array(value, token -> vocabulary.checkRole(token, "/instruments/" + name + "/roles")));
            case "tags" -> body.set("tags", array(value, token -> vocabulary.checkTag(token, "/instruments/" + name + "/tags")));
            case "clashWith" -> body.set("clashWith", array(value, token -> {
                if (token.startsWith("tag:")) {
                    vocabulary.checkTag(token.substring(4), "/instruments/" + name + "/clashWith");
                }
            }));
            case "blendWith", "genres" -> body.set(key, array(value, token -> {
            }));
            default -> throw new KitException(new KitException.Issue(
                    "INVALID_INPUT", key, "unknown key " + key, TagVocabulary.hint(key, FIELDS)));
        }
    }

    private static ArrayNode array(String value, Checker checker) throws KitException {
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        for (String part : value.split(",")) {
            String token = part.trim().toLowerCase(Locale.ROOT);
            if (token.isBlank()) {
                continue;
            }
            checker.check(token);
            array.add(token);
        }
        return array;
    }

    @FunctionalInterface
    private interface Checker {
        void check(String token) throws KitException;
    }
}
