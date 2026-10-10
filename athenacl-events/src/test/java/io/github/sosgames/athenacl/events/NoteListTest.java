// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.events;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoteListTest {
    @Test
    void cMajorScaleIsEightNotesAndStable() throws Exception {
        Path sample = Path.of("samples/notes/c-major-scale.yaml");
        assertTrue(Files.isRegularFile(sample), "missing " + sample.toAbsolutePath());
        String yaml = Files.readString(sample);
        Score score = NoteLists.parse(sample.toString(), yaml, 0);
        assertEquals(8, score.notes().size());
        assertEquals(2, score.bars());
        NoteEvent e4 = score.notes().get(2);
        assertEquals(3, e4.id());
        assertEquals(60 + 4, PitchName.midi("E4"));
        assertEquals("E4", e4.pitchName());
        assertEquals(1, e4.bar());
        assertEquals("3", e4.beat());
        assertEquals(960, e4.durationTicks());
        String hash = EventLog.hash(score);
        assertEquals(64, hash.length());
        assertEquals(hash, EventLog.hash(NoteLists.parse(sample.toString(), yaml, 0)));
        Score again = EventLog.read(new String(EventLog.encode(score), StandardCharsets.UTF_8));
        assertEquals(hash, EventLog.hash(again));
    }
}
