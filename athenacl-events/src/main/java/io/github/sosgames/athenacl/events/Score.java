// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.events;

import java.util.List;

/** Notes plus the tempo and meter they were written in. */
public record Score(
        List<ControlEvent> controls,
        List<NoteEvent> notes,
        int bpm,
        int numerator,
        int denominator,
        int bars) {

    public Score {
        controls = List.copyOf(controls);
        notes = List.copyOf(notes);
    }

    public long barTicks() {
        return TimeBase.barTicks(numerator, denominator);
    }

    public void emit(OutputSink sink) {
        for (NoteEvent note : notes) {
            sink.note(note);
        }
    }
}
