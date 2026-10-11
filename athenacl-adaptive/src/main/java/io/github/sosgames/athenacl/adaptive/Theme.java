// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

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
        List<String> kits) {

    public Theme {
        states = Collections.unmodifiableMap(new LinkedHashMap<>(states));
        stingers = Collections.unmodifiableMap(new LinkedHashMap<>(stingers));
        rules = List.copyOf(rules);
        timeline = List.copyOf(timeline);
        warnings = List.copyOf(warnings);
        kits = kits == null ? List.of() : List.copyOf(kits);
    }

    public Theme withBars(int bars) {
        return new Theme(id, bars, bpm, numerator, denominator, states, stingers, defaultQuantize, rules,
                timeline, preset, loudness, warnings, motifEvery, hold, kits);
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

    /** Rule for this destination, or the theme default. Specific {@code to} wins. */
    public Quantize quantizeTo(String to) {
        for (Rule rule : rules) {
            if (rule.to().contains(to)) {
                return rule.quantize();
            }
        }
        return defaultQuantize;
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

    public record StingerDef(String id, String pitch, String duration) {
    }

    public record Rule(String from, List<String> to, Quantize quantize, boolean retract) {
        public Rule(String from, List<String> to, Quantize quantize) {
            this(from, to, quantize, false);
        }

        public Rule {
            to = List.copyOf(to);
        }
    }

    /** The rule that names this destination, or null when the theme default applies. */
    public Rule ruleFor(String to) {
        for (Rule rule : rules) {
            if (rule.to().contains(to)) {
                return rule;
            }
        }
        return null;
    }

    /** A request to be in {@code state} at this bar and beat, with an optional intensity. */
    public record Control(int bar, int beat, String state, double intensity) {
    }

    /** The state an empty script stays on, and how hard that music plays. */
    public record Hold(String state, double intensity) {
    }
}
