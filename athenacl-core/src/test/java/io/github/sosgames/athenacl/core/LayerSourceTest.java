// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerSourceTest {
    @Test
    void aSieveKeepsTheNamedRemainder() {
        assertEquals(List.of(2, 5, 8, 11), LayerSource.sieve("sieve,3@2", 0, 11));
    }

    @Test
    void theSameSeedDrawsTheSameMarkovRhythm() {
        String po = "markovPulse,(2,1,0.5),(3,4,2)";
        assertEquals(LayerSource.rhythm(po, 8, 1), LayerSource.rhythm(po, 8, 1));
        assertTrue(LayerSource.knownTexture("LineGroove"));
    }

    @Test
    void aBasketWalksItsList() {
        assertEquals(List.of("1/4", "1/8", "1/2", "1/4"),
                LayerSource.rhythm("basketGen,oc,(1,0.5,2)", 4, 7));
    }
}
