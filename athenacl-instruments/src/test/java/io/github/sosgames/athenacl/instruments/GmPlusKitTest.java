// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments;

import io.github.sosgames.athenacl.instruments.analysis.Ensemble;
import io.github.sosgames.athenacl.instruments.descriptors.DescriptorResolver;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GmPlusKitTest {
    private static final String YDP = "Zenph Studios YDP samples, SF2 by Roberto for FreePats";

    @Test
    void overlayReplacesPianoAndNylonAndKeepsTheRest() throws Exception {
        Kit kit = Kits.load(Path.of("instruments/gm-plus.yaml"));
        assertEquals("gm-plus", kit.id());
        assertEquals("upright", kit.instrument("piano").source());
        assertEquals(0, kit.instrument("piano").program());
        assertEquals(List.of("gm-full:piano", "gm-lite:piano"), kit.instrument("piano").fallbacks());
        assertEquals("guitar", kit.instrument("nylon").source());
        assertEquals("guitar", kit.instrument("guitar.nylon").source());
        assertEquals(List.of("gm-full:nylon", "gm-lite:nylon"), kit.instrument("guitar.nylon").fallbacks());
        assertEquals("ydp", kit.instrument("piano.grand").source());
        assertEquals("msg", kit.instrument("harp").source());
        assertTrue(kit.source("ydp").optional());
        assertFalse(kit.source("upright").optional());
        for (String key : List.of("upright", "guitar", "ydp")) {
            Kit.Source source = kit.source(key);
            assertTrue(source.license().redistribute());
            assertTrue(source.license().commercial());
            assertTrue(Files.isRegularFile(Path.of(source.license().file())));
            assertEquals(LicensePolicy.Verdict.ALLOW, LicensePolicy.load(false).judge(source.license()));
        }
        assertEquals("CC0-1.0", kit.source("upright").license().spdx());
        assertEquals("CC0-1.0", kit.source("guitar").license().spdx());
        assertEquals("CC-BY-3.0", kit.source("ydp").license().spdx());
        assertEquals(YDP, kit.source("ydp").license().attribution());
        assertTrue(Files.readString(Path.of(kit.source("ydp").license().file())).contains(YDP));
    }

    @Test
    void missingSpecialtyBanksFallThroughAndYdpStaysQuiet() throws Exception {
        Path cache = Files.createTempDirectory("gm-plus-empty");
        List<KitSession.Notice> notices = KitSession.peek(Path.of("instruments/gm-plus.yaml"), cache);
        assertTrue(notices.stream().anyMatch(notice ->
                "upright".equals(notice.source()) && "gm-full".equals(notice.fallback())
                        && notice.hint().contains("freepats-upright-kw")));
        assertTrue(notices.stream().anyMatch(notice ->
                "guitar".equals(notice.source()) && "gm-full".equals(notice.fallback())));
        assertTrue(notices.stream().noneMatch(notice -> "ydp".equals(notice.source())));
        KitException missing = org.junit.jupiter.api.Assertions.assertThrows(KitException.class,
                () -> KitSession.open(Path.of("instruments/gm-plus.yaml"), cache, false));
        assertEquals("KIT_SOURCE_MISSING", missing.issues().get(0).code());
    }

    @Test
    void twoFallbacksIntoTheSameKitAreNotACycle() throws Exception {
        Path dir = Files.createTempDirectory("gm-plus-fallback");
        Files.writeString(dir.resolve("plus.yaml"), """
                kit: plus
                version: 1
                extends: cc-by-grand
                sources:
                  gone:
                    id: not-a-catalog-bank
                    format: sf2
                    license:
                      spdx: CC0-1.0
                      attribution: "missing"
                      redistribute: true
                      commercial: true
                instruments:
                  piano.grand: {source: gone, bank: 0, program: 0, fallback: [cc-by-grand:piano.grand]}
                  other: {source: gone, bank: 0, program: 0, fallback: [cc-by-grand:piano.grand]}
                """);
        KitSession session = KitSession.open(dir.resolve("plus.yaml"), dir, false);
        assertEquals(2, session.fallbacksTaken());
        assertEquals("ydp", session.get("piano.grand").sourceKey());
        assertEquals("ydp", session.get("other").sourceKey());
    }

    @Test
    void declaredDescriptorsAndEnsembleSelect() throws Exception {
        DescriptorResolver.Resolved piano = DescriptorResolver.one(Path.of("instruments/gm-plus.yaml"), "piano");
        assertEquals("declared", piano.rolesSource());
        assertTrue(piano.roles().contains("lead"));
        assertEquals("declared", piano.tagsSource());
        DescriptorResolver.Resolved grand = DescriptorResolver.one(Path.of("instruments/gm-plus.yaml"), "piano.grand");
        assertTrue(grand.notes().contains(YDP));
        for (int seed = 1; seed <= 20; seed++) {
            Ensemble.Result result = Ensemble.select("fantasy", "sneaking", null, "gm-plus", seed);
            assertFalse(result.fallback());
            assertFalse(result.roles().isEmpty());
        }
    }

    @Test
    void creditsNameTheCcByAttributionWhenGrandIsUsed() throws Exception {
        Path out = Files.createTempDirectory("gm-plus-credits");
        Path cache = Files.createTempDirectory("gm-plus-cache");
        CreditsWriter.Written written = CreditsWriter.write(
                List.of(Path.of("samples/kits/cc-by-theme.yaml")), out, cache, false);
        assertTrue(written.licenses().contains("CC-BY-3.0"));
        String text = Files.readString(out.resolve("CREDITS.md"));
        assertTrue(text.contains(YDP));
        assertTrue(Files.isRegularFile(out.resolve("FreePats-YDP-Grand-CC-BY-3.0.txt")));
    }
}
