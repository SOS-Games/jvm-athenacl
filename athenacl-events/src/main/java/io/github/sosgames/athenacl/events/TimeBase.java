// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.events;

/** Ticks per quarter note. The same 960 athenaCL uses for a MIDI file. */
public final class TimeBase {
    public static final int PPQ = 960;

    private TimeBase() {
    }

    /** Length of one bar in ticks. 4/4 is 3840. */
    public static long barTicks(int numerator, int denominator) {
        return (long) numerator * PPQ * 4 / denominator;
    }

    /**
     * A duration written as a fraction of a whole note ({@code 1/4}) or as a number of
     * quarter notes ({@code 1.5}).
     */
    public static long durationTicks(String text) {
        String value = text.trim();
        int slash = value.indexOf('/');
        if (slash >= 0) {
            long numerator = Long.parseLong(value.substring(0, slash).trim());
            long denominator = Long.parseLong(value.substring(slash + 1).trim());
            if (denominator == 0) {
                throw new NumberFormatException("duration denominator is 0");
            }
            return PPQ * 4 * numerator / denominator;
        }
        return Math.round(Double.parseDouble(value) * PPQ);
    }

    /** 1-based beat in quarter notes. Beat 1 is the downbeat. */
    public static long beatOffsetTicks(String beat) {
        double quarter = Double.parseDouble(beat.trim());
        return Math.round((quarter - 1.0) * PPQ);
    }
}
