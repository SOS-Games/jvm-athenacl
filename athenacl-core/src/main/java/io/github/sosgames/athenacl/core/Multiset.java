// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.core;

import io.github.sosgames.athenacl.compat.Py;

import java.util.ArrayList;
import java.util.List;

/**
 * One pitch group: ordered pitch-space values that can be transposed or inverted together.
 * Set-class names (Forte numbers) are not stored here. Those tables come with the theory slice.
 */
public final class Multiset {
    private final List<Double> pitches = new ArrayList<>();
    private double duration = 1;

    public Multiset(List<Double> pitches) {
        if (pitches == null || pitches.isEmpty()) {
            throw new IllegalArgumentException("a pitch group needs at least one pitch");
        }
        this.pitches.addAll(pitches);
    }

    public static Multiset names(List<String> names) {
        List<Double> pitches = new ArrayList<>();
        for (String name : names) {
            pitches.add(Pitch.nameToPs(name));
        }
        return new Multiset(pitches);
    }

    public int size() {
        return pitches.size();
    }

    public double dur() {
        return duration;
    }

    public void setDur(double duration) {
        if (duration <= 0) {
            throw new IllegalArgumentException("duration must be positive");
        }
        this.duration = duration;
    }

    public List<Double> pitches() {
        return List.copyOf(pitches);
    }

    public void transpose(double steps) {
        for (int i = 0; i < pitches.size(); i++) {
            pitches.set(i, pitches.get(i) + steps);
        }
    }

    public void invert() {
        for (int i = 0; i < pitches.size(); i++) {
            pitches.set(i, -pitches.get(i));
        }
    }

    public String repr(String kind) {
        List<String> parts = new ArrayList<>();
        for (double ps : pitches) {
            parts.add(switch (kind) {
                case "psReal" -> Pitch.formatPs(ps);
                case "psName" -> Pitch.psToNoteName(ps);
                case "midi" -> Integer.toString(Pitch.psToMidi(ps));
                case "pc" -> Long.toString(Py.round(Pitch.pc(ps)));
                default -> throw new IllegalArgumentException("bad format: " + kind);
            });
        }
        return "(" + String.join(",", parts) + ")";
    }

    @Override
    public String toString() {
        return repr("psReal");
    }

}
