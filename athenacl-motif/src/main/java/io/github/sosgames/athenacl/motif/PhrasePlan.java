// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.motif;

import java.util.ArrayList;
import java.util.List;

/**
 * How a state's bars reuse {@code m1}. A sentence is idea, repeat, fragment then sequence, then retrograde.
 * Combat keeps the same motif, turned upside down and shortened. Coming back to a state starts the phrase over.
 */
public final class PhrasePlan {
    private PhrasePlan() {
    }

    public static String chain(String state, int phraseBar) {
        List<String> steps = new ArrayList<>();
        switch (state) {
            case "combat" -> {
                steps.add("invert");
                steps.add("diminish");
            }
            case "trading" -> steps.add("transpose");
            case "partying" -> steps.add("ornament");
            case "danger" -> steps.add("invert");
            default -> {
            }
        }
        steps.addAll(sentence(phraseBar));
        return "m1:" + String.join("+", steps);
    }

    public static List<String> sentence(int phraseBar) {
        return switch (Math.floorMod(phraseBar, 4)) {
            case 0 -> List.of("idea");
            case 1 -> List.of("repeat");
            case 2 -> List.of("fragment", "sequence");
            default -> List.of("retrograde");
        };
    }

    /** Antecedent, then a consequent that is the idea turned upside down. */
    public static List<String> period(int phraseBar) {
        return Math.floorMod(phraseBar, 2) == 0 ? List.of("idea") : List.of("invert");
    }

    public static Motif develop(Motif motif, String chain) {
        String prefix = motif.id() + ":";
        String body = chain.startsWith(prefix) ? chain.substring(prefix.length()) : chain;
        Motif current = motif;
        if (body.isBlank()) {
            return current;
        }
        for (String step : body.split("\\+")) {
            current = MotifTransforms.apply(current, step);
        }
        return current;
    }
}
