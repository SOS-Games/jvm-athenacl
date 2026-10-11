// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.AtomicFiles;
import io.github.sosgames.athenacl.cli.model.Config;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.Issue;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.cli.model.Severity;
import io.github.sosgames.athenacl.events.EventLog;
import io.github.sosgames.athenacl.events.Score;
import io.github.sosgames.athenacl.events.ScoreException;
import io.github.sosgames.athenacl.render.SmfWriter;
import io.github.sosgames.athenacl.render.TextDump;
import io.github.sosgames.athenacl.render.WavRenderer;
import io.github.sosgames.athenacl.instruments.KitException;
import io.github.sosgames.athenacl.instruments.KitScore;
import io.github.sosgames.athenacl.instruments.KitSession;
import io.github.sosgames.athenacl.instruments.Kits;
import io.github.sosgames.athenacl.instruments.ProbeNormalizer;
import io.github.sosgames.athenacl.render.SlotSpec;
import io.github.sosgames.athenacl.render.soundbank.SoundbankPaths;
import io.github.sosgames.athenacl.render.soundbank.SoundbankRejected;
import io.github.sosgames.athenacl.render.soundbank.SoundbankResolver;
import io.github.sosgames.athenacl.rhythm.Durations;
import io.github.sosgames.athenacl.rhythm.Groove;
import io.github.sosgames.athenacl.rhythm.Swing;
import io.github.sosgames.athenacl.adaptive.Themes;
import picocli.CommandLine;

import javax.sound.midi.MidiUnavailableException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Turns a note-list YAML file into MIDI, WAV, an event log, and a text dump.
 * The hash is SHA-256 of the event log. A second run of the same notes prints
 * {@code changed=false} when every requested file already has those bytes.
 * {@code --dry-run} reports that plan and does not create the output folder.
 */
@CommandLine.Command(name = "render", description = "Render a note list to MIDI, WAV, events, and text.")
public class RenderCommand implements Callable<Integer> {
    private static final List<String> KNOWN = List.of("mid", "wav", "events", "txt");

    @CommandLine.ParentCommand AgentCli parent;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<notes.yaml>")
    Path notes;
    @CommandLine.Option(names = "--out", paramLabel = "<dir>")
    Path outDir;
    @CommandLine.Option(names = "--formats", paramLabel = "<mid,wav,events,txt>", defaultValue = "mid,wav,events,txt")
    String formats;
    @CommandLine.Option(names = "--seed", paramLabel = "<long>", defaultValue = "0")
    long seed;
    @CommandLine.Option(names = "--soundbank", paramLabel = "<id|file>")
    String soundbank;
    @CommandLine.Option(names = "--kit", paramLabel = "<id|file>")
    String kit;
    @CommandLine.Option(names = "--cache", paramLabel = "<dir>")
    Path cache;
    @CommandLine.Option(names = "--groove", paramLabel = "<swing=0.62>")
    String groove;
    @CommandLine.Option(names = "--timeline", paramLabel = "<file.json>")
    Path timeline;
    @CommandLine.Option(names = "--bars", paramLabel = "<from-to>")
    String barWindow;
    @CommandLine.Option(names = "--at", paramLabel = "<bar state name intensity>")
    List<String> at = new ArrayList<>();

    @Override
    public Integer call() {
        parent.commandName = "render";
        if (notes == null) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]", "usage: render <notes.yaml> --out <dir>",
                    "See samples/notes/c-major-scale.yaml.");
        }
        if (!Files.isRegularFile(notes)) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[1]",
                    "note list not found",
                    "Pass a YAML note list such as samples/notes/c-major-scale.yaml.");
        }
        if (Themes.looksLike(notes)) {
            return ThemeCli.render(parent, notes, timeline, barWindow, at, seed, outDir, formats, soundbank, cache);
        }
        Groove parsedGroove = null;
        if (groove != null) {
            try {
                parsedGroove = Groove.parse(groove);
            } catch (IllegalArgumentException e) {
                return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--groove", e.getMessage(),
                        "Use swing=0.62. 0.50 is straight and 1 is the next downbeat.");
            }
        }
        if (outDir == null && soundbank == null && kit == null) {
            return parent.fail(ExitCode.USAGE, "USAGE", "--out", "usage: render <notes.yaml> --out <dir>",
                    "Pass --out as a directory this command may create.");
        }
        SoundbankResolver.Selection selection;
        try {
            if (kit != null && soundbank == null) {
                selection = new SoundbankResolver.Selection(kit, null, false);
            } else {
                String request = soundbank != null ? soundbank : Config.get("athenacl.soundbank");
                selection = SoundbankResolver.select(request, SoundbankPaths.cache(cache));
            }
        } catch (SoundbankRejected e) {
            return parent.fail(ExitCode.INVALID_INPUT, e.code(), e.path(), e.getMessage(), e.fix());
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--soundbank", e.getMessage(),
                    "Check that the soundbank file is readable.");
        }
        if (outDir == null) {
            return soundbankOnly(selection);
        }
        List<String> requested;
        try {
            requested = parseFormats(formats);
        } catch (IllegalArgumentException e) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--formats", e.getMessage(),
                    "Use a comma list of mid, wav, events, txt.");
        }
        Score score;
        try {
            score = NoteFiles.yaml(notes, seed);
        } catch (ScoreException e) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "argv[1]", e.getMessage(), e.fix());
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[1]", e.getMessage(),
                    "Check that the note list is readable.");
        }
        if (parsedGroove != null) {
            score = Swing.apply(score, parsedGroove);
        }
        List<SlotSpec> slots = null;
        if (kit != null) {
            try {
                KitSession session = KitSession.open(Kits.find(kit), SoundbankPaths.cache(cache), false);
                KitScore.Bound bound = KitScore.bind(notes, score, session);
                score = bound.score();
                slots = bound.used().stream().map(ProbeNormalizer::spec).toList();
            } catch (KitException e) {
                KitException.Issue issue = e.issues().get(0);
                return parent.fail(ExitCode.INVALID_INPUT, issue.code(), issue.path(), issue.message(), issue.fix());
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--kit", e.getMessage(),
                        "Check that the kit file and the soundfont cache are readable.");
            } catch (MidiUnavailableException e) {
                return parent.fail(ExitCode.ENVIRONMENT, "GERVILL_ABSENT", "gervill",
                        "this JDK cannot open an offline Gervill stream",
                        "Use a JDK that includes com.sun.media.sound and keep --add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED.");
            }
        }
        Map<String, byte[]> bodies = new LinkedHashMap<>();
        try {
            for (String format : requested) {
                bodies.put(format, body(format, score, selection.file(), slots));
            }
        } catch (MidiUnavailableException e) {
            return parent.fail(ExitCode.ENVIRONMENT, "GERVILL_ABSENT", "gervill",
                    "this JDK cannot open an offline Gervill stream",
                    "Use a JDK that includes com.sun.media.sound and keep --add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED.");
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--formats", e.getMessage(),
                    "Rendering the WAV or MIDI file failed. Try again, or drop wav from --formats.");
        }
        boolean changed = false;
        try {
            for (Map.Entry<String, byte[]> entry : bodies.entrySet()) {
                Path file = outDir.resolve(fileName(entry.getKey()));
                AtomicFiles.WritePlan plan = AtomicFiles.write(file, entry.getValue(), parent.dryRun);
                changed = changed || plan.changed();
            }
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--out", e.getMessage(),
                    "Check that --out is a writable directory and try again.");
        }
        String hash = EventLog.hash(score);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("files", Integer.toString(requested.size()));
        fields.put("notes", Integer.toString(score.notes().size()));
        fields.put("bars", Integer.toString(score.bars()));
        fields.put("changed", Boolean.toString(changed));
        fields.put("hash", hash);
        fields.put("dryRun", Boolean.toString(parent.dryRun));
        fields.put("soundbank", selection.name());
        if (selection.fallback()) {
            fields.put("warning", "SOUNDBANK_FALLBACK");
        }
        fields.put("maxDurBeats", Durations.maxBeats(score));
        if (parsedGroove != null) {
            Swing.measure(score).ifPresent(measured -> fields.put("swing.measured", Swing.format(measured)));
        }
        if (slots != null) {
            fields.put("slots", Integer.toString(slots.size()));
            fields.put("kit", kit);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("files", requested.size());
        data.put("notes", score.notes().size());
        data.put("bars", score.bars());
        data.put("changed", changed);
        data.put("hash", hash);
        data.put("dryRun", parent.dryRun);
        data.put("seed", Long.toString(seed));
        data.put("soundbank", selection.name());
        data.put("maxDurBeats", Durations.maxBeats(score));
        if (parsedGroove != null) {
            Swing.measure(score).ifPresent(measured -> data.put("swingMeasured", measured));
        }
        return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.render/1",
                data, warnings(selection), List.of());
    }

    private Integer soundbankOnly(SoundbankResolver.Selection selection) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("soundbank", selection.name());
        if (selection.fallback()) {
            fields.put("warning", "SOUNDBANK_FALLBACK");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("soundbank", selection.name());
        data.put("fallback", selection.fallback());
        return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.render/1",
                data, warnings(selection), List.of());
    }

    static List<Issue> warnings(SoundbankResolver.Selection selection) {
        if (!selection.fallback()) {
            return List.of();
        }
        return List.of(new Issue(
                "SOUNDBANK_FALLBACK",
                Severity.WARNING,
                "--soundbank",
                "soundbank fell back to the emergency bank",
                "Install generaluser, or pass a path to an .sf2 file that exists.",
                "AGENTS.md#cli"));
    }

    static byte[] body(String format, Score score, Path soundbank)
            throws IOException, MidiUnavailableException {
        return body(format, score, soundbank, null);
    }

    static byte[] body(String format, Score score, Path soundbank, List<SlotSpec> slots)
            throws IOException, MidiUnavailableException {
        return switch (format) {
            case "mid" -> SmfWriter.write(score);
            case "wav" -> slots == null
                    ? WavRenderer.write(score, soundbank)
                    : WavRenderer.write(score, null, slots);
            case "events" -> EventLog.encode(score);
            case "txt" -> TextDump.write(score);
            default -> throw new IllegalArgumentException(format);
        };
    }

    static String fileName(String format) {
        return switch (format) {
            case "mid" -> "score.mid";
            case "wav" -> "score.wav";
            case "events" -> "events.jsonl";
            case "txt" -> "score.txt";
            default -> throw new IllegalArgumentException(format);
        };
    }

    static List<String> parseFormats(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("formats list is empty");
        }
        List<String> found = new ArrayList<>();
        for (String part : text.split(",")) {
            String name = part.trim().toLowerCase();
            if (name.isEmpty() || !KNOWN.contains(name)) {
                throw new IllegalArgumentException("unknown format " + part.trim());
            }
            if (!found.contains(name)) {
                found.add(name);
            }
        }
        return List.copyOf(found);
    }
}
