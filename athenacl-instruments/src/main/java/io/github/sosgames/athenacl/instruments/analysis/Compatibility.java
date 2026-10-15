// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.sosgames.athenacl.adaptive.Composition;
import io.github.sosgames.athenacl.adaptive.Resolver;
import io.github.sosgames.athenacl.events.PitchName;
import io.github.sosgames.athenacl.instruments.Kit;
import io.github.sosgames.athenacl.instruments.KitException;
import io.github.sosgames.athenacl.instruments.Kits;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Checks a resolved state against a kit profile. It does not render audio.
 * A bad instrument, a poor role fit, or two sustained parts that cover each other
 * become a finding with the layer that chose the instrument.
 */
public final class Compatibility {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final double MASK_WARN = 0.7;
    private static final double FIT_WARN = 0.5;

    private Compatibility() {
    }

    public record Finding(
            String code,
            String severity,
            String state,
            String roles,
            String instruments,
            String path,
            String from,
            String fix,
            String detail) {
        public String line() {
            StringBuilder sb = new StringBuilder();
            sb.append("finding=").append(code);
            sb.append(" severity=").append(severity);
            if (!state.isBlank()) {
                sb.append(" state=").append(state);
            }
            if (!roles.isBlank()) {
                sb.append(" roles=").append(roles);
            }
            if (!instruments.isBlank()) {
                sb.append(" instruments=").append(instruments);
            }
            if (!path.isBlank()) {
                sb.append(" path=").append(path);
            }
            if (!from.isBlank()) {
                sb.append(" from=").append(from);
            }
            if (!fix.isBlank()) {
                sb.append(" fix=").append(fix);
            }
            if (!detail.isBlank()) {
                sb.append(' ').append(detail);
            }
            return sb.toString();
        }
    }

    public record Lint(List<Finding> findings, boolean skipped, List<String> catalog) {
        public Lint(List<Finding> findings, boolean skipped) {
            this(findings, skipped, List.of());
        }

        public Lint {
            findings = List.copyOf(findings);
            catalog = List.copyOf(catalog);
        }

        public int errors() {
            int count = 0;
            for (Finding finding : findings) {
                if ("error".equals(finding.severity())) {
                    count++;
                }
            }
            return count;
        }

        public int warnings() {
            return findings.size() - errors();
        }

        public String text() {
            StringBuilder sb = new StringBuilder();
            for (String line : catalog) {
                sb.append(line).append('\n');
            }
            for (Finding finding : findings) {
                if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append('\n');
                }
                sb.append(finding.line()).append('\n');
            }
            sb.append("errors=").append(errors()).append(" warnings=").append(warnings());
            if (!catalog.isEmpty()) {
                sb.append(" instruments=").append(catalog.size());
            }
            if (skipped) {
                sb.append(" lint.skipped=no-profile");
            }
            return sb.toString();
        }
    }

    public record Pair(double roughness, double mask, String verdict) {
    }

    public static double fitness(String role, InstrumentProfile.Row row) {
        String kind = roleOf(role);
        double score = switch (kind) {
            case "pad" -> pad(row);
            case "bass" -> bass(row);
            case "arp" -> arp(row);
            case "perc" -> perc(row);
            default -> lead(row);
        };
        if ("bad".equals(row.verdict())) {
            score = Math.min(score, 0.2);
        }
        return quantize(clamp(score));
    }

    public static Pair pair(InstrumentProfile.Row left, InstrumentProfile.Row right, String leftRange, String rightRange) {
        double registers = overlap(leftRange, rightRange);
        double spectral = left.name().equals(right.name())
                ? 1
                : 1.0 / (1.0 + Math.abs(left.centroidRatio() - right.centroidRatio()));
        double mask = quantize(clamp(registers * spectral));
        double roughness = quantize(Math.max(left.roughness(), right.roughness()));
        String verdict = mask >= MASK_WARN ? "clash" : "ok";
        return new Pair(roughness, mask, verdict);
    }

    public static Lint lintTheme(Path themeFile, String onlyState) throws IOException, KitException {
        if (!Files.isRegularFile(themeFile)) {
            throw new KitException(new KitException.Issue(
                    "NOT_FOUND", "argv[2]", "theme not found", "Pass a theme YAML file."));
        }
        JsonNode root = YAML.readTree(Files.readString(themeFile, StandardCharsets.UTF_8));
        String genre = root.path("genre").asText(root.path("preset").asText(""));
        if (genre.isBlank()) {
            throw new KitException(new KitException.Issue(
                    "INVALID_INPUT", "--genre", "theme has no genre",
                    "Set genre so the state can be resolved."));
        }
        JsonNode states = root.path("states");
        if (!states.isObject() || states.isEmpty()) {
            throw new KitException(new KitException.Issue(
                    "INVALID_INPUT", "/states", "theme has no states",
                    "Declare at least one state."));
        }
        if (onlyState != null && !onlyState.isBlank() && states.get(onlyState) == null) {
            throw new KitException(new KitException.Issue(
                    "NOT_FOUND", "--state", "unknown state " + onlyState,
                    "Use a state declared in the theme."));
        }
        String kitId = kitId(root);
        Path kitFile = Kits.find(kitId);
        if (!java.nio.file.Files.isRegularFile(kitFile)) {
            return new Lint(List.of(), true);
        }
        Kit kit = Kits.load(kitFile);
        InstrumentProfile.Report profile = InstrumentProfile.analyze(kitFile, null);
        List<String> stateIds = new ArrayList<>();
        if (onlyState != null && !onlyState.isBlank()) {
            stateIds.add(onlyState);
        } else {
            states.fieldNames().forEachRemaining(stateIds::add);
        }
        List<Finding> findings = new ArrayList<>();
        for (String stateId : stateIds) {
            Composition composition = Resolver.resolveTheme(genre, stateId, themeFile);
            if (composition == null) {
                throw new KitException(new KitException.Issue(
                        "NOT_FOUND", "--genre", "unknown genre " + genre,
                        "Use a genre such as fantasy."));
            }
            findings.addAll(lintState(stateId, composition, kit, profile));
        }
        return new Lint(findings, false);
    }

    private static List<Finding> lintState(String stateId, Composition composition, Kit kit,
                                            InstrumentProfile.Report profile) {
        List<Finding> findings = new ArrayList<>();
        List<String> layers = composition.list("layers");
        if (layers.isEmpty()) {
            layers = List.of("pad");
        }
        List<Played> played = new ArrayList<>();
        for (String layer : layers) {
            String name = composition.text("instruments." + layer, layer);
            InstrumentProfile.Row row = find(profile, name);
            String from = layerName(composition.source("instruments." + layer));
            String path = "/states/" + stateId + "/instruments/" + layer;
            if (row == null) {
                findings.add(new Finding("INSTR_BAD", "error", stateId, layer, name, path, from,
                        "try " + layer + "=" + best(profile, roleOf(layer), ""), ""));
                continue;
            }
            played.add(new Played(layer, name, row, from, path));
            if ("bad".equals(row.verdict())) {
                findings.add(new Finding("INSTR_BAD", "error", stateId, layer, name, path, from,
                        "try " + layer + "=" + best(profile, roleOf(layer), name),
                        "reasons=" + String.join(",", row.reasons())));
                continue;
            }
            String register = composition.text(layer + ".register", "");
            if (!register.isBlank() && !covers(kit, name, register)) {
                findings.add(new Finding("INSTR_RANGE", "warn", stateId, layer, name, path, from,
                        "try " + layer + "=" + best(profile, roleOf(layer), name),
                        "register=" + register.replace(",", "..")));
            }
            double fit = fitness(roleOf(layer), row);
            if (fit < FIT_WARN) {
                findings.add(new Finding("ROLE_FIT_LOW", "warn", stateId, layer, name, path, from,
                        "try " + layer + "=" + best(profile, roleOf(layer), name),
                        "fitness=" + num(fit)));
            }
        }
        for (int i = 0; i < played.size(); i++) {
            for (int j = i + 1; j < played.size(); j++) {
                Played a = played.get(i);
                Played b = played.get(j);
                if (!sustained(a.layer) || !sustained(b.layer)) {
                    continue;
                }
                if (!"lead".equals(a.layer) && !"lead".equals(b.layer)) {
                    continue;
                }
                String rangeA = compositionRange(composition, a.layer, kit, a.name);
                String rangeB = compositionRange(composition, b.layer, kit, b.name);
                Pair pair = pair(a.row, b.row, rangeA, rangeB);
                if (pair.mask >= MASK_WARN) {
                    findings.add(new Finding("PAIR_MASK", "warn", stateId, a.layer + "," + b.layer,
                            a.name + "," + b.name, "/states/" + stateId + "/instruments", a.from,
                            "try lead=" + best(profile, "lead", "lead".equals(a.layer) ? a.name : b.name),
                            "mask=" + num(pair.mask)));
                }
            }
        }
        return findings;
    }

    private static String compositionRange(Composition composition, String layer, Kit kit, String name) {
        String register = composition.text(layer + ".register", "");
        if (!register.isBlank()) {
            return register;
        }
        Kit.Instrument instrument = kit.instrument(name);
        if (instrument != null && instrument.keyLow() != null && instrument.keyHigh() != null) {
            return instrument.keyLow() + ".." + instrument.keyHigh();
        }
        return "C4..C5";
    }

    private static boolean covers(Kit kit, String name, String register) {
        Kit.Instrument instrument = kit.instrument(name);
        if (instrument == null || instrument.keyLow() == null || instrument.keyHigh() == null) {
            return true;
        }
        int[] role = span(register);
        int low = PitchName.midi(instrument.keyLow());
        int high = PitchName.midi(instrument.keyHigh());
        return role[0] >= low && role[1] <= high;
    }

    public static String best(InstrumentProfile.Report profile, String role, String except) {
        String winner = "";
        double best = -1;
        for (InstrumentProfile.Row row : profile.rows()) {
            if (row.name().equals(except) || "bad".equals(row.verdict())) {
                continue;
            }
            double score = fitness(role, row);
            if (score > best || (score == best && (winner.isEmpty() || row.name().compareTo(winner) < 0))) {
                best = score;
                winner = row.name();
            }
        }
        return winner.isEmpty() ? "saw" : winner;
    }

    private static InstrumentProfile.Row find(InstrumentProfile.Report profile, String name) {
        for (InstrumentProfile.Row row : profile.rows()) {
            if (row.name().equals(name)) {
                return row;
            }
        }
        return null;
    }

    static String roleOf(String layer) {
        return switch (layer) {
            case "drums", "taiko", "perc" -> "perc";
            case "chords", "brass", "strings" -> "pad";
            case "harp" -> "arp";
            default -> layer;
        };
    }

    private static boolean sustained(String layer) {
        return switch (layer) {
            case "lead", "pad", "bass", "brass", "chords", "strings" -> true;
            default -> false;
        };
    }

    private static double lead(InstrumentProfile.Row row) {
        double pitch = slope(Math.abs(row.pitchErrorCents()), 5, 30);
        double bright = row.centroidRatio() >= 2 && row.centroidRatio() <= 8 ? 1
                : row.centroidRatio() <= 0 ? 0.4 : clamp(row.centroidRatio() / 2.0);
        double attack = row.attackMs() <= 120 ? 0.85 : 0.45;
        return 0.55 * pitch + 0.25 * bright + 0.20 * attack;
    }

    private static double pad(InstrumentProfile.Row row) {
        double attack = row.attackMs() >= 80 ? 1 : clamp(row.attackMs() / 80.0);
        double sustain = row.died() ? 0 : 1;
        double quiet = row.flatness() >= 0.35 || row.tags().contains("noisy") ? 0 : 1;
        return 0.45 * attack + 0.45 * sustain + 0.10 * quiet;
    }

    private static double bass(InstrumentProfile.Row row) {
        double clear = row.reasons().contains("inharmonicity") ? 0.2 : 0.8;
        double body = row.centroidRatio() > 0 && row.centroidRatio() < 3 ? 1 : 0.4;
        return 0.5 * clear + 0.5 * body;
    }

    private static double arp(InstrumentProfile.Row row) {
        double attack = row.attackMs() <= 40 ? 1 : row.attackMs() >= 200 ? 0 : (200 - row.attackMs()) / 160.0;
        double decay = row.died() ? 1 : 0.15;
        double tag = row.tags().contains("plucky") ? 1 : 0;
        return 0.4 * attack + 0.4 * decay + 0.2 * tag;
    }

    private static double perc(InstrumentProfile.Row row) {
        if (row.tags().contains("noisy") || row.tags().contains("plucky")) {
            return 0.8;
        }
        return 0.3;
    }

    private static double slope(double value, double good, double bad) {
        if (value <= good) {
            return 1;
        }
        if (value >= bad) {
            return 0;
        }
        return (bad - value) / (bad - good);
    }

    static double overlap(String left, String right) {
        int[] a = span(left);
        int[] b = span(right);
        int start = Math.max(a[0], b[0]);
        int end = Math.min(a[1], b[1]);
        if (end < start) {
            return 0;
        }
        int shared = end - start + 1;
        int smaller = Math.min(a[1] - a[0] + 1, b[1] - b[0] + 1);
        return smaller <= 0 ? 0 : Math.min(1, shared / (double) smaller);
    }

    private static int[] span(String text) {
        String raw = text == null ? "" : text.trim();
        String low;
        String high;
        if (raw.contains("..")) {
            String[] parts = raw.split("\\.\\.", 2);
            low = parts[0];
            high = parts.length > 1 ? parts[1] : parts[0];
        } else if (raw.contains(",")) {
            String[] parts = raw.split(",", 2);
            low = parts[0];
            high = parts.length > 1 ? parts[1] : parts[0];
        } else if (raw.isBlank()) {
            low = "C4";
            high = "C5";
        } else {
            low = raw;
            high = raw;
        }
        int a = PitchName.midi(low.trim());
        int b = PitchName.midi(high.trim());
        return a <= b ? new int[] {a, b} : new int[] {b, a};
    }

    static String layerName(String source) {
        if (source == null || source.isBlank()) {
            return "";
        }
        if (source.startsWith("theme:")) {
            String path = source.substring("theme:".length()).replace('\\', '/');
            int slash = path.lastIndexOf('/');
            return "theme:" + (slash >= 0 ? path.substring(slash + 1) : path);
        }
        return source;
    }

    private static String kitId(JsonNode root) {
        JsonNode kits = root.path("instruments").path("kits");
        if (kits.isArray() && !kits.isEmpty()) {
            return kits.get(0).asText("gm-lite");
        }
        return "gm-lite";
    }

    private static double clamp(double value) {
        if (value < 0) {
            return 0;
        }
        return Math.min(1, value);
    }

    private static double quantize(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public static String num(double value) {
        return String.format(Locale.US, "%.2f", quantize(value));
    }

    private record Played(String layer, String name, InstrumentProfile.Row row, String from, String path) {
    }
}
