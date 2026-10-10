// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.rhythm;

/**
 * A one-bar drum pattern. {@code x} is a hit and {@code .} is a rest.
 * {@code x..x..x.} is three hits. The row uses the same marks as the piano roll.
 */
public final class DrumPattern {
    private DrumPattern() {
    }

    public record Grid(int bars, int onsets, Meter meter, String row) {
    }

    public static Grid grid(String pattern, Meter meter, int bars) {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("pattern is empty");
        }
        if (bars < 1) {
            throw new IllegalArgumentException("bars must be 1 or more");
        }
        int hits = 0;
        StringBuilder cells = new StringBuilder();
        for (int bar = 0; bar < bars; bar++) {
            for (int i = 0; i < pattern.length(); i++) {
                char step = pattern.charAt(i);
                if (step == 'x' || step == 'X') {
                    hits++;
                    cells.append('●');
                } else if (step == '.') {
                    cells.append('·');
                } else {
                    throw new IllegalArgumentException("pattern step " + step);
                }
            }
        }
        return new Grid(bars, hits, meter, "kick |" + cells);
    }
}
