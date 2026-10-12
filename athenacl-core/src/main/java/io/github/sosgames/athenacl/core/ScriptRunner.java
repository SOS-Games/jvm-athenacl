// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.core;

import java.util.Locale;

/**
 * Picks the runner for one script. A {@code tin} line is a LineGroove beat.
 * Anything else stays on the path-only runner.
 */
public final class ScriptRunner {
    private ScriptRunner() {
    }

    public static PathScript.Result run(String text, String title) {
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String command = line.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
            if ("tin".equals(command)) {
                return GrooveScript.run(text, title);
            }
        }
        return PathScript.run(text, title);
    }
}
