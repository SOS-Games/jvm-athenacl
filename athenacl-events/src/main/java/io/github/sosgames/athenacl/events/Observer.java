// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.events;

import java.util.Arrays;

/**
 * Checks that a debug observer does not change the event log. The sink records every
 * note while {@link EventLog} encodes, and the bytes must still match a run with no sink.
 */
public final class Observer {
    private Observer() {
    }

    public static boolean matches(Score score) {
        byte[] plain = EventLog.encode(score);
        int[] seen = {0};
        byte[] traced = EventLog.encode(score, new DebugSink() {
            @Override
            public void note(NoteEvent note) {
                seen[0]++;
            }
        });
        return seen[0] == score.notes().size() && Arrays.equals(plain, traced);
    }
}
