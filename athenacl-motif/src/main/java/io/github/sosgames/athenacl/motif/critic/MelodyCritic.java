// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.motif.critic;

import io.github.sosgames.athenacl.events.DefaultRandom;
import io.github.sosgames.athenacl.events.PitchName;
import io.github.sosgames.athenacl.events.RandomSource;
import io.github.sosgames.athenacl.events.TimeBase;
import io.github.sosgames.athenacl.motif.Motif;
import io.github.sosgames.athenacl.motif.MotifNote;
import io.github.sosgames.athenacl.motif.hook.HookGenerator;
import io.github.sosgames.athenacl.motif.hook.HookSettings;
import io.github.sosgames.athenacl.theory.Key;
import io.github.sosgames.athenacl.theory.RomanNumeral;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ranks hook candidates. Hard rules reject a line, then a weighted sum of features in
 * {@code [0,1]} picks the winner. Ties keep the lowest candidate number. The same seed
 * always ranks the same way: features are added in a fixed order and nothing here reads
 * the clock. The score is a ranking of those proxies. It is not a measure of beauty.
 */
public final class MelodyCritic {
    public static final String[] FEATURES = {
            "chordTone", "tonalStability", "implication", "leapRecovery", "range",
            "novelty", "rhythm", "contour", "closure", "distinctiveness"
    };
    private static final double[] PROFILE = {1.0, 0.20, 0.75, 0.30, 0.90, 0.40, 0.45};

    private MelodyCritic() {
    }

    public record Scored(
            int cand,
            Motif motif,
            String contour,
            String form,
            double score,
            Map<String, Double> features,
            String rejected) {
    }

    public record Ranked(List<Scored> candidates, Scored chosen) {
    }

    public static double[] defaultWeights() {
        return new double[] {0.18, 0.14, 0.12, 0.10, 0.10, 0.12, 0.10, 0.08, 0.12, 0.04};
    }

    public static Ranked rank(Key key, long seed, String state, HookSettings settings,
                              List<int[]> chords, List<int[]> references) {
        HookSettings resolved = settings == null ? HookSettings.defaults() : settings;
        String scope = state == null || state.isBlank() ? "melody" : state;
        List<int[]> harmony = chords == null || chords.isEmpty() ? List.of(triad(key)) : chords;
        List<int[]> guards = references == null ? List.of() : references;
        Set<String> banned = grams(guards);
        int[] scale = scaleMidi(key);
        int count = resolved.candidates();
        List<Scored> scored = new ArrayList<>();
        Scored best = null;
        for (int k = 0; k < count; k++) {
            Scored one = k == 0 && !guards.isEmpty()
                    ? copyCandidate(key, guards.get(0), resolved, banned)
                    : generated(key, seed, scope, resolved, harmony, banned, k, scale);
            scored.add(one);
            if (one.rejected() == null && (best == null || one.score() > best.score())) {
                best = one;
            }
        }
        if (best == null) {
            best = scored.get(0);
        }
        return new Ranked(List.copyOf(scored), best);
    }

    public static Scored scorePitches(int[] midi, String[] durations, Key key, HookSettings settings,
                                      List<int[]> chords, List<int[]> references) {
        HookSettings resolved = settings == null ? HookSettings.defaults() : settings;
        List<int[]> harmony = chords == null || chords.isEmpty() ? List.of(triad(key)) : chords;
        Set<String> banned = grams(references == null ? List.of() : references);
        return judge(midi, durations, key, harmony, resolved, banned, 1, motifFrom(midi, key), "scored", "sentence");
    }

    public static List<int[]> chords(Key key, String text) {
        if (text == null || text.isBlank()) {
            return List.of(triad(key));
        }
        String[] tokens = text.trim().split("\\s+");
        List<int[]> chords = new ArrayList<>();
        for (String token : tokens) {
            chords.add(RomanNumeral.parse(token).pitchClasses(key));
        }
        return chords;
    }

    public static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static Scored generated(Key key, long seed, String state, HookSettings settings,
                                    List<int[]> chords, Set<String> banned, int index, int[] scale) {
        RandomSource rng = DefaultRandom.of(seed).fork("hook".hashCode(), state.hashCode(), index);
        HookGenerator.Grown grown = HookGenerator.grow(key, rng, settings, chords);
        int[] midi = new int[grown.motif().notes().size()];
        String[] durations = new String[midi.length];
        for (int i = 0; i < midi.length; i++) {
            MotifNote note = grown.motif().notes().get(i);
            midi[i] = scale[Math.floorMod(note.degree(), scale.length)];
            durations[i] = note.duration();
        }
        return judge(midi, durations, key, chords, settings, banned, index + 1,
                grown.motif(), grown.contour(), grown.form());
    }

    private static Scored copyCandidate(Key key, int[] reference, HookSettings settings, Set<String> banned) {
        String[] durations = new String[reference.length];
        for (int i = 0; i < durations.length; i++) {
            durations[i] = "1/4";
        }
        return judge(reference, durations, key, List.of(triad(key)), settings, banned, 1,
                motifFrom(reference, key), "reference", "sentence");
    }

    private static Scored judge(int[] midi, String[] durations, Key key, List<int[]> chords,
                                HookSettings settings, Set<String> banned, int cand,
                                Motif motif, String contour, String form) {
        int count = midi.length;
        int[] steps = key.mode().steps();
        int tonic = key.tonicPc();
        int[] degree = new int[count];
        boolean[] strong = new boolean[count];
        boolean[] chordTone = new boolean[count];
        int[] interval = new int[count];
        long tick = 0;
        for (int i = 0; i < count; i++) {
            degree[i] = nearestDegree(steps, tonic, midi[i]);
            String duration = i < durations.length && durations[i] != null ? durations[i] : "1/4";
            strong[i] = tick % TimeBase.PPQ == 0
                    && ((tick / TimeBase.PPQ) % 4 == 0 || (tick / TimeBase.PPQ) % 4 == 2);
            int chordIndex = chords.size() == 1 ? 0 : Math.min(chords.size() - 1, i * chords.size() / Math.max(1, count));
            chordTone[i] = contains(chords.get(chordIndex), Math.floorMod(midi[i], 12));
            interval[i] = i == 0 ? 0 : midi[i] - midi[i - 1];
            tick += TimeBase.durationTicks(duration);
        }
        String rejected = reject(midi, degree, interval, durations, settings, banned);
        Map<String, Double> features = features(degree, strong, chordTone, interval, durations, contour, settings);
        double score = rejected == null ? weighted(features, settings.weights()) : 0;
        return new Scored(cand, motif, contour, form, score, features, rejected);
    }

    private static String reject(int[] midi, int[] degree, int[] interval, String[] durations,
                                 HookSettings settings, Set<String> banned) {
        if (copyGuard(interval, durations, banned)) {
            return "copyGuard";
        }
        for (int pitch : midi) {
            if (pitch < 48 || pitch > 84) {
                return "inRange";
            }
        }
        int last = degree.length == 0 ? -1 : degree[degree.length - 1];
        if (last != 0 && last != 2 && last != 4) {
            return "cadenceStable";
        }
        int limit = settings.maxLeap();
        for (int i = 1; i < interval.length; i++) {
            if (Math.abs(interval[i]) > limit) {
                return "maxLeap";
            }
        }
        int run = 1;
        for (int i = 1; i < midi.length; i++) {
            run = midi[i] == midi[i - 1] ? run + 1 : 1;
            if (run > settings.maxSame()) {
                return "maxSamePitchRun";
            }
        }
        for (int i = 1; i < interval.length; i++) {
            if (Math.abs(interval[i]) != 6) {
                continue;
            }
            if (i + 1 >= interval.length) {
                return "noUnresolvedTritoneLeap";
            }
            int next = interval[i + 1];
            boolean stepsBack = Integer.signum(next) == -Integer.signum(interval[i]) && Math.abs(next) <= 2 && next != 0;
            if (!stepsBack) {
                return "noUnresolvedTritoneLeap";
            }
        }
        return null;
    }

    private static boolean copyGuard(int[] interval, String[] durations, Set<String> banned) {
        if (banned.isEmpty()) {
            return false;
        }
        List<String> steps = steps(interval, durations);
        if (steps.size() < 3) {
            return false;
        }
        int grams = 0;
        int matched = 0;
        for (int i = 0; i + 2 < steps.size(); i++) {
            grams++;
            if (banned.contains(steps.get(i) + "~" + steps.get(i + 1) + "~" + steps.get(i + 2))) {
                matched++;
            }
        }
        return grams >= 2 && matched * 2 >= grams;
    }

    private static Set<String> grams(List<int[]> references) {
        Set<String> grams = new LinkedHashSet<>();
        for (int[] midi : references) {
            if (midi == null || midi.length < 4) {
                continue;
            }
            int[] interval = new int[midi.length];
            String[] durations = new String[midi.length];
            for (int i = 0; i < midi.length; i++) {
                interval[i] = i == 0 ? 0 : midi[i] - midi[i - 1];
                durations[i] = "1/4";
            }
            List<String> steps = steps(interval, durations);
            for (int i = 0; i + 2 < steps.size(); i++) {
                grams.add(steps.get(i) + "~" + steps.get(i + 1) + "~" + steps.get(i + 2));
            }
        }
        return grams;
    }

    private static List<String> steps(int[] interval, String[] durations) {
        List<String> steps = new ArrayList<>();
        for (int i = 1; i < interval.length; i++) {
            String duration = i < durations.length && durations[i] != null ? durations[i] : "1/4";
            steps.add(interval[i] + ":" + duration);
        }
        return steps;
    }

    private static Map<String, Double> features(int[] degree, boolean[] strong, boolean[] chordTone,
                                                int[] interval, String[] durations, String contour,
                                                HookSettings settings) {
        Map<String, Double> features = new LinkedHashMap<>();
        features.put("chordTone", chordTone(strong, chordTone));
        features.put("tonalStability", meanProfile(degree));
        features.put("implication", leapFollow(interval, false));
        features.put("leapRecovery", leapFollow(interval, true));
        features.put("range", range(degree));
        features.put("novelty", novelty(interval, settings.noveltyLo(), settings.noveltyHi()));
        features.put("rhythm", rhythm(durations));
        features.put("contour", contourScore(contour, degree));
        features.put("closure", closure(degree));
        features.put("distinctiveness", noveltyRatio(interval));
        return features;
    }

    private static double weighted(Map<String, Double> features, double[] weights) {
        double[] used = weights == null || weights.length < FEATURES.length ? defaultWeights() : weights;
        double sum = 0;
        double weightSum = 0;
        for (int i = 0; i < FEATURES.length; i++) {
            double weight = used[i];
            sum += weight * features.get(FEATURES[i]);
            weightSum += weight;
        }
        if (weightSum <= 0) {
            return 0;
        }
        return sum / weightSum;
    }

    private static double chordTone(boolean[] strong, boolean[] chordTone) {
        int hits = 0;
        int total = 0;
        for (int i = 0; i < strong.length; i++) {
            if (!strong[i]) {
                continue;
            }
            total++;
            if (chordTone[i]) {
                hits++;
            }
        }
        return total == 0 ? 1 : hits / (double) total;
    }

    private static double meanProfile(int[] degree) {
        if (degree.length == 0) {
            return 0;
        }
        double sum = 0;
        for (int step : degree) {
            int index = Math.floorMod(step, PROFILE.length);
            sum += PROFILE[index];
        }
        return sum / degree.length;
    }

    private static double leapFollow(int[] interval, boolean stepOnly) {
        int leaps = 0;
        int recovered = 0;
        for (int i = 1; i < interval.length; i++) {
            int leap = interval[i];
            if (Math.abs(leap) <= 5) {
                continue;
            }
            leaps++;
            if (i + 1 >= interval.length) {
                continue;
            }
            int next = interval[i + 1];
            boolean opposite = Integer.signum(next) == -Integer.signum(leap) && next != 0;
            boolean smaller = Math.abs(next) < Math.abs(leap);
            boolean step = Math.abs(next) <= 2;
            if (opposite && smaller && (!stepOnly || step)) {
                recovered++;
            }
        }
        return leaps == 0 ? 1 : recovered / (double) leaps;
    }

    private static double range(int[] degree) {
        if (degree.length == 0) {
            return 1;
        }
        int low = degree[0];
        int high = degree[0];
        for (int step : degree) {
            low = Math.min(low, step);
            high = Math.max(high, step);
        }
        int span = high - low;
        if (span <= 6) {
            return 1;
        }
        return Math.max(0, 1 - (span - 6) / 6.0);
    }

    private static double novelty(int[] interval, double low, double high) {
        double ratio = noveltyRatio(interval);
        if (ratio >= low && ratio <= high) {
            return 1;
        }
        double distance = ratio < low ? low - ratio : ratio - high;
        return Math.max(0, 1 - distance / 0.5);
    }

    private static double noveltyRatio(int[] interval) {
        if (interval.length <= 1) {
            return 0;
        }
        Set<Integer> unique = new LinkedHashSet<>();
        for (int i = 1; i < interval.length; i++) {
            unique.add(interval[i]);
        }
        return unique.size() / (double) (interval.length - 1);
    }

    private static double rhythm(String[] durations) {
        if (durations.length <= 1) {
            return 1;
        }
        for (int i = 1; i < durations.length; i++) {
            if (durations[i] != null && !durations[i].equals(durations[0])) {
                return 1;
            }
        }
        return 0.55;
    }

    private static double contourScore(String name, int[] degree) {
        if (degree.length < 2) {
            return 0.35;
        }
        int peak = 0;
        for (int i = 1; i < degree.length; i++) {
            if (degree[i] > degree[peak]) {
                peak = i;
            }
        }
        boolean arch = peak > 0 && peak < degree.length - 1;
        boolean fall = degree[degree.length - 1] < degree[0];
        boolean match = switch (name == null ? "" : name) {
            case "fall" -> fall && !arch;
            case "call-response" -> arch && degree[1] <= 2;
            case "rise-and-resolve" -> arch && degree[degree.length - 1] == 0;
            default -> arch && degree[degree.length - 1] <= degree[0];
        };
        return match ? 1 : 0.35;
    }

    private static double closure(int[] degree) {
        if (degree.length == 0) {
            return 0;
        }
        int last = degree[degree.length - 1];
        if (last == 0) {
            return 1;
        }
        if (last == 2 || last == 4) {
            return 0.7;
        }
        return 0;
    }

    private static int[] scaleMidi(Key key) {
        int[] steps = key.mode().steps();
        var names = key.pitchClassNames();
        int[] midi = new int[steps.length];
        for (int i = 0; i < midi.length; i++) {
            midi[i] = PitchName.midi(names.get(i) + "5");
        }
        return midi;
    }

    private static int nearestDegree(int[] steps, int tonic, int midi) {
        int relative = Math.floorMod(midi - tonic, 12);
        int best = 0;
        int bestDistance = 99;
        for (int i = 0; i < steps.length; i++) {
            int distance = Math.abs(relative - steps[i]);
            distance = Math.min(distance, 12 - distance);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private static boolean contains(int[] pcs, int pc) {
        for (int tone : pcs) {
            if (Math.floorMod(tone, 12) == pc) {
                return true;
            }
        }
        return false;
    }

    private static int[] triad(Key key) {
        int[] steps = key.mode().steps();
        return new int[] {
                Math.floorMod(key.tonicPc() + steps[0], 12),
                Math.floorMod(key.tonicPc() + steps[Math.min(2, steps.length - 1)], 12),
                Math.floorMod(key.tonicPc() + steps[Math.min(4, steps.length - 1)], 12)
        };
    }

    private static Motif motifFrom(int[] midi, Key key) {
        int[] steps = key.mode().steps();
        List<MotifNote> notes = new ArrayList<>();
        for (int pitch : midi) {
            notes.add(new MotifNote(nearestDegree(steps, key.tonicPc(), pitch), "1/4"));
        }
        return new Motif("m1", Math.max(1, steps.length), notes);
    }
}
