// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.theory;

/**
 * One chord per bar, in scale degrees. A phrase ends on the tonic.
 * That cadence, and any other tonic, adds the note that marks the mode
 * (the raised fourth in Lydian, the natural sixth in Dorian).
 */
public final class HarmonicRhythm {
    private HarmonicRhythm() {
    }

    public record Slot(int[] degrees, boolean cadence) {
    }

    public static Slot at(Key key, int bar, int phraseBars) {
        int length = Math.max(1, phraseBars);
        int slot = Math.floorMod(bar - 1, length);
        boolean cadence = slot == length - 1;
        int root = cadence ? 0 : switch (slot % 4) {
            case 1 -> 3;
            case 2 -> 4;
            default -> 0;
        };
        int third = Math.floorMod(root + 2, 7);
        int fifth = Math.floorMod(root + 4, 7);
        int color = root == 0 ? color(key.mode()) : -1;
        if (color < 0 || color == third || color == fifth) {
            return new Slot(new int[] {root, third, fifth}, cadence);
        }
        return new Slot(new int[] {root, third, color, fifth}, cadence);
    }

    /** Scale degree that makes this mode audible, or -1 for plain major. */
    private static int color(Mode mode) {
        return switch (mode) {
            case LYDIAN -> 3;
            case DORIAN, AEOLIAN -> 5;
            case PHRYGIAN -> 1;
            case MIXOLYDIAN -> 6;
            case LOCRIAN -> 4;
            case IONIAN -> -1;
        };
    }
}
