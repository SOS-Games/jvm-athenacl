// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.motif;

import java.util.ArrayList;
import java.util.List;

/**
 * The ten ways a motif can come back. Inversion is diatonic and turns around the first note,
 * so inverting twice is the motif you started with.
 */
public final class MotifTransforms {
    private MotifTransforms() {
    }

    public static Motif apply(Motif motif, String name) {
        return switch (name) {
            case "idea", "repeat" -> motif;
            case "invert" -> invert(motif);
            case "retrograde" -> reverse(motif);
            case "transpose" -> transpose(motif, 1);
            case "augment" -> durations(motif, 2, 1);
            case "diminish" -> durations(motif, 1, 2);
            case "fragment" -> fragment(motif);
            case "sequence" -> sequence(motif);
            case "ornament" -> ornament(motif);
            case "displace" -> displace(motif);
            default -> throw new IllegalArgumentException("unknown transform " + name);
        };
    }

    public static Motif invert(Motif motif) {
        int axis = motif.notes().get(0).degree();
        List<MotifNote> notes = new ArrayList<>();
        for (MotifNote note : motif.notes()) {
            notes.add(new MotifNote(Math.floorMod(axis * 2 - note.degree(), motif.steps()), note.duration()));
        }
        return new Motif(motif.id(), motif.steps(), notes);
    }

    private static Motif reverse(Motif motif) {
        List<MotifNote> notes = new ArrayList<>();
        for (int i = motif.notes().size() - 1; i >= 0; i--) {
            notes.add(motif.notes().get(i));
        }
        return new Motif(motif.id(), motif.steps(), notes);
    }

    private static Motif transpose(Motif motif, int interval) {
        List<MotifNote> notes = new ArrayList<>();
        for (MotifNote note : motif.notes()) {
            notes.add(new MotifNote(Math.floorMod(note.degree() + interval, motif.steps()), note.duration()));
        }
        return new Motif(motif.id(), motif.steps(), notes);
    }

    private static Motif durations(Motif motif, int numerator, int denominator) {
        List<MotifNote> notes = new ArrayList<>();
        for (MotifNote note : motif.notes()) {
            notes.add(new MotifNote(note.degree(), scale(note.duration(), numerator, denominator)));
        }
        return new Motif(motif.id(), motif.steps(), notes);
    }

    private static Motif fragment(Motif motif) {
        int keep = Math.max(1, motif.notes().size() / 2);
        return new Motif(motif.id(), motif.steps(), motif.notes().subList(0, keep));
    }

    private static Motif sequence(Motif motif) {
        List<MotifNote> notes = new ArrayList<>(motif.notes());
        for (MotifNote note : motif.notes()) {
            notes.add(new MotifNote(Math.floorMod(note.degree() + 1, motif.steps()), note.duration()));
        }
        return new Motif(motif.id(), motif.steps(), notes);
    }

    private static Motif ornament(Motif motif) {
        List<MotifNote> notes = new ArrayList<>();
        for (MotifNote note : motif.notes()) {
            String half = scale(note.duration(), 1, 2);
            notes.add(new MotifNote(Math.floorMod(note.degree() + 1, motif.steps()), half));
            notes.add(new MotifNote(note.degree(), half));
        }
        return new Motif(motif.id(), motif.steps(), notes);
    }

    private static Motif displace(Motif motif) {
        List<MotifNote> notes = new ArrayList<>();
        int count = motif.notes().size();
        for (int i = 0; i < count; i++) {
            notes.add(new MotifNote(motif.notes().get(i).degree(), motif.notes().get((i + 1) % count).duration()));
        }
        return new Motif(motif.id(), motif.steps(), notes);
    }

    static String scale(String duration, int numeratorFactor, int denominatorFactor) {
        int slash = duration.indexOf('/');
        int numerator = Integer.parseInt(duration.substring(0, slash).trim()) * numeratorFactor;
        int denominator = Integer.parseInt(duration.substring(slash + 1).trim()) * denominatorFactor;
        int gcd = gcd(numerator, denominator);
        return (numerator / gcd) + "/" + (denominator / gcd);
    }

    private static int gcd(int left, int right) {
        int a = Math.abs(left);
        int b = Math.abs(right);
        while (b != 0) {
            int next = a % b;
            a = b;
            b = next;
        }
        return Math.max(1, a);
    }
}
