// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render.soundbank;

import com.sun.media.sound.ModelPatch;

import javax.sound.midi.Instrument;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Soundbank;
import java.io.IOException;
import java.nio.file.Path;

/** Counts presets in a soundbank file. Drum kits are the percussion patches. */
public final class InstrumentIndex {
    private InstrumentIndex() {
    }

    public record Counts(int instruments, int drumkits) {
    }

    public static Counts count(Path file) throws IOException, InvalidMidiDataException {
        Soundbank bank = MidiSystem.getSoundbank(file.toFile());
        int drums = 0;
        Instrument[] instruments = bank.getInstruments();
        for (Instrument instrument : instruments) {
            if (instrument.getPatch() instanceof ModelPatch patch && patch.isPercussion()) {
                drums++;
            }
        }
        return new Counts(instruments.length, drums);
    }
}
