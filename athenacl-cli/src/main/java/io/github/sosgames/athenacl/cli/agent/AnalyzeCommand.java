// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.adaptive.ControlLog;
import io.github.sosgames.athenacl.adaptive.LoudnessContour;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.events.Score;
import io.github.sosgames.athenacl.events.ScoreException;
import io.github.sosgames.athenacl.render.loudness.StateLoudness;
import io.github.sosgames.athenacl.motif.MotifReuse;
import io.github.sosgames.athenacl.rhythm.Swing;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Measures a rendered WAV. A directory uses {@code score.wav}. The line is
 * {@code loudness.integrated=} LUFS, {@code truePeak=} dBTP, and {@code shortTerm=}.
 * {@code --by state} instead prints each state's offset from the contour reference,
 * plus {@code loudness.spread=}. When the directory also has {@code events.jsonl}
 * with swung eighths, the plain line adds {@code swing.measured=}. A render whose lead
 * carries a motif also adds {@code motif.reuse.per8bars=}.
 */
@CommandLine.Command(name = "analyze", description = "Measure loudness of a rendered WAV.")
public class AnalyzeCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<wav-or-dir>")
    Path input;
    @CommandLine.Option(names = "--by", paramLabel = "state")
    String by;

    @Override
    public Integer call() {
        parent.commandName = "analyze";
        if (input == null) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]", "usage: analyze <wav-or-dir>",
                    "Pass score.wav or the directory render wrote.");
        }
        Path wav = wav(input);
        if (wav == null) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[1]", "wav not found",
                    "Render with --formats wav, then pass that directory or score.wav.");
        }
        if ("state".equals(by)) {
            return byState(wav);
        }
        if (by != null) {
            return parent.fail(ExitCode.USAGE, "USAGE", "--by", "unknown grouping " + by,
                    "Use --by state.");
        }
        return LoudnessReports.finish(parent, wav, "athenacl.result.analyze/1", extras(input));
    }

    private int byState(Path wav) {
        Path dir = Files.isDirectory(input) ? input : input.getParent();
        if (dir == null) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--by",
                    "state loudness needs a render directory",
                    "Pass the directory that contains control.jsonl and events.jsonl.");
        }
        Path control = dir.resolve("control.jsonl");
        Path events = dir.resolve("events.jsonl");
        if (!Files.isRegularFile(control) || !Files.isRegularFile(events)) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "--by",
                    "control.jsonl or events.jsonl is missing",
                    "Render a theme with --out, then analyze that directory.");
        }
        try {
            ControlLog.Log log = ControlLog.read(control);
            Score score = NoteFiles.events(events);
            List<String> states = new ArrayList<>();
            for (int bar = 1; bar <= log.bars(); bar++) {
                String state = null;
                for (var controlPoint : log.controls()) {
                    if (controlPoint.bar() <= bar) {
                        state = controlPoint.state();
                    }
                }
                states.add(state == null ? "" : state);
            }
            double reference = log.reference() == null ? -23.0 : log.reference();
            Map<String, Double> offsets = StateLoudness.offsets(
                    Files.readAllBytes(wav), score.bpm(), score.barTicks(), states, reference);
            Map<String, String> fields = new LinkedHashMap<>();
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (Map.Entry<String, Double> entry : offsets.entrySet()) {
                if (!Double.isFinite(entry.getValue())) {
                    continue;
                }
                fields.put("loudness." + entry.getKey(), LoudnessContour.tenths(entry.getValue()));
                min = Math.min(min, entry.getValue());
                max = Math.max(max, entry.getValue());
            }
            double spread = fields.isEmpty() ? 0 : max - min;
            fields.put("loudness.spread", LoudnessContour.tenths(spread).replace("+", ""));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("reference", reference);
            data.put("spread", spread);
            return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.analyze/1",
                    data, List.of(), List.of());
        } catch (ScoreException | IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[1]", e.getMessage(),
                    "Check that the WAV and the control log are readable.");
        }
    }

    private static String extras(Path input) {
        String swing = swingSuffix(input);
        String motif = motifSuffix(input);
        if (swing == null) {
            return motif;
        }
        if (motif == null) {
            return swing;
        }
        return swing + " " + motif;
    }

    private static String motifSuffix(Path input) {
        Path dir = Files.isDirectory(input) ? input : input.getParent();
        if (dir == null) {
            return null;
        }
        Path events = dir.resolve("events.jsonl");
        if (!Files.isRegularFile(events)) {
            return null;
        }
        try {
            Score score = NoteFiles.events(events);
            Double reuse = MotifReuse.per8bars(score);
            if (reuse == null) {
                return null;
            }
            return "motif.reuse.per8bars=" + String.format(java.util.Locale.ROOT, "%.1f", reuse);
        } catch (ScoreException | IOException e) {
            return null;
        }
    }

    private static String swingSuffix(Path input) {
        Path dir = Files.isDirectory(input) ? input : input.getParent();
        if (dir == null) {
            return null;
        }
        Path events = dir.resolve("events.jsonl");
        if (!Files.isRegularFile(events)) {
            return null;
        }
        try {
            Score score = NoteFiles.events(events);
            return Swing.measure(score).stream()
                    .mapToObj(measured -> "swing.measured=" + Swing.format(measured))
                    .findFirst()
                    .orElse(null);
        } catch (ScoreException | IOException e) {
            return null;
        }
    }

    static Path wav(Path input) {
        if (Files.isRegularFile(input)) {
            return input;
        }
        if (Files.isDirectory(input)) {
            Path score = input.resolve("score.wav");
            if (Files.isRegularFile(score)) {
                return score;
            }
        }
        return null;
    }
}
