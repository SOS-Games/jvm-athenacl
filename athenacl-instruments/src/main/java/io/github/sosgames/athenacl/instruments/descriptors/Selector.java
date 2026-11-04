// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.descriptors;

import io.github.sosgames.athenacl.adaptive.Composition;
import io.github.sosgames.athenacl.events.DefaultRandom;
import io.github.sosgames.athenacl.events.RandomSource;
import io.github.sosgames.athenacl.instruments.KitException;

import java.io.IOException;
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
 * Fills each ensemble role from declared descriptors. Bass is chosen first, then pad, lead,
 * arp, and perc, so a later role can hear the ones already taken. The same seed and the same
 * descriptors always pick the same names. Nothing here reads a measured profile.
 */
public final class Selector {
    private static final List<String> ORDER = List.of("bass", "pad", "lead", "arp", "perc");
    private static final Set<String> MELODIC = Set.of(
            "lead", "pad", "arp", "brass", "harp", "bell", "chords", "strings");

    private Selector() {
    }

    public record Pick(String role, String instrument, double score, String from, String soundClass, String seedPath) {
    }

    public record Outcome(List<Pick> picks, boolean fallback, String detail) {
        public Outcome {
            picks = List.copyOf(picks);
        }
    }

    public static Outcome choose(Composition composition, String genre, Path kitFile, long seed)
            throws IOException, KitException {
        List<DescriptorResolver.Resolved> catalog = DescriptorResolver.all(kitFile, composition);
        Map<String, DescriptorResolver.Resolved> byName = new LinkedHashMap<>();
        for (DescriptorResolver.Resolved row : catalog) {
            byName.put(row.id(), row);
        }
        List<String> roles = roles(composition);
        List<Pick> picks = new ArrayList<>();
        StringBuilder detail = new StringBuilder();
        int topK = (int) number(composition, "ensemble.topK", 3);
        for (String role : roles) {
            String pin = composition.text("ensemble.roles." + role + ".pin", "");
            String seedPath = seed + "/ensemble/" + role;
            if (!pin.isBlank()) {
                DescriptorResolver.Resolved row = byName.get(pin);
                picks.add(new Pick(role, pin, 1, "pin", row == null ? "" : row.soundClass(), seedPath));
                detail.append("role=").append(role).append(" choice=").append(pin).append(" from=pin\n");
                continue;
            }
            Need need = need(composition, role);
            List<String> relaxed = new ArrayList<>();
            List<Scored> found = List.of();
            boolean dropPrefer = false;
            boolean dropRegister = false;
            boolean dropRequire = false;
            boolean dropClass = false;
            String[] steps = {"", "preferTags", "register", "requireTags", "class"};
            for (String step : steps) {
                if (!step.isEmpty()) {
                    relaxed.add(step);
                    dropPrefer = dropPrefer || "preferTags".equals(step);
                    dropRegister = dropRegister || "register".equals(step);
                    dropRequire = dropRequire || "requireTags".equals(step);
                    dropClass = dropClass || "class".equals(step);
                }
                found = score(catalog, picks, role, genre, need, dropPrefer, dropRegister, dropRequire, dropClass);
                if (!found.isEmpty()) {
                    break;
                }
            }
            if (found.isEmpty()) {
                String fallback = composition.text("ensemble.roles." + role + ".fallback", "");
                if (fallback.isBlank()) {
                    fallback = composition.text("instruments." + role, "");
                }
                if (fallback.isBlank()) {
                    throw new KitException(new KitException.Issue(
                            "ROLE_EMPTY", "/ensemble/roles/" + role,
                            "no instrument for role " + role,
                            "Add a fallback instrument or widen requireTags."));
                }
                picks.add(new Pick(role, fallback, 0, "fallback", "", seedPath));
                detail.append("role=").append(role).append(" choice=").append(fallback).append(" from=fallback");
                if (!relaxed.isEmpty()) {
                    detail.append(" relaxed=").append(String.join(",", relaxed));
                }
                detail.append('\n');
                continue;
            }
            found.sort(Comparator.comparingDouble((Scored row) -> row.score).reversed()
                    .thenComparing(row -> row.row.id()));
            int count = Math.max(1, (int) number(composition, "ensemble.roles." + role + ".count", 1));
            int keep = Math.max(1, Math.min(Math.max(1, topK), found.size()));
            List<Scored> top = new ArrayList<>(found.subList(0, keep));
            RandomSource random = DefaultRandom.of(seed).fork("ensemble".hashCode()).fork(role.hashCode());
            detail.append("role=").append(role).append(" candidates=").append(found.size());
            int shown = 0;
            for (Scored row : found) {
                if (shown++ >= 12) {
                    break;
                }
                detail.append("\ncandidate=").append(row.row.id()).append(" score=").append(num(row.score));
            }
            if (!relaxed.isEmpty() && !(relaxed.size() == 1 && foundWasFull(steps, relaxed))) {
                detail.append("\nrelaxed=").append(String.join(",", relaxed));
            }
            for (int n = 0; n < count && !top.isEmpty(); n++) {
                int index = top.size() == 1 ? 0 : random.nextInt(top.size());
                Scored chosen = top.remove(index);
                picks.add(new Pick(role, chosen.row.id(), chosen.score, "auto", chosen.row.soundClass(), seedPath));
                detail.append("\nchoice=").append(chosen.row.id()).append(" index=").append(index);
            }
            detail.append('\n');
        }
        return new Outcome(picks, false, detail.toString().stripTrailing());
    }

    private static boolean foundWasFull(String[] steps, List<String> relaxed) {
        return relaxed.isEmpty();
    }

    private static List<Scored> score(List<DescriptorResolver.Resolved> catalog, List<Pick> chosen, String role,
                                      String genre, Need need, boolean dropPrefer, boolean dropRegister,
                                      boolean dropRequire, boolean dropClass) {
        Map<String, DescriptorResolver.Resolved> byName = index(catalog);
        List<Scored> scored = new ArrayList<>();
        for (DescriptorResolver.Resolved row : catalog) {
            if (row.avoid()) {
                continue;
            }
            if (!matchesRole(role, row.roles())) {
                continue;
            }
            if (!dropRequire && !need.require.isEmpty() && !row.tags().containsAll(need.require)) {
                continue;
            }
            if (row.tags().stream().anyMatch(need.exclude::contains)) {
                continue;
            }
            if (!dropClass && !need.classes.isEmpty() && !need.classes.contains(row.soundClass())) {
                continue;
            }
            if (!dropRegister && !row.register().covers(need.target)) {
                continue;
            }
            if (need.maxPerFamily > 0 && familyCount(chosen, row, byName) >= need.maxPerFamily) {
                continue;
            }
            double score = 0;
            if (!dropPrefer) {
                for (var prefer : need.prefer.entrySet()) {
                    if (row.tags().contains(prefer.getKey())) {
                        score += prefer.getValue();
                    }
                }
            }
            if (genre != null && !genre.isBlank() && row.genres().contains(genre.toLowerCase(Locale.ROOT))) {
                score += 1;
            }
            score += fit(row.register(), need.target);
            score -= 2.0 * clashes(row, chosen, byName);
            if (MELODIC.contains(role) || "lead".equals(role)) {
                score -= overlaps(role, row, chosen);
            }
            scored.add(new Scored(row, score));
        }
        return scored;
    }

    private static Map<String, DescriptorResolver.Resolved> index(List<DescriptorResolver.Resolved> catalog) {
        Map<String, DescriptorResolver.Resolved> byName = new LinkedHashMap<>();
        for (DescriptorResolver.Resolved row : catalog) {
            byName.put(row.id(), row);
        }
        return byName;
    }

    private static int clashes(DescriptorResolver.Resolved row, List<Pick> chosen,
                               Map<String, DescriptorResolver.Resolved> byName) {
        int hits = 0;
        for (Pick pick : chosen) {
            hits += directed(row.clashWith(), byName.get(pick.instrument()), pick.instrument());
            DescriptorResolver.Resolved other = byName.get(pick.instrument());
            if (other != null) {
                hits += directed(other.clashWith(), row, row.id());
            }
        }
        return hits;
    }

    private static int directed(List<String> rules, DescriptorResolver.Resolved other, String otherId) {
        int hits = 0;
        for (String rule : rules) {
            if (rule.equals(otherId)) {
                hits++;
            } else if (rule.startsWith("tag:") && other != null && other.tags().contains(rule.substring(4))) {
                hits++;
            }
        }
        return hits;
    }

    private static double overlaps(String role, DescriptorResolver.Resolved row, List<Pick> chosen) {
        double penalty = 0;
        for (Pick pick : chosen) {
            boolean leadPair = "lead".equals(role) || "lead".equals(pick.role);
            if (!leadPair || !MELODIC.contains(pick.role)) {
                continue;
            }
            if (row.register().sweetOverlaps(new Register(0, 127, 48, 84)) && "lead".equals(role)) {
                penalty += 0.15;
            }
        }
        return Math.min(1.5, penalty);
    }

    private static int familyCount(List<Pick> chosen, DescriptorResolver.Resolved row,
                                   Map<String, DescriptorResolver.Resolved> byName) {
        int family = row.percussion() ? -1 : row.program() / 8;
        int count = 0;
        for (Pick pick : chosen) {
            DescriptorResolver.Resolved other = byName.get(pick.instrument());
            if (other == null) {
                continue;
            }
            int otherFamily = other.percussion() ? -1 : other.program() / 8;
            if (otherFamily == family) {
                count++;
            }
        }
        return count;
    }

    private static double fit(Register register, Register target) {
        if (target == null || target.unset() || register.unset()) {
            return 0;
        }
        int low = Math.max(register.sweetLow(), target.low());
        int high = Math.min(register.sweetHigh(), target.high());
        int span = Math.max(1, target.high() - target.low());
        if (high < low) {
            return 0;
        }
        return (high - low + 1) / (double) span;
    }

    private static boolean matchesRole(String role, List<String> roles) {
        if (roles.contains(role)) {
            return true;
        }
        return switch (role) {
            case "harp", "pluck", "plucked", "bell" -> roles.contains("arp");
            case "drums", "taiko" -> roles.contains("perc");
            case "chords", "strings", "drone" -> roles.contains("pad") || roles.contains("drone");
            case "brass" -> roles.contains("lead");
            default -> false;
        };
    }

    private static Need need(Composition composition, String role) {
        String prefix = "ensemble.roles." + role;
        return new Need(
                split(composition.text(prefix + ".requireTags", "")),
                weights(composition, prefix + ".preferTags"),
                split(composition.text(prefix + ".excludeTags", "")),
                split(composition.text(prefix + ".class", "")),
                target(composition, prefix + ".register"),
                (int) number(composition, prefix + ".maxPerFamily", number(composition, "ensemble.maxPerFamily", 0)));
    }

    private static Register target(Composition composition, String prefix) {
        if (!composition.leaves().containsKey(prefix + ".low") && !composition.leaves().containsKey(prefix + ".high")) {
            return Register.UNSET;
        }
        int low = (int) number(composition, prefix + ".low", 0);
        int high = (int) number(composition, prefix + ".high", 127);
        return new Register(low, high, low, high);
    }

    private static Map<String, Double> weights(Composition composition, String prefix) {
        Map<String, Double> weights = new LinkedHashMap<>();
        String direct = composition.text(prefix, "");
        if (!direct.isBlank()) {
            for (String tag : split(direct)) {
                weights.put(tag, 1.0);
            }
        }
        for (String path : composition.leaves().keySet()) {
            if (!path.startsWith(prefix + ".")) {
                continue;
            }
            String tag = path.substring(prefix.length() + 1);
            if (tag.contains(".")) {
                continue;
            }
            weights.put(tag.toLowerCase(Locale.ROOT), number(composition, path, 1));
        }
        return weights;
    }

    public static List<String> roles(Composition composition) {
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
        for (String role : ORDER) {
            if (found.remove(role)) {
                ordered.add(role);
            }
        }
        List<String> extra = new ArrayList<>(found);
        extra.sort(String::compareTo);
        ordered.addAll(extra);
        return ordered;
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

    private static double number(Composition composition, String key, double fallback) {
        String text = composition.text(key, "");
        if (text.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static String num(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private record Need(Set<String> require, Map<String, Double> prefer, Set<String> exclude, Set<String> classes,
                        Register target, int maxPerFamily) {
    }

    private record Scored(DescriptorResolver.Resolved row, double score) {
    }
}
