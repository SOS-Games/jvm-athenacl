// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import io.github.sosgames.athenacl.core.LayerSource;
import io.github.sosgames.athenacl.events.DefaultRandom;
import io.github.sosgames.athenacl.events.RandomSource;
import io.github.sosgames.athenacl.theory.Key;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a merged theme into concrete values from the seed.
 * A genre can offer a choice, a range, a probability, or a generator
 * (a Markov chain, a grammar, a sieve, or an athenaCL parameter object).
 * The same seed and section always pick the same value. A pin replaces that
 * pick. With no seed, each field keeps the choice marked {@code fallback}.
 */
public final class Sampler {
    /** The only section this layer plays. Later layers add more. */
    public static final String SECTION = "s1";

    private static final int ATTEMPTS = 24;
    private static final Map<String, Map<String, List<String>>> MODELS = Map.of(
            "minor-functional", Map.of(
                    "i", List.of("VI", "iv", "v", "VII", "III"),
                    "VI", List.of("iv", "VII", "i", "III"),
                    "iv", List.of("v", "VII", "i", "VI"),
                    "v", List.of("i", "VI", "VII"),
                    "V", List.of("i", "VI"),
                    "VII", List.of("i", "VI", "III"),
                    "III", List.of("VI", "iv", "i")),
            "major-functional", Map.of(
                    "I", List.of("vi", "IV", "V", "ii"),
                    "vi", List.of("IV", "ii", "V", "I"),
                    "IV", List.of("V", "I", "ii", "vi"),
                    "V", List.of("I", "vi"),
                    "ii", List.of("V", "vi", "I")),
            "modal-phrygian", Map.of(
                    "i", List.of("bII", "bIII", "V", "VII"),
                    "bII", List.of("i", "V", "bIII"),
                    "bIII", List.of("bII", "i", "V"),
                    "V", List.of("i", "bII"),
                    "VII", List.of("i", "bII")));

    private Sampler() {
    }

    /** The most likely value of each distribution, leaving the distribution in place. */
    public static Composition materialize(Composition composition) {
        Map<String, Composition.Leaf> next = new LinkedHashMap<>(composition.leaves());
        for (String root : roots(next)) {
            if (next.containsKey(root)) {
                continue;
            }
            Drawn drawn = mode(next, root);
            if (drawn == null) {
                continue;
            }
            next.put(root, drawn.leaf());
        }
        return Composition.of(next, composition.chain());
    }

    /** Merge is already done. Sample, then let a pin replace any field. */
    public static Composition sample(Composition composition, long seed, String section) {
        String part = section == null || section.isBlank() ? SECTION : section;
        Map<String, Composition.Leaf> next = new LinkedHashMap<>(composition.leaves());
        boolean sampledTempo = false;
        String tempoDetail = "";
        String tempoSource = "";
        String tempoDistribution = "";
        for (String root : roots(next)) {
            if ("tempo.bpm".equals(root)) {
                Drawn drawn = draw(next, root, seed, part);
                if (drawn == null) {
                    continue;
                }
                sampledTempo = true;
                tempoDetail = drawn.leaf().detail();
                tempoSource = drawn.leaf().source();
                tempoDistribution = drawn.leaf().distribution();
                next.put(root, drawn.leaf());
                dropChildren(next, root);
                continue;
            }
            Drawn drawn = draw(next, root, seed, part);
            if (drawn == null) {
                continue;
            }
            next.put(root, drawn.leaf());
            dropChildren(next, root);
        }
        if (sampledTempo && next.containsKey("tempo.relative")) {
            Composition.Leaf relative = next.get("tempo.relative");
            int bpm = (int) Math.round(parseDouble(next.get("tempo.bpm").value(), 120) * parseDouble(relative.value(), 1));
            String detail = tempoDetail + "*" + relative.source() + " relative=" + relative.value();
            next.put("tempo.bpm", new Composition.Leaf(Integer.toString(bpm), tempoSource, "sampled", detail,
                    seedPath(seed, part, "tempo.bpm"), tempoDistribution));
            next.remove("tempo.relative");
        }
        applyPins(next);
        return Composition.of(next, composition.chain());
    }

    /** Each state's key and tempo follow the sampled document. Layers stay as declared. */
    public static Theme apply(Theme theme, long seed) {
        Map<String, Theme.StateDef> states = new LinkedHashMap<>();
        for (Map.Entry<String, Theme.StateDef> entry : theme.states().entrySet()) {
            states.put(entry.getKey(), apply(entry.getValue(), seed));
        }
        return theme.withStates(states);
    }

    private static Theme.StateDef apply(Theme.StateDef state, long seed) {
        if (state.composition() == null) {
            return state;
        }
        Composition sampled = sample(state.composition(), seed, SECTION);
        String tonic = sampled.text("scale.tonic", "C");
        String mode = sampled.text("scale.mode", "aeolian");
        Key key = Key.parse(tonic + " " + mode);
        int tempo = sampled.number("tempo.bpm", state.tempo());
        return new Theme.StateDef(state.id(), key, tempo, state.layers(), state.stinger(),
                state.programs(), state.instruments(), state.meter(), sampled, state.specs());
    }

    private static void applyPins(Map<String, Composition.Leaf> leaves) {
        List<String> pins = new ArrayList<>();
        for (String key : leaves.keySet()) {
            if (key.startsWith("pin.")) {
                pins.add(key);
            }
        }
        for (String key : pins) {
            Composition.Leaf pin = leaves.get(key);
            String target = key.substring("pin.".length());
            leaves.put(target, new Composition.Leaf(pin.value(), pin.source(), "pin", "", "", ""));
            dropChildren(leaves, target);
        }
        leaves.keySet().removeIf(key -> key.equals("pin") || key.startsWith("pin."));
    }

    private static List<String> roots(Map<String, Composition.Leaf> leaves) {
        Set<String> found = new LinkedHashSet<>();
        for (String key : leaves.keySet()) {
            if (key.startsWith("pin.")) {
                continue;
            }
            for (String suffix : List.of(".choose", ".range", ".p", ".generate")) {
                if (key.endsWith(suffix)) {
                    found.add(key.substring(0, key.length() - suffix.length()));
                }
            }
        }
        return List.copyOf(found);
    }

    private static void dropChildren(Map<String, Composition.Leaf> leaves, String root) {
        String prefix = root + ".";
        leaves.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /** The fallback, or the heaviest choice, kept so a render without a seed stays put. */
    private static Drawn mode(Map<String, Composition.Leaf> leaves, String root) {
        if (leaves.containsKey(root + ".fallback")) {
            return new Drawn(fixed(leaves, root, valueOf(leaves, root, leaves.get(root + ".fallback").value())));
        }
        if (leaves.containsKey(root + ".choose")) {
            List<String> options = split(leaves.get(root + ".choose").value());
            List<Integer> weights = weights(leaves, root, options.size());
            int best = 0;
            for (int i = 1; i < options.size(); i++) {
                if (weights.get(i) > weights.get(best)) {
                    best = i;
                }
            }
            return new Drawn(fixed(leaves, root, options.get(best)));
        }
        if (leaves.containsKey(root + ".range")) {
            int[] range = range(leaves, root);
            int shown = shownNumber(leaves, root, Integer.toString(range[0]));
            return new Drawn(fixed(leaves, root, Integer.toString(shown)));
        }
        if (leaves.containsKey(root + ".p")) {
            double p = parseDouble(leaves.get(root + ".p").value(), 0);
            return new Drawn(fixed(leaves, root, p >= 0.5 ? "true" : "false"));
        }
        return null;
    }

    private static String valueOf(Map<String, Composition.Leaf> leaves, String root, String fallback) {
        if (!"tempo.bpm".equals(root) || !leaves.containsKey("tempo.relative")) {
            return fallback;
        }
        if (!leaves.containsKey(root + ".range") && !leaves.containsKey(root + ".choose")) {
            return fallback;
        }
        return Integer.toString(shownNumber(leaves, root, fallback));
    }

    private static int shownNumber(Map<String, Composition.Leaf> leaves, String root, String fallback) {
        double value = parseDouble(fallback, 0);
        if ("tempo.bpm".equals(root) && leaves.containsKey("tempo.relative")
                && (leaves.containsKey(root + ".range") || leaves.containsKey(root + ".choose"))) {
            value = value * parseDouble(leaves.get("tempo.relative").value(), 1);
        }
        return (int) Math.round(value);
    }

    private static Composition.Leaf fixed(Map<String, Composition.Leaf> leaves, String root, String value) {
        return new Composition.Leaf(value, sourceOf(leaves, root), "fixed", "", "", describe(leaves, root));
    }

    private static Drawn draw(Map<String, Composition.Leaf> leaves, String root, long seed, String section) {
        RandomSource rng = DefaultRandom.of(seed).fork(section.hashCode(), mix(root));
        String path = seedPath(seed, section, root);
        String distribution = describe(leaves, root);
        if (leaves.containsKey(root + ".choose")) {
            List<String> options = split(leaves.get(root + ".choose").value());
            if (options.isEmpty()) {
                return null;
            }
            List<Integer> weights = weights(leaves, root, options.size());
            int total = 0;
            for (int weight : weights) {
                total += weight;
            }
            int draw = rng.nextInt(Math.max(total, 1));
            int cursor = 0;
            String picked = options.get(0);
            for (int i = 0; i < options.size(); i++) {
                cursor += weights.get(i);
                if (draw < cursor) {
                    picked = options.get(i);
                    break;
                }
            }
            return new Drawn(new Composition.Leaf(picked, sourceOf(leaves, root), "sampled", "choose", path, distribution));
        }
        if (leaves.containsKey(root + ".range")) {
            int[] bounds = range(leaves, root);
            int count = (bounds[1] - bounds[0]) / bounds[2] + 1;
            int index = rng.nextInt(Math.max(count, 1));
            int picked = bounds[0] + index * bounds[2];
            String detail = "range[" + bounds[0] + "," + bounds[1] + "]";
            return new Drawn(new Composition.Leaf(Integer.toString(picked), sourceOf(leaves, root), "sampled",
                    detail, path, distribution));
        }
        if (leaves.containsKey(root + ".p")) {
            double p = parseDouble(leaves.get(root + ".p").value(), 0);
            boolean yes = rng.nextDouble() < p;
            return new Drawn(new Composition.Leaf(yes ? "true" : "false", sourceOf(leaves, root), "sampled",
                    "p", path, distribution));
        }
        if (leaves.containsKey(root + ".generate")) {
            String kind = leaves.get(root + ".generate").value();
            String value = switch (kind) {
                case "markov" -> markov(leaves, root, rng);
                case "grammar" -> grammar(leaves, root, rng);
                case "sieve" -> sieve(leaves, root);
                case "po" -> po(leaves, root, seed, section);
                default -> fallback(leaves, root);
            };
            if (value.isBlank()) {
                return null;
            }
            String detail = generatorDetail(leaves, root, kind);
            return new Drawn(new Composition.Leaf(value, sourceOf(leaves, root), "generated", detail, path, distribution));
        }
        return null;
    }

    private static String markov(Map<String, Composition.Leaf> leaves, String root, RandomSource rng) {
        String modelName = text(leaves, root + ".model", "minor-functional");
        Map<String, List<String>> model = MODELS.getOrDefault(modelName, MODELS.get("minor-functional"));
        int length = (int) parseDouble(text(leaves, root + ".length", "8"), 8);
        length = Math.max(length, 1);
        String start = text(leaves, root + ".constraints.startOn", "");
        if (start.isBlank()) {
            start = first(fallback(leaves, root), "i");
        }
        List<String> ends = split(text(leaves, root + ".constraints.endOn", start));
        if (ends.isEmpty()) {
            ends = List.of(start);
        }
        Map<Integer, String> cadences = cadences(leaves, root);
        int least = length >= 3 ? 3 : 1;
        List<String> last = List.of();
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            RandomSource draw = rng.fork(attempt);
            List<String> chords = walk(model, length, start, ends, cadences, draw);
            last = chords;
            if (distinct(chords) >= least && accepts(chords, start, ends, cadences)) {
                return String.join(",", chords);
            }
        }
        String safe = fallback(leaves, root);
        if (!safe.isBlank()) {
            return safe;
        }
        return String.join(",", last);
    }

    private static List<String> walk(Map<String, List<String>> model, int length, String start, List<String> ends,
                                     Map<Integer, String> cadences, RandomSource rng) {
        List<String> chords = new ArrayList<>();
        String previous = start;
        for (int index = 0; index < length; index++) {
            int bar = index + 1;
            String chord;
            if (index == 0) {
                chord = start;
            } else if (cadences.containsKey(bar)) {
                chord = cadenceChord(cadences.get(bar), start);
            } else if (index == length - 1) {
                chord = ends.get(0);
            } else {
                chord = next(model, previous, rng);
            }
            chords.add(chord);
            previous = chord;
        }
        return chords;
    }

    private static String next(Map<String, List<String>> model, String previous, RandomSource rng) {
        List<String> options = model.get(previous);
        if (options == null || options.isEmpty()) {
            options = model.values().iterator().next();
        }
        return options.get(rng.nextInt(options.size()));
    }

    private static boolean accepts(List<String> chords, String start, List<String> ends, Map<Integer, String> cadences) {
        if (chords.isEmpty() || !chords.get(0).equals(start)) {
            return false;
        }
        if (!ends.contains(chords.get(chords.size() - 1))) {
            return false;
        }
        for (Map.Entry<Integer, String> cadence : cadences.entrySet()) {
            int index = cadence.getKey() - 1;
            if (index < 0 || index >= chords.size()) {
                continue;
            }
            if (!chords.get(index).equals(cadenceChord(cadence.getValue(), start))) {
                return false;
            }
        }
        return true;
    }

    private static String cadenceChord(String name, String tonic) {
        return switch (name) {
            case "half" -> "V";
            case "authentic" -> tonic;
            case "plagal" -> tonic.equals(tonic.toLowerCase()) ? "iv" : "IV";
            default -> name;
        };
    }

    private static Map<Integer, String> cadences(Map<String, Composition.Leaf> leaves, String root) {
        Map<Integer, String> marks = new LinkedHashMap<>();
        String prefix = root + ".constraints.cadenceAt.";
        for (Map.Entry<String, Composition.Leaf> entry : leaves.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) {
                continue;
            }
            String index = entry.getKey().substring(prefix.length());
            if (!index.isEmpty() && index.chars().allMatch(Character::isDigit)) {
                marks.put(Integer.parseInt(index), entry.getValue().value());
            }
        }
        return marks;
    }

    private static String grammar(Map<String, Composition.Leaf> leaves, String root, RandomSource rng) {
        List<String> templates = new ArrayList<>();
        String raw = text(leaves, root + ".templates", "");
        if (!raw.isBlank()) {
            for (String part : raw.split("\\|")) {
                if (!part.isBlank()) {
                    templates.add(part.trim().replace(' ', ','));
                }
            }
        }
        if (templates.isEmpty()) {
            return fallback(leaves, root);
        }
        String start = text(leaves, root + ".constraints.startOn", "");
        List<String> ends = split(text(leaves, root + ".constraints.endOn", ""));
        List<String> valid = new ArrayList<>();
        for (String template : templates) {
            List<String> chords = split(template);
            if (chords.isEmpty()) {
                continue;
            }
            if (!start.isBlank() && !chords.get(0).equals(start)) {
                continue;
            }
            if (!ends.isEmpty() && !ends.contains(chords.get(chords.size() - 1))) {
                continue;
            }
            valid.add(template);
        }
        List<String> pool = valid.isEmpty() ? templates : valid;
        return pool.get(rng.nextInt(pool.size()));
    }

    private static String sieve(Map<String, Composition.Leaf> leaves, String root) {
        String logic = text(leaves, root + ".sieve", "");
        int length = (int) parseDouble(text(leaves, root + ".length", "8"), 8);
        if (logic.isBlank() || length < 1) {
            return fallback(leaves, root);
        }
        List<Integer> hits = LayerSource.sieve("sieve," + logic, 0, length - 1);
        List<String> text = new ArrayList<>();
        for (int hit : hits) {
            text.add(Integer.toString(hit));
        }
        return text.isEmpty() ? fallback(leaves, root) : String.join(",", text);
    }

    private static String po(Map<String, Composition.Leaf> leaves, String root, long seed, String section) {
        String symbols = text(leaves, root + ".symbols", "");
        int count = (int) parseDouble(text(leaves, root + ".count", "3"), 3);
        count = Math.max(count, 1);
        if (!symbols.isBlank()) {
            List<String> names = split(symbols);
            if (names.isEmpty()) {
                return fallback(leaves, root);
            }
            RandomSource rng = DefaultRandom.of(seed).fork(section.hashCode(), mix(root), "po".hashCode());
            int start = rng.nextInt(names.size());
            List<String> picked = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                picked.add(names.get((start + i) % names.size()));
            }
            return String.join(",", picked);
        }
        String body = text(leaves, root + ".po", "");
        if (body.isBlank()) {
            return fallback(leaves, root);
        }
        long poSeed = Integer.toUnsignedLong(DefaultRandom.of(seed)
                .fork(section.hashCode(), mix(root), "po".hashCode())
                .nextInt(Integer.MAX_VALUE));
        try {
            if (body.startsWith("sieve")) {
                List<Integer> hits = LayerSource.sieve(body, 0, count - 1);
                List<String> text = new ArrayList<>();
                for (int hit : hits) {
                    text.add(Integer.toString(hit));
                }
                return String.join(",", text);
            }
            return String.join(",", LayerSource.rhythm(body, count, poSeed));
        } catch (IllegalArgumentException e) {
            return fallback(leaves, root);
        }
    }

    private static String generatorDetail(Map<String, Composition.Leaf> leaves, String root, String kind) {
        return switch (kind) {
            case "markov" -> "markov(" + text(leaves, root + ".model", "minor-functional") + ")";
            case "grammar" -> "grammar";
            case "sieve" -> "sieve(" + text(leaves, root + ".sieve", "") + ")";
            case "po" -> "po(" + text(leaves, root + ".po", "basketGen") + ")";
            default -> kind;
        };
    }

    private static String describe(Map<String, Composition.Leaf> leaves, String root) {
        if (leaves.containsKey(root + ".choose")) {
            return "choose:" + leaves.get(root + ".choose").value();
        }
        if (leaves.containsKey(root + ".range")) {
            return "range:" + leaves.get(root + ".range").value();
        }
        if (leaves.containsKey(root + ".p")) {
            return "p:" + leaves.get(root + ".p").value();
        }
        if (leaves.containsKey(root + ".generate")) {
            return "generate:" + generatorDetail(leaves, root, leaves.get(root + ".generate").value());
        }
        return "";
    }

    private static String sourceOf(Map<String, Composition.Leaf> leaves, String root) {
        for (String suffix : List.of(".choose", ".range", ".p", ".generate", ".fallback")) {
            Composition.Leaf leaf = leaves.get(root + suffix);
            if (leaf != null) {
                return leaf.source();
            }
        }
        return "";
    }

    private static String fallback(Map<String, Composition.Leaf> leaves, String root) {
        Composition.Leaf leaf = leaves.get(root + ".fallback");
        return leaf == null ? "" : leaf.value();
    }

    private static String text(Map<String, Composition.Leaf> leaves, String path, String fallback) {
        Composition.Leaf leaf = leaves.get(path);
        return leaf == null || leaf.value().isBlank() ? fallback : leaf.value();
    }

    private static List<String> split(String value) {
        List<String> parts = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return parts;
        }
        for (String part : value.split(",")) {
            if (!part.isBlank()) {
                parts.add(part.trim());
            }
        }
        return parts;
    }

    private static List<Integer> weights(Map<String, Composition.Leaf> leaves, String root, int count) {
        List<String> raw = split(text(leaves, root + ".weights", ""));
        List<Integer> weights = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int weight = 1;
            if (i < raw.size()) {
                weight = Math.max(1, (int) parseDouble(raw.get(i), 1));
            }
            weights.add(weight);
        }
        return weights;
    }

    /** {@code low, high, step}. The step stays positive and the high end is kept. */
    private static int[] range(Map<String, Composition.Leaf> leaves, String root) {
        List<String> raw = split(text(leaves, root + ".range", "0,1"));
        int low = raw.isEmpty() ? 0 : (int) parseDouble(raw.get(0), 0);
        int high = raw.size() < 2 ? low : (int) parseDouble(raw.get(1), low);
        if (high < low) {
            int swap = low;
            low = high;
            high = swap;
        }
        int step = (int) parseDouble(text(leaves, root + ".step", "1"), 1);
        if (step < 1) {
            step = 1;
        }
        return new int[] {low, high, step};
    }

    private static String first(String list, String fallback) {
        List<String> parts = split(list);
        return parts.isEmpty() ? fallback : parts.get(0);
    }

    private static int distinct(List<String> chords) {
        return new LinkedHashSet<>(chords).size();
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long mix(String text) {
        long mixed = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < text.length(); i++) {
            mixed = mixed * 31 + text.charAt(i);
        }
        return mixed;
    }

    static String seedPath(long seed, String section, String key) {
        return seed + "/" + section + "/" + key;
    }

    private record Drawn(Composition.Leaf leaf) {
    }
}
