// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.model;

import java.util.Collection;
import java.util.Locale;

/** Picks a close command or schema id for a fix hint. Only a distance of 1 or 2 counts. */
public final class Suggestions {
    private Suggestions() {
    }

    public static String nearest(String token, Collection<String> names) {
        return nearest(token, names, 2);
    }

    /** Like {@link #nearest(String, Collection)} but the caller picks how far a hint may reach. */
    public static String nearest(String token, Collection<String> names, int maxDistance) {
        if (token == null || token.isBlank() || names == null || maxDistance < 0) {
            return null;
        }
        String want = token.toLowerCase(Locale.ROOT);
        String best = null;
        int bestDistance = maxDistance + 1;
        for (String name : names) {
            int distance = distance(want, name.toLowerCase(Locale.ROOT));
            if (distance < bestDistance) {
                best = name;
                bestDistance = distance;
            }
        }
        return bestDistance <= maxDistance ? best : null;
    }

    static int distance(String left, String right) {
        int n = left.length();
        int m = right.length();
        int[] row = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            row[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            int previous = row[0];
            row[0] = i;
            for (int j = 1; j <= m; j++) {
                int old = row[j];
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                row[j] = Math.min(Math.min(row[j] + 1, row[j - 1] + 1), previous + cost);
                previous = old;
            }
        }
        return row[m];
    }
}
