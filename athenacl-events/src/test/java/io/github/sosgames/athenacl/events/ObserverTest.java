// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.events;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObserverTest {
    @Test
    void provenanceDoesNotChangeTheEventLog() {
        NoteEvent note = new NoteEvent(1, 0, 960, 60, "C4", 96, 0, "melody", 1, "1", "1/4", "0", null);
        Score score = new Score(List.of(), List.of(note), 120, 4, 4, 1);
        assertTrue(Observer.matches(score));
        String sidecar = new String(Provenance.notes(score), StandardCharsets.UTF_8);
        assertEquals(1, sidecar.split("\n", -1).length - 1);
        assertTrue(sidecar.contains("\"layer\":\"melody\""));
    }
}
