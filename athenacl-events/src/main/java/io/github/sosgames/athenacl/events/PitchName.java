// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.events;

/** Scientific pitch names. C4 is MIDI 60. */
public final class PitchName {
    private static final String[] SHARPS = {
            "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };

    private PitchName() {
    }

    public static int midi(String name) {
        String text = name.trim();
        if (text.length() < 2) {
            throw new IllegalArgumentException("pitch " + name);
        }
        int letter = "CDEFGAB".indexOf(Character.toUpperCase(text.charAt(0)));
        if (letter < 0) {
            throw new IllegalArgumentException("pitch " + name);
        }
        int index = 1;
        int accidental = 0;
        if (index < text.length() && (text.charAt(index) == '#' || text.charAt(index) == 'b')) {
            accidental = text.charAt(index) == '#' ? 1 : -1;
            index++;
        }
        int octave;
        try {
            octave = Integer.parseInt(text.substring(index));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("pitch " + name);
        }
        int pc = (natural(letter) + accidental) % 12;
        if (pc < 0) {
            pc += 12;
        }
        int midi = (octave + 1) * 12 + pc;
        if (midi < 0 || midi > 127) {
            throw new IllegalArgumentException("pitch " + name + " is outside MIDI");
        }
        return midi;
    }

    public static String name(int midi) {
        int pc = Math.floorMod(midi, 12);
        int octave = midi / 12 - 1;
        return SHARPS[pc] + octave;
    }

    private static int natural(int letterIndex) {
        return switch (letterIndex) {
            case 0 -> 0;
            case 1 -> 2;
            case 2 -> 4;
            case 3 -> 5;
            case 4 -> 7;
            case 5 -> 9;
            case 6 -> 11;
            default -> throw new IllegalArgumentException();
        };
    }
}
