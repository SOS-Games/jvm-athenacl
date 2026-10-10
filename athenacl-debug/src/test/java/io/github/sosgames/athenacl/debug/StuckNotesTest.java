// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.debug;

import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.events.Score;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StuckNotesTest {
    @Test
    void overlapOnTheSamePitchIsStuck() {
        NoteEvent first = note(1, 0, 960, 60);
        NoteEvent second = note(2, 480, 960, 60);
        Score score = new Score(List.of(), List.of(first, second), 120, 4, 4, 1);
        assertEquals(1, StuckNotes.count(score));
    }

    @Test
    void aLongTieIsNotStuck() {
        NoteEvent tie = note(1, 0, 5760, 60);
        Score score = new Score(List.of(), List.of(tie), 120, 4, 4, 2);
        assertEquals(0, StuckNotes.count(score));
    }

    private static NoteEvent note(long id, long start, long duration, int pitch) {
        return new NoteEvent(id, start, duration, pitch, "C4", 96, 0, "melody", 1, "1", "1/4", "0", null);
    }
}
