// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render;

import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.events.OutputSink;
import io.github.sosgames.athenacl.events.Score;

import java.nio.charset.StandardCharsets;

/** One grep-stable line per note. This is the {@code txt} format. */
public final class TextDump implements OutputSink {
    private final StringBuilder text = new StringBuilder();

    private TextDump() {
    }

    public static byte[] write(Score score) {
        TextDump dump = new TextDump();
        score.emit(dump);
        return dump.text.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void note(NoteEvent note) {
        text.append("note id=").append(note.id())
                .append(" bar=").append(note.bar())
                .append(" beat=").append(note.beat())
                .append(" pitch=").append(note.pitchName())
                .append(" dur=").append(note.duration())
                .append(" vel=").append(note.velocity())
                .append('\n');
    }
}
