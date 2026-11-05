// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render.soundbank;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundbankCatalogTest {
    @TempDir
    Path dir;

    @Test
    void freepatsPinsAreArchivedAndDryRunWritesNothing() throws Exception {
        assertTrue(SoundbankCatalog.knownIds().contains("freepats-upright-kw"));
        assertTrue(SoundbankCatalog.knownIds().contains("freepats-spanish-guitar"));
        assertTrue(SoundbankCatalog.knownIds().contains("freepats-ydp-grand"));
        SoundbankCatalog.Entry upright = SoundbankCatalog.FREEPATS_UPRIGHT;
        assertTrue(upright.archived());
        assertEquals("CC0-1.0", upright.license());
        assertEquals(57_377_848L, upright.size());
        assertEquals(28_832_466L, upright.archiveSize());
        SoundbankCatalog.Entry ydp = SoundbankCatalog.FREEPATS_YDP;
        assertEquals("CC-BY-3.0", ydp.license());
        assertTrue(ydp.licenseFile().endsWith("FreePats-YDP-Grand-CC-BY-3.0.txt"));
        Path cache = dir.resolve("cache");
        SoundbankInstaller.Plan plan = SoundbankInstaller.install(upright.id(), cache, true);
        assertEquals("download", plan.action());
        assertEquals(upright.sha256(), plan.sha256());
        assertTrue(plan.dryRun());
        assertFalse(Files.exists(cache));
    }
}
