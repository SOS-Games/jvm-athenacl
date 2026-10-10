// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.rhythm;

/**
 * A time signature. {@code 6/8} groups as 3+3. Other meters accent every beat.
 */
public record Meter(int numerator, int denominator) {
    public Meter {
        if (numerator <= 0 || denominator <= 0) {
            throw new IllegalArgumentException("meter must be above 0");
        }
    }

    public static Meter parse(String text) {
        if (text == null || text.isBlank() || !text.contains("/")) {
            throw new IllegalArgumentException("meter " + text);
        }
        String[] parts = text.trim().split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("meter " + text);
        }
        try {
            return new Meter(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("meter " + text);
        }
    }

    public String text() {
        return numerator + "/" + denominator;
    }

    /** Beat groups that take an accent, such as {@code 3+3} in 6/8. */
    public String grouping() {
        if (numerator == 6 && denominator == 8) {
            return "3+3";
        }
        if (numerator == 5 && denominator == 8) {
            return "3+2";
        }
        if (numerator == 7 && denominator == 8) {
            return "2+2+3";
        }
        StringBuilder groups = new StringBuilder();
        for (int i = 0; i < numerator; i++) {
            if (i > 0) {
                groups.append('+');
            }
            groups.append('1');
        }
        return groups.toString();
    }
}
