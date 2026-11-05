// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.adaptive.Theme;
import io.github.sosgames.athenacl.instruments.KitDefaults;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Fills a theme that did not name a kit. Fantasy and sci-fi presets used to assume
 * {@code gm-lite}. They now use {@link KitDefaults}, so {@code --kit}, {@code ATHENACL_KIT},
 * and {@code kit.default} apply. A file that lists {@code instruments.kits} is left alone
 * unless {@code --kit} is set.
 */
final class PlaybackKit {
    private PlaybackKit() {
    }

    static Theme apply(Theme theme, Path file, String flag) throws IOException {
        if (flag != null && !flag.isBlank()) {
            return theme.withKits(java.util.List.of(KitDefaults.id(flag)));
        }
        if (!KitDefaults.pins(file).isEmpty() || !theme.kits().isEmpty()) {
            return theme;
        }
        String preset = theme.preset();
        if (!"fantasy".equals(preset) && !"scifi-space".equals(preset)) {
            return theme;
        }
        return theme.withKits(java.util.List.of(KitDefaults.id(null)));
    }
}
