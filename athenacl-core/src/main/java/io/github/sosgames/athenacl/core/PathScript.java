// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Runs a path-only athenaCL script: {@code pin} builds a path, {@code eln} writes MIDI.
 * Texture commands ({@code tin}, {@code tie}, and the rest) are refused. They need the
 * texture slice. With no {@code total}, each pitch group lasts one second.
 */
public final class PathScript {
    private static final Set<String> TEXTURES = Set.of(
            "emo", "tin", "tie", "tmo", "ticp", "tio", "timode", "elh", "tcn");
    private static final Set<String> KNOWN = Set.of(
            "pin", "tempo", "total", "velocity", "pan", "program", "pidf", "tpsd", "tmsd", "eln");

    private PathScript() {
    }

    public record Result(byte[] midi, int notes, int tracks) {
    }

    public static Result run(String text, String title) {
        Map<String, PolyPath> paths = new LinkedHashMap<>();
        int tempo = 120;
        Double total = null;
        int velocity = 90;
        int pan = 64;
        int program = 0;
        boolean eln = false;
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
            if (TEXTURES.contains(command)) {
                throw new ScriptException("ATHENA_TEXTURE", where,
                        "texture command " + command + " is not in this slice",
                        "Use a path-only script such as conformance/paths/c-major.ath.");
            }
            if (!KNOWN.contains(command)) {
                throw new ScriptException("ATHENA_UNKNOWN", where,
                        "unknown command: " + command,
                        "Path-only commands are pin, tempo, total, velocity, pan, program, pidf, and eln.");
            }
            switch (command) {
                case "pin" -> pin(paths, tokens, where);
                case "pidf" -> pidf(paths, line, where);
                case "tempo" -> tempo = integer(tokens, where, 1, 400);
                case "total" -> total = number(tokens, where);
                case "velocity" -> velocity = integer(tokens, where, 1, 127);
                case "pan" -> pan = integer(tokens, where, 0, 127);
                case "program" -> program = integer(tokens, where, 0, 127);
                case "eln" -> eln = true;
                default -> {
                }
            }
        }
        if (paths.isEmpty()) {
            throw new ScriptException("ATHENA_SYNTAX", "pin", "a path script needs a pin",
                    "Add pin <name> c4,e4,g4.");
        }
        if (!eln) {
            throw new ScriptException("ATHENA_SYNTAX", "eln", "a path script needs eln",
                    "End the script with eln.");
        }
        List<MidiScore.Track> tracks = new ArrayList<>();
        int notes = 0;
        for (PolyPath path : paths.values()) {
            double seconds = total == null ? path.size() : total;
            EventList events = EventList.fromPath(path, seconds, velocity, pan);
            List<MidiScore.Note> scored = events.notes();
            notes += scored.size();
            tracks.add(new MidiScore.Track(path.name(), program, null, scored));
        }
        MidiScore.Bytes midi = MidiScore.write(tracks, title, tempo);
        return new Result(midi.midi(), notes, midi.tracks());
    }

    private static void pin(Map<String, PolyPath> paths, String[] tokens, String where) {
        if (tokens.length < 3) {
            throw new ScriptException("ATHENA_SYNTAX", where, "pin needs a name and a pitch group",
                    "Try pin scale c4,e4,g4.");
        }
        PolyPath path = new PolyPath(tokens[1]);
        for (int i = 2; i < tokens.length; i++) {
            List<String> names = new ArrayList<>();
            for (String name : tokens[i].split(",")) {
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
            try {
                path.add(Multiset.names(names));
            } catch (RuntimeException e) {
                throw new ScriptException("ATHENA_SYNTAX", where, "bad pitch group " + tokens[i],
                        "Use names like c4, d$4, f#4, or g~4.");
            }
        }
        paths.put(path.name(), path);
    }

    private static void pidf(Map<String, PolyPath> paths, String line, String where) {
        String[] tokens = line.trim().split("\\s+", 3);
        if (tokens.length < 3) {
            throw new ScriptException("ATHENA_SYNTAX", where, "pidf needs a path and weights",
                    "Try pidf low 1,2.");
        }
        PolyPath path = paths.get(tokens[1]);
        if (path == null) {
            throw new ScriptException("ATHENA_SYNTAX", where, "unknown path " + tokens[1],
                    "pin the path before pidf.");
        }
        String[] weights = tokens[2].replace(" ", "").split(",");
        if (weights.length != path.size()) {
            throw new ScriptException("ATHENA_SYNTAX", where,
                    "pidf has " + weights.length + " weights for " + path.size() + " groups",
                    "Give one weight per pitch group.");
        }
        for (int i = 0; i < weights.length; i++) {
            path.groups().get(i).setDur(Double.parseDouble(weights[i]));
        }
    }

    private static int integer(String[] tokens, String where, int min, int max) {
        double value = number(tokens, where);
        int rounded = (int) value;
        if (rounded != value || rounded < min || rounded > max) {
            throw new ScriptException("ATHENA_SYNTAX", where, tokens[0] + " is out of range",
                    "Use a whole number from " + min + " to " + max + ".");
        }
        return rounded;
    }

    private static double number(String[] tokens, String where) {
        if (tokens.length < 2) {
            throw new ScriptException("ATHENA_SYNTAX", where, tokens[0] + " needs a number",
                    "Give one number after " + tokens[0] + ".");
        }
        return Double.parseDouble(tokens[1]);
    }
}
