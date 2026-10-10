// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.events;

/**
 * One note. {@code id} is fixed from the score order, so a re-render keeps the same ids.
 * {@code seedPath} records where a generated note would have drawn randomness. A handwritten
 * note still carries one, and it does not change between runs.
 */
public record NoteEvent(
        long id,
        long startTick,
        long durationTicks,
        int pitch,
        String pitchName,
        int velocity,
        int channel,
        String layer,
        int bar,
        String beat,
        String duration,
        String seedPath) {
}
