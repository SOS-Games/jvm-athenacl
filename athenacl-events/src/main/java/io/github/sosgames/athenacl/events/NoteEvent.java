// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.events;

/**
 * One note. {@code id} is fixed from the score order, so a re-render keeps the same ids.
 * {@code seedPath} records where a generated note would have drawn randomness. A handwritten
 * note still carries one, and it does not change between runs.
 * {@code motif} is empty for a handwritten note. When a lead plays {@code m1}, it is {@code m1:idea}
 * or a transform chain such as {@code m1:invert+diminish}. A blank motif is left out of the event log,
 * so older scores keep their hash.
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
        String seedPath,
        String motif) {
}
