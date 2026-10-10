// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.events;

/** A timed MIDI control. Note lists in this layer emit tempo, meter, and a program change. */
public sealed interface ControlEvent
        permits ControlEvent.Tempo, ControlEvent.Meter, ControlEvent.ProgramChange,
        ControlEvent.ControlChange, ControlEvent.PitchBend {

    long startTick();

    record Tempo(long startTick, int bpm) implements ControlEvent {
    }

    record Meter(long startTick, int numerator, int denominator) implements ControlEvent {
    }

    record ProgramChange(long startTick, int channel, int program) implements ControlEvent {
    }

    record ControlChange(long startTick, int channel, int controller, int value) implements ControlEvent {
    }

    record PitchBend(long startTick, int channel, int value) implements ControlEvent {
    }
}
