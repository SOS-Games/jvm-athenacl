// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.theory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheoryTest {
    @Test
    void dorianScale() {
        Key key = Key.parse("D dorian");
        assertEquals("D-dorian", key.name());
        assertEquals("D,E,F,G,A,B,C", key.pcs());
        assertEquals(key.name(), Key.parse("D-dorian").name());
    }

    @Test
    void minorSeventhInDorian() {
        Chord chord = Chord.parse("Dm7");
        Key key = Key.parse("D dorian");
        assertEquals("i7", chord.roman(key).format());
        assertEquals("D,F,A,C", chord.pcs(key));
    }

    @Test
    void flatSevenIsMajorReferential() {
        RomanNumeral seven = RomanNumeral.parse("bVII");
        RomanNumeral six = RomanNumeral.parse("bVI");
        Key key = Key.parse("D aeolian");
        assertEquals("C,E,G", new Chord(seven.pitchClasses(key)[0], ChordQuality.MAJOR).pcs(key));
        assertEquals("Bb,D,F", new Chord(six.pitchClasses(key)[0], ChordQuality.MAJOR).pcs(key));
    }

    @Test
    void voiceLedLoopStaysInDAeolian() {
        Progressions.Realized realized = Progressions.realize(
                "i bVII bVI bVII", Key.parse("D aeolian"), true);
        assertEquals(4, realized.chords());
        assertTrue(realized.maxMotion() <= 4, "maxMotion=" + realized.maxMotion());
        assertEquals(0, realized.parallels());
        HarmonyEstimate.Reading reading = HarmonyEstimate.read(realized.score());
        assertEquals("D-aeolian", reading.key());
        assertTrue(Double.parseDouble(reading.confidence()) >= 0.7, reading.confidence());
        assertEquals("Dm", reading.chord(1));
    }
}
