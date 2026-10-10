// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.debug;

import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.events.Score;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Counts notes that never turn off. A note with no duration is stuck, and so is a second
 * hit on the same pitch that starts while the first is still sounding. A long tie is one
 * note with an end, so it is not stuck.
 */
public final class StuckNotes {
    private StuckNotes() {
    }

    public static int count(Score score) {
        int stuck = 0;
        List<NoteEvent> notes = new ArrayList<>(score.notes());
        notes.sort(Comparator.comparingInt(NoteEvent::channel)
                .thenComparingInt(NoteEvent::pitch)
                .thenComparingLong(NoteEvent::startTick)
                .thenComparingLong(NoteEvent::id));
        NoteEvent previous = null;
        for (NoteEvent note : notes) {
            if (note.durationTicks() <= 0) {
                stuck++;
            }
            if (previous != null
                    && previous.channel() == note.channel()
                    && previous.pitch() == note.pitch()
                    && note.startTick() < previous.startTick() + previous.durationTicks()) {
                stuck++;
            }
            previous = note;
        }
        return stuck;
    }
}
