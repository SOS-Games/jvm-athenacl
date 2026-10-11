// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.instruments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.sosgames.athenacl.events.ControlEvent;
import io.github.sosgames.athenacl.events.NoteEvent;
import io.github.sosgames.athenacl.events.PitchName;
import io.github.sosgames.athenacl.events.Score;

import javax.sound.midi.MidiUnavailableException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Points each note at the kit instrument named on that note, then writes the bank select,
 * program change, and volume for the slot. Notes keep their timing. The pitch moves by
 * {@code transpose} and by {@code keyRange} when the note would otherwise fall outside.
 */
public final class KitScore {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private KitScore() {
    }

    public record Bound(Score score, List<Playable> used) {
    }

    public static Bound bind(Path notes, Score score, KitSession session)
            throws IOException, KitException, MidiUnavailableException {
        JsonNode root = YAML.readTree(Files.readString(notes, StandardCharsets.UTF_8));
        JsonNode items = root.path("notes");
        List<String> refs = new ArrayList<>();
        Map<String, Playable> distinct = new LinkedHashMap<>();
        int index = 0;
        for (JsonNode item : items) {
            String ref = item.path("instrument").asText("");
            if (ref.isBlank()) {
                ref = session.get("piano") != null ? "piano" : session.instruments().get(0).name();
            }
            Playable playable = session.resolve(ref);
            if (playable == null) {
                throw new KitException(new KitException.Issue(
                        "KIT_UNKNOWN_INSTRUMENT", "/notes/" + index + "/instrument",
                        "unknown instrument " + ref,
                        "Use a name from the kit, such as piano or gm-lite:harp."));
            }
            String id = identity(playable);
            refs.add(id);
            distinct.putIfAbsent(id, playable);
            index++;
        }
        Map<String, Playable> slotted = reslot(distinct);
        Map<String, Integer> volume = new LinkedHashMap<>();
        for (Playable playable : slotted.values()) {
            volume.put(identity(playable), ProbeNormalizer.measure(playable).cc7());
        }
        Map<String, Integer> channels = new LinkedHashMap<>();
        int next = 0;
        for (Playable playable : slotted.values()) {
            if (playable.percussion()) {
                channels.put(identity(playable), 9);
            } else {
                if (next == 9) {
                    next++;
                }
                channels.put(identity(playable), next++);
            }
        }
        List<ControlEvent> controls = new ArrayList<>(score.controls());
        for (Playable playable : slotted.values()) {
            int channel = channels.get(identity(playable));
            int cc7 = volume.getOrDefault(identity(playable), 100);
            controls.addAll(ProbeNormalizer.controls(playable, channel, cc7));
        }
        List<NoteEvent> rebound = new ArrayList<>();
        for (int i = 0; i < score.notes().size(); i++) {
            NoteEvent note = score.notes().get(i);
            Playable playable = slotted.get(refs.get(i));
            int channel = channels.get(identity(playable));
            int pitch = pitched(note.pitch(), playable);
            if (pitch < 0) {
                continue;
            }
            rebound.add(new NoteEvent(note.id(), note.startTick(), note.durationTicks(), pitch, note.pitchName(),
                    note.velocity(), channel, note.layer(), note.bar(), note.beat(), note.duration(),
                    note.seedPath(), note.motif()));
        }
        return new Bound(new Score(controls, rebound, score.bpm(), score.numerator(), score.denominator(), score.bars()),
                List.copyOf(slotted.values()));
    }

    private static String identity(Playable playable) {
        return playable.file() + "|" + playable.sourceBank() + "|" + playable.sourceProgram() + "|"
                + playable.preset() + "|" + playable.drumkit();
    }

    private static Map<String, Playable> reslot(Map<String, Playable> distinct) {
        List<Map.Entry<String, Playable>> melodic = new ArrayList<>();
        List<Map.Entry<String, Playable>> drums = new ArrayList<>();
        for (Map.Entry<String, Playable> entry : distinct.entrySet()) {
            if (entry.getValue().percussion()) {
                drums.add(entry);
            } else {
                melodic.add(entry);
            }
        }
        Map<String, Playable> slotted = new LinkedHashMap<>();
        for (int i = 0; i < melodic.size(); i++) {
            slotted.put(melodic.get(i).getKey(), moved(melodic.get(i).getValue(), 1 + i / 128, i % 128));
        }
        for (int i = 0; i < drums.size(); i++) {
            slotted.put(drums.get(i).getKey(), moved(drums.get(i).getValue(), 0, i));
        }
        return slotted;
    }

    private static Playable moved(Playable playable, int bank, int program) {
        return new Playable(playable.name(), playable.kitId(), playable.sourceKey(), playable.file(),
                playable.sourceBank(), playable.sourceProgram(), playable.preset(), playable.drumkit(),
                playable.percussion(), bank, program, playable.gainDb(), playable.transpose(),
                playable.keyLow(), playable.keyHigh(), playable.outOfRange(),
                playable.license(), playable.attribution(), playable.licenseFile(), playable.fallbacksTaken());
    }

    static int pitched(int pitch, Playable playable) {
        int moved = pitch + playable.transpose();
        if (playable.keyLow() == null || playable.keyHigh() == null) {
            return clamp(moved);
        }
        int low = PitchName.midi(playable.keyLow());
        int high = PitchName.midi(playable.keyHigh());
        if (moved >= low && moved <= high) {
            return moved;
        }
        String mode = playable.outOfRange() == null || playable.outOfRange().isBlank()
                ? "fold" : playable.outOfRange();
        if ("drop".equals(mode)) {
            return -1;
        }
        if ("clamp".equals(mode)) {
            return moved < low ? low : high;
        }
        int guard = 0;
        while (moved < low && guard++ < 12) {
            moved += 12;
        }
        guard = 0;
        while (moved > high && guard++ < 12) {
            moved -= 12;
        }
        return clamp(moved);
    }

    private static int clamp(int pitch) {
        return Math.max(0, Math.min(127, pitch));
    }
}
