// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import io.github.sosgames.athenacl.events.TimeBase;

/**
 * How far ahead notes may be queued, and whether a switch landed inside that budget.
 * The wait is musical time (ticks and tempo). It does not read the wall clock.
 */
public final class Scheduler {
    /** Notes are released this far ahead of the playhead. A beat switch must fit inside one beat plus this. */
    public static final int LOOKAHEAD_MS = 150;

    /** Sounding notes are cut at the switch. The fade they would get is this long. */
    public static final int RELEASE_MS = 100;

    private Scheduler() {
    }

    public static long ticksToMs(long ticks, int bpm) {
        if (bpm <= 0 || ticks <= 0) {
            return 0;
        }
        return ticks * 60_000L / (bpm * (long) TimeBase.PPQ);
    }

    /** True when the landing is later than the rule's grid plus the lookahead. */
    public static boolean late(long requestTick, long appliedTick, Quantize quantize, long barTicks, int bpm) {
        long grid = switch (quantize) {
            case BEAT -> TimeBase.PPQ;
            case BAR -> barTicks;
            case IMMEDIATE -> 0;
        };
        long lookaheadTicks = LOOKAHEAD_MS * (long) bpm * TimeBase.PPQ / 60_000L;
        return appliedTick - requestTick > grid + lookaheadTicks;
    }
}
