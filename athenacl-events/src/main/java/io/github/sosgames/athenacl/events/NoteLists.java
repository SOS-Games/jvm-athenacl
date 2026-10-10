// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a handwritten note list. Pitches are names like {@code C4}. Beats are 1-based quarter
 * notes inside the bar. {@code dur: 1/4} is a quarter note at 960 ticks.
 */
public final class NoteLists {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private NoteLists() {
    }

    public static Score load(Path path, long seed) throws IOException, ScoreException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        return parse(path.toString(), text, seed);
    }

    public static Score parse(String path, String yaml, long seed) throws ScoreException {
        JsonNode root;
        try {
            root = YAML.readTree(yaml);
        } catch (IOException e) {
            throw new ScoreException(path, "note list is not YAML",
                    "Use bpm, meter, and a notes list. See samples/notes/c-major-scale.yaml.");
        }
        if (root == null || !root.isObject()) {
            throw new ScoreException(path, "note list is empty",
                    "Add bpm, meter, and a notes list.");
        }
        int bpm = root.path("bpm").asInt(120);
        if (bpm <= 0) {
            throw new ScoreException(path, "bpm must be above 0", "Set bpm to a value such as 120.");
        }
        String meter = root.path("meter").asText("4/4");
        int slash = meter.indexOf('/');
        if (slash < 0) {
            throw new ScoreException(path, "meter " + meter, "Use a meter like 4/4.");
        }
        int numerator;
        int denominator;
        try {
            numerator = Integer.parseInt(meter.substring(0, slash).trim());
            denominator = Integer.parseInt(meter.substring(slash + 1).trim());
        } catch (NumberFormatException e) {
            throw new ScoreException(path, "meter " + meter, "Use a meter like 4/4.");
        }
        if (numerator <= 0 || denominator <= 0) {
            throw new ScoreException(path, "meter " + meter, "Use a meter like 4/4.");
        }
        String layer = root.path("layer").asText("melody");
        int velocity = root.path("velocity").asInt(96);
        int channel = root.path("channel").asInt(0);
        JsonNode notes = root.get("notes");
        if (notes == null || !notes.isArray() || notes.isEmpty()) {
            throw new ScoreException(path, "notes list is empty", "Add at least one pitch.");
        }
        long barTicks = TimeBase.barTicks(numerator, denominator);
        List<NoteEvent> built = new ArrayList<>();
        int index = 0;
        for (JsonNode item : notes) {
            index++;
            String pitchName = item.path("pitch").asText("");
            if (pitchName.isBlank()) {
                throw new ScoreException(path, "note " + index + " has no pitch",
                        "Set pitch to a name such as E4.");
            }
            int midi;
            try {
                midi = PitchName.midi(pitchName);
            } catch (IllegalArgumentException e) {
                throw new ScoreException(path, e.getMessage(), "Use a name like C4, F#3, or Bb4.");
            }
            int bar = item.path("bar").asInt(1);
            if (bar < 1) {
                throw new ScoreException(path, "note " + index + " bar must be 1 or more",
                        "Bars are numbered from 1.");
            }
            String beat = item.path("beat").isNumber()
                    ? item.path("beat").asText()
                    : item.path("beat").asText("1");
            String duration = item.path("dur").asText("1/4");
            long start;
            long length;
            try {
                start = (bar - 1L) * barTicks + TimeBase.beatOffsetTicks(beat);
                length = TimeBase.durationTicks(duration);
            } catch (NumberFormatException e) {
                throw new ScoreException(path, "note " + index + " has a bad beat or dur",
                        "Beat is a quarter-note number (1, 2, 1.5). Dur is a fraction such as 1/4.");
            }
            if (length <= 0) {
                throw new ScoreException(path, "note " + index + " duration must be above 0",
                        "Use a duration such as 1/4.");
            }
            int noteVelocity = item.path("velocity").asInt(velocity);
            int noteChannel = item.path("channel").asInt(channel);
            String noteLayer = item.path("layer").asText(layer);
            if (noteVelocity < 1 || noteVelocity > 127 || noteChannel < 0 || noteChannel > 15) {
                throw new ScoreException(path, "note " + index + " velocity or channel is out of range",
                        "Velocity is 1..127. Channel is 0..15. Drums use channel 9.");
            }
            long id = built.size() + 1L;
            String seedPath = seed + "/" + noteLayer + "/bar" + bar + "/note#" + id;
            built.add(new NoteEvent(id, start, length, midi, pitchName, noteVelocity, noteChannel,
                    noteLayer, bar, beat, duration, seedPath));
        }
        int bars = 1;
        for (NoteEvent note : built) {
            bars = Math.max(bars, note.bar());
            long lastTick = note.startTick() + note.durationTicks() - 1;
            if (lastTick >= 0) {
                bars = Math.max(bars, (int) (lastTick / barTicks) + 1);
            }
        }
        List<ControlEvent> controls = List.of(
                new ControlEvent.Tempo(0, bpm),
                new ControlEvent.Meter(0, numerator, denominator),
                new ControlEvent.ProgramChange(0, channel, 0));
        return new Score(controls, built, bpm, numerator, denominator, bars);
    }
}
