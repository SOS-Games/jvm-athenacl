// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render.soundbank;

import com.sun.media.sound.ModelPatch;

import javax.sound.midi.Instrument;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Soundbank;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Lists presets in a soundbank file. Drum kits are the percussion patches. */
public final class InstrumentIndex {
    private InstrumentIndex() {
    }

    public record Counts(int instruments, int drumkits) {
    }

    public record Preset(int bank, int program, String name, boolean percussion) {
    }

    public static Counts count(Path file) throws IOException, InvalidMidiDataException {
        List<Preset> presets = list(file);
        int drums = 0;
        for (Preset preset : presets) {
            if (preset.percussion()) {
                drums++;
            }
        }
        return new Counts(presets.size(), drums);
    }

    public static List<Preset> list(Path file) throws IOException, InvalidMidiDataException {
        Soundbank bank = MidiSystem.getSoundbank(file.toFile());
        List<Preset> presets = new ArrayList<>();
        for (Instrument instrument : bank.getInstruments()) {
            boolean percussion = instrument.getPatch() instanceof ModelPatch patch && patch.isPercussion();
            presets.add(new Preset(instrument.getPatch().getBank(), instrument.getPatch().getProgram(),
                    instrument.getName(), percussion));
        }
        return presets;
    }
}
