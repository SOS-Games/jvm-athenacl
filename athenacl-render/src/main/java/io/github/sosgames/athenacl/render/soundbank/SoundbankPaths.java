// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render.soundbank;

import java.nio.file.Path;

/** Where an installed soundbank lives. {@code --cache} overrides the user cache. */
public final class SoundbankPaths {
    private SoundbankPaths() {
    }

    public static Path cache(Path override) {
        if (override != null) {
            return override;
        }
        return Path.of(System.getProperty("user.home"), ".cache", "athenacl-j", "soundbanks");
    }
}
