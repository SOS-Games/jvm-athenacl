// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.motif.hook;

import io.github.sosgames.athenacl.motif.critic.MelodyCritic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How a hook is grown and how the critic weighs it. A preset can supply these when the
 * theme file has no {@code hooks:} block. The theme's block wins when it has one.
 * Contour names here are the shapes the critic scores. They are not {@code Motif.contour()},
 * which stays the up/down/same string from L7.
 */
public record HookSettings(
        int minNotes,
        int maxNotes,
        int candidates,
        List<String> cells,
        Map<String, Double> contours,
        double[] weights,
        double noveltyLo,
        double noveltyHi,
        int maxLeap,
        int maxSame,
        Map<String, HookSettings> states) {

    public HookSettings {
        minNotes = Math.max(1, minNotes);
        maxNotes = Math.max(minNotes, maxNotes);
        candidates = Math.max(1, candidates);
        cells = cells == null || cells.isEmpty() ? List.of("hook") : List.copyOf(cells);
        // LinkedHashMap, not Map.copyOf: the immutable copy shuffles its order each run.
        contours = contours == null || contours.isEmpty()
                ? Collections.unmodifiableMap(defaultContours())
                : Collections.unmodifiableMap(new LinkedHashMap<>(contours));
        weights = weights == null ? MelodyCritic.defaultWeights() : weights.clone();
        maxLeap = maxLeap <= 0 ? 12 : maxLeap;
        maxSame = maxSame <= 0 ? 3 : maxSame;
        states = states == null || states.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(states));
    }

    /** CLI default: 3–6 notes, 64 candidates, the rhythm cells from the plan. */
    public static HookSettings defaults() {
        return new HookSettings(3, 6, 64, List.of(
                "hook", "long-short-short", "short-short-long", "anticipation", "driving-8ths"),
                defaultContours(), MelodyCritic.defaultWeights(), 0.35, 0.60, 12, 3, Map.of());
    }

    public static Map<String, Double> defaultContours() {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("arch", 0.45);
        weights.put("rise-and-resolve", 0.25);
        weights.put("fall", 0.15);
        weights.put("call-response", 0.15);
        return weights;
    }

    public HookSettings withCandidates(int count) {
        return new HookSettings(minNotes, maxNotes, count, cells, contours, weights,
                noveltyLo, noveltyHi, maxLeap, maxSame, states);
    }

    /** State block when the theme has one, otherwise these settings. */
    public HookSettings forState(String state) {
        if (state == null) {
            return this;
        }
        HookSettings over = states.get(state);
        return over == null ? this : over;
    }

    /** Stable cache key. The same numbers always print the same text. */
    public String fingerprint() {
        StringBuilder text = new StringBuilder();
        text.append(minNotes).append(',').append(maxNotes).append(',').append(candidates);
        for (String cell : cells) {
            text.append(',').append(cell);
        }
        for (Map.Entry<String, Double> entry : contours.entrySet()) {
            text.append(',').append(entry.getKey()).append('@').append(entry.getValue());
        }
        for (double weight : weights) {
            text.append(',').append(weight);
        }
        text.append(',').append(noveltyLo).append(',').append(noveltyHi);
        text.append(',').append(maxLeap).append(',').append(maxSame);
        return text.toString();
    }

    public static double[] copyWeights(double[] source) {
        double[] weights = MelodyCritic.defaultWeights();
        if (source == null) {
            return weights;
        }
        System.arraycopy(source, 0, weights, 0, Math.min(source.length, weights.length));
        return weights;
    }

    public static List<String> cellsOrHook(List<String> cells) {
        return cells == null || cells.isEmpty() ? List.of("hook") : new ArrayList<>(cells);
    }
}
