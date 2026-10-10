// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.theory;

/** Intervals above a chord root, in semitones. */
public enum ChordQuality {
    MAJOR(0, 4, 7),
    MINOR(0, 3, 7),
    DIMINISHED(0, 3, 6),
    AUGMENTED(0, 4, 8),
    DOMINANT7(0, 4, 7, 10),
    MINOR7(0, 3, 7, 10),
    MAJOR7(0, 4, 7, 11),
    HALF_DIM(0, 3, 6, 10),
    DIM7(0, 3, 6, 9),
    SUS2(0, 2, 7),
    SUS4(0, 5, 7);

    private final int[] intervals;

    ChordQuality(int... intervals) {
        this.intervals = intervals;
    }

    public int[] intervals() {
        return intervals.clone();
    }
}
