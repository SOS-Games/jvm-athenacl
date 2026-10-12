// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.theory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A Forte set class such as {@code 3-11}, the major triad. The table lists each
 * prime form and its inversion. Typing {@code 3-11} means the prime form
 * {@code 0,3,7}. The letter B is the inversion, {@code 0,4,7}.
 */
public record SetClass(String name, String pcs, String icv, String invariance, int z) {
    public static SetClass parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("missing set class");
        }
        String token = text.trim();
        SetClass found = table().get(token);
        if (found == null && !token.endsWith("A") && !token.endsWith("B")) {
            found = table().get(token + "A");
        }
        if (found == null) {
            throw new IllegalArgumentException("unknown set class: " + token);
        }
        return found;
    }

    public String forte(String requested) {
        String token = requested.trim();
        if (name.endsWith("A") && token.equals(name.substring(0, name.length() - 1))) {
            return token;
        }
        return name;
    }

    public String partner() {
        if (z == 0) {
            return "";
        }
        int dash = name.indexOf('-');
        String card = name.substring(0, dash);
        return card + "-" + z;
    }

    private static Map<String, SetClass> table() {
        return Table.FORMS;
    }

    private static final class Table {
        private static final Map<String, SetClass> FORMS = load();

        private Table() {
        }

        private static Map<String, SetClass> load() {
            Map<String, SetClass> forms = new LinkedHashMap<>();
            var resource = SetClass.class.getResourceAsStream("/setclass.txt");
            if (resource == null) {
                throw new IllegalStateException("set class table is missing");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    String[] cells = line.split("\t");
                    forms.put(cells[0], new SetClass(cells[0], cells[1], cells[2], cells[3], Integer.parseInt(cells[4])));
                }
            } catch (IOException e) {
                throw new IllegalStateException("set class table", e);
            }
            return forms;
        }
    }
}
