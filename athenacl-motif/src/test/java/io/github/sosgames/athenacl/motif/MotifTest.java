// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.motif;

import io.github.sosgames.athenacl.theory.Key;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MotifTest {
    @Test
    void invertTwiceIsTheMotif() {
        Motif motif = MotifGenerator.generate(Key.parse("D dorian"), 3);
        Motif turned = MotifTransforms.invert(MotifTransforms.invert(motif));
        assertEquals(motif.notes(), turned.notes());
        assertTrue(motif.notes().size() >= 2 && motif.notes().size() <= 8);
    }

    @Test
    void sentenceOpensWithTheIdea() {
        assertEquals("m1:idea", PhrasePlan.chain("peaceful", 0));
        assertEquals("m1:invert+diminish+idea", PhrasePlan.chain("combat", 0));
        assertEquals(java.util.List.of("invert"), PhrasePlan.period(1));
    }

    @Test
    void themeMidiIsOneMotif() throws Exception {
        Motif motif = MotifImport.read(Path.of("samples/motifs/theme.mid"));
        assertEquals("m1", motif.id());
        assertTrue(motif.notes().size() >= 1);
    }
}
