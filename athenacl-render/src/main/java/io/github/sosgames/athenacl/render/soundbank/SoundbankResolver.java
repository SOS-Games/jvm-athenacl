// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render.soundbank;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Picks a soundbank for a render. An explicit file that is missing, or a catalog id that
 * is not installed, falls back to Gervill's emergency bank so the render is never silent.
 * The default chain is the configured path, then a cached GeneralUser GS, then emergency.
 */
public final class SoundbankResolver {
    private SoundbankResolver() {
    }

    public record Selection(String name, Path file, boolean fallback) {
        public static Selection emergency(boolean fallback) {
            return new Selection("emergency", null, fallback);
        }
    }

    public static Selection select(String request, Path cache) throws IOException {
        if (request == null || request.isBlank()) {
            Path cached = cache.resolve(SoundbankCatalog.GENERAL_USER.fileName());
            if (SoundbankInstaller.matches(cached, SoundbankCatalog.GENERAL_USER)) {
                return new Selection("generaluser", cached, false);
            }
            return new Selection("default", null, false);
        }
        String text = request.trim();
        if (text.equalsIgnoreCase("emergency")) {
            return Selection.emergency(false);
        }
        if (text.equalsIgnoreCase("generaluser")) {
            Path cached = cache.resolve(SoundbankCatalog.GENERAL_USER.fileName());
            if (SoundbankInstaller.matches(cached, SoundbankCatalog.GENERAL_USER)) {
                return new Selection("generaluser", cached, false);
            }
            return Selection.emergency(true);
        }
        if (text.equalsIgnoreCase("system")) {
            Path system = systemBank();
            if (system != null) {
                return new Selection("system", system, false);
            }
            return Selection.emergency(true);
        }
        SoundbankCatalog.Entry known = SoundbankCatalog.byId(text);
        if (known != null) {
            Path cached = cache.resolve(known.fileName());
            if (SoundbankInstaller.matches(cached, known)) {
                return new Selection(known.id(), cached, false);
            }
            return Selection.emergency(true);
        }
        Path path = Path.of(text);
        if (Files.isRegularFile(path)) {
            String name = path.getFileName().toString();
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                name = name.substring(0, dot);
            }
            return new Selection(name, path, false);
        }
        return Selection.emergency(true);
    }

    private static Path systemBank() {
        String root = System.getenv("SystemRoot");
        if (root == null || root.isBlank()) {
            return null;
        }
        Path gm = Path.of(root, "System32", "drivers", "gm.dls");
        return Files.isRegularFile(gm) ? gm : null;
    }
}
