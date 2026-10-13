// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import io.github.sosgames.athenacl.core.LayerSource;
import io.github.sosgames.athenacl.events.DefaultRandom;
import io.github.sosgames.athenacl.events.RandomSource;

import java.util.List;

/**
 * Reads {@code source.<layer>.po} and {@code source.<layer>.texture} from the resolved state.
 * The seed is a fork of the theme seed, so the same bar asks the generator the same question.
 * A sieve does not draw; a Markov pulse does.
 */
public final class LayerPo {
    private LayerPo() {
    }

    public static String po(Theme.StateDef state, String layer) {
        if (state.composition() == null) {
            return "";
        }
        return state.composition().text("source." + layer + ".po", "");
    }

    public static List<String> rhythmTokens(Theme.StateDef state, long themeSeed, int index) {
        String po = po(state, "lead");
        if (po.isBlank()) {
            return List.of();
        }
        texture(state, "lead");
        return LayerSource.rhythm(po, 8, seed(themeSeed, state.id(), "lead", index));
    }

    /** Eighth or sixteenth indexes the sieve keeps. Empty when this arp has no source. */
    public static List<Integer> arpHits(Theme.StateDef state, int steps) {
        String po = po(state, "arp");
        if (po.isBlank() || steps < 1) {
            return List.of();
        }
        texture(state, "arp");
        return LayerSource.sieve(po, 0, steps - 1);
    }

    private static void texture(Theme.StateDef state, String layer) {
        String name = state.composition().text("source." + layer + ".texture", "");
        if (!name.isBlank() && !LayerSource.knownTexture(name)) {
            throw new IllegalArgumentException("unknown texture " + name);
        }
    }

    static long seed(long themeSeed, String state, String layer, int index) {
        RandomSource rng = DefaultRandom.of(themeSeed)
                .fork(state.hashCode(), layer.hashCode(), "po".hashCode(), index);
        return rng.seedPath().hashCode();
    }
}
