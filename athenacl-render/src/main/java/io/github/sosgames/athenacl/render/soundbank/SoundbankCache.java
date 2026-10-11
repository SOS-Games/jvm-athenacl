// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.render.soundbank;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Soundbank;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parses each soundfont once per process. Sample bytes stay on disk until an instrument is loaded. */
public final class SoundbankCache {
    private static final Map<Path, Soundbank> PARSED = new LinkedHashMap<>();

    private SoundbankCache() {
    }

    public static Soundbank get(Path path) throws IOException, InvalidMidiDataException {
        Path key = path.toAbsolutePath().normalize();
        synchronized (PARSED) {
            Soundbank cached = PARSED.get(key);
            if (cached != null) {
                return cached;
            }
        }
        SoundbankSniff.Kind kind = SoundbankSniff.inspect(path);
        if (kind == SoundbankSniff.Kind.SF3) {
            throw new IOException("SF3 soundfonts are not supported");
        }
        Soundbank loaded = MidiSystem.getSoundbank(path.toFile());
        synchronized (PARSED) {
            PARSED.put(key, loaded);
        }
        return loaded;
    }
}
