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

    /**
     * Snaps a request down onto a fixed frame before the musical grid.
     * {@code frameMs} of 0 leaves the tick alone. Two requests in one frame share a tick.
     */
    public static long snapFrame(long tick, int frameMs, int bpm) {
        if (frameMs <= 0 || bpm <= 0 || tick <= 0) {
            return tick;
        }
        long grid = frameMs * (long) bpm * TimeBase.PPQ / 60_000L;
        if (grid <= 1) {
            return tick;
        }
        return tick - Math.floorMod(tick, grid);
    }
}
