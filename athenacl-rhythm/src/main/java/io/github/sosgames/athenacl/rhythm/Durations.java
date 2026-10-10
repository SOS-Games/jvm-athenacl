// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.rhythm;

import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.events.Score;
import io.github.sosgames.athenacl.events.TimeBase;

import java.util.Locale;

/** Longest note in a score, counted in quarter notes. A tie across the bar is still one note. */
public final class Durations {
    private Durations() {
    }

    public static String maxBeats(Score score) {
        long max = 0;
        for (NoteEvent note : score.notes()) {
            max = Math.max(max, note.durationTicks());
        }
        if (max % TimeBase.PPQ == 0) {
            return Long.toString(max / TimeBase.PPQ);
        }
        return String.format(Locale.ROOT, "%.2f", max / (double) TimeBase.PPQ);
    }
}
