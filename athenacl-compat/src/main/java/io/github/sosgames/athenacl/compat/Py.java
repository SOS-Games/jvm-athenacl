// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.compat;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Python arithmetic the port has to match. {@code round} is half-to-even.
 * {@code //} and {@code %} round toward negative infinity, including on negatives.
 */
public final class Py {
    private Py() {
    }

    public static long round(double value) {
        return new BigDecimal(value).setScale(0, RoundingMode.HALF_EVEN).longValue();
    }

    public static double round(double value, int digits) {
        if (digits <= 0) {
            return round(value);
        }
        return new BigDecimal(value).setScale(digits, RoundingMode.HALF_EVEN).doubleValue();
    }

    public static long floorDiv(long left, long right) {
        return Math.floorDiv(left, right);
    }

    public static long floorMod(long left, long right) {
        return Math.floorMod(left, right);
    }

    /** Shortest round-trip text, the same digits CPython prints for these draws. */
    public static String repr(double value) {
        return Double.toString(value);
    }
}
