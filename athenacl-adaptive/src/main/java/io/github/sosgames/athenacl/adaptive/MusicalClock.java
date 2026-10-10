// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import io.github.sosgames.athenacl.events.TimeBase;

/**
 * Turns a bar and beat into a tick, then snaps a request onto the grid the rule asked for.
 * Bar 1 beat 1 is tick 0. A tick already on the grid stays there.
 */
public final class MusicalClock {
    private MusicalClock() {
    }

    public static long tick(int bar, int beat, long barTicks) {
        if (bar < 1 || beat < 1) {
            throw new IllegalArgumentException("bar and beat start at 1");
        }
        return (bar - 1L) * barTicks + (beat - 1L) * TimeBase.PPQ;
    }

    public static long quantize(long requestTick, Quantize quantize, long barTicks) {
        long grid = switch (quantize) {
            case BAR -> barTicks;
            case BEAT -> TimeBase.PPQ;
            case IMMEDIATE -> 0;
        };
        if (grid <= 0 || requestTick % grid == 0) {
            return requestTick;
        }
        return (requestTick / grid + 1) * grid;
    }

    public static boolean onBarLine(long tick, long barTicks) {
        return barTicks > 0 && tick % barTicks == 0;
    }
}
