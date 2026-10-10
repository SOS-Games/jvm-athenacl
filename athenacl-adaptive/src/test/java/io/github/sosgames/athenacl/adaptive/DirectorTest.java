// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import io.github.sosgames.athenacl.events.EventLog;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectorTest {
    @Test
    void unknownStatePointsAtTheRule() {
        String yaml = """
                id: broken
                states:
                  peaceful:
                    key: "C major"
                transitions:
                  rules:
                    - {to: nowhere, quantize: bar}
                """;
        ThemeException error = org.junit.jupiter.api.Assertions.assertThrows(ThemeException.class,
                () -> Themes.parse("broken.yaml", yaml));
        ThemeIssue issue = error.issues().get(0);
        assertEquals("THEME_UNKNOWN_STATE", issue.code());
        assertEquals("/transitions/rules/0/to", issue.path());
    }

    @Test
    void barRequestsStayOnTheBarLine() {
        long barTicks = 3840;
        assertEquals(15360, MusicalClock.quantize(15360, Quantize.BAR, barTicks));
        assertEquals(19200, MusicalClock.quantize(16000, Quantize.BAR, barTicks));
        assertTrue(MusicalClock.onBarLine(30720, barTicks));
    }

    @Test
    void drumsStayOnUntilTheLowThreshold() {
        IntensityTrack track = new IntensityTrack();
        assertTrue(!track.drums(0.45));
        assertTrue(track.drums(0.60));
        assertTrue(track.drums(0.40));
        assertTrue(!track.drums(0.30));
    }

    @Test
    void aWindowMatchesTheFullRender() throws Exception {
        Theme theme = Themes.load(Path.of("samples/themes/poc.yaml"));
        List<Theme.Control> timeline = ControlLog.readTimeline(Path.of("samples/timelines/poc.json"));
        Director.Performance full = Director.perform(theme, timeline, 42);
        assertEquals("peaceful,sneaking,combat", full.stateList());
        assertEquals(2, full.transitions().size());
        for (Director.Transition transition : full.transitions()) {
            assertTrue(MusicalClock.onBarLine(transition.appliedTick(), theme.barTicks()));
        }
        List<Theme.Control> onlyCombat = List.of(new Theme.Control(9, 1, "combat", 0.6));
        Director.Performance jumped = Director.perform(theme, onlyCombat, 42);
        assertEquals(EventLog.hash(Director.slice(full, 9, 10).score()),
                EventLog.hash(Director.slice(jumped, 9, 10).score()));
        byte[] log = ControlLog.encode("samples/themes/poc.yaml", 42, theme.bars(), timeline);
        Path written = Path.of("build/l4-unit-control.jsonl");
        java.nio.file.Files.createDirectories(written.getParent());
        java.nio.file.Files.write(written, log);
        ControlLog.Log read = ControlLog.read(written);
        Theme replayTheme = theme.withBars(read.bars());
        Director.Performance replay = Director.perform(replayTheme, read.controls(), read.seed());
        assertEquals(EventLog.hash(full.score()), EventLog.hash(replay.score()));
    }
}
