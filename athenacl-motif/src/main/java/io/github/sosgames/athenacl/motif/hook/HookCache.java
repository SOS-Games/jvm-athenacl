// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.motif.hook;

import io.github.sosgames.athenacl.motif.Motif;
import io.github.sosgames.athenacl.motif.critic.MelodyCritic;
import io.github.sosgames.athenacl.theory.Key;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Picks the hook for one seed and state, then remembers it. The director asks for this
 * before the bar loop, so later bars reuse the motif and the critic is not inside the
 * per-bar budget. The stored nanoseconds are only the budget flag. They are not part of
 * the motif, and a slow clock cannot change the notes.
 */
public final class HookCache {
    public static final long BUDGET_NANOS = 10_000_000L;
    private static final Map<String, HookChoice> CACHE = new LinkedHashMap<>();

    private HookCache() {
    }

    public record HookChoice(
            Motif motif,
            int cand,
            int candidates,
            double score,
            String contour,
            String form,
            long nanos) {

        public String scoreText() {
            return String.format(Locale.ROOT, "%.3f", score);
        }

        public boolean deferred() {
            return nanos > BUDGET_NANOS;
        }

        /** Stored on the note. Slashes keep the text free of spaces and equals signs, so it stays one bare token. */
        public String label(String chain) {
            return chain + "/hook.cand/" + cand + "/" + candidates + "/score/" + scoreText();
        }
    }

    public static HookChoice select(Key key, long seed, String state, HookSettings settings) {
        String cacheKey = seed + "|" + state + "|" + key.name() + "|" + settings.fingerprint();
        synchronized (CACHE) {
            HookChoice hit = CACHE.get(cacheKey);
            if (hit != null) {
                return hit;
            }
            long started = System.nanoTime();
            MelodyCritic.Ranked ranked = MelodyCritic.rank(key, seed, state, settings, List.of(), List.of());
            long nanos = System.nanoTime() - started;
            MelodyCritic.Scored chosen = ranked.chosen();
            HookChoice choice = new HookChoice(chosen.motif(), chosen.cand(), settings.candidates(),
                    chosen.score(), chosen.contour(), chosen.form(), nanos);
            CACHE.put(cacheKey, choice);
            return choice;
        }
    }
}
