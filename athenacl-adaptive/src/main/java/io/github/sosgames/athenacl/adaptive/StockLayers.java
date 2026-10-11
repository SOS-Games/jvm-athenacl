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
import io.github.sosgames.athenacl.theory.Key;

import java.util.List;

/**
 * Layers a theme can name, plus a stinger on the bar a state is entered.
 * The lead plays motif {@code m1} every {@code motif.every} bars. Between those statements it
 * keeps a short scale line, so the melody does not drop out. A sentence is idea, repeat,
 * a fragment moved up, then the idea backwards. Entering a state starts that count over,
 * so a later window matches the full render.
 * Combat uses the same motif turned upside down and shortened, with a trumpet, taiko on
 * every beat, horn stabs, and a kick-snare drum pattern.
 * A dorian pad also sounds the sixth, so the key guess can tell dorian from aeolian.
 */
public final class StockLayers {
    private StockLayers() {
    }

    static void stinger(Theme theme, Theme.StateDef state, Director.BarInfo bar, Theme.StingerDef stinger,
                        long start, long seed, Director.Ids ids, List<Director.Played> notes) {
        long id = ids.take();
        int midi = PitchName.midi(stinger.pitch());
        notes.add(played(id, start, stinger.duration(), midi, PitchName.name(midi), velocity(bar.intensity()),
                3, "stinger", bar, seed, state.id(), "stinger"));
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
                        ? lead(state, info, start, seed, ids, notes, degree, phraseBar)
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
                default -> degree;
            };
        }
        return degree;
    }

    private static void pad(Theme.StateDef state, Director.BarInfo info, long start, long seed,
                            Director.Ids ids, List<Director.Played> notes) {
        int[] degrees = "dorian".equals(state.key().mode().id()) ? new int[] {0, 2, 4, 5} : new int[] {0, 2, 4};
        for (int degree : degrees) {
            long id = ids.take();
            int midi = scaleMidi(state.key(), degree, 4);
            notes.add(played(id, start, "1/1", midi, PitchName.name(midi), velocity(info.intensity()),
                    0, "pad", info, seed, state.id(), "chord-tone"));
        }
    }

    private static void bass(Theme.StateDef state, Director.BarInfo info, long start, long seed,
                             Director.Ids ids, List<Director.Played> notes) {
        int midi = scaleMidi(state.key(), 0, 2);
        int step = combat(state) ? 1 : 2;
        for (int beat = 0; beat < 4; beat += step) {
            long id = ids.take();
            notes.add(played(id, start + beat * TimeBase.PPQ, "1/4", midi, PitchName.name(midi),
                    velocity(info.intensity()), 1, "bass", info, seed, state.id(), "root"));
        }
    }

    private static int lead(Theme.StateDef state, Director.BarInfo info, long start, long seed,
                            Director.Ids ids, List<Director.Played> notes, int previous, int phraseBar) {
        Motif motif = MotifGenerator.generate(state.key().pitchClassNames().size(), seed);
        String chain = PhrasePlan.chain(state.id(), phraseBar);
        Motif developed = PhrasePlan.develop(motif, chain);
        long cursor = start;
        for (MotifNote tone : developed.notes()) {
            long id = ids.take();
            int midi = scaleMidi(state.key(), Math.floorMod(tone.degree(), motif.steps()), 5);
            notes.add(played(id, cursor, tone.duration(), midi, PitchName.name(midi), velocity(info.intensity()),
                    2, "lead", info, seed, state.id(), "scale-step", chain));
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
            default -> layer.equals("pad") ? 48 : 0;
        };
        return state.program(layer, fallback);
    }

    private static void drone(Theme.StateDef state, Director.BarInfo info, long start, long seed,
                              Director.Ids ids, List<Director.Played> notes) {
        long id = ids.take();
        int midi = scaleMidi(state.key(), 0, 2);
        notes.add(played(id, start, "1/1", midi, PitchName.name(midi), velocity(info.intensity()),
                4, "drone", info, seed, state.id(), "drone"));
    }

    private static void harp(Theme.StateDef state, Director.BarInfo info, long start, long seed,
                             Director.Ids ids, List<Director.Played> notes) {
        int[] degrees = {0, 2, 4, 2};
        for (int beat = 0; beat < degrees.length; beat++) {
            long id = ids.take();
            int midi = scaleMidi(state.key(), degrees[beat], 4);
            notes.add(played(id, start + beat * TimeBase.PPQ, "1/4", midi, PitchName.name(midi),
                    velocity(info.intensity()), 6, "harp", info, seed, state.id(), "harp"));
        }
    }

    private static void taiko(Theme.StateDef state, Director.BarInfo info, long start, long seed,
                              Director.Ids ids, List<Director.Played> notes) {
        int midi = 45;
        int step = combat(state) ? 1 : 2;
        int vel = combat(state) ? Math.max(velocity(info.intensity()), 114) : velocity(info.intensity());
        for (int beat = 0; beat < 4; beat += step) {
            long id = ids.take();
            notes.add(played(id, start + beat * TimeBase.PPQ, "1/8", midi, PitchName.name(midi),
                    vel, 5, "taiko", info, seed, state.id(), "taiko"));
        }
    }

    private static void brass(Theme.StateDef state, Director.BarInfo info, long start, long seed,
                              Director.Ids ids, List<Director.Played> notes) {
        if (combat(state)) {
            int vel = Math.max(velocity(info.intensity()), 110);
            for (int beat = 0; beat < 4; beat += 2) {
                for (int degree : new int[] {0, 4}) {
                    long id = ids.take();
                    int midi = scaleMidi(state.key(), degree, 4);
                    notes.add(played(id, start + beat * TimeBase.PPQ, "1/8", midi, PitchName.name(midi),
                            vel, 7, "brass", info, seed, state.id(), "brass"));
                }
            }
            return;
        }
        for (int degree : new int[] {0, 4}) {
            long id = ids.take();
            int midi = scaleMidi(state.key(), degree, 4);
            notes.add(played(id, start, "1/1", midi, PitchName.name(midi), velocity(info.intensity()),
                    7, "brass", info, seed, state.id(), "brass"));
        }
    }

    private static void drums(Theme.StateDef state, Director.BarInfo info, long start, long seed,
                              Director.Ids ids, List<Director.Played> notes) {
        if (combat(state)) {
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

    private static boolean combat(Theme.StateDef state) {
        return "combat".equals(state.id());
    }

    private static int scaleMidi(Key key, int degree, int octave) {
        String name = key.pitchClassNames().get(degree) + octave;
        return PitchName.midi(name);
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
