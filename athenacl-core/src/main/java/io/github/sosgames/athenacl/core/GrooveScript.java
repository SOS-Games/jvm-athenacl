// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.core;

import io.github.sosgames.athenacl.compat.Py;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Plays a LineGroove percussion script: {@code emo mp}, {@code tin}, {@code tie r},
 * and {@code eln}. Each instrument is one repeating pulse on MIDI channel 10.
 * Creating an instrument scores it twice and throws those notes away: once with the
 * default pulse, then again after {@code tie r}. {@code eln} scores it a third time,
 * and that pass is the file. The generators are not reseeded between passes.
 * A pulse with accent 0 is a rest. The note in the file lasts the sustain, not the
 * gap until the next hit. Amplitude is the default beta draw, mapped with {@code round(amp * 124)}.
 */
public final class GrooveScript {
    private static final Set<String> REFUSED = Set.of(
            "ticp", "tio", "timode", "elh", "tcn", "pin", "tempo", "total", "velocity", "pan", "program", "pidf");
    private static final double LENGTH = 20.0;
    private static final int BPM = 120;
    private static final String DEFAULT_RHYTHM = "pt,(c,4),(bg,rp,(1,1,2,3)),(c,1),(c,.75)";

    private GrooveScript() {
    }

    public static PathScript.Result run(String text, String title) {
        Score score = new Score();
        int lineNo = 0;
        for (String raw : text.split("\\R")) {
            lineNo++;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] tokens = line.split("\\s+");
            String command = tokens[0].toLowerCase(Locale.ROOT);
            String where = "line " + lineNo;
            switch (command) {
                case "tpsd" -> score.parameterSeed = integer(tokens, where);
                case "tmsd" -> score.textureSeed = integer(tokens, where);
                case "emo" -> score.mode(tokens, where);
                case "tin" -> score.tin(tokens, where);
                case "tie" -> score.tie(line, tokens, where);
                case "tmo" -> score.module(tokens, where);
                case "eln" -> score.eln = true;
                default -> {
                    if (REFUSED.contains(command)) {
                        throw new ScriptException("ATHENA_TEXTURE", where,
                                "command " + command + " is not in this slice",
                                "This slice plays emo mp, tin, tie r, tpsd, tmsd, and eln.");
                    }
                    throw new ScriptException("ATHENA_UNKNOWN", where,
                            "unknown command: " + command,
                            "LineGroove commands are emo, tin, tie, tmo, tpsd, tmsd, and eln.");
                }
            }
        }
        if (score.voices.isEmpty()) {
            throw new ScriptException("ATHENA_SYNTAX", "tin", "a texture script needs a tin",
                    "Add tin a 36.");
        }
        if (!score.eln) {
            throw new ScriptException("ATHENA_SYNTAX", "eln", "a texture script needs eln",
                    "End the script with eln.");
        }
        return score.write(title);
    }

    private static long integer(String[] tokens, String where) {
        if (tokens.length < 2) {
            throw new ScriptException("ATHENA_SYNTAX", where, "expected a number",
                    "Try tpsd 7.");
        }
        try {
            return Long.parseLong(tokens[1]);
        } catch (NumberFormatException e) {
            throw new ScriptException("ATHENA_SYNTAX", where, "expected a number",
                    "Try tpsd 7.");
        }
    }

    private static final class Score {
        private final List<Voice> voices = new ArrayList<>();
        private long parameterSeed;
        private long textureSeed;
        private boolean percussion;
        private boolean eln;
        private Voice current;

        void mode(String[] tokens, String where) {
            if (tokens.length < 2 || !"mp".equalsIgnoreCase(tokens[1])) {
                throw new ScriptException("ATHENA_TEXTURE", where,
                        "event mode " + (tokens.length < 2 ? "" : tokens[1]) + " is not in this slice",
                        "Use emo mp.");
            }
            percussion = true;
        }

        void module(String[] tokens, String where) {
            String name = tokens.length < 2 ? "" : tokens[1];
            if (!"lg".equalsIgnoreCase(name) && !"linegroove".equalsIgnoreCase(name)) {
                throw new ScriptException("ATHENA_TEXTURE", where,
                        "texture module " + name + " is not in this slice",
                        "Use tmo lg.");
            }
        }

        void tin(String[] tokens, String where) {
            if (!percussion) {
                throw new ScriptException("ATHENA_SYNTAX", where, "tin needs emo mp first",
                        "Set emo mp before tin.");
            }
            if (tokens.length < 3) {
                throw new ScriptException("ATHENA_SYNTAX", where, "tin needs a name and a drum",
                        "Try tin a 36.");
            }
            int drum;
            try {
                drum = Integer.parseInt(tokens[2]);
            } catch (NumberFormatException e) {
                throw new ScriptException("ATHENA_SYNTAX", where, "tin needs a drum number",
                        "Try tin a 36.");
            }
            current = new Voice(tokens[1], drum);
            voices.add(current);
        }

        void tie(String line, String[] tokens, String where) {
            if (current == null) {
                throw new ScriptException("ATHENA_SYNTAX", where, "tie needs a tin first",
                        "Add tin a 36 before tie.");
            }
            if (tokens.length < 2 || !"r".equalsIgnoreCase(tokens[1])) {
                throw new ScriptException("ATHENA_TEXTURE", where,
                        "tie " + (tokens.length < 2 ? "" : tokens[1]) + " is not in this slice",
                        "This slice sets the rhythm with tie r.");
            }
            int cut = line.toLowerCase(Locale.ROOT).indexOf(" r ");
            String spec = cut < 0 ? "" : line.substring(cut + 3).trim();
            ParameterObjects.rhythm(spec);
            current.rhythm = spec;
        }

        PathScript.Result write(String title) {
            ParameterObjects.Streams streams = ParameterObjects.Streams.seeds(parameterSeed, textureSeed);
            for (Voice voice : voices) {
                if (voice.rhythm == null) {
                    throw new ScriptException("ATHENA_SYNTAX", voice.name, "a texture needs a rhythm",
                            "Add tie r pt,(c,2),(c,1),(c,1).");
                }
                voice.play(DEFAULT_RHYTHM, streams);
                voice.play(voice.rhythm, streams);
            }
            List<MidiScore.Track> tracks = new ArrayList<>();
            int notes = 0;
            for (Voice voice : voices) {
                List<MidiScore.Note> scored = voice.play(voice.rhythm, streams);
                notes += scored.size();
                tracks.add(new MidiScore.Track(voice.name, 0, 10, scored));
            }
            MidiScore.Bytes midi = MidiScore.write(tracks, title, BPM);
            return new PathScript.Result(midi.midi(), notes, midi.tracks());
        }
    }

    private static final class Voice {
        private final String name;
        private final int pitch;
        private String rhythm;
        private final ParameterObjects.Value amplitude = ParameterObjects.betaAmplitude();

        Voice(String name, int pitch) {
            this.name = name;
            this.pitch = pitch;
        }

        List<MidiScore.Note> play(String spec, ParameterObjects.Streams streams) {
            ParameterObjects.RhythmPo rhythm = ParameterObjects.rhythm(spec);
            List<MidiScore.Note> notes = new ArrayList<>();
            double time = 0;
            while (time < LENGTH) {
                ParameterObjects.Rhythm pulse = rhythm.next(BPM, streams);
                if (pulse.acc() == 0) {
                    time += pulse.dur();
                    continue;
                }
                double amp = amplitude.next(streams) * pulse.acc();
                int velocity = (int) Py.round(amp * 124.0);
                if (velocity < 0) {
                    velocity = 0;
                } else if (velocity > 127) {
                    velocity = 127;
                }
                int pan = (int) Py.round(0.5 * 127.0);
                notes.add(new MidiScore.Note(time, pulse.sus(), velocity, pitch, pan));
                time += pulse.dur();
            }
            return notes;
        }
    }
}
