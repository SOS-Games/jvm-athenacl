// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.theory;

import java.util.ArrayList;
import java.util.List;

/**
 * A tonic plus a mode. {@code D dorian} and {@code D-dorian} are the same key.
 * Printed names look like {@code D-dorian}. Pitch-class names follow the scale letters,
 * so D aeolian spells B flat rather than A sharp.
 */
public record Key(int letter, int accidental, int tonicPc, Mode mode) {
    private static final int[] MAJOR = {0, 2, 4, 5, 7, 9, 11};

    public Key {
        letter = Math.floorMod(letter, 7);
        tonicPc = Math.floorMod(tonicPc, 12);
    }

    public static Key parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("missing key");
        }
        String trimmed = text.trim();
        String tonic;
        String modeName;
        int space = trimmed.indexOf(' ');
        int hyphen = trimmed.lastIndexOf('-');
        if (space > 0) {
            tonic = trimmed.substring(0, space).trim();
            modeName = trimmed.substring(space + 1).trim();
        } else if (hyphen > 0) {
            tonic = trimmed.substring(0, hyphen).trim();
            modeName = trimmed.substring(hyphen + 1).trim();
        } else {
            throw new IllegalArgumentException("key " + text + " needs a tonic and a mode");
        }
        ParsedTonic parsed = parseTonic(tonic);
        return new Key(parsed.letter, parsed.accidental, parsed.pc, Mode.parse(modeName));
    }

    /** Best letter spelling for a pitch class and mode. Prefers fewer double accidentals. */
    public static Key infer(int tonicPc, Mode mode) {
        int pc = Math.floorMod(tonicPc, 12);
        Key best = null;
        int bestPenalty = Integer.MAX_VALUE;
        for (int letter = 0; letter < 7; letter++) {
            int acc = Spell.normalize(pc - Spell.NATURAL[letter]);
            if (Math.abs(acc) > 1) {
                continue;
            }
            Key candidate = new Key(letter, acc, pc, mode);
            int doubles = 0;
            int marks = 0;
            for (String name : candidate.pitchClassNames()) {
                int size = Math.abs(Spell.accidental(name));
                marks += size;
                if (size > 1) {
                    doubles++;
                }
            }
            int penalty = doubles * 10 + marks;
            if (penalty < bestPenalty) {
                best = candidate;
                bestPenalty = penalty;
            }
        }
        if (best == null) {
            throw new IllegalArgumentException("no spelling for pitch class " + pc);
        }
        return best;
    }

    public String name() {
        return tonicName() + "-" + mode.id();
    }

    public String tonicName() {
        return Spell.note(letter, accidental);
    }

    public String pcs() {
        return String.join(",", pitchClassNames());
    }

    public List<String> pitchClassNames() {
        List<String> names = new ArrayList<>();
        int cursor = letter;
        for (int step : mode.steps()) {
            int want = Math.floorMod(tonicPc + step, 12);
            int acc = Spell.normalize(want - Spell.NATURAL[cursor]);
            names.add(Spell.note(cursor, acc));
            cursor = (cursor + 1) % 7;
        }
        return names;
    }

    /** Scale spelling when the pitch class is diatonic, otherwise a chromatic name. */
    public String spell(int pc) {
        int want = Math.floorMod(pc, 12);
        int[] steps = mode.steps();
        List<String> names = pitchClassNames();
        for (int i = 0; i < steps.length; i++) {
            if (Math.floorMod(tonicPc + steps[i], 12) == want) {
                return names.get(i);
            }
        }
        return Spell.chromatic(want, prefersFlats());
    }

    public String noteName(int midi) {
        String name = spell(midi);
        int acc = Spell.accidental(name);
        int octave = (midi - acc) / 12 - 1;
        return name + octave;
    }

    /** Major-scale degree, then the accidental. {@code bVII} in D is C, not B. */
    public int degreePc(int degree, int shift) {
        if (degree < 1 || degree > 7) {
            throw new IllegalArgumentException("degree " + degree);
        }
        return Math.floorMod(tonicPc + MAJOR[degree - 1] + shift, 12);
    }

    private boolean prefersFlats() {
        if (accidental < 0) {
            return true;
        }
        for (String name : pitchClassNames()) {
            if (name.indexOf('b') >= 0) {
                return true;
            }
        }
        return false;
    }

    private static ParsedTonic parseTonic(String token) {
        if (token.isEmpty()) {
            throw new IllegalArgumentException("missing tonic");
        }
        int letter = "CDEFGAB".indexOf(Character.toUpperCase(token.charAt(0)));
        if (letter < 0) {
            throw new IllegalArgumentException("pitch " + token + " is not a tonic");
        }
        int acc = 0;
        for (int i = 1; i < token.length(); i++) {
            char mark = token.charAt(i);
            if (mark == '#') {
                acc++;
            } else if (mark == 'b') {
                acc--;
            } else {
                throw new IllegalArgumentException("pitch " + token + " is not a tonic");
            }
        }
        if (Math.abs(acc) > 2) {
            throw new IllegalArgumentException("pitch " + token + " is not a tonic");
        }
        int pc = Math.floorMod(Spell.NATURAL[letter] + acc, 12);
        return new ParsedTonic(letter, acc, pc);
    }

    private record ParsedTonic(int letter, int accidental, int pc) {
    }
}
