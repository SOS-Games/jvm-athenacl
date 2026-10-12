// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.theory;

/**
 * A tuning that moves each pitch class and leaves the octave where it was.
 * Twelve-tone equal temperament leaves the pitch alone. Pythagorean, just, and
 * meantone use the static cents tables, written here in semitone steps.
 */
public final class Temperament {
    private static final double[] TWELVE = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    private static final double[] PYTHAGOREAN = {0.00, 0.90, 2.04, 2.94, 4.08, 4.98, 6.00, 7.02, 7.92, 9.06, 9.96, 11.09};
    private static final double[] JUST = {0.00, 1.12, 2.04, 3.16, 3.86, 4.98, 6.00, 7.02, 8.14, 8.84, 10.18, 10.88};
    private static final double[] MEANTONE = {0.00, 1.17, 1.93, 3.10, 3.86, 5.03, 5.80, 6.97, 8.14, 8.90, 10.07, 10.83};

    private Temperament() {
    }

    public static double[] twelve() {
        return tune(TWELVE);
    }

    public static double[] pythagorean() {
        return tune(PYTHAGOREAN);
    }

    public static double[] just() {
        return tune(JUST);
    }

    public static double[] meantone() {
        return tune(MEANTONE);
    }

    private static double[] tune(double[] table) {
        double[] out = new double[12];
        for (int pc = 0; pc < 12; pc++) {
            out[pc] = pitch(pc, table);
        }
        return out;
    }

    static double pitch(double ps, double[] table) {
        double oct = Math.floor(ps / 12.0);
        double within = ps - oct * 12.0;
        int pc = (int) Math.floor(within);
        double micro = within - pc;
        return oct * 12.0 + table[pc] + micro;
    }
}
