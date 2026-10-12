// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.compat;

import java.util.Map;

/**
 * Expands a short token to the name stored in the map. {@code oc} and {@code OC}
 * both match the key {@code oc}. A full name, ignoring case, matches too.
 * No match returns an empty string.
 */
public final class Acronyms {
    private Acronyms() {
    }

    public static String expand(String token, Map<String, String> names) {
        if (token == null || token.isBlank() || names == null) {
            return "";
        }
        String folded = token.toLowerCase();
        for (Map.Entry<String, String> entry : names.entrySet()) {
            if (folded.equals(entry.getKey().toLowerCase()) || folded.equals(entry.getValue().toLowerCase())) {
                return entry.getValue();
            }
        }
        return "";
    }
}
