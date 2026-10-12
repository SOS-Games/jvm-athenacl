// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import io.github.sosgames.athenacl.events.NoteEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bass line that sits on one pitch for more than four bars is the tonic drone the
 * diagnosis called out. A state that sets {@code harmony.pedal} (or {@code harmony.style: pedal})
 * is allowed to hold.
 */
class BassMotionTest {
    @Test
    void shippedPresetsMoveTheBass() throws Exception {
        for (String id : Presets.ids()) {
            Presets.Preset preset = Presets.get(id);
            for (String state : preset.states().keySet()) {
                String yaml = "id: " + id + "\npreset: " + id + "\nbars: 16\nmeter: \""
                        + preset.meter() + "\"\n";
                Theme theme = Themes.parse(id + ".yaml", yaml);
                Director.Performance performance = Director.perform(theme,
                        List.of(new Theme.Control(1, 1, state, 0.8)), 1);
                assertMoves(id + "/" + state, theme, performance);
            }
        }
    }

    @Test
    void shippedThemesMoveTheBass() throws IOException {
        List<Path> files = new ArrayList<>();
        if (Files.isDirectory(Path.of("themes"))) {
            try (var stream = Files.list(Path.of("themes"))) {
                stream.filter(path -> path.toString().endsWith(".yaml")).forEach(files::add);
            }
        }
        if (Files.isDirectory(Path.of("samples/themes"))) {
            try (var stream = Files.list(Path.of("samples/themes"))) {
                stream.filter(path -> path.toString().endsWith(".yaml")).forEach(files::add);
            }
        }
        for (Path file : files) {
            Theme theme;
            try {
                theme = Themes.load(file);
            } catch (ThemeException | IllegalArgumentException e) {
                continue;
            }
            Director.Performance performance = Director.perform(theme, theme.timeline(), 1);
            assertMoves(file.toString().replace('\\', '/'), theme, performance);
        }
    }

    private static void assertMoves(String label, Theme theme, Director.Performance performance) {
        TreeMap<Integer, NoteEvent> bass = new TreeMap<>();
        for (Director.Played played : performance.notes()) {
            NoteEvent note = played.note();
            if ("bass".equals(note.layer())) {
                bass.putIfAbsent(note.bar(), note);
            }
        }
        int run = 0;
        int previous = Integer.MIN_VALUE;
        for (var entry : bass.entrySet()) {
            NoteEvent note = entry.getValue();
            if (pedal(theme, note.seedPath())) {
                run = 0;
                previous = Integer.MIN_VALUE;
                continue;
            }
            int pitch = note.pitch();
            run = pitch == previous ? run + 1 : 1;
            previous = pitch;
            assertTrue(run <= 4, label + " bass pitch " + pitch + " held for " + run + " bars");
        }
    }

    private static boolean pedal(Theme theme, String seedPath) {
        String[] parts = seedPath.split("/");
        if (parts.length < 2) {
            return false;
        }
        Theme.StateDef state = theme.states().get(parts[1]);
        if (state == null || state.composition() == null) {
            return false;
        }
        Composition composition = state.composition();
        return composition.flag("harmony.pedal", false)
                || "pedal".equals(composition.text("harmony.style", ""));
    }
}
