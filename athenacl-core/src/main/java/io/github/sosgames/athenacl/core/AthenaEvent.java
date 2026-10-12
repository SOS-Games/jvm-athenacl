// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.core;

/**
 * One note in an athenaCL event list. Time and duration are seconds.
 * {@code acc} is 0 for a rest, which the MIDI writer skips.
 * {@code amp} is a MIDI velocity and {@code pan} is 0–127 in this slice.
 */
public record AthenaEvent(
        double time,
        double dur,
        double sus,
        double acc,
        double amp,
        double ps,
        double pan,
        String inst) {
}
