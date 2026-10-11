// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments;

import java.nio.file.Path;
import java.util.List;

/**
 * One named instrument after fallbacks and slot assignment. The slot is the patch the synth
 * will use. The source bank and program are where the sample lives in the soundfont.
 */
public record Playable(
        String name,
        String kitId,
        String sourceKey,
        Path file,
        int sourceBank,
        int sourceProgram,
        String preset,
        int drumkit,
        boolean percussion,
        int slotBank,
        int slotProgram,
        double gainDb,
        int transpose,
        String keyLow,
        String keyHigh,
        String outOfRange,
        String license,
        String attribution,
        String licenseFile,
        List<String> fallbacksTaken) {
    public Playable {
        fallbacksTaken = List.copyOf(fallbacksTaken);
    }

    public String slotText() {
        if (percussion) {
            return "p:" + slotProgram;
        }
        return slotBank + ":" + slotProgram;
    }
}
