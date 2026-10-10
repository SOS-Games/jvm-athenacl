// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.events;

import java.util.ArrayList;
import java.util.List;

/**
 * Tick to microsecond conversion. A note list has one tempo at tick 0. Later layers can append
 * changes. The math stays in integers so two runs agree.
 */
public final class TempoMap {
    private final List<ControlEvent.Tempo> changes = new ArrayList<>();

    public TempoMap(int bpm) {
        changes.add(new ControlEvent.Tempo(0, bpm));
    }

    public long toMicros(long tick) {
        long micros = 0;
        long previousTick = 0;
        int bpm = changes.get(0).bpm();
        for (ControlEvent.Tempo change : changes) {
            if (change.startTick() >= tick) {
                break;
            }
            if (change.startTick() > previousTick) {
                micros += span(previousTick, change.startTick(), bpm);
            }
            bpm = change.bpm();
            previousTick = change.startTick();
        }
        if (tick > previousTick) {
            micros += span(previousTick, tick, bpm);
        }
        return micros;
    }

    private static long span(long fromTick, long toTick, int bpm) {
        return (toTick - fromTick) * 60_000_000L / (bpm * (long) TimeBase.PPQ);
    }
}
