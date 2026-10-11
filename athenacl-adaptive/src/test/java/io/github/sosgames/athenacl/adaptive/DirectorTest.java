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

    @Test
    void aFrameSnapsDownBeforeTheMusicalGrid() {
        assertEquals(960, MusicalClock.snapFrame(1000, 500, 120));
        assertEquals(1000, MusicalClock.snapFrame(1000, 0, 120));
    }

    @Test
    void anOverlayDoesNotReplaceTheState() {
        List<Theme.Control> merged = Director.merge(
                List.of(new Theme.Control(4, 1, "combat", 0.8)),
                List.of(new Theme.Control(4, 1, "", 0, "lowHealth", 1)));
        assertEquals(2, merged.size());
    }

    @Test
    void leavingCombatWindsDownOnTheBar() throws Exception {
        Theme theme = Themes.load(Path.of("themes/fantasy-dungeon.yaml"));
        List<Theme.Control> controls = List.of(
                new Theme.Control(1, 1, "sneaking", 0.35),
                new Theme.Control(4, 3, "combat", 0.8),
                new Theme.Control(6, 1, "peaceful", 0.4),
                new Theme.Control(4, 1, "", 0, "lowHealth", 1));
        Director.Performance performance = Director.perform(theme, controls, 1);
        assertEquals("lowHealth", performance.overlayList());
        boolean entered = false;
        boolean left = false;
        for (Director.Transition transition : performance.transitions()) {
            if ("combat".equals(transition.to())) {
                assertEquals("beat", transition.quantize());
                assertEquals(13440, transition.appliedTick());
                entered = true;
            }
            if ("combat".equals(transition.from()) && "peaceful".equals(transition.to())) {
                assertEquals("bar", transition.quantize());
                assertEquals("windDown", transition.bridge());
                left = true;
            }
        }
        assertTrue(entered);
        assertTrue(left);
    }

    @Test
    void aDegreeStingerFollowsTheKeyAndThenWaits() throws Exception {
        String yaml = """
                id: sting
                bars: 4
                states:
                  peaceful:
                    key: "D dorian"
                    layers: [pad]
                    stinger: hit
                  combat:
                    key: "D aeolian"
                    layers: [pad]
                stingers:
                  hit:
                    degree: 0
                    dur: "1/8"
                    cooldownBars: 4
                """;
        Theme theme = Themes.parse("sting.yaml", yaml);
        Director.Performance performance = Director.perform(theme, List.of(
                new Theme.Control(1, 1, "peaceful", 0.4),
                new Theme.Control(2, 1, "combat", 0.6),
                new Theme.Control(3, 1, "peaceful", 0.4)), 1);
        int hits = 0;
        for (Director.Played played : performance.notes()) {
            if ("stinger".equals(played.note().layer())) {
                assertEquals(74, played.note().pitch());
                assertEquals(1, played.note().bar());
                hits++;
            }
        }
        assertEquals(1, hits);
    }
}
