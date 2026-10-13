// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import io.github.sosgames.athenacl.theory.Progression;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The chord of each bar, written beside a render so {@code melody score --progression}
 * and {@code analyze} can read the same roman numerals the director used.
 */
public final class HarmonyNote {
    private HarmonyNote() {
    }

    /** One numeral per bar, using that bar's state. The key and style are the first bar's. */
    public static String json(Theme theme, List<Director.BarInfo> bars) {
        StringBuilder progression = new StringBuilder();
        String key = "";
        String style = "";
        boolean drone = false;
        boolean first = true;
        int count = 0;
        for (Director.BarInfo bar : bars) {
            Theme.StateDef state = theme.states().get(bar.state());
            if (state == null) {
                continue;
            }
            if (first) {
                key = state.key().name();
                style = state.composition() == null ? "" : state.composition().text("harmony.style", "");
                drone = state.composition() != null && state.composition().flag("drone.enabled", false);
                first = false;
            }
            if (count > 0) {
                progression.append(' ');
            }
            progression.append(chord(state, bar.bar()).numeral());
            count++;
        }
        return document(key, style, drone, progression.toString());
    }

    public static String json(Theme.StateDef state, int bars) {
        StringBuilder progression = new StringBuilder();
        int count = Math.max(bars, 1);
        for (int bar = 1; bar <= count; bar++) {
            if (bar > 1) {
                progression.append(' ');
            }
            progression.append(chord(state, bar).numeral());
        }
        String style = state.composition() == null ? "" : state.composition().text("harmony.style", "");
        boolean drone = state.composition() != null && state.composition().flag("drone.enabled", false);
        return document(state.key().name(), style, drone, progression.toString());
    }

    private static String document(String key, String style, boolean drone, String progression) {
        return "{\"key\":\"" + escape(key)
                + "\",\"style\":\"" + escape(style)
                + "\",\"drone\":" + drone
                + ",\"progression\":\"" + escape(progression)
                + "\"}\n";
    }

    static Progression.Chord chord(Theme.StateDef state, int bar) {
        Composition composition = state.composition();
        List<String> numerals = composition == null ? List.of() : composition.list("harmony.progression");
        int rhythm = composition == null ? 1 : composition.number("harmony.harmonicRhythm", 1);
        boolean leading = composition != null && composition.flag("scale.leadingTone", false);
        boolean pedal = composition != null && (composition.flag("harmony.pedal", false)
                || "pedal".equals(composition.text("harmony.style", "")));
        return Progression.of(state.key(), numerals, rhythm, leading, pedal, cadences(composition)).at(bar);
    }

    private static Map<Integer, String> cadences(Composition composition) {
        Map<Integer, String> marks = new LinkedHashMap<>();
        if (composition == null) {
            return marks;
        }
        String prefix = "harmony.cadences.";
        for (Map.Entry<String, Composition.Leaf> entry : composition.leaves().entrySet()) {
            if (!entry.getKey().startsWith(prefix)) {
                continue;
            }
            String index = entry.getKey().substring(prefix.length());
            if (!index.isEmpty() && index.chars().allMatch(Character::isDigit)) {
                marks.put(Integer.parseInt(index), entry.getValue().value());
            }
        }
        return marks;
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
