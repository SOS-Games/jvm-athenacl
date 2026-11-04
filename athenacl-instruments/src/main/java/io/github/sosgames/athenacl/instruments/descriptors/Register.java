// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.descriptors;

/** A MIDI register. {@code low} and {@code high} are the playable span. The sweet pair is where it sounds best. */
public record Register(int low, int high, int sweetLow, int sweetHigh) {
    public static final Register UNSET = new Register(0, 0, 0, 0);

    public boolean unset() {
        return high <= 0 && low <= 0;
    }

    /** The instrument can play every note of the target span. An unset target always fits. */
    public boolean covers(Register target) {
        if (target == null || target.unset()) {
            return true;
        }
        return low <= target.low && high >= target.high;
    }

    public boolean sweetOverlaps(Register other) {
        if (other == null || other.sweetHigh <= other.sweetLow || sweetHigh <= sweetLow) {
            return false;
        }
        return sweetLow <= other.sweetHigh && other.sweetLow <= sweetHigh;
    }

    public String text() {
        if (unset()) {
            return "";
        }
        return low + ".." + high + " sweet=" + sweetLow + ".." + sweetHigh;
    }
}
