// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render;

import com.sun.media.sound.AudioSynthesizer;
import com.sun.media.sound.ModelConnectionBlock;
import com.sun.media.sound.ModelDestination;
import com.sun.media.sound.ModelInstrument;
import com.sun.media.sound.ModelPatch;
import com.sun.media.sound.ModelPerformer;
import com.sun.media.sound.SimpleInstrument;
import io.github.sosgames.athenacl.render.soundbank.SoundbankCache;
import io.github.sosgames.athenacl.render.soundbank.SoundbankRejected;
import io.github.sosgames.athenacl.render.soundbank.SoundbankResolver;

import javax.sound.midi.Instrument;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Patch;
import javax.sound.midi.Receiver;
import javax.sound.midi.Soundbank;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
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
            try {
                SoundbankResolver.rejectSf3(soundbank);
                Soundbank bank = SoundbankCache.get(soundbank);
                if (!synth.isSoundbankSupported(bank) || !synth.loadAllInstruments(bank)) {
                    synth.close();
                    throw new MidiUnavailableException("could not load soundbank");
                }
            } catch (SoundbankRejected e) {
                synth.close();
                throw new IOException(e.getMessage(), e);
            }
        }
        return new GervillStreams(synth, stream);
    }

    /**
     * Loads only the named slots. Each source instrument is wrapped so its patch is the slot
     * the engine assigned, which keeps two banks from replacing each other.
     */
    public void loadSlots(List<SlotSpec> slots) throws IOException, InvalidMidiDataException {
        for (SlotSpec slot : slots) {
            try {
                SoundbankResolver.rejectSf3(slot.soundbank());
            } catch (SoundbankRejected e) {
                throw new IOException(e.getMessage(), e);
            }
            Soundbank bank = SoundbankCache.get(slot.soundbank());
            Instrument found = find(bank, slot);
            if (!(found instanceof ModelInstrument model)) {
                throw new IOException("instrument not found in " + slot.soundbank().getFileName());
            }
            SimpleInstrument simple = new SimpleInstrument();
            simple.setName(model.getName());
            simple.setPatch(new ModelPatch(slot.slotBank(), slot.slotProgram(), slot.percussion()));
            simple.add(copyPerformers(model, slot.gainDb()));
            if (!synth.loadInstrument(simple)) {
                throw new IOException("could not load " + model.getName());
            }
        }
    }

    /**
     * Copies performers so a gain tweak never rewrites the cached soundfont.
     * Mixer gain is in tenths of a decibel.
     */
    private static ModelPerformer[] copyPerformers(ModelInstrument model, double gainDb) {
        ModelPerformer[] source = model.getPerformers();
        ModelPerformer[] copies = new ModelPerformer[source.length];
        for (int i = 0; i < source.length; i++) {
            ModelPerformer from = source[i];
            ModelPerformer copy = new ModelPerformer();
            copy.setName(from.getName());
            copy.setExclusiveClass(from.getExclusiveClass());
            copy.setSelfNonExclusive(from.isSelfNonExclusive());
            copy.setKeyFrom(from.getKeyFrom());
            copy.setKeyTo(from.getKeyTo());
            copy.setVelFrom(from.getVelFrom());
            copy.setVelTo(from.getVelTo());
            copy.setReleaseTriggered(from.isReleaseTriggered());
            copy.setUserObject(from.getUserObject());
            copy.setDefaultConnectionsEnabled(from.isDefaultConnectionsEnabled());
            copy.getOscillators().addAll(from.getOscillators());
            copy.getConnectionBlocks().addAll(from.getConnectionBlocks());
            if (gainDb != 0) {
                copy.getConnectionBlocks().add(new ModelConnectionBlock(
                        gainDb * 10.0,
                        new ModelDestination(ModelDestination.DESTINATION_GAIN)));
            }
            copies[i] = copy;
        }
        return copies;
    }

    public static Instrument find(Soundbank bank, SlotSpec slot) {
        Instrument preset = null;
        for (Instrument instrument : bank.getInstruments()) {
            Patch patch = instrument.getPatch();
            boolean percussion = patch instanceof ModelPatch model && model.isPercussion();
            if (slot.drumkit() >= 0) {
                if (percussion && patch.getProgram() == slot.drumkit()) {
                    return instrument;
                }
                continue;
            }
            if (slot.preset() != null && !slot.preset().isBlank()) {
                if (instrument.getName() != null
                        && instrument.getName().equalsIgnoreCase(slot.preset())) {
                    return instrument;
                }
                continue;
            }
            if (!percussion && patch.getBank() == slot.sourceBank() && patch.getProgram() == slot.sourceProgram()) {
                return instrument;
            }
            if (!percussion && patch.getProgram() == slot.sourceProgram() && preset == null) {
                preset = instrument;
            }
        }
        return preset;
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
