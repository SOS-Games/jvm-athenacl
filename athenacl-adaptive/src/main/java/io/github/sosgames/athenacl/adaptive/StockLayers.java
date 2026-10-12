// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import io.github.sosgames.athenacl.events.DefaultRandom;
import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.events.PitchName;
import io.github.sosgames.athenacl.events.RandomSource;
import io.github.sosgames.athenacl.events.TimeBase;
import io.github.sosgames.athenacl.motif.Motif;
import io.github.sosgames.athenacl.motif.MotifGenerator;
import io.github.sosgames.athenacl.motif.MotifNote;
import io.github.sosgames.athenacl.motif.PhrasePlan;
import io.github.sosgames.athenacl.motif.hook.HookCache;
import io.github.sosgames.athenacl.motif.hook.HookSettings;
import io.github.sosgames.athenacl.theory.ClosedVoiceLeader;
import io.github.sosgames.athenacl.theory.Key;
import io.github.sosgames.athenacl.theory.Progression;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Layers a theme can name, plus a stinger on the bar a state is entered.
 * The lead plays motif {@code m1} every {@code motif.every} bars. Between those statements it
 * keeps a short scale line, so the melody does not drop out. A sentence is idea, repeat,
 * a fragment moved up, then the idea backwards. Entering a state starts that count over,
 * so a later window matches the full render.
 * Combat uses the same motif turned upside down and shortened, with a trumpet, taiko on
 * every beat, horn stabs, and a kick-snare drum pattern.
 * A pad stacks the chord of this bar. The bass plays that chord's root, unless the state asked for a pedal.
 * When the theme has {@code hooks:}, the lead's idea is the critic's pick. That pick is
 * made once per state, before the bar loop. A theme without the block still calls
 * {@code MotifGenerator}, so L7's notes stay the same.
 * {@code arp} is an even grid: eighths, or sixteenths in combat. {@code bell} is a slow celesta.
 */
public final class StockLayers {
    private StockLayers() {
    }

    /** Warms the hook cache before any bar is built. No-op when the theme has no hooks. */
    static void prepareHooks(Theme theme, long seed) {
        HookSettings hooks = theme.hooks();
        if (hooks == null) {
            return;
        }
        for (Theme.StateDef state : theme.states().values()) {
            HookCache.select(state.key(), seed, state.id(), hooks.forState(state.id()));
        }
    }

    static void stinger(Theme theme, Theme.StateDef state, Director.BarInfo bar, Theme.StingerDef stinger,
                        long start, long seed, Director.Ids ids, List<Director.Played> notes) {
        long id = ids.take();
        int midi = stinger.followsKey()
                ? harmony(state, bar.bar()).midi(stinger.degree(), 5)
                : PitchName.midi(stinger.pitch());
        notes.add(played(id, start, stinger.duration(), midi, PitchName.name(midi), velocity(bar.intensity()),
                3, "stinger", bar, seed, state.id(), "stinger"));
    }

    /** A quiet pulse on top of the state. Short enough that it does not sit on the next hit. */
    static void heartbeat(Theme.StateDef state, Director.BarInfo info, long start, long seed,
                          Director.Ids ids, List<Director.Played> notes, double level) {
        int velocity = Math.max(1, Math.min(28, 12 + (int) Math.round(level * 10)));
        long id = ids.take();
        notes.add(played(id, start, "1/16", 36, "C2", velocity, 8, "heartbeat", info, seed, state.id(), "heartbeat"));
    }

    /** Four falling steps on the bar combat is left. Each one is quieter than the last. */
    static void windDown(Theme.StateDef state, Director.BarInfo info, long start, long seed,
                         Director.Ids ids, List<Director.Played> notes) {
        int[] degrees = {4, 2, 1, 0};
        int[] velocity = {36, 30, 24, 18};
        for (int i = 0; i < degrees.length; i++) {
            long id = ids.take();
            int midi = harmony(state, info.bar()).midi(degrees[i], 4);
            notes.add(played(id, start + i * TimeBase.PPQ, "1/4", midi, PitchName.name(midi),
                    velocity[i], 11, "bridge", info, seed, state.id(), "wind-down"));
        }
    }

    static int bar(Theme theme, Theme.StateDef state, Director.BarInfo info, long start, long seed,
                   Director.Ids ids, List<Director.Played> notes, int leadDegree, boolean drumsOn,
                   int phraseBar, boolean playMotif) {
        int degree = leadDegree;
        for (String layer : state.layers()) {
            degree = switch (layer) {
                case "pad" -> {
                    pad(state, info, start, seed, ids, notes);
                    yield degree;
                }
                case "bass" -> {
                    bass(state, info, start, seed, ids, notes);
                    yield degree;
                }
                case "lead" -> playMotif
                        ? lead(theme, state, info, start, seed, ids, notes, degree, phraseBar)
                        : scaleLead(state, info, start, seed, ids, notes, degree);
                case "drums" -> {
                    if (drumsOn) {
                        drums(state, info, start, seed, ids, notes);
                    }
                    yield degree;
                }
                case "drone" -> {
                    drone(state, info, start, seed, ids, notes);
                    yield degree;
                }
                case "harp" -> {
                    harp(state, info, start, seed, ids, notes);
                    yield degree;
                }
                case "taiko" -> {
                    taiko(state, info, start, seed, ids, notes);
                    yield degree;
                }
                case "brass" -> {
                    brass(state, info, start, seed, ids, notes);
                    yield degree;
                }
                case "arp" -> {
                    arp(state, info, start, seed, ids, notes);
                    yield degree;
                }
                case "bell" -> {
                    bell(state, info, start, seed, ids, notes);
                    yield degree;
                }
                case "chords" -> {
                    chords(theme, state, info, start, seed, ids, notes);
                    yield degree;
                }
                default -> degree;
            };
        }
        return degree;
    }

    private static void pad(Theme.StateDef state, Director.BarInfo info, long start, long seed,
                            Director.Ids ids, List<Director.Played> notes) {
        for (int midi : voicing(state, info.bar())) {
            long id = ids.take();
            notes.add(played(id, start, "1/1", midi, PitchName.name(midi), velocity(info.intensity()),
                    0, "pad", info, seed, state.id(), "chord-tone"));
        }
    }

    private static void bass(Theme.StateDef state, Director.BarInfo info, long start, long seed,
                             Director.Ids ids, List<Director.Played> notes) {
        Progression.Chord chord = harmony(state, info.bar());
        int midi = chord.pedal() ? 12 * 3 + state.key().tonicPc() : chord.rootMidi(2);
        int step = count(state, "density.bass.step", 2);
        for (int beat = 0; beat < 4; beat += step) {
            long id = ids.take();
            notes.add(played(id, start + beat * TimeBase.PPQ, "1/4", midi, PitchName.name(midi),
                    velocity(info.intensity()), 1, "bass", info, seed, state.id(), "root"));
        }
    }

    private static int lead(Theme theme, Theme.StateDef state, Director.BarInfo info, long start, long seed,
                            Director.Ids ids, List<Director.Played> notes, int previous, int phraseBar) {
        Motif motif;
        HookCache.HookChoice choice = null;
        if (theme.hooks() != null) {
            choice = HookCache.select(state.key(), seed, state.id(), theme.hooks().forState(state.id()));
            motif = choice.motif();
        } else {
            motif = MotifGenerator.generate(state.key().pitchClassNames().size(), seed);
        }
        String chain = PhrasePlan.chain(state.id(), phraseBar);
        Motif developed = PhrasePlan.develop(motif, chain);
        String label = choice == null ? chain : choice.label(chain);
        long cursor = start;
        for (MotifNote tone : developed.notes()) {
            long id = ids.take();
            int midi = scaleMidi(state.key(), tone.degree(), 5);
            notes.add(played(id, cursor, tone.duration(), midi, PitchName.name(midi), velocity(info.intensity()),
                    2, "lead", info, seed, state.id(), "scale-step", label));
            cursor += TimeBase.durationTicks(tone.duration());
        }
        return previous < 0 ? 0 : previous;
    }

    /** Scale steps on the bars the motif sits out. The line remembers its last degree. */
    private static int scaleLead(Theme.StateDef state, Director.BarInfo info, long start, long seed,
                                 Director.Ids ids, List<Director.Played> notes, int previous) {
        int steps = state.key().pitchClassNames().size();
        int degree = previous;
        int notesInBar = info.intensity() >= 0.5 ? 4 : 2;
        for (int i = 0; i < notesInBar; i++) {
            int beat = notesInBar == 4 ? i : i * 2;
            RandomSource rng = DefaultRandom.of(seed).fork(state.id().hashCode(), "lead".hashCode(), info.bar(), i);
            if (degree < 0) {
                degree = rng.nextInt(steps);
            } else {
                degree = Math.floorMod(degree + rng.nextInt(3) - 1, steps);
            }
            long id = ids.take();
            int midi = scaleMidi(state.key(), degree, 5);
            notes.add(played(id, start + beat * TimeBase.PPQ, "1/4", midi, PitchName.name(midi),
                    velocity(info.intensity()), 2, "lead", info, seed, state.id(), "scale-step"));
        }
        return degree;
    }

    public static int channel(String layer) {
        return switch (layer) {
            case "bass" -> 1;
            case "lead" -> 2;
            case "stinger" -> 3;
            case "drone" -> 4;
            case "taiko" -> 5;
            case "harp" -> 6;
            case "brass" -> 7;
            case "drums" -> 9;
            case "arp" -> 10;
            case "bell" -> 6;
            case "chords" -> 12;
            default -> 0;
        };
    }

    public static int program(Theme.StateDef state, String layer) {
        int fallback = switch (layer) {
            case "bass" -> 32;
            case "lead" -> 73;
            case "stinger" -> 56;
            case "drone" -> 49;
            case "taiko" -> 116;
            case "harp" -> 46;
            case "brass" -> 60;
            case "arp" -> 80;
            case "bell" -> 8;
            case "chords" -> 48;
            default -> layer.equals("pad") ? 48 : 0;
        };
        return state.program(layer, fallback);
    }

    private static void drone(Theme.StateDef state, Director.BarInfo info, long start, long seed,
                              Director.Ids ids, List<Director.Played> notes) {
        long id = ids.take();
        int octave = count(state, "drone.octave", 2);
        int midi = state.composition() != null && state.composition().has("drone.degree")
                ? scaleMidi(state.key(), state.composition().number("drone.degree", 0), octave)
                : harmony(state, info.bar()).rootMidi(octave);
        notes.add(played(id, start, "1/1", midi, PitchName.name(midi), velocity(info.intensity()),
                4, "drone", info, seed, state.id(), "drone"));
    }

    private static void harp(Theme.StateDef state, Director.BarInfo info, long start, long seed,
                             Director.Ids ids, List<Director.Played> notes) {
        Progression.Chord chord = harmony(state, info.bar());
        int[] degrees = {0, 2, 4, 2};
        for (int beat = 0; beat < degrees.length; beat++) {
            long id = ids.take();
            int midi = chord.midi(degrees[beat], 4);
            notes.add(played(id, start + beat * TimeBase.PPQ, "1/4", midi, PitchName.name(midi),
                    velocity(info.intensity()), 6, "harp", info, seed, state.id(), "harp"));
        }
    }

    private static void taiko(Theme.StateDef state, Director.BarInfo info, long start, long seed,
                              Director.Ids ids, List<Director.Played> notes) {
        int midi = 45;
        int step = count(state, "density.taiko.step", 2);
        int floor = count(state, "density.taiko.minVelocity", 0);
        int vel = floor > 0 ? Math.max(velocity(info.intensity()), floor) : velocity(info.intensity());
        for (int beat = 0; beat < 4; beat += step) {
            long id = ids.take();
            notes.add(played(id, start + beat * TimeBase.PPQ, "1/8", midi, PitchName.name(midi),
                    vel, 5, "taiko", info, seed, state.id(), "taiko"));
        }
    }

    private static void brass(Theme.StateDef state, Director.BarInfo info, long start, long seed,
                              Director.Ids ids, List<Director.Played> notes) {
        if (on(state, "density.brass.stab")) {
            int vel = Math.max(velocity(info.intensity()), 110);
            for (int beat = 0; beat < 4; beat += 2) {
                for (int midi : voicing(state, info.bar())) {
                    long id = ids.take();
                    notes.add(played(id, start + beat * TimeBase.PPQ, "1/8", midi, PitchName.name(midi),
                            vel, 7, "brass", info, seed, state.id(), "brass"));
                }
            }
            return;
        }
        for (int midi : voicing(state, info.bar())) {
            long id = ids.take();
            notes.add(played(id, start, "1/1", midi, PitchName.name(midi), velocity(info.intensity()),
                    7, "brass", info, seed, state.id(), "brass"));
        }
    }

    private static void drums(Theme.StateDef state, Director.BarInfo info, long start, long seed,
                              Director.Ids ids, List<Director.Played> notes) {
        if (on(state, "density.drums.fill")) {
            hit(ids, notes, info, seed, state, start, 0, "1/8", 36, 124);
            hit(ids, notes, info, seed, state, start, 2, "1/8", 36, 124);
            hit(ids, notes, info, seed, state, start, 1, "1/8", 38, 118);
            hit(ids, notes, info, seed, state, start, 3, "1/8", 38, 118);
            for (int eighth = 0; eighth < 8; eighth++) {
                long id = ids.take();
                notes.add(played(id, start + eighth * (TimeBase.PPQ / 2), "1/16", 42, PitchName.name(42),
                        96, 9, "drums", info, seed, state.id(), "drum-grid"));
            }
            return;
        }
        int midi = 36;
        for (int beat = 0; beat < 4; beat += 2) {
            long id = ids.take();
            notes.add(played(id, start + beat * TimeBase.PPQ, "1/16", midi, PitchName.name(midi),
                    110, 9, "drums", info, seed, state.id(), "drum-grid"));
        }
    }

    private static void hit(Director.Ids ids, List<Director.Played> notes, Director.BarInfo info, long seed,
                            Theme.StateDef state, long start, int beat, String duration, int midi, int velocity) {
        long id = ids.take();
        notes.add(played(id, start + beat * TimeBase.PPQ, duration, midi, PitchName.name(midi),
                velocity, 9, "drums", info, seed, state.id(), "drum-grid"));
    }

    /** Even notes on the eighth, or the sixteenth in combat. Every attack sits on that grid. */
    private static void arp(Theme.StateDef state, Director.BarInfo info, long start, long seed,
                            Director.Ids ids, List<Director.Played> notes) {
        int[] degrees = degrees(state, "density.arp.degrees", 0, 2, 4, 2);
        int steps = count(state, "density.arp.steps", 8);
        boolean sixteenths = steps >= 16;
        String duration = sixteenths ? "1/16" : "1/8";
        long cell = TimeBase.PPQ / (sixteenths ? 4 : 2);
        for (int i = 0; i < steps; i++) {
            long id = ids.take();
            int midi = harmony(state, info.bar()).midi(degrees[i % degrees.length], 5);
            notes.add(played(id, start + i * cell, duration, midi, PitchName.name(midi),
                    velocity(info.intensity()), 10, "arp", info, seed, state.id(), "arp"));
        }
    }

    /** The phrase chord for this bar, held. */
    private static void chords(Theme theme, Theme.StateDef state, Director.BarInfo info, long start, long seed,
                               Director.Ids ids, List<Director.Played> notes) {
        boolean cadence = harmony(state, info.bar()).cadence();
        for (int midi : voicing(state, info.bar())) {
            long id = ids.take();
            notes.add(played(id, start, "1/1", midi, PitchName.name(midi), velocity(info.intensity()),
                    12, "chords", info, seed, state.id(), cadence ? "cadence" : "chord"));
        }
    }

    /** Two held bell tones, the root and the fifth. */
    private static void bell(Theme.StateDef state, Director.BarInfo info, long start, long seed,
                             Director.Ids ids, List<Director.Played> notes) {
        for (int midi : voicing(state, info.bar())) {
            long id = ids.take();
            notes.add(played(id, start, "1/1", midi, PitchName.name(midi), velocity(info.intensity()),
                    6, "bell", info, seed, state.id(), "bell"));
        }
    }

    private static int[] degrees(Theme.StateDef state, String path, int... fallback) {
        if (state.composition() == null) {
            return fallback;
        }
        List<Integer> listed = state.composition().ints(path);
        if (listed.isEmpty()) {
            return fallback;
        }
        int[] values = new int[listed.size()];
        for (int i = 0; i < listed.size(); i++) {
            values[i] = listed.get(i);
        }
        return values;
    }

    private static int count(Theme.StateDef state, String path, int fallback) {
        return state.composition() == null ? fallback : state.composition().number(path, fallback);
    }

    private static boolean on(Theme.StateDef state, String path) {
        return state.composition() != null && state.composition().flag(path, false);
    }

    /**
     * Close voicing of this bar's chord, voice-led from bar 1 when the state asks for it.
     * {@code voicing.style: open5} is the only path that keeps a bare fifth.
     */
    private static int[] voicing(Theme.StateDef state, int bar) {
        if (state.composition() != null && "open5".equals(state.composition().text("voicing.style", ""))) {
            int root = harmony(state, bar).rootMidi(4);
            return new int[] {root, root + 7};
        }
        boolean lead = state.composition() == null || state.composition().flag("voicing.voiceLead", true);
        int[] previous = null;
        int[] current = new int[0];
        for (int at = 1; at <= Math.max(1, bar); at++) {
            int[] pcs = pitchClasses(harmony(state, at));
            if (!lead || previous == null || previous.length != pcs.length) {
                current = ClosedVoiceLeader.first(pcs);
            } else {
                current = ClosedVoiceLeader.next(pcs, previous);
            }
            previous = current;
        }
        return current;
    }

    private static int[] pitchClasses(Progression.Chord chord) {
        int[] intervals = chord.intervals();
        int[] pcs = new int[intervals.length];
        for (int i = 0; i < intervals.length; i++) {
            pcs[i] = Math.floorMod(chord.rootPc() + intervals[i], 12);
        }
        return pcs;
    }

    /** The chord this bar plays. A missing progression uses the eight-bar default. */
    private static Progression.Chord harmony(Theme.StateDef state, int bar) {
        Composition composition = state.composition();
        List<String> numerals = composition == null ? List.of() : composition.list("harmony.progression");
        int rhythm = composition == null ? 1 : composition.number("harmony.harmonicRhythm", 1);
        boolean leading = composition != null && composition.flag("scale.leadingTone", false);
        boolean pedal = composition != null && (composition.flag("harmony.pedal", false)
                || "pedal".equals(composition.text("harmony.style", "")));
        return Progression.of(state.key(), numerals, rhythm, leading, pedal, cadences(composition)).at(bar);
    }

    private static Map<Integer, String> cadences(Composition composition) {
        Map<Integer, String> marks = new LinkedHashMap<>();
        if (composition == null) {
            return marks;
        }
        String prefix = "harmony.cadences.";
        for (Map.Entry<String, Composition.Leaf> entry : composition.leaves().entrySet()) {
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

    private static int scaleMidi(Key key, int degree, int octave) {
        return key.degreeMidi(degree, octave);
    }

    private static int velocity(double intensity) {
        int value = 48 + (int) Math.round(intensity * 60);
        return Math.max(1, Math.min(127, value));
    }

    private static Director.Played played(long id, long start, String duration, int midi, String name, int velocity,
                                          int channel, String layer, Director.BarInfo info, long seed,
                                          String state, String why) {
        return played(id, start, duration, midi, name, velocity, channel, layer, info, seed, state, why, null);
    }

    private static Director.Played played(long id, long start, String duration, int midi, String name, int velocity,
                                          int channel, String layer, Director.BarInfo info, long seed,
                                          String state, String why, String motif) {
        long length = TimeBase.durationTicks(duration);
        int beat = (int) ((start % infoBarTicks(info)) / TimeBase.PPQ) + 1;
        String path = seed + "/" + state + "/" + layer + "/bar" + info.bar() + "/note#" + id;
        NoteEvent note = new NoteEvent(id, start, length, midi, name, velocity, channel, layer,
                info.bar(), Integer.toString(beat), duration, path, motif);
        return new Director.Played(note, why);
    }

    private static long infoBarTicks(Director.BarInfo info) {
        return TimeBase.barTicks(4, 4);
    }
}
