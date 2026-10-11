// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import io.github.sosgames.athenacl.motif.hook.HookSettings;
import io.github.sosgames.athenacl.theory.Key;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A theme the director can play without a game running. States name the music.
 * The timeline is the demo script used when a render does not pass {@code --timeline}.
 * An empty script stays on {@code hold}, or on the first state when hold is absent.
 * {@code motif.every} is how many bars pass before the lead states the motif again.
 * {@code 1} plays it every bar.
 * {@code hooks} is empty when the theme has no hook block, and the lead then grows
 * {@code m1} the way L7 always has.
 * {@code overlays} add a layer on top of the current state, such as {@code lowHealth}.
 * {@code frameMs} is 0 unless the theme snaps requests to a fixed frame before the musical grid.
 */
public record Theme(
        String id,
        int bars,
        int bpm,
        int numerator,
        int denominator,
        Map<String, StateDef> states,
        Map<String, StingerDef> stingers,
        Quantize defaultQuantize,
        List<Rule> rules,
        List<Control> timeline,
        String preset,
        LoudnessContour loudness,
        List<ThemeIssue> warnings,
        int motifEvery,
        Hold hold,
        List<String> kits,
        HookSettings hooks,
        Map<String, OverlayDef> overlays,
        int frameMs) {

    public Theme {
        states = Collections.unmodifiableMap(new LinkedHashMap<>(states));
        stingers = Collections.unmodifiableMap(new LinkedHashMap<>(stingers));
        rules = List.copyOf(rules);
        timeline = List.copyOf(timeline);
        warnings = List.copyOf(warnings);
        kits = kits == null ? List.of() : List.copyOf(kits);
        overlays = overlays == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(overlays));
    }

    public Theme withBars(int bars) {
        return new Theme(id, bars, bpm, numerator, denominator, states, stingers, defaultQuantize, rules,
                timeline, preset, loudness, warnings, motifEvery, hold, kits, hooks, overlays, frameMs);
    }

    /** Instrument names for one state, in layer order. Empty when that state is missing. */
    public String instrumentList(String stateId) {
        StateDef state = states.get(stateId);
        if (state == null) {
            return "";
        }
        return String.join(",", state.layers().stream().map(state::instrument).toList());
    }

    public long barTicks() {
        return io.github.sosgames.athenacl.events.TimeBase.barTicks(numerator, denominator);
    }

    public StateDef state(String id) {
        return states.get(id);
    }

    public String firstState() {
        return states.keySet().iterator().next();
    }

    public OverlayDef overlay(String id) {
        return overlays.get(id);
    }

    /**
     * The rule for this pair. A named state beats {@code *}, and a named
     * {@code from} beats a named {@code to}, so leaving combat can wind down
     * without stealing the beat rule that enters combat.
     */
    public Rule ruleFor(String from, String to) {
        Rule best = null;
        int bestScore = -1;
        for (Rule rule : rules) {
            if (!rule.matches(from, to)) {
                continue;
            }
            int score = rule.score();
            if (score > bestScore) {
                best = rule;
                bestScore = score;
            }
        }
        return best;
    }

    /** Grid for this pair, or the theme default when no rule matches. */
    public Quantize quantize(String from, String to) {
        Rule rule = ruleFor(from, to);
        return rule == null ? defaultQuantize : rule.quantize();
    }

    /** Grid for this destination when the previous state is unknown. */
    public Quantize quantizeTo(String to) {
        return quantize("*", to);
    }

    public record StateDef(String id, Key key, int tempo, List<String> layers, String stinger,
                           Map<String, Integer> programs, Map<String, String> instruments) {
        public StateDef(String id, Key key, int tempo, List<String> layers, String stinger) {
            this(id, key, tempo, layers, stinger, Map.of(), Map.of());
        }

        public StateDef(String id, Key key, int tempo, List<String> layers, String stinger,
                        Map<String, Integer> programs) {
            this(id, key, tempo, layers, stinger, programs, Map.of());
        }

        public StateDef {
            layers = List.copyOf(layers);
            programs = programs == null || programs.isEmpty()
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(programs));
            instruments = instruments == null || instruments.isEmpty()
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(instruments));
        }

        public int program(String layer, int fallback) {
            return programs.getOrDefault(layer, fallback);
        }

        /** Kit instrument for this layer. The layer name is the fallback. */
        public String instrument(String layer) {
            String name = instruments.get(layer);
            if (name == null || name.isBlank()) {
                return layer;
            }
            return name;
        }
    }

    public record StingerDef(String id, String pitch, String duration, int degree, int cooldownBars) {
        public StingerDef(String id, String pitch, String duration) {
            this(id, pitch, duration, -1, 0);
        }

        /** A degree of 0 or more follows the state's key. A pitch-only stinger does not. */
        public boolean followsKey() {
            return degree >= 0;
        }
    }

    /**
     * One transition rule. {@code to} may be {@code *} for every state.
     * {@code bridge} is empty when the change is only a grid and a retract flag.
     */
    public record Rule(String from, List<String> to, Quantize quantize, boolean retract,
                       String bridge, int crossfadeBars) {
        public Rule(String from, List<String> to, Quantize quantize, boolean retract) {
            this(from, to, quantize, retract, "", 0);
        }

        public Rule(String from, List<String> to, Quantize quantize) {
            this(from, to, quantize, false, "", 0);
        }

        public Rule {
            to = List.copyOf(to);
            bridge = bridge == null ? "" : bridge;
        }

        boolean matches(String fromState, String toState) {
            boolean fromOk = "*".equals(from) || from.equals(fromState);
            boolean toOk = to.contains("*") || to.contains(toState);
            return fromOk && toOk;
        }

        /** Named from beats a wildcard, and a named to beats {@code *}. */
        int score() {
            int fromScore = "*".equals(from) ? 0 : 2;
            int toScore = to.contains("*") ? 0 : 1;
            return fromScore + toScore;
        }
    }

    /** A layer added on top of the main state. {@code loudness} is the extra LU, at most 1. */
    public record OverlayDef(String id, List<String> layers, double loudness) {
        public OverlayDef {
            layers = List.copyOf(layers);
        }
    }

    /**
     * A request at this bar and beat. {@code state} is empty when the line only
     * sets an overlay. {@code overlay} is empty when it only sets the state.
     */
    public record Control(int bar, int beat, String state, double intensity, String overlay, double overlayLevel) {
        public Control(int bar, int beat, String state, double intensity) {
            this(bar, beat, state, intensity, "", 0);
        }

        public boolean hasState() {
            return state != null && !state.isBlank();
        }

        public boolean hasOverlay() {
            return overlay != null && !overlay.isBlank();
        }
    }

    /** The state an empty script stays on, and how hard that music plays. */
    public record Hold(String state, double intensity) {
    }
}
