// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.core;

import io.github.sosgames.athenacl.compat.Py;

/**
 * Pitch-space conversions. Middle C is {@code c4}, pitch space 0, and MIDI 60.
 * Flats are {@code $}, sharps are {@code #}, and {@code ~} is a quarter tone.
 * MIDI numbers are limited to 0–127. A value that lands halfway rounds to the even number.
 */
public final class Pitch {
    private static final int[] LETTER = new int[128];
    private static final String[] PC_NAME = {
            "c", "c#", "d", "d#", "e", "f", "f#", "g", "g#", "a", "a#", "b"
    };

    static {
        LETTER['a'] = 9;
        LETTER['b'] = 11;
        LETTER['c'] = 0;
        LETTER['d'] = 2;
        LETTER['e'] = 4;
        LETTER['f'] = 5;
        LETTER['g'] = 7;
    }

    private Pitch() {
    }

    public static double nameToPs(String name) {
        String text = name.toLowerCase();
        if (text.isEmpty() || LETTER[text.charAt(0)] == 0 && text.charAt(0) != 'c') {
            throw new IllegalArgumentException("not a pitch name: " + name);
        }
        double pc = LETTER[text.charAt(0)];
        int flats = 0;
        int sharps = 0;
        int quarters = 0;
        StringBuilder octave = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '$') {
                flats++;
            } else if (ch == '#') {
                sharps++;
            } else if (ch == '~') {
                quarters++;
            } else if (Character.isDigit(ch) || ch == '-') {
                octave.append(ch);
            }
        }
        int oct = octave.isEmpty() ? 4 : Integer.parseInt(octave.toString());
        pc = pc - flats + sharps + quarters * 0.5;
        return pc + (oct - 4) * 12.0;
    }

    public static int psToMidi(double ps) {
        return limit(60 + ps);
    }

    public static double midiToPs(double midi) {
        return midi - 60;
    }

    public static String psToNoteName(double ps) {
        double[] parts = splitPs(ps);
        int oct = (int) parts[0];
        int pc = (int) parts[1];
        double micro = parts[2];
        String quarter = "";
        int bump = 0;
        if (micro >= 0.25 && micro < 0.75) {
            quarter = "~";
        } else if (micro >= 0.75) {
            bump = 1;
        }
        int wrapped = pc + bump;
        int octOffset = wrapped > 11 ? 1 : 0;
        return (PC_NAME[Math.floorMod(wrapped, 12)] + quarter + (oct + octOffset + 4)).toUpperCase();
    }

    public static String midiToNoteName(int midi) {
        return psToNoteName(midiToPs(midi));
    }

    public static double psToFq(double ps) {
        return 440 * Math.pow(2, (ps - 9) / 12.0);
    }

    public static double pc(double ps) {
        return floorMod(floorMod(ps, 12), 12);
    }

    /** Integer pitch space prints as an integer. A fraction keeps two digits when it is that coarse. */
    public static String formatPs(double value) {
        double frac = value - Math.floor(value);
        int sig = sigDig(frac);
        if (sig == 0) {
            return Long.toString(Py.round(value));
        }
        String text = Double.toString(Py.round(value, sig));
        int dot = text.indexOf('.');
        if (dot < 0) {
            return text;
        }
        String tail = text.substring(dot + 1);
        if (tail.length() < sig) {
            return text + "0".repeat(sig - tail.length());
        }
        return text;
    }

    static int limit(double midi) {
        int rounded = (int) Py.round(midi);
        if (rounded > 127) {
            return 127;
        }
        if (rounded < 0) {
            return 0;
        }
        return rounded;
    }

    private static double[] splitPs(double pitch) {
        double oct = Math.floor(pitch / 12.0);
        double within = pitch - oct * 12.0;
        double pc = Math.floor(within);
        return new double[] {oct, pc, within - pc};
    }

    private static double floorMod(double value, double divisor) {
        return value - Math.floor(value / divisor) * divisor;
    }

    private static int sigDig(double frac) {
        if (frac == 0) {
            return 0;
        }
        if (frac < 0.0000001) {
            return 8;
        }
        if (frac < 0.000001) {
            return 7;
        }
        if (frac < 0.00001) {
            return 6;
        }
        if (frac < 0.0001) {
            return 5;
        }
        if (frac < 0.001) {
            return 4;
        }
        if (frac < 0.01) {
            return 3;
        }
        return 2;
    }
}
