// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.descriptors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.sosgames.athenacl.adaptive.Composition;
import io.github.sosgames.athenacl.instruments.Kit;
import io.github.sosgames.athenacl.instruments.KitException;
import io.github.sosgames.athenacl.instruments.Kits;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds one {@link Resolved} per kit instrument. A field uses the first source that sets it:
 * a game override, then the kit YAML, then an external describer file, then the General MIDI
 * table. The line for that field names the source, so {@code kit describe} can say where it came from.
 */
public final class DescriptorResolver {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final Set<String> KEYS = Set.of(
            "source", "bank", "program", "preset", "drumkit", "gainDb", "transpose", "keyRange",
            "outOfRange", "fallback", "class", "analysis", "roles", "tags", "register",
            "loudnessTrimDb", "avoid", "blendWith", "clashWith", "genres", "notes");
    private static final Set<String> REGISTER_KEYS = Set.of("low", "high", "sweetLow", "sweetHigh");
    private static GmTable gm;
    private static Map<String, JsonNode> external = Map.of();
    private static boolean externalRead;

    private DescriptorResolver() {
    }

    public record Resolved(
            String id,
            int bank,
            int program,
            boolean percussion,
            List<String> roles,
            String rolesSource,
            List<String> tags,
            String tagsSource,
            Register register,
            String registerSource,
            String soundClass,
            String classSource,
            double loudnessTrimDb,
            String trimSource,
            boolean avoid,
            String avoidSource,
            List<String> blendWith,
            String blendSource,
            List<String> clashWith,
            String clashSource,
            List<String> genres,
            String genresSource,
            String notes,
            String notesSource) {

        public String text() {
            StringBuilder sb = new StringBuilder();
            sb.append("instrument=").append(id);
            sb.append(" program=").append(program);
            sb.append(" percussion=").append(percussion).append('\n');
            field(sb, "roles", String.join(",", roles), rolesSource);
            field(sb, "tags", String.join(",", tags), tagsSource);
            field(sb, "register", register.text(), registerSource);
            field(sb, "class", soundClass, classSource);
            field(sb, "loudnessTrimDb", trim(loudnessTrimDb), trimSource);
            field(sb, "avoid", Boolean.toString(avoid), avoidSource);
            field(sb, "blendWith", String.join(",", blendWith), blendSource);
            field(sb, "clashWith", String.join(",", clashWith), clashSource);
            field(sb, "genres", String.join(",", genres), genresSource);
            field(sb, "notes", notes == null ? "" : notes, notesSource);
            return sb.toString().stripTrailing();
        }

        public String blurb() {
            String tagsText = tags.isEmpty() ? soundClass : String.join(", ", tags);
            String note = notes == null || notes.isBlank() ? "" : "\n" + notes;
            return tagsText + "\n" + soundClass + note;
        }

        private static void field(StringBuilder sb, String key, String value, String source) {
            sb.append(key).append('=').append(value == null ? "" : value);
            sb.append(" source=").append(source).append('\n');
        }

        private static String trim(double value) {
            if (value == Math.rint(value)) {
                return Long.toString(Math.round(value));
            }
            return String.format(Locale.ROOT, "%.2f", value);
        }
    }

    public static List<Resolved> all(Path kitFile) throws IOException, KitException {
        return all(kitFile, null);
    }

    public static List<Resolved> all(Path kitFile, Composition composition) throws IOException, KitException {
        Kit kit = Kits.load(kitFile);
        Map<String, JsonNode> declared = declaredNodes(kitFile);
        Map<String, String> hashes = hashes(kit);
        TagVocabulary vocabulary = TagVocabulary.get();
        GmTable table = gm();
        Map<String, JsonNode> externalRows = external();
        List<Resolved> rows = new ArrayList<>();
        for (Kit.Instrument instrument : kit.instruments()) {
            JsonNode node = declared.getOrDefault(instrument.name(), YAML.nullNode());
            rows.add(resolve(instrument, node, hashes.getOrDefault(instrument.source(), ""),
                    composition, vocabulary, table, externalRows));
        }
        return rows;
    }

    public static Resolved one(Path kitFile, String name) throws IOException, KitException {
        for (Resolved row : all(kitFile)) {
            if (row.id.equals(name)) {
                return row;
            }
        }
        return null;
    }

    public static String blurb(int program, boolean percussion) {
        try {
            GmTable table = gm();
            GmRow row = percussion ? table.drums : table.byProgram(program);
            if (row == null) {
                return percussion ? "drum kit" : "preset";
            }
            return String.join(", ", row.tags) + "\n" + row.soundClass;
        } catch (IOException | KitException e) {
            return percussion ? "drum kit" : "preset";
        }
    }

    private static Resolved resolve(Kit.Instrument instrument, JsonNode node, String hash,
                                    Composition composition, TagVocabulary vocabulary, GmTable table,
                                    Map<String, JsonNode> externalRows) throws KitException {
        checkKeys(instrument.name(), node);
        int[] placed = place(instrument, node);
        int bank = placed[0];
        int program = placed[1];
        boolean percussion = instrument.percussion() || bank == 128;
        GmRow defaults = percussion ? table.drums : table.byProgram(program);
        JsonNode external = ExternalProfiles.find(externalRows, hash, percussion ? 128 : bank, program);
        String path = "/instruments/" + instrument.name();
        ListField roles = lists("roles", instrument.name(), node, external, defaults == null ? List.of() : defaults.roles,
                composition, vocabulary, true);
        ListField tags = lists("tags", instrument.name(), node, external, defaults == null ? List.of() : defaults.tags,
                composition, vocabulary, false);
        ListField blend = lists("blendWith", instrument.name(), node, external, List.of(), composition, vocabulary, false);
        ListField clash = lists("clashWith", instrument.name(), node, external, List.of(), composition, vocabulary, false);
        checkClash(instrument.name(), clash.values, vocabulary);
        ListField genres = lists("genres", instrument.name(), node, external, List.of(), composition, vocabulary, false);
        Sourced<Register> register = register(instrument.name(), node, external, defaults, composition);
        Sourced<String> soundClass = soundClass(instrument.name(), node, external, defaults, composition, vocabulary);
        Sourced<Double> trim = number("loudnessTrimDb", instrument.name(), node, external, composition);
        Sourced<Boolean> avoid = flag("avoid", instrument.name(), node, external, composition);
        Sourced<String> notes = text("notes", instrument.name(), node, external, composition);
        return new Resolved(
                instrument.name(), bank, program, percussion,
                roles.values, roles.source,
                tags.values, tags.source,
                register.value, register.source,
                soundClass.value, soundClass.source,
                trim.value, trim.source,
                avoid.value, avoid.source,
                blend.values, blend.source,
                clash.values, clash.source,
                genres.values, genres.source,
                notes.value, notes.source);
    }

    private static int[] place(Kit.Instrument instrument, JsonNode node) {
        int bank = node.path("bank").asInt(instrument.bank());
        int program = node.has("program") ? node.path("program").asInt() : instrument.program();
        if (node.has("drumkit")) {
            return new int[] {128, node.path("drumkit").asInt()};
        }
        if (instrument.percussion()) {
            return new int[] {128, instrument.drumkit()};
        }
        if (program < 0) {
            int[] parsed = parsePreset(node.path("preset").asText(instrument.preset() == null ? "" : instrument.preset()));
            if (parsed != null) {
                return parsed;
            }
        }
        return new int[] {bank, Math.max(program, 0)};
    }

    private static int[] parsePreset(String preset) {
        if (preset == null || preset.isBlank()) {
            return null;
        }
        String[] parts = preset.split("/");
        if (parts.length < 2) {
            return null;
        }
        try {
            int program = Integer.parseInt(parts[parts.length - 1]);
            int bank = Integer.parseInt(parts[parts.length - 2]);
            return new int[] {bank, program};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void checkKeys(String name, JsonNode node) throws KitException {
        if (node == null || !node.isObject()) {
            return;
        }
        var fields = node.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (!KEYS.contains(entry.getKey())) {
                throw new KitException(new KitException.Issue(
                        "INVALID_INPUT", "/instruments/" + name + "/" + entry.getKey(),
                        "unknown key " + entry.getKey(),
                        TagVocabulary.hint(entry.getKey(), KEYS)));
            }
            if ("register".equals(entry.getKey()) && entry.getValue().isObject()) {
                var corners = entry.getValue().fields();
                while (corners.hasNext()) {
                    String corner = corners.next().getKey();
                    if (!REGISTER_KEYS.contains(corner)) {
                        throw new KitException(new KitException.Issue(
                                "INVALID_INPUT", "/instruments/" + name + "/register/" + corner,
                                "unknown key " + corner,
                                TagVocabulary.hint(corner, REGISTER_KEYS)));
                    }
                }
            }
        }
    }

    private static ListField lists(String key, String name, JsonNode node, JsonNode external, List<String> gm,
                                   Composition composition, TagVocabulary vocabulary, boolean roles) throws KitException {
        String overridePath = "descriptors." + name + "." + key;
        if (composition != null && composition.leaves().containsKey(overridePath)) {
            List<String> values = split(composition.text(overridePath, ""));
            checkList(name, key, values, vocabulary, roles);
            return new ListField(values, composition.leaves().get(overridePath).source());
        }
        if (node != null && node.has(key)) {
            List<String> values = strings(node.get(key));
            checkList(name, key, values, vocabulary, roles);
            return new ListField(values, "declared");
        }
        if (external != null && external.has(key)) {
            List<String> values = strings(external.get(key));
            checkList(name, key, values, vocabulary, roles);
            return new ListField(values, "external");
        }
        if (gm != null && !gm.isEmpty() && ("roles".equals(key) || "tags".equals(key))) {
            return new ListField(gm, "gm-default");
        }
        return new ListField(List.of(), "unset");
    }

    private static void checkList(String name, String key, List<String> values, TagVocabulary vocabulary, boolean roles)
            throws KitException {
        for (String value : values) {
            if (roles) {
                vocabulary.checkRole(value, "/instruments/" + name + "/" + key);
            } else if ("tags".equals(key)) {
                vocabulary.checkTag(value, "/instruments/" + name + "/" + key);
            }
        }
    }

    private static void checkClash(String name, List<String> values, TagVocabulary vocabulary) throws KitException {
        for (String value : values) {
            if (value.startsWith("tag:")) {
                vocabulary.checkTag(value.substring(4), "/instruments/" + name + "/clashWith");
            }
        }
    }

    private static Sourced<Register> register(String name, JsonNode node, JsonNode external, GmRow defaults,
                                              Composition composition) {
        String overridePath = "descriptors." + name + ".register";
        if (composition != null && hasPrefix(composition, overridePath)) {
            return new Sourced<>(readRegister(composition, overridePath, defaults), compositionSource(composition, overridePath));
        }
        if (node != null && node.has("register")) {
            return new Sourced<>(readRegister(node.get("register"), defaults), "declared");
        }
        if (external != null && external.has("register")) {
            return new Sourced<>(readRegister(external.get("register"), defaults), "external");
        }
        if (defaults != null) {
            return new Sourced<>(defaults.register, "gm-default");
        }
        return new Sourced<>(Register.UNSET, "unset");
    }

    private static Sourced<String> soundClass(String name, JsonNode node, JsonNode external, GmRow defaults,
                                              Composition composition, TagVocabulary vocabulary) throws KitException {
        String overridePath = "descriptors." + name + ".class";
        if (composition != null && composition.leaves().containsKey(overridePath)) {
            String value = vocabulary.canonicalClass(composition.text(overridePath, ""), "/instruments/" + name + "/class");
            return new Sourced<>(value, composition.leaves().get(overridePath).source());
        }
        String declared = node == null ? "" : node.path("class").asText("");
        if (!declared.isBlank()) {
            return new Sourced<>(vocabulary.canonicalClass(declared, "/instruments/" + name + "/class"), "declared");
        }
        if (external != null && !external.path("class").asText("").isBlank()) {
            return new Sourced<>(vocabulary.canonicalClass(external.path("class").asText(), "/instruments/" + name + "/class"),
                    "external");
        }
        if (defaults != null && defaults.soundClass != null) {
            return new Sourced<>(defaults.soundClass, "gm-default");
        }
        return new Sourced<>("", "unset");
    }

    private static Sourced<Double> number(String key, String name, JsonNode node, JsonNode external, Composition composition) {
        String overridePath = "descriptors." + name + "." + key;
        if (composition != null && composition.leaves().containsKey(overridePath)) {
            return new Sourced<>(parse(composition.text(overridePath, "0")), composition.leaves().get(overridePath).source());
        }
        if (node != null && node.has(key)) {
            return new Sourced<>(node.path(key).asDouble(0), "declared");
        }
        if (external != null && external.has(key)) {
            return new Sourced<>(external.path(key).asDouble(0), "external");
        }
        return new Sourced<>(0.0, "unset");
    }

    private static Sourced<Boolean> flag(String key, String name, JsonNode node, JsonNode external, Composition composition) {
        String overridePath = "descriptors." + name + "." + key;
        if (composition != null && composition.leaves().containsKey(overridePath)) {
            return new Sourced<>(Boolean.parseBoolean(composition.text(overridePath, "false")),
                    composition.leaves().get(overridePath).source());
        }
        if (node != null && node.has(key)) {
            return new Sourced<>(node.path(key).asBoolean(false), "declared");
        }
        if (external != null && external.has(key)) {
            return new Sourced<>(external.path(key).asBoolean(false), "external");
        }
        return new Sourced<>(false, "unset");
    }

    private static Sourced<String> text(String key, String name, JsonNode node, JsonNode external, Composition composition) {
        String overridePath = "descriptors." + name + "." + key;
        if (composition != null && composition.leaves().containsKey(overridePath)) {
            return new Sourced<>(composition.text(overridePath, ""), composition.leaves().get(overridePath).source());
        }
        if (node != null && node.has(key)) {
            return new Sourced<>(node.path(key).asText(""), "declared");
        }
        if (external != null && external.has(key)) {
            return new Sourced<>(external.path(key).asText(""), "external");
        }
        return new Sourced<>("", "unset");
    }

    private static Register readRegister(JsonNode node, GmRow defaults) {
        Register base = defaults == null ? Register.UNSET : defaults.register;
        int low = node.has("low") ? node.path("low").asInt() : base.low();
        int high = node.has("high") ? node.path("high").asInt() : base.high();
        int sweetLow = node.has("sweetLow") ? node.path("sweetLow").asInt() : (node.has("low") ? low : base.sweetLow());
        int sweetHigh = node.has("sweetHigh") ? node.path("sweetHigh").asInt() : (node.has("high") ? high : base.sweetHigh());
        return new Register(low, high, sweetLow, sweetHigh);
    }

    private static Register readRegister(Composition composition, String prefix, GmRow defaults) {
        Register base = defaults == null ? Register.UNSET : defaults.register;
        int low = corner(composition, prefix + ".low", base.low());
        int high = corner(composition, prefix + ".high", base.high());
        int sweetLow = corner(composition, prefix + ".sweetLow", low);
        int sweetHigh = corner(composition, prefix + ".sweetHigh", high);
        return new Register(low, high, sweetLow, sweetHigh);
    }

    private static int corner(Composition composition, String path, int fallback) {
        if (!composition.leaves().containsKey(path)) {
            return fallback;
        }
        return (int) parse(composition.text(path, Integer.toString(fallback)));
    }

    private static boolean hasPrefix(Composition composition, String prefix) {
        if (composition.leaves().containsKey(prefix)) {
            return true;
        }
        for (String path : composition.leaves().keySet()) {
            if (path.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
    }

    private static String compositionSource(Composition composition, String prefix) {
        if (composition.leaves().containsKey(prefix)) {
            return composition.leaves().get(prefix).source();
        }
        for (var entry : composition.leaves().entrySet()) {
            if (entry.getKey().startsWith(prefix + ".")) {
                return entry.getValue().source();
            }
        }
        return "pin";
    }

    private static Map<String, JsonNode> declaredNodes(Path file) throws IOException, KitException {
        Map<String, JsonNode> nodes = new LinkedHashMap<>();
        readDeclared(file, nodes, new ArrayList<>());
        return nodes;
    }

    private static void readDeclared(Path file, Map<String, JsonNode> nodes, List<String> stack)
            throws IOException, KitException {
        if (!Files.isRegularFile(file)) {
            return;
        }
        String id = file.toAbsolutePath().normalize().toString();
        if (stack.contains(id)) {
            return;
        }
        stack.add(id);
        JsonNode root = YAML.readTree(Files.readString(file, StandardCharsets.UTF_8));
        String parent = root.path("extends").asText("");
        if (!parent.isBlank()) {
            Path beside = file.getParent().resolve(parent + ".yaml");
            Path parentFile = Files.isRegularFile(beside) ? beside : Kits.find(parent);
            readDeclared(parentFile, nodes, stack);
        }
        JsonNode instruments = root.path("instruments");
        if (instruments.isObject()) {
            instruments.fields().forEachRemaining(entry -> nodes.put(entry.getKey(), entry.getValue()));
        }
        stack.remove(stack.size() - 1);
    }

    private static Map<String, String> hashes(Kit kit) {
        Map<String, String> hashes = new LinkedHashMap<>();
        for (Kit.Source source : kit.sources()) {
            hashes.put(source.key(), source.sha256() == null ? "" : source.sha256());
        }
        return hashes;
    }

    private static List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || node.isNull()) {
            return values;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String text = item.asText("").trim().toLowerCase(Locale.ROOT);
                if (!text.isBlank()) {
                    values.add(text);
                }
            }
            return values;
        }
        return split(node.asText(""));
    }

    private static List<String> split(String text) {
        List<String> values = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return values;
        }
        for (String part : text.split(",")) {
            String token = part.trim().toLowerCase(Locale.ROOT);
            if (!token.isBlank()) {
                values.add(token);
            }
        }
        return values;
    }

    private static double parse(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static GmTable gm() throws IOException, KitException {
        if (gm != null) {
            return gm;
        }
        Path file = Path.of("presets", "gm-defaults.yaml");
        if (!Files.isRegularFile(file)) {
            throw new KitException(new KitException.Issue(
                    "NOT_FOUND", "presets/gm-defaults.yaml", "GM defaults not found",
                    "Keep presets/gm-defaults.yaml in the repo."));
        }
        JsonNode root = YAML.readTree(Files.readString(file, StandardCharsets.UTF_8));
        GmRow[] programs = new GmRow[128];
        for (JsonNode group : root.path("groups")) {
            int low = group.path("range").path(0).asInt(0);
            int high = group.path("range").path(1).asInt(low);
            GmRow row = gmRow(group);
            for (int program = Math.max(0, low); program <= Math.min(127, high); program++) {
                programs[program] = row;
            }
        }
        gm = new GmTable(programs, gmRow(root.path("drums")));
        return gm;
    }

    private static GmRow gmRow(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return new GmRow(List.of("perc"), List.of("percussive"), "unpitched-perc",
                    new Register(35, 81, 36, 60));
        }
        JsonNode register = node.path("register");
        return new GmRow(
                strings(node.get("roles")),
                strings(node.get("tags")),
                node.path("class").asText("synth"),
                new Register(
                        register.path("low").asInt(0),
                        register.path("high").asInt(127),
                        register.path("sweetLow").asInt(register.path("low").asInt(0)),
                        register.path("sweetHigh").asInt(register.path("high").asInt(127))));
    }

    private static Map<String, JsonNode> external() throws IOException {
        if (!externalRead) {
            external = ExternalProfiles.load();
            externalRead = true;
        }
        return external;
    }

    private record ListField(List<String> values, String source) {
    }

    private record Sourced<T>(T value, String source) {
    }

    private record GmRow(List<String> roles, List<String> tags, String soundClass, Register register) {
    }

    private record GmTable(GmRow[] programs, GmRow drums) {
        GmRow byProgram(int program) {
            if (program < 0 || program >= programs.length) {
                return null;
            }
            return programs[program];
        }
    }

    static void forget() {
        gm = null;
        external = Map.of();
        externalRead = false;
    }
}
