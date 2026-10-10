// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Canonical JSONL for a score. Key order is fixed, lines end with {@code \n}, and the hash is
 * SHA-256 of those bytes. Wall-clock fields are not written, so two runs of the same notes match.
 */
public final class EventLog {
    private static final ObjectMapper JSON = new ObjectMapper();

    private EventLog() {
    }

    public static byte[] encode(Score score) {
        return encode(score, DebugSink.NOOP);
    }

    public static byte[] encode(Score score, DebugSink sink) {
        StringBuilder text = new StringBuilder();
        List<Row> rows = new ArrayList<>();
        for (ControlEvent control : score.controls()) {
            rows.add(new Row(control.startTick(), rank(control), 0, controlLine(control)));
        }
        for (NoteEvent note : score.notes()) {
            sink.note(note);
            rows.add(new Row(note.startTick(), 5, note.id(), noteLine(note)));
        }
        rows.sort(Comparator.comparingLong(Row::tick).thenComparingInt(Row::rank).thenComparingLong(Row::id));
        for (Row row : rows) {
            text.append(row.line()).append('\n');
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static String hash(Score score) {
        return sha256(encode(score));
    }

    public static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Score read(String jsonl) throws ScoreException {
        int bpm = 120;
        int numerator = 4;
        int denominator = 4;
        List<ControlEvent> controls = new ArrayList<>();
        List<NoteEvent> notes = new ArrayList<>();
        int lineNo = 0;
        for (String line : jsonl.split("\n", -1)) {
            lineNo++;
            if (line.isBlank()) {
                continue;
            }
            JsonNode node;
            try {
                node = JSON.readTree(line);
            } catch (IOException e) {
                throw new ScoreException("line " + lineNo, "event log line is not JSON",
                        "Re-render the note list. events.jsonl is one JSON object per line.");
            }
            String type = text(node, "type");
            switch (type) {
                case "tempo" -> {
                    bpm = node.path("bpm").asInt(120);
                    controls.add(new ControlEvent.Tempo(node.path("startTick").asLong(), bpm));
                }
                case "meter" -> {
                    numerator = node.path("numerator").asInt(4);
                    denominator = node.path("denominator").asInt(4);
                    controls.add(new ControlEvent.Meter(node.path("startTick").asLong(), numerator, denominator));
                }
                case "program" -> controls.add(new ControlEvent.ProgramChange(
                        node.path("startTick").asLong(), node.path("channel").asInt(), node.path("program").asInt()));
                case "control" -> controls.add(new ControlEvent.ControlChange(
                        node.path("startTick").asLong(), node.path("channel").asInt(),
                        node.path("controller").asInt(), node.path("value").asInt()));
                case "bend" -> controls.add(new ControlEvent.PitchBend(
                        node.path("startTick").asLong(), node.path("channel").asInt(), node.path("value").asInt()));
                case "note" -> notes.add(new NoteEvent(
                        node.path("id").asLong(),
                        node.path("startTick").asLong(),
                        node.path("durationTicks").asLong(),
                        node.path("pitch").asInt(),
                        text(node, "pitchName"),
                        node.path("velocity").asInt(),
                        node.path("channel").asInt(),
                        text(node, "layer"),
                        node.path("bar").asInt(),
                        text(node, "beat"),
                        text(node, "dur"),
                        text(node, "seed")));
                default -> throw new ScoreException("line " + lineNo, "unknown event type " + type,
                        "Expected note, tempo, meter, program, control, or bend.");
            }
        }
        int bars = 1;
        for (NoteEvent note : notes) {
            bars = Math.max(bars, note.bar());
        }
        return new Score(controls, notes, bpm, numerator, denominator, bars);
    }

    private static String controlLine(ControlEvent control) {
        return switch (control) {
            case ControlEvent.Tempo tempo -> "{\"type\":\"tempo\",\"startTick\":" + tempo.startTick()
                    + ",\"bpm\":" + tempo.bpm() + "}";
            case ControlEvent.Meter meter -> "{\"type\":\"meter\",\"startTick\":" + meter.startTick()
                    + ",\"numerator\":" + meter.numerator() + ",\"denominator\":" + meter.denominator() + "}";
            case ControlEvent.ProgramChange program -> "{\"type\":\"program\",\"startTick\":" + program.startTick()
                    + ",\"channel\":" + program.channel() + ",\"program\":" + program.program() + "}";
            case ControlEvent.ControlChange change -> "{\"type\":\"control\",\"startTick\":" + change.startTick()
                    + ",\"channel\":" + change.channel() + ",\"controller\":" + change.controller()
                    + ",\"value\":" + change.value() + "}";
            case ControlEvent.PitchBend bend -> "{\"type\":\"bend\",\"startTick\":" + bend.startTick()
                    + ",\"channel\":" + bend.channel() + ",\"value\":" + bend.value() + "}";
        };
    }

    private static String noteLine(NoteEvent note) {
        return "{\"type\":\"note\",\"id\":" + note.id()
                + ",\"bar\":" + note.bar()
                + ",\"beat\":\"" + escape(note.beat()) + "\""
                + ",\"startTick\":" + note.startTick()
                + ",\"durationTicks\":" + note.durationTicks()
                + ",\"pitch\":" + note.pitch()
                + ",\"pitchName\":\"" + escape(note.pitchName()) + "\""
                + ",\"velocity\":" + note.velocity()
                + ",\"channel\":" + note.channel()
                + ",\"layer\":\"" + escape(note.layer()) + "\""
                + ",\"dur\":\"" + escape(note.duration()) + "\""
                + ",\"seed\":\"" + escape(note.seedPath()) + "\"}";
    }

    private static int rank(ControlEvent control) {
        return switch (control) {
            case ControlEvent.Tempo ignored -> 0;
            case ControlEvent.Meter ignored -> 1;
            case ControlEvent.ProgramChange ignored -> 2;
            case ControlEvent.ControlChange ignored -> 3;
            case ControlEvent.PitchBend ignored -> 4;
        };
    }

    private static String text(JsonNode node, String field) throws ScoreException {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new ScoreException(field, "event is missing " + field,
                    "Re-render the note list so the event log is complete.");
        }
        return value.asText();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record Row(long tick, int rank, long id, String line) {
    }
}
