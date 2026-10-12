// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A named row of pitch groups. Each group is a chord. The row is what a texture will walk later.
 * This slice can turn the row into notes itself: equal time, or weights from {@code pidf}.
 */
public final class PolyPath {
    private final String name;
    private final List<Multiset> groups = new ArrayList<>();

    public PolyPath(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public int size() {
        return groups.size();
    }

    public List<Multiset> groups() {
        return List.copyOf(groups);
    }

    public void add(Multiset group) {
        groups.add(group);
    }

    public void transpose(double steps) {
        for (Multiset group : groups) {
            group.transpose(steps);
        }
    }

    public void invert() {
        for (Multiset group : groups) {
            group.invert();
        }
    }

    public void retro() {
        java.util.Collections.reverse(groups);
    }

    public double durSum() {
        double sum = 0;
        for (Multiset group : groups) {
            sum += group.dur();
        }
        return sum;
    }

    public String repr(String kind) {
        if (groups.isEmpty()) {
            return "empty PolyPath";
        }
        List<String> parts = new ArrayList<>();
        for (Multiset group : groups) {
            parts.add(group.repr(kind));
        }
        return String.join(",", parts);
    }
}
