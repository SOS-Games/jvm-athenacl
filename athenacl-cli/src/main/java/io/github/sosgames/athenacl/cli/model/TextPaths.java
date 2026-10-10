// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.model;

import java.nio.file.Path;

/** Prints paths with forward slashes, relative to the working directory when possible. */
public final class TextPaths {
    private TextPaths() {
    }

    public static String portable(Path path) {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path abs = path.toAbsolutePath().normalize();
        Path shown = abs.startsWith(cwd) ? cwd.relativize(abs) : abs;
        return shown.toString().replace('\\', '/');
    }
}
