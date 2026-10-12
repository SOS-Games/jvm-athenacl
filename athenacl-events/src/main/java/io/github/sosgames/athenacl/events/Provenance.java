// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.events;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A sidecar for one render. It is not part of {@code events.jsonl}, so turning it on
 * does not change the hash. {@code notes} is the full record. {@code trace} is only
 * the id, bar, and layer.
 */
public final class Provenance {
    private Provenance() {
    }

    public static byte[] notes(Score score) {
        StringBuilder text = new StringBuilder();
        for (NoteEvent note : ordered(score)) {
            text.append("{\"id\":").append(note.id())
                    .append(",\"bar\":").append(note.bar())
                    .append(",\"layer\":\"").append(escape(note.layer())).append('"')
                    .append(",\"seed\":\"").append(escape(note.seedPath())).append('"')
                    .append(",\"pitch\":").append(note.pitch());
            if (note.motif() != null && !note.motif().isBlank()) {
                text.append(",\"motif\":\"").append(escape(note.motif())).append('"');
            }
            text.append("}\n");
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] trace(Score score) {
        StringBuilder text = new StringBuilder();
        for (NoteEvent note : ordered(score)) {
            text.append("{\"id\":").append(note.id())
                    .append(",\"bar\":").append(note.bar())
                    .append(",\"layer\":\"").append(escape(note.layer())).append("\"}\n");
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static int count(Score score) {
        return score.notes().size();
    }

    private static List<NoteEvent> ordered(Score score) {
        List<NoteEvent> notes = new ArrayList<>(score.notes());
        notes.sort(Comparator.comparingLong(NoteEvent::id));
        return notes;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
