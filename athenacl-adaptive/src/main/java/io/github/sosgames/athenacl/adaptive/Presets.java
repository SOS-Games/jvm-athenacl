// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import io.github.sosgames.athenacl.theory.Key;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Built-in genre presets. A theme names one with {@code preset: fantasy} and may override any state.
 * The director only sees the merged theme.
 */
public final class Presets {
    private Presets() {
    }

    public record Preset(String id, String limits, Map<String, Theme.StateDef> states, LoudnessContour loudness) {
    }

    public static Preset get(String id) {
        if ("fantasy".equals(id)) {
            return fantasy();
        }
        return null;
    }

    public static List<String> ids() {
        return List.of("fantasy");
    }

    /**
     * Dungeon music: modal exploration, a low drone while sneaking, harp and flute in town,
     * taiko and horn in a fight. Each state keeps several of those sounding together.
     * Loudness follows the shared contour.
     */
    public static Preset fantasy() {
        Map<String, Theme.StateDef> states = new LinkedHashMap<>();
        states.put("peaceful", state("peaceful", "A aeolian", 100, List.of("harp", "pad", "bass", "lead"),
                Map.of("pad", 48, "harp", 46, "bass", 43, "lead", 73)));
        states.put("sneaking", state("sneaking", "D dorian", 100, List.of("drone", "pad", "bass", "harp"),
                Map.of("drone", 49, "pad", 52, "bass", 43, "harp", 46)));
        states.put("combat", state("combat", "D aeolian", 100,
                List.of("pad", "brass", "bass", "lead", "taiko", "drums"),
                Map.of("pad", 48, "brass", 60, "bass", 43, "lead", 73, "taiko", 116)));
        states.put("danger", state("danger", "E phrygian", 100, List.of("drone", "pad", "bass", "brass", "taiko"),
                Map.of("drone", 49, "pad", 52, "bass", 43, "brass", 60, "taiko", 116)));
        states.put("trading", state("trading", "D dorian", 100, List.of("harp", "pad", "bass", "lead"),
                Map.of("harp", 46, "pad", 52, "bass", 43, "lead", 73)));
        states.put("partying", state("partying", "D dorian", 100, List.of("harp", "pad", "bass", "lead", "drums"),
                Map.of("harp", 46, "pad", 48, "bass", 43, "lead", 110)));
        Map<String, Double> offsets = new LinkedHashMap<>();
        offsets.put("sneaking", -1.5);
        offsets.put("peaceful", -1.0);
        offsets.put("trading", -1.0);
        offsets.put("partying", 1.5);
        offsets.put("danger", 2.0);
        offsets.put("combat", 4.0);
        return new Preset("fantasy",
                "GM choir, harp, horn, flute, fiddle, and taiko. No sung lyrics.",
                states,
                new LoudnessContour(-23, offsets, 6));
    }

    private static Theme.StateDef state(String id, String key, int tempo, List<String> layers,
                                        Map<String, Integer> programs) {
        return new Theme.StateDef(id, Key.parse(key), tempo, layers, null, programs);
    }
}
