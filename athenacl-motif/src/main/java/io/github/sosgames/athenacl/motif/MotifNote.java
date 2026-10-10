// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.motif;

/** One tone of a motif. {@code degree} is a scale step. {@code duration} is a fraction of a whole note, such as {@code 1/4}. */
public record MotifNote(int degree, String duration) {
}
