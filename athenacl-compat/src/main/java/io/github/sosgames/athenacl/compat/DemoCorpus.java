// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.compat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The 31 MIDI demos, one command per line, under {@code conformance/demos}.
 * Each file is seeded with {@code tpsd 7} and {@code tmsd 7} and ends with {@code eln}.
 * Names sort by the file name so the list does not depend on disk order.
 */
public final class DemoCorpus {
    private DemoCorpus() {
    }

    public static List<Path> scripts(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Path> found = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".ath")).forEach(found::add);
        }
        found.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return found;
    }

    public static int commands(Path script) throws IOException {
        int count = 0;
        for (String line : Files.readAllLines(script, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                count++;
            }
        }
        return count;
    }
}
