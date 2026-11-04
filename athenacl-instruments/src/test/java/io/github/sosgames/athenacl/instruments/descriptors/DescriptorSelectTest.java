// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments.descriptors;

import io.github.sosgames.athenacl.instruments.analysis.Ensemble;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DescriptorSelectTest {
    @Test
    void anAvoidedInstrumentIsNeverPicked() throws Exception {
        Path out = Path.of("build/l7k/tagged-test.yaml");
        Files.createDirectories(out.getParent());
        KitTagger.write(Path.of("samples/kits/tag.yaml"), "soft_flute", new String[] {"avoid=true"}, out);
        for (int seed = 1; seed <= 20; seed++) {
            Ensemble.Result result = Ensemble.select("fantasy", "sneaking", null, out.toString(), seed);
            for (Ensemble.Role role : result.roles()) {
                assertFalse("soft_flute".equals(role.instrument()), result.text());
            }
        }
        DescriptorResolver.Resolved flute = DescriptorResolver.one(out, "soft_flute");
        assertTrue(flute.avoid());
        assertEquals("declared", flute.avoidSource());
    }

    @Test
    void anUntaggedPresetUsesTheGmTable() throws Exception {
        DescriptorResolver.Resolved flute = DescriptorResolver.one(Path.of("instruments/gm-lite.yaml"), "flute");
        assertEquals("gm-default", flute.rolesSource());
        assertTrue(flute.roles().contains("lead"));
        assertTrue(flute.text().contains("source=gm-default"));
    }
}
