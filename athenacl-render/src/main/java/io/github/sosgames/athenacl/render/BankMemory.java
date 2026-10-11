// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render;

import com.sun.media.sound.ModelByteBuffer;
import com.sun.media.sound.ModelByteBufferWavetable;
import com.sun.media.sound.ModelInstrument;
import com.sun.media.sound.ModelOscillator;
import com.sun.media.sound.ModelPerformer;
import io.github.sosgames.athenacl.render.soundbank.SoundbankCache;

import javax.sound.midi.Instrument;
import javax.sound.midi.InvalidMidiDataException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Bytes of sample data one instrument points at. Shared samples are counted once. */
public final class BankMemory {
    private BankMemory() {
    }

    public static long sampleBytes(SlotSpec slot) throws IOException, InvalidMidiDataException {
        Instrument found = GervillStreams.find(SoundbankCache.get(slot.soundbank()), slot);
        if (!(found instanceof ModelInstrument model)) {
            return 0;
        }
        long bytes = 0;
        Set<String> seen = new HashSet<>();
        for (ModelPerformer performer : model.getPerformers()) {
            for (ModelOscillator oscillator : performer.getOscillators()) {
                if (oscillator instanceof ModelByteBufferWavetable table) {
                    ModelByteBuffer buffer = table.getBuffer();
                    if (buffer == null) {
                        continue;
                    }
                    String key = String.valueOf(buffer.getFile()) + ":" + buffer.getFilePointer()
                            + ":" + buffer.capacity();
                    if (seen.add(key)) {
                        bytes += buffer.capacity();
                    }
                }
            }
        }
        return bytes;
    }
}
