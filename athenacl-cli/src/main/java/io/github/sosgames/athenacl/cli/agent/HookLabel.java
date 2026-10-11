// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import java.util.Map;

/**
 * The event log stores the hook pick on the motif string, after the L7 chain.
 * Printing splits that suffix into {@code hook.cand=} and {@code score=} so the
 * chain itself stays {@code m1:idea} and older checks can still see {@code motif=m1:}.
 */
final class HookLabel {
    private HookLabel() {
    }

    static void put(Map<String, String> fields, String motif) {
        if (motif == null || motif.isBlank()) {
            return;
        }
        int at = motif.indexOf("/hook.cand/");
        if (at < 0) {
            fields.put("motif", motif);
            return;
        }
        fields.put("motif", motif.substring(0, at));
        String rest = motif.substring(at + "/hook.cand/".length());
        int scoreAt = rest.indexOf("/score/");
        if (scoreAt < 0) {
            fields.put("hook.cand", rest);
            return;
        }
        fields.put("hook.cand", rest.substring(0, scoreAt));
        fields.put("score", rest.substring(scoreAt + "/score/".length()));
    }
}
