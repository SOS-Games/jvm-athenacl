// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitDefaultsTest {
    @Test
    void flagNamesTheKit() {
        KitDefaults.Choice choice = KitDefaults.resolve("gm-lite");
        assertEquals("gm-lite", choice.id());
        assertEquals("flag", choice.source());
    }

    @Test
    void propertyAndRcBeatTheBuiltin() throws Exception {
        if (System.getenv("ATHENACL_KIT") != null && !System.getenv("ATHENACL_KIT").isBlank()) {
            return;
        }
        String property = System.getProperty("kit.default");
        String rc = System.getProperty("athenacl.rc");
        Path file = Files.createTempFile("athenacl-rc", ".rc");
        try {
            System.setProperty("kit.default", "gm-lite");
            assertEquals("rc", KitDefaults.resolve(null).source());
            assertEquals("gm-lite", KitDefaults.id(null));
            System.clearProperty("kit.default");
            Files.writeString(file, "kit.default=gm-lite\n");
            System.setProperty("athenacl.rc", file.toString());
            assertEquals("gm-lite", KitDefaults.id(null));
            assertEquals("rc", KitDefaults.resolve(null).source());
        } finally {
            restore("kit.default", property);
            restore("athenacl.rc", rc);
            Files.deleteIfExists(file);
        }
    }

    @Test
    void missingMuseScoreIsOneWarning() throws Exception {
        Path cache = Files.createTempDirectory("athenacl-empty-cache");
        try {
            var notices = KitSession.peek(Kits.find("gm-full"), cache);
            assertFalse(notices.isEmpty());
            KitSession.Notice notice = notices.get(0);
            assertEquals("gm-full", notice.kit());
            assertEquals("msg", notice.source());
            assertEquals("gm-lite", notice.fallback());
            assertTrue(notice.hint().contains("musescore-general"));
            assertEquals(1, notices.size());
        } finally {
            Files.deleteIfExists(cache);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
