// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.analysis;

import io.github.sosgames.athenacl.adaptive.Composition;
import io.github.sosgames.athenacl.adaptive.Resolver;
import io.github.sosgames.athenacl.events.DefaultRandom;
import io.github.sosgames.athenacl.instruments.KitException;
import io.github.sosgames.athenacl.instruments.Kits;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Picks one instrument per role from a kit profile.
 * The same seed and the same profile always pick the same ensemble.
 * A missing profile leaves the theme's own instruments in place.
 */
public final class Ensemble {
    private static final int BEAM = 16;
    private static final List<String> ROLE_ORDER = List.of(
            "lead", "pad", "bass", "arp", "brass", "chords", "perc", "drums", "taiko");
    private static final List<String> GENRE_STATES = List.of(
            "sneaking", "peaceful", "danger", "combat", "trading", "partying");

    private Ensemble() {
    }

    public record Role(String role, String instrument, double score, String verdict, String from) {
    }

    public record Result(List<Role> roles, double maxRoughness, double roughLimit, boolean fallback, String seedPath) {
        public Result {
            roles = List.copyOf(roles);
        }

        public String text() {
            StringBuilder sb = new StringBuilder();
            if (fallback) {
                sb.append("ensemble.fallback=fixed\n");
            }
            for (Role role : roles) {
                sb.append("role=").append(role.role);
                sb.append(" instrument=").append(role.instrument);
                sb.append(" score=").append(Compatibility.num(role.score));
                sb.append(" verdict=").append(role.verdict);
                sb.append(" from=").append(role.from);
                sb.append('\n');
            }
            sb.append("pairs.maxRoughness=").append(Compatibility.num(maxRoughness)).append('\n');
            sb.append("pairs.maxRoughness<=").append(Compatibility.num(roughLimit)).append('\n');
            sb.append("seedPath=").append(seedPath);
            return sb.toString();
        }

        public String instrumentLines() {
            StringBuilder sb = new StringBuilder();
            for (Role role : roles) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append("instruments.").append(role.role).append('=').append(role.instrument);
                sb.append(" source=ensemble:").append(fallback ? "fixed" : role.from);
            }
            return sb.toString();
        }
    }

    public static Result select(String genre, String state, Path theme, String kitOverride, long seed)
            throws IOException, KitException {
        Composition composition = theme == null
                ? Resolver.resolve(genre, state, "", null)
                : Resolver.resolveTheme(genre, state, theme);
        if (composition == null) {
            throw new KitException(new KitException.Issue(
                    "NOT_FOUND", "--genre", "unknown genre " + genre,
                    "Use a genre such as fantasy."));
        }
        String kitId = kitOverride == null || kitOverride.isBlank() ? firstKit(composition) : kitOverride;
        InstrumentProfile.Report profile = open(kitId);
        if (profile == null || !"auto".equals(composition.text("ensemble.select", "fixed"))) {
            return fallback(composition, state, seed);
        }
        Result chosen = choose(profile, composition, state, seed);
        return chosen.roles.isEmpty() ? fallback(composition, state, seed) : chosen;
    }

    public static Result choose(InstrumentProfile.Report profile, Composition composition, String state, long seed) {
        List<String> roles = roles(composition);
        double roughLimit = number(composition, "ensemble.constraints.maxPairRoughness", 5);
        double maskLimit = number(composition, "ensemble.constraints.maxMask.lead", 0.95);
        int topK = (int) number(composition, "ensemble.topK", 3);
        Map<String, List<Pick>> options = new LinkedHashMap<>();
        for (String role : roles) {
            options.put(role, candidates(profile, composition, role));
        }
        List<Assignment> beam = search(roles, options, roughLimit, maskLimit);
        if (beam.isEmpty()) {
            return new Result(List.of(), 0, roughLimit, false, path(seed, state));
        }
        int keep = Math.max(1, Math.min(topK, beam.size()));
        List<Assignment> top = beam.subList(0, keep);
        int index = top.size() == 1
                ? 0
                : DefaultRandom.of(seed).fork("ensemble".hashCode(), state.hashCode()).nextInt(top.size());
        Assignment picked = top.get(index);
        return new Result(toRoles(picked), roughness(picked), roughLimit, false, path(seed, state));
    }

    public static Compatibility.Lint lintGenre(String genre, long seed) throws IOException, KitException {
        List<Compatibility.Finding> findings = new ArrayList<>();
        for (String state : GENRE_STATES) {
            Result result = select(genre, state, null, null, seed);
            for (Role role : result.roles) {
                if ("bad".equals(role.verdict()) || role.instrument.isBlank()) {
                    findings.add(new Compatibility.Finding(
                            "INSTR_BAD", "error", state, role.role, role.instrument,
                            "/ensemble/roles/" + role.role, "ensemble:auto",
                            "try " + role.role + "=" + role.instrument, ""));
                }
            }
        }
        return new Compatibility.Lint(findings, false);
    }

    static Result fallback(Composition composition, String state, long seed) {
        List<Role> roles = new ArrayList<>();
        List<String> names = roles(composition);
        if (names.isEmpty()) {
            names = instrumentRoles(composition);
        }
        for (String role : names) {
            String pin = composition.text("ensemble.roles." + role + ".pin", "");
            if (!pin.isBlank()) {
                roles.add(new Role(role, pin, 1, "ok", "pin"));
                continue;
            }
            roles.add(new Role(role, composition.text("instruments." + role, role), 0, "fixed", "fixed"));
        }
        return new Result(roles, 0, number(composition, "ensemble.constraints.maxPairRoughness", 5), true,
                path(seed, state));
    }

    private static List<Pick> candidates(InstrumentProfile.Report profile, Composition composition, String role) {
        String pin = composition.text("ensemble.roles." + role + ".pin", "");
        if (!pin.isBlank()) {
            InstrumentProfile.Row row = find(profile, pin);
            if (row == null) {
                row = new InstrumentProfile.Row(pin, "ok", List.of(), List.of(), 0, 0, "pin",
                        0, 0, 0, false, 0, 0);
            }
            return List.of(new Pick(role, row, 1, "pin"));
        }
        Set<String> allowed = split(composition.text("ensemble.constraints.verdict", "ok,caution"));
        Set<String> any = split(composition.text("ensemble.roles." + role + ".tags.any", ""));
        Set<String> none = split(composition.text("ensemble.roles." + role + ".tags.none", ""));
        double fitMin = bound(composition.text("ensemble.roles." + role + ".fit", ""));
        double attackMin = bound(composition.text("ensemble.roles." + role + ".attackMs", ""));
        double[] centroid = range(composition.text("ensemble.roles." + role + ".centroidRatio", ""));
        List<Pick> picks = new ArrayList<>();
        for (InstrumentProfile.Row row : profile.rows()) {
            if (!allowed.isEmpty() && !allowed.contains(row.verdict())) {
                continue;
            }
            if (!any.isEmpty() && row.tags().stream().noneMatch(any::contains)) {
                continue;
            }
            if (row.tags().stream().anyMatch(none::contains)) {
                continue;
            }
            double fit = Compatibility.fitness(Compatibility.roleOf(role), row);
            if (fitMin >= 0 && fit < fitMin) {
                continue;
            }
            if (attackMin >= 0 && row.attackMs() < attackMin) {
                continue;
            }
            if (centroid != null && (row.centroidRatio() < centroid[0] || row.centroidRatio() > centroid[1])) {
                continue;
            }
            picks.add(new Pick(role, row, fit, "auto"));
        }
        picks.sort(Comparator.comparingDouble((Pick pick) -> pick.score).reversed()
                .thenComparing(pick -> pick.row.name()));
        if (picks.size() > 8) {
            return List.copyOf(picks.subList(0, 8));
        }
        return picks;
    }

    private static List<Assignment> search(List<String> roles, Map<String, List<Pick>> options,
                                           double roughLimit, double maskLimit) {
        List<Assignment> beam = new ArrayList<>();
        beam.add(new Assignment(List.of(), 0));
        for (String role : roles) {
            List<Pick> choices = options.getOrDefault(role, List.of());
            if (choices.isEmpty()) {
                return List.of();
            }
            List<Assignment> next = new ArrayList<>();
            for (Assignment partial : beam) {
                for (Pick pick : choices) {
                    if (clashes(partial, pick, roughLimit, maskLimit)) {
                        continue;
                    }
                    List<Pick> picks = new ArrayList<>(partial.picks);
                    picks.add(pick);
                    next.add(new Assignment(picks, partial.score + pick.score));
                }
            }
            if (next.isEmpty()) {
                return List.of();
            }
            next.sort(Comparator.comparingDouble((Assignment item) -> item.score).reversed()
                    .thenComparing(Assignment::signature));
            beam = next.size() > BEAM ? new ArrayList<>(next.subList(0, BEAM)) : next;
        }
        return beam;
    }

    private static boolean clashes(Assignment partial, Pick pick, double roughLimit, double maskLimit) {
        if ("pin".equals(pick.from)) {
            return false;
        }
        for (Pick earlier : partial.picks) {
            if ("pin".equals(earlier.from)) {
                continue;
            }
            if (!sustained(earlier.role) || !sustained(pick.role)) {
                continue;
            }
            if (!"lead".equals(earlier.role) && !"lead".equals(pick.role)) {
                continue;
            }
            Compatibility.Pair pair = Compatibility.pair(earlier.row, pick.row, "C4..C5", "C4..C5");
            if (pair.mask() > maskLimit || pair.roughness() > roughLimit) {
                return true;
            }
        }
        return false;
    }

    private static boolean sustained(String role) {
        return switch (role) {
            case "lead", "pad", "bass", "brass", "chords", "strings" -> true;
            default -> false;
        };
    }

    private static double roughness(Assignment assignment) {
        double max = 0;
        List<Pick> picks = assignment.picks;
        for (int i = 0; i < picks.size(); i++) {
            for (int j = i + 1; j < picks.size(); j++) {
                Pick a = picks.get(i);
                Pick b = picks.get(j);
                if (!sustained(a.role) || !sustained(b.role) || !"lead".equals(a.role) && !"lead".equals(b.role)) {
                    continue;
                }
                max = Math.max(max, Compatibility.pair(a.row, b.row, "C4..C5", "C4..C5").roughness());
            }
        }
        return max;
    }

    private static List<Role> toRoles(Assignment assignment) {
        List<Role> roles = new ArrayList<>();
        for (Pick pick : assignment.picks) {
            roles.add(new Role(pick.role, pick.row.name(), pick.score, pick.row.verdict(), pick.from));
        }
        return roles;
    }

    private static InstrumentProfile.Report open(String kitId) throws IOException, KitException {
        if (kitId == null || kitId.isBlank()) {
            return null;
        }
        Path file = Kits.find(kitId);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        if ("analysis".equals(kitId) || file.toString().replace('\\', '/').contains("samples/kits/")) {
            return InstrumentProfile.analyze(file, null);
        }
        return InstrumentProfile.openCached(file, null);
    }

    private static List<String> roles(Composition composition) {
        Set<String> found = new LinkedHashSet<>();
        for (String path : composition.leaves().keySet()) {
            if (!path.startsWith("ensemble.roles.")) {
                continue;
            }
            String rest = path.substring("ensemble.roles.".length());
            int dot = rest.indexOf('.');
            found.add(dot < 0 ? rest : rest.substring(0, dot));
        }
        List<String> ordered = new ArrayList<>();
        for (String role : ROLE_ORDER) {
            if (found.remove(role)) {
                ordered.add(role);
            }
        }
        List<String> extra = new ArrayList<>(found);
        extra.sort(String::compareTo);
        ordered.addAll(extra);
        return ordered;
    }

    private static List<String> instrumentRoles(Composition composition) {
        List<String> roles = new ArrayList<>();
        for (String path : composition.leaves().keySet()) {
            if (path.startsWith("instruments.") && !path.substring("instruments.".length()).contains(".")) {
                roles.add(path.substring("instruments.".length()));
            }
        }
        return roles;
    }

    private static InstrumentProfile.Row find(InstrumentProfile.Report profile, String name) {
        for (InstrumentProfile.Row row : profile.rows()) {
            if (row.name().equals(name)) {
                return row;
            }
        }
        return null;
    }

    private static String firstKit(Composition composition) {
        String kits = composition.text("ensemble.kits", "");
        if (kits.isBlank()) {
            return "";
        }
        return kits.split(",")[0].trim();
    }

    private static String path(long seed, String state) {
        return seed + "/ensemble/" + state;
    }

    private static double number(Composition composition, String key, double fallback) {
        String text = composition.text(key, "");
        if (text.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double bound(String text) {
        if (text == null || text.isBlank()) {
            return -1;
        }
        String raw = text.startsWith(">=") ? text.substring(2) : text;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static double[] range(String text) {
        if (text == null || text.isBlank() || !text.contains(",")) {
            return null;
        }
        String[] parts = text.split(",", 2);
        try {
            return new double[] {Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Set<String> split(String text) {
        Set<String> values = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return values;
        }
        for (String part : text.split(",")) {
            if (!part.isBlank()) {
                values.add(part.trim().toLowerCase(Locale.ROOT));
            }
        }
        return values;
    }

    private record Pick(String role, InstrumentProfile.Row row, double score, String from) {
    }

    private record Assignment(List<Pick> picks, double score) {
        String signature() {
            StringBuilder sb = new StringBuilder();
            for (Pick pick : picks) {
                if (!sb.isEmpty()) {
                    sb.append(',');
                }
                sb.append(pick.row.name());
            }
            return sb.toString();
        }
    }
}
