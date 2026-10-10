// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.debug;

import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.events.Score;

import java.util.ArrayList;
import java.util.List;

/** Filter note lines from an event log. {@code pitch=E4} matches the pitch name. */
public final class Inspect {
    private Inspect() {
    }

    public static List<String> lines(Score score, String where) {
        String key = null;
        String value = null;
        if (where != null && !where.isBlank()) {
            int eq = where.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException("where must look like pitch=E4");
            }
            key = where.substring(0, eq).trim();
            value = where.substring(eq + 1).trim();
        }
        List<String> lines = new ArrayList<>();
        for (NoteEvent note : score.notes()) {
            if (key == null || matches(note, key, value)) {
                lines.add(line(note));
            }
        }
        return lines;
    }

    public static String line(NoteEvent note) {
        return "note id=" + note.id()
                + " bar=" + note.bar()
                + " beat=" + note.beat()
                + " layer=" + note.layer()
                + " pitch=" + note.pitchName()
                + " dur=" + note.duration()
                + " vel=" + note.velocity();
    }

    private static boolean matches(NoteEvent note, String key, String value) {
        return switch (key) {
            case "pitch", "pitchName" -> note.pitchName().equalsIgnoreCase(value)
                    || Integer.toString(note.pitch()).equals(value);
            case "id" -> Long.toString(note.id()).equals(value);
            case "bar" -> Integer.toString(note.bar()).equals(value);
            case "layer" -> note.layer().equals(value);
            case "velocity", "vel" -> Integer.toString(note.velocity()).equals(value);
            case "channel" -> Integer.toString(note.channel()).equals(value);
            default -> false;
        };
    }
}
