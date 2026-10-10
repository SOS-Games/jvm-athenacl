// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.rhythm;

import io.github.sosgames.athenacl.events.NoteLists;
import io.github.sosgames.athenacl.events.Score;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmTest {
    @Test
    void patternRepeatsAcrossBars() {
        DrumPattern.Grid grid = DrumPattern.grid("x..x..x.", Meter.parse("6/8"), 2);
        assertEquals(2, grid.bars());
        assertEquals(6, grid.onsets());
        assertEquals("3+3", grid.meter().grouping());
        assertTrue(grid.row().startsWith("kick |●··●··●·"));
    }

    @Test
    void swingLandsNearTheRequestedFraction() throws Exception {
        String yaml = """
                bpm: 120
                meter: "4/4"
                notes:
                  - { pitch: C4, bar: 1, beat: 1, dur: "1/8" }
                  - { pitch: C4, bar: 1, beat: 1.5, dur: "1/8" }
                  - { pitch: C4, bar: 1, beat: 2, dur: "1/8" }
                  - { pitch: C4, bar: 1, beat: 2.5, dur: "1/8" }
                """;
        Score straight = NoteLists.parse("swing.yaml", yaml, 7);
        Score swung = Swing.apply(straight, Groove.parse("swing=0.62"));
        double measured = Swing.measure(swung).orElseThrow();
        assertEquals(0.62, measured, 0.01);
        assertEquals("0.62", Swing.format(measured));
    }

    @Test
    void aTieIsOneNoteSixBeatsLong() throws Exception {
        String yaml = """
                bpm: 120
                meter: "4/4"
                notes:
                  - { pitch: C4, bar: 1, beat: 1, dur: "6/4" }
                """;
        Score score = NoteLists.parse("tie.yaml", yaml, 0);
        assertEquals(1, score.notes().size());
        assertEquals("6", Durations.maxBeats(score));
        assertEquals(2, score.bars());
    }

    @Test
    void threeAgainstTwoMeetsOnTheDownbeat() {
        Polyrhythm.Voices voices = Polyrhythm.render(3, 2, 2);
        assertEquals(2, voices.layers());
        assertEquals("1,2", voices.alignedAtBar());
        assertEquals(10, voices.score().notes().size());
    }
}
