// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render;

import com.sun.media.sound.AudioSynthesizer;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Soundbank;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The one place that touches Gervill's offline stream. Compiling and running it needs
 * {@code --add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED}. Reverb and chorus are off
 * so a second render of the same notes can match the first byte for byte on one JDK.
 */
public final class GervillStreams implements AutoCloseable {
    private final AudioSynthesizer synth;
    private final AudioInputStream stream;

    private GervillStreams(AudioSynthesizer synth, AudioInputStream stream) {
        this.synth = synth;
        this.stream = stream;
    }

    public static GervillStreams open(AudioFormat format) throws MidiUnavailableException {
        try {
            return open(format, null);
        } catch (IOException | InvalidMidiDataException e) {
            throw new MidiUnavailableException(e.getMessage());
        }
    }

    /**
     * Opens an offline stream. A null path uses the JDK default bank, which stays byte-stable
     * across two renders. A file is loaded on its own, with that default bank left unloaded.
     */
    public static GervillStreams open(AudioFormat format, Path soundbank)
            throws MidiUnavailableException, IOException, InvalidMidiDataException {
        if (!(MidiSystem.getSynthesizer() instanceof AudioSynthesizer synth)) {
            throw new MidiUnavailableException("this JDK has no Gervill AudioSynthesizer");
        }
        Map<String, Object> info = new HashMap<>();
        info.put("load default soundbank", soundbank == null);
        info.put("reverb", Boolean.FALSE);
        info.put("chorus", Boolean.FALSE);
        info.put("max polyphony", 64);
        AudioInputStream stream = synth.openStream(format, info);
        if (soundbank != null) {
            Soundbank bank = MidiSystem.getSoundbank(soundbank.toFile());
            if (!synth.isSoundbankSupported(bank) || !synth.loadAllInstruments(bank)) {
                synth.close();
                throw new MidiUnavailableException("could not load soundbank");
            }
        }
        return new GervillStreams(synth, stream);
    }

    public Receiver receiver() throws MidiUnavailableException {
        return synth.getReceiver();
    }

    public AudioInputStream stream() {
        return stream;
    }

    @Override
    public void close() {
        synth.close();
    }
}
