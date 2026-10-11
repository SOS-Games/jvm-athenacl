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

    public record Preset(String id, String limits, Map<String, Theme.StateDef> states, LoudnessContour loudness,
                         String instruments) {
    }

    public static Preset get(String id) {
        if ("fantasy".equals(id)) {
            return fantasy();
        }
        if ("scifi-space".equals(id)) {
            return scifi();
        }
        return null;
    }

    public static List<String> ids() {
        return List.of("fantasy", "scifi-space");
    }

    /**
     * Dungeon music: modal exploration, a low drone while sneaking, harp and flute in town,
     * taiko, horn, and trumpet in a fight. Each state keeps several of those sounding together.
     * Loudness follows the shared contour.
     */
    public static Preset fantasy() {
        Map<String, Theme.StateDef> states = new LinkedHashMap<>();
        states.put("peaceful", state("peaceful", "A aeolian", 100, List.of("harp", "pad", "bass", "lead"),
                Map.of("pad", 48, "harp", 46, "bass", 43, "lead", 73),
                Map.of("harp", "harp", "pad", "strings", "bass", "cello", "lead", "flute")));
        states.put("sneaking", state("sneaking", "D dorian", 100, List.of("drone", "pad", "bass", "harp"),
                Map.of("drone", 49, "pad", 52, "bass", 43, "harp", 46),
                Map.of("drone", "strings.slow", "pad", "choir", "bass", "cello", "harp", "harp")));
        states.put("combat", state("combat", "D aeolian", 100,
                List.of("pad", "brass", "bass", "lead", "taiko", "drums"),
                Map.of("pad", 48, "brass", 60, "bass", 43, "lead", 56, "taiko", 116),
                Map.of("pad", "strings", "brass", "horn", "bass", "cello", "lead", "trumpet",
                        "taiko", "taiko", "drums", "drums.orchestral")));
        states.put("danger", state("danger", "E phrygian", 100, List.of("drone", "pad", "bass", "brass", "taiko"),
                Map.of("drone", 49, "pad", 52, "bass", 43, "brass", 60, "taiko", 116),
                Map.of("drone", "strings.slow", "pad", "choir", "bass", "cello", "brass", "horn", "taiko", "taiko")));
        states.put("trading", state("trading", "D dorian", 100, List.of("harp", "pad", "bass", "lead"),
                Map.of("harp", 46, "pad", 52, "bass", 43, "lead", 73),
                Map.of("harp", "harp", "pad", "choir", "bass", "cello", "lead", "flute")));
        states.put("partying", state("partying", "D dorian", 100, List.of("harp", "pad", "bass", "lead", "drums"),
                Map.of("harp", 46, "pad", 48, "bass", 43, "lead", 110),
                Map.of("harp", "harp", "pad", "strings", "bass", "cello", "lead", "fiddle",
                        "drums", "drums.orchestral")));
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
                new LoudnessContour(-23, offsets, 6),
                "harp,choir,strings,horn,flute,fiddle,taiko");
    }

    /**
     * Space music: Lydian pads, an arpeggio on the eighth-note grid, and a synth bass.
     * Combat doubles the arp into sixteenths and adds an 808 kit. Trading uses celesta.
     * There is no whole-tone scale in the theory layer, so the raised fourth stands in for it.
     * Loudness uses the same contour as the dungeon.
     */
    public static Preset scifi() {
        Map<String, Theme.StateDef> states = new LinkedHashMap<>();
        states.put("peaceful", state("peaceful", "C lydian", 100, List.of("pad", "arp", "bass"),
                Map.of("pad", 89, "arp", 80, "bass", 38),
                Map.of("pad", "pad.warm", "arp", "lead.square", "bass", "bass.synth")));
        states.put("sneaking", state("sneaking", "A lydian", 100, List.of("pad", "arp", "bass"),
                Map.of("pad", 95, "arp", 80, "bass", 38),
                Map.of("pad", "pad.sweep", "arp", "lead.square", "bass", "bass.synth")));
        states.put("combat", state("combat", "F lydian", 100, List.of("pad", "arp", "bass", "lead", "drums"),
                Map.of("pad", 95, "arp", 80, "bass", 38, "lead", 81, "drums", 25),
                Map.of("pad", "pad.sweep", "arp", "lead.square", "bass", "bass.synth",
                        "lead", "lead.saw", "drums", "drums.808")));
        states.put("danger", state("danger", "E lydian", 100, List.of("pad", "arp", "bass"),
                Map.of("pad", 99, "arp", 80, "bass", 38),
                Map.of("pad", "fx.atmosphere", "arp", "lead.square", "bass", "bass.synth")));
        states.put("trading", state("trading", "G lydian", 100, List.of("bell", "pad", "arp", "bass"),
                Map.of("bell", 8, "pad", 88, "arp", 80, "bass", 38),
                Map.of("bell", "celesta", "pad", "pad.newage", "arp", "lead.square", "bass", "bass.synth")));
        states.put("partying", state("partying", "D lydian", 100, List.of("pad", "arp", "bass", "lead", "drums"),
                Map.of("pad", 89, "arp", 80, "bass", 38, "lead", 81, "drums", 25),
                Map.of("pad", "pad.warm", "arp", "lead.square", "bass", "bass.synth",
                        "lead", "lead.saw", "drums", "drums.808")));
        Map<String, Double> offsets = new LinkedHashMap<>();
        offsets.put("sneaking", -1.5);
        offsets.put("peaceful", -1.0);
        offsets.put("trading", -1.0);
        offsets.put("partying", 1.5);
        offsets.put("danger", 2.0);
        offsets.put("combat", 4.0);
        return new Preset("scifi-space",
                "GM synth pads, square and saw leads, synth bass, celesta, and an 808 kit. "
                        + "No filter sweeps until the effects stage. No sung lyrics.",
                states,
                new LoudnessContour(-23, offsets, 6),
                "pad,square,saw,bass,celesta,808");
    }

    private static Theme.StateDef state(String id, String key, int tempo, List<String> layers,
                                        Map<String, Integer> programs, Map<String, String> instruments) {
        return new Theme.StateDef(id, Key.parse(key), tempo, layers, null, programs, instruments);
    }
}
