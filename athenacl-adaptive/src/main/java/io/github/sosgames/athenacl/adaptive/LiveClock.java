// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.adaptive;

import java.util.List;

/**
 * A playhead over a score the director already built. {@code poll} moves it forward.
 * The notes do not depend on the wall clock. {@code cpu.msPerBar} is how long that build took,
 * and it is not part of the event-log hash.
 */
public final class LiveClock {
    private final Director.Performance performance;
    private final double barSeconds;
    private final long cpuMsPerBar;
    private double seconds;

    private LiveClock(Director.Performance performance, double barSeconds, long cpuMsPerBar) {
        this.performance = performance;
        this.barSeconds = barSeconds;
        this.cpuMsPerBar = cpuMsPerBar;
    }

    public static LiveClock start(Theme theme, List<Theme.Control> controls, long seed) {
        long began = System.nanoTime();
        Director.Performance performance = Director.perform(theme, controls, seed);
        long elapsedMs = Math.max(0, (System.nanoTime() - began) / 1_000_000L);
        int bars = Math.max(1, performance.score().bars());
        int bpm = performance.score().bpm();
        double barSeconds = bpm <= 0 ? 1 : 60.0 * theme.numerator() / theme.denominator() / bpm;
        return new LiveClock(performance, barSeconds, elapsedMs / bars);
    }

    public void poll(double deltaSeconds) {
        if (deltaSeconds > 0) {
            seconds += deltaSeconds;
        }
    }

    public Director.Performance performance() {
        return performance;
    }

    public String snapshot() {
        int count = Math.max(1, performance.score().bars());
        int number = (int) (seconds / barSeconds) + 1;
        if (number < 1) {
            number = 1;
        }
        if (number > count) {
            number = count;
        }
        Director.BarInfo bar = performance.bars().get(0);
        for (Director.BarInfo info : performance.bars()) {
            if (info.bar() == number) {
                bar = info;
            }
        }
        return "state=" + bar.state()
                + " intensity=" + Themes.number(bar.intensity())
                + " bar=" + bar.bar()
                + " sched.lookaheadMs=" + Scheduler.LOOKAHEAD_MS
                + " cpu.msPerBar=" + cpuMsPerBar;
    }
}
