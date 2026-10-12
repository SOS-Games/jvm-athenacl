// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoreTest {
    @Test
    void pitchNamesMatchAthenaCl() {
        assertEquals(0, Pitch.nameToPs("c4"));
        assertEquals(60, Pitch.psToMidi(0));
        assertEquals("C4", Pitch.psToNoteName(0));
        assertEquals("C~4", Pitch.psToNoteName(0.5));
        assertEquals("A#4", Pitch.psToNoteName(10));
        assertEquals("A4", Pitch.midiToNoteName(69));
        assertEquals(60, Pitch.psToMidi(0.5));
        assertEquals(7.5, Pitch.nameToPs("g~4"));
        assertEquals(68, Pitch.psToMidi(7.5));
        assertEquals("G~4", Pitch.psToNoteName(7.5));
        assertEquals(-11, Pitch.nameToPs("d$3"));
        assertEquals(49, Pitch.psToMidi(-11));
        assertEquals(-70, Pitch.nameToPs("d-2"));
        assertEquals(0, Pitch.psToMidi(-70));
        assertEquals("D-2", Pitch.psToNoteName(-70));
        assertEquals(440.0, Pitch.psToFq(9));
        assertEquals(261.6255653005986, Pitch.psToFq(0), 1e-12);
        assertEquals("7.50", Pitch.formatPs(7.5));
        assertEquals("0", Pitch.formatPs(0));
    }

    @Test
    void pathReprTransposeAndInvert() {
        PolyPath path = new PolyPath("scale");
        path.add(Multiset.names(java.util.List.of("c4", "e4", "g4")));
        assertEquals("(0,4,7)", path.repr("psReal"));
        assertEquals("(C4,E4,G4)", path.repr("psName"));
        assertEquals("(60,64,67)", path.repr("midi"));
        path.transpose(2);
        assertEquals("(2,6,9)", path.repr("psReal"));
        path.invert();
        assertEquals("(-2,-6,-9)", path.repr("psReal"));
        path.retro();
        assertEquals(1, path.size());
    }

    @Test
    void pathScriptsMatchGoldenMidi() throws Exception {
        assertGolden("c-major");
        assertGolden("triads");
    }

    @Test
    void valueGeneratorsMatchGolden() throws Exception {
        ValueGenerators.Check check = ValueGenerators.check(Path.of("conformance/po/value.txt"));
        assertEquals(List.of(), check.failed());
        assertEquals(45, check.pass());
    }

    @Test
    void omdeAndRhythmMatchGolden() throws Exception {
        OmdeGenerators.Check waves = OmdeGenerators.check(Path.of("conformance/po/omde.txt"));
        RhythmGenerators.Check beats = RhythmGenerators.check(Path.of("conformance/po/rhythm.txt"));
        assertEquals(List.of(), waves.failed());
        assertEquals(List.of(), beats.failed());
        assertEquals(36, waves.pass());
        assertEquals(13, beats.pass());
    }

    @Test
    void textureAndCloneMatchGolden() throws Exception {
        TextureClone.Check check = TextureClone.check(Path.of("conformance/po/texture-clone.txt"));
        assertEquals(List.of(), check.failed());
        assertEquals(53, check.pass());
    }

    @Test
    void basketWalksInOrder() {
        assertEquals(List.of("1", "2", "3", "1", "2", "3"),
                ParameterObjects.sample("basketGen", "oc,(1,2,3)", 6, 0));
    }

    @Test
    void lineGrooveMatchesGoldenMidi() throws Exception {
        for (String stem : List.of("script02a", "script02b")) {
            Path dir = Path.of("conformance/demos");
            String text = Files.readString(dir.resolve(stem + ".ath"));
            byte[] got = ScriptRunner.run(text, stem).midi();
            byte[] expected = Files.readAllBytes(dir.resolve(stem + ".mid"));
            assertArrayEquals(expected, got, stem);
        }
    }

    @Test
    void textureCommandIsRefused() {
        ScriptException error = assertThrows(ScriptException.class,
                () -> PathScript.run("emo mp\ntin a 36\neln\n", "x"));
        assertEquals("ATHENA_TEXTURE", error.code());
    }

    private static void assertGolden(String stem) throws Exception {
        Path dir = Path.of("conformance/paths");
        String text = Files.readString(dir.resolve(stem + ".ath"));
        byte[] got = PathScript.run(text, stem).midi();
        byte[] expected = Files.readAllBytes(dir.resolve(stem + ".mid"));
        assertArrayEquals(expected, got);
    }
}
