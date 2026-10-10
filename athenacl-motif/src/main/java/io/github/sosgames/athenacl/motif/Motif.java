// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.motif;

import java.util.List;

/**
 * A short idea: a few scale degrees and their durations. {@code m1} is the motif a seed grows.
 * Transforms return a new motif and leave this one alone.
 */
public record Motif(String id, int steps, List<MotifNote> notes) {
    public Motif {
        notes = List.copyOf(notes);
        steps = Math.max(1, steps);
    }

    /** Up, down, or same between each pair of degrees, joined by commas. */
    public String contour() {
        if (notes.size() < 2) {
            return "same";
        }
        StringBuilder text = new StringBuilder();
        for (int i = 1; i < notes.size(); i++) {
            if (i > 1) {
                text.append(',');
            }
            int delta = notes.get(i).degree() - notes.get(i - 1).degree();
            if (delta > 0) {
                text.append("up");
            } else if (delta < 0) {
                text.append("down");
            } else {
                text.append("same");
            }
        }
        return text.toString();
    }
}
