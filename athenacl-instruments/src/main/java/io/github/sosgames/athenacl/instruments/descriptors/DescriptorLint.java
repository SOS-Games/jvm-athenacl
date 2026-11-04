// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.descriptors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.sosgames.athenacl.adaptive.Composition;
import io.github.sosgames.athenacl.adaptive.Resolver;
import io.github.sosgames.athenacl.instruments.KitException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rule lint for a resolved theme. It reports a pinned instrument marked avoid, a role with
 * nobody left, a declared clash, and two melodic parts whose sweet ranges sit on top of each
 * other. It does not measure audio.
 */
public final class DescriptorLint {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final List<String> STATES = List.of(
            "sneaking", "peaceful", "danger", "combat", "trading", "partying");

    private DescriptorLint() {
    }

    public record Finding(String code, String severity, String path, String source, String message, String fix) {
        public String line() {
            return "finding=" + code + " path=" + path + " source=" + source + " fix=" + fix;
        }
    }

    public record Report(List<Finding> findings) {
        public Report {
            findings = List.copyOf(findings);
        }

        public int errors() {
            int count = 0;
            for (Finding finding : findings) {
                if ("error".equals(finding.severity)) {
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
            sb.append("errors=").append(errors()).append(" warnings=").append(warnings());
            for (Finding finding : findings) {
                sb.append('\n').append(finding.line());
            }
            return sb.toString();
        }
    }

    public static Report theme(Path themeFile, String onlyState) throws IOException, KitException {
        if (!Files.isRegularFile(themeFile)) {
            throw new KitException(new KitException.Issue(
                    "NOT_FOUND", "argv[2]", "theme not found", "Pass a theme YAML file."));
        }
        JsonNode root = YAML.readTree(Files.readString(themeFile, StandardCharsets.UTF_8));
        String genre = root.path("genre").asText(root.path("preset").asText(""));
        if (genre.isBlank()) {
            throw new KitException(new KitException.Issue(
                    "INVALID_INPUT", "--genre", "theme has no genre", "Set genre so the state can be resolved."));
        }
        List<String> states = new ArrayList<>();
        JsonNode declared = root.path("states");
        if (onlyState != null && !onlyState.isBlank()) {
            states.add(onlyState);
        } else if (declared.isObject() && !declared.isEmpty()) {
            declared.fieldNames().forEachRemaining(states::add);
        } else {
            states.addAll(STATES);
        }
        List<Finding> findings = new ArrayList<>();
        for (String state : states) {
            Composition composition = Resolver.resolveTheme(genre, state, themeFile);
            if (composition == null) {
                continue;
            }
            findings.addAll(inspect(composition, genre, state, 1));
        }
        return new Report(findings);
    }

    public static Report theme(Path themeFile, String onlyState, String kitOverride, long seed)
            throws IOException, KitException {
        if (kitOverride == null || kitOverride.isBlank()) {
            return theme(themeFile, onlyState);
        }
        JsonNode root = YAML.readTree(Files.readString(themeFile, StandardCharsets.UTF_8));
        String genre = root.path("genre").asText(root.path("preset").asText(""));
        List<String> states = new ArrayList<>();
        if (onlyState != null && !onlyState.isBlank()) {
            states.add(onlyState);
        } else {
            JsonNode declared = root.path("states");
            if (declared.isObject()) {
                declared.fieldNames().forEachRemaining(states::add);
            }
        }
        List<Finding> findings = new ArrayList<>();
        for (String state : states) {
            Composition composition = Resolver.resolveTheme(genre, state, themeFile);
            if (composition == null) {
                continue;
            }
            findings.addAll(inspect(withKit(composition, kitOverride), genre, state, seed));
        }
        return new Report(findings);
    }

    public static Report genre(String genre, long seed, String kitOverride) throws IOException, KitException {
        List<Finding> findings = new ArrayList<>();
        for (String state : STATES) {
            Composition composition = Resolver.resolve(genre, state, "", null);
            if (composition == null) {
                throw new KitException(new KitException.Issue(
                        "NOT_FOUND", "--genre", "unknown genre " + genre, "Use a genre such as fantasy."));
            }
            if (kitOverride != null && !kitOverride.isBlank()) {
                composition = withKit(composition, kitOverride);
            }
            findings.addAll(inspect(composition, genre, state, seed));
        }
        return new Report(findings);
    }

    private static Composition withKit(Composition composition, String kit) throws IOException {
        return composition.merge(YAML.readTree("ensemble:\n  kits: [" + kit + "]\n"), "kit");
    }

    private static List<Finding> inspect(Composition composition, String genre, String state, long seed)
            throws IOException, KitException {
        List<Finding> findings = new ArrayList<>();
        String kitId = firstKit(composition);
        Path kitFile = kitId.isBlank() ? null : KitsFind(kitId);
        if (kitFile == null || !java.nio.file.Files.isRegularFile(kitFile)) {
            return findings;
        }
        Map<String, DescriptorResolver.Resolved> byName = new LinkedHashMap<>();
        for (DescriptorResolver.Resolved row : DescriptorResolver.all(kitFile, composition)) {
            byName.put(row.id(), row);
        }
        for (String role : Selector.roles(composition)) {
            String pin = composition.text("ensemble.roles." + role + ".pin", "");
            if (pin.isBlank()) {
                continue;
            }
            DescriptorResolver.Resolved row = byName.get(pin);
            if (row != null && row.avoid()) {
                String path = "/states/" + state + "/ensemble/roles/" + role + "/pin";
                String source = sourceOf(composition, "ensemble.roles." + role + ".pin");
                findings.add(new Finding(
                        "AVOID_PINNED", "warning", path, source,
                        "pinned instrument " + pin + " is marked avoid",
                        "Unpin " + pin + " or clear avoid."));
            }
        }
        Selector.Outcome outcome;
        try {
            outcome = Selector.choose(composition, genre, kitFile, seed);
        } catch (KitException e) {
            KitException.Issue issue = e.issues().get(0);
            findings.add(new Finding(issue.code(), "error", issue.path(), "ensemble", issue.message(), issue.fix()));
            return findings;
        }
        List<Selector.Pick> melodic = new ArrayList<>();
        for (Selector.Pick pick : outcome.picks()) {
            DescriptorResolver.Resolved row = byName.get(pick.instrument());
            if (row == null) {
                continue;
            }
            for (Selector.Pick earlier : outcome.picks()) {
                if (earlier == pick) {
                    break;
                }
                DescriptorResolver.Resolved other = byName.get(earlier.instrument());
                if (other != null && clashes(row, other)) {
                    findings.add(new Finding(
                            "CLASH", "warning",
                            "/states/" + state + "/ensemble/roles/" + pick.role(),
                            row.clashSource(),
                            pick.instrument() + " clashes with " + earlier.instrument(),
                            "Pick a different " + pick.role() + " or clear clashWith."));
                }
            }
            if (isMelodic(pick.role())) {
                melodic.add(pick);
            }
        }
        for (int i = 0; i < melodic.size(); i++) {
            for (int j = i + 1; j < melodic.size(); j++) {
                Selector.Pick left = melodic.get(i);
                Selector.Pick right = melodic.get(j);
                if (!"lead".equals(left.role()) && !"lead".equals(right.role())) {
                    continue;
                }
                DescriptorResolver.Resolved a = byName.get(left.instrument());
                DescriptorResolver.Resolved b = byName.get(right.instrument());
                if (a != null && b != null && a.register().sweetOverlaps(b.register())) {
                    findings.add(new Finding(
                            "REGISTER_COLLISION", "warning",
                            "/states/" + state + "/ensemble/roles/" + right.role(),
                            a.registerSource(),
                            left.role() + " and " + right.role() + " share a sweet range",
                            "Narrow one register or pick a darker " + right.role() + "."));
                }
            }
        }
        return findings;
    }

    private static boolean clashes(DescriptorResolver.Resolved left, DescriptorResolver.Resolved right) {
        return hits(left.clashWith(), right) || hits(right.clashWith(), left);
    }

    private static boolean hits(List<String> rules, DescriptorResolver.Resolved other) {
        for (String rule : rules) {
            if (rule.equals(other.id()) || (rule.startsWith("tag:") && other.tags().contains(rule.substring(4)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMelodic(String role) {
        return switch (role) {
            case "lead", "pad", "arp", "brass", "harp", "bell" -> true;
            default -> false;
        };
    }

    private static String sourceOf(Composition composition, String path) {
        if (composition.leaves().containsKey(path)) {
            return composition.leaves().get(path).source();
        }
        return "theme";
    }

    private static String firstKit(Composition composition) {
        String kits = composition.text("ensemble.kits", "");
        if (kits.isBlank()) {
            kits = composition.text("instruments.kits", "");
        }
        if (kits.isBlank()) {
            return "";
        }
        return kits.split(",")[0].trim();
    }

    private static Path KitsFind(String id) {
        return io.github.sosgames.athenacl.instruments.Kits.find(id);
    }
}
