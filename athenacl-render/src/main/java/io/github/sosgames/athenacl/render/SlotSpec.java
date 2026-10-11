// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render;

import java.nio.file.Path;

/**
 * One instrument loaded into a synth slot the engine assigned. The slot's bank and program
 * are not the source file's own patch, so two pianos from two banks can both stay loaded.
 * {@code drumkit} is -1 for a melodic instrument. {@code preset} is set when the kit names a preset.
 * {@code gainDb} is extra level on the loaded instrument. Channel volume tops out a few decibels
 * above its usual setting, so a quiet bank needs this to reach the probe loudness.
 */
public record SlotSpec(
        Path soundbank,
        int sourceBank,
        int sourceProgram,
        String preset,
        int drumkit,
        int slotBank,
        int slotProgram,
        boolean percussion,
        double gainDb) {
}
