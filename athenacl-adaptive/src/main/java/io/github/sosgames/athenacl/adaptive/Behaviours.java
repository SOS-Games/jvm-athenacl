// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import java.util.List;
import java.util.Map;

/**
 * The players a layer can name. Adding a player is a new id here. Adding an instrument
 * or a layer name is YAML. The old fixed names (pad, harp, taiko) keep the channel and
 * program they already wrote into event logs.
 */
public final class Behaviours {
    public static final List<String> IDS = List.of(
            "chord-pad", "bass-line", "phrase-lead", "arpeggio", "drone", "ostinato",
            "drum-pattern", "po-source");

    private static final Map<String, String> LEGACY = Map.ofEntries(
            Map.entry("pad", "chord-pad"),
            Map.entry("chords", "chord-pad"),
            Map.entry("bass", "bass-line"),
            Map.entry("lead", "phrase-lead"),
            Map.entry("harp", "arpeggio"),
            Map.entry("bell", "arpeggio"),
            Map.entry("arp", "po-source"),
            Map.entry("drone", "drone"),
            Map.entry("taiko", "ostinato"),
            Map.entry("brass", "ostinato"),
            Map.entry("drums", "drum-pattern"));

    private static final Map<String, Integer> CHANNELS = Map.ofEntries(
            Map.entry("bass", 1),
            Map.entry("lead", 2),
            Map.entry("stinger", 3),
            Map.entry("drone", 4),
            Map.entry("taiko", 5),
            Map.entry("harp", 6),
            Map.entry("bell", 6),
            Map.entry("brass", 7),
            Map.entry("drums", 9),
            Map.entry("arp", 10),
            Map.entry("chords", 12));

    private static final Map<String, Integer> PROGRAMS = Map.ofEntries(
            Map.entry("pad", 48),
            Map.entry("chords", 48),
            Map.entry("bass", 32),
            Map.entry("lead", 73),
            Map.entry("stinger", 56),
            Map.entry("drone", 49),
            Map.entry("taiko", 116),
            Map.entry("harp", 46),
            Map.entry("brass", 60),
            Map.entry("arp", 80),
            Map.entry("bell", 8));

    private Behaviours() {
    }

    public static boolean known(String behaviour) {
        return IDS.contains(behaviour);
    }

    /** The player for a fixed layer name, or null when the name is not one of those. */
    public static String legacy(String name) {
        return LEGACY.get(name);
    }

    public static int channel(String name) {
        return CHANNELS.getOrDefault(name, 0);
    }

    public static int program(String name) {
        return PROGRAMS.getOrDefault(name, 0);
    }

    /** A close player id, or null when nothing is within two edits. */
    public static String suggest(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String best = null;
        int bestDistance = 3;
        for (String id : IDS) {
            int distance = distance(text, id);
            if (distance < bestDistance) {
                best = id;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static int distance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }
}
