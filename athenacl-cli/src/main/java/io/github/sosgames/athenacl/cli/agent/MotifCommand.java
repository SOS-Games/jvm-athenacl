// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.AtomicFiles;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.cli.model.TextPaths;
import io.github.sosgames.athenacl.motif.Motif;
import io.github.sosgames.athenacl.motif.MotifGenerator;
import io.github.sosgames.athenacl.motif.MotifImport;
import io.github.sosgames.athenacl.motif.MotifTransforms;
import io.github.sosgames.athenacl.theory.Key;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Motifs you can grow, turn over, and import. {@code motif generate} prints {@code m1}.
 * {@code motif transform m1 --invert --invert} comes back to that same motif.
 * {@code motif import} reads a MIDI melody into YAML.
 */
@CommandLine.Command(name = "motif", description = "Generate, transform, or import a motif.",
        subcommands = {MotifCommand.Generate.class, MotifCommand.Transform.class, MotifCommand.ImportMidi.class})
public class MotifCommand implements Callable<Integer> {
    private static final List<String> TRANSFORMS = List.of(
            "repeat", "invert", "retrograde", "transpose", "augment", "diminish",
            "fragment", "sequence", "ornament", "displace");

    @CommandLine.ParentCommand AgentCli parent;

    @Override
    public Integer call() {
        parent.commandName = "motif";
        return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]",
                "usage: motif generate | motif transform <id> | motif import <file.mid>",
                "Try motif generate --key \"D dorian\" --seed 3.");
    }

    AgentCli cli() {
        return parent;
    }

    @CommandLine.Command(name = "generate", description = "Grow a motif from a key and a seed.")
    static class Generate implements Callable<Integer> {
        @CommandLine.ParentCommand MotifCommand motifCommand;
        @CommandLine.Option(names = "--key", paramLabel = "<key>")
        String key;
        @CommandLine.Option(names = "--seed", paramLabel = "<long>", defaultValue = "0")
        long seed;

        @Override
        public Integer call() {
            AgentCli parent = motifCommand.cli();
            parent.commandName = "motif generate";
            Key parsed;
            try {
                parsed = Key.parse(key == null || key.isBlank() ? "C major" : key);
            } catch (IllegalArgumentException e) {
                return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--key", e.getMessage(),
                        "Use a key such as \"D dorian\".");
            }
            Motif motif = MotifGenerator.generate(parsed, seed);
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("motif", motif.id());
            fields.put("notes", Integer.toString(motif.notes().size()));
            fields.put("contour", motif.contour());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("motif", motif.id());
            data.put("notes", motif.notes().size());
            data.put("contour", motif.contour());
            return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.motif/1",
                    data, List.of(), List.of());
        }
    }

    @CommandLine.Command(name = "transform", description = "Apply transforms. Two inversions restore the motif.")
    static class Transform implements Callable<Integer> {
        @CommandLine.ParentCommand MotifCommand motifCommand;
        @CommandLine.Spec CommandLine.Model.CommandSpec spec;
        @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<id>")
        String id;
        @CommandLine.Option(names = "--key", paramLabel = "<key>")
        String key;
        @CommandLine.Option(names = "--seed", paramLabel = "<long>", defaultValue = "0")
        long seed;
        @CommandLine.Option(names = "--repeat") boolean[] repeat;
        @CommandLine.Option(names = "--invert") boolean[] invert;
        @CommandLine.Option(names = "--retrograde") boolean[] retrograde;
        @CommandLine.Option(names = "--transpose") boolean[] transpose;
        @CommandLine.Option(names = "--augment") boolean[] augment;
        @CommandLine.Option(names = "--diminish") boolean[] diminish;
        @CommandLine.Option(names = "--fragment") boolean[] fragment;
        @CommandLine.Option(names = "--sequence") boolean[] sequence;
        @CommandLine.Option(names = "--ornament") boolean[] ornament;
        @CommandLine.Option(names = "--displace") boolean[] displace;

        @Override
        public Integer call() {
            AgentCli parent = motifCommand.cli();
            parent.commandName = "motif transform";
            if (id == null || id.isBlank()) {
                return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                        "usage: motif transform <id> --invert",
                        "Pass m1, then one or more transforms such as --invert --invert.");
            }
            Key parsed;
            try {
                parsed = Key.parse(key == null || key.isBlank() ? "C major" : key);
            } catch (IllegalArgumentException e) {
                return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--key", e.getMessage(),
                        "Use a key such as \"D dorian\".");
            }
            List<String> steps = steps();
            Motif motif = MotifGenerator.generate(parsed, seed);
            Motif current = motif;
            try {
                for (String step : steps) {
                    current = MotifTransforms.apply(current, step);
                }
            } catch (IllegalArgumentException e) {
                return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]", e.getMessage(),
                        "Use repeat, invert, retrograde, transpose, augment, diminish, fragment, sequence, ornament, or displace.");
            }
            boolean identical = motif.notes().equals(current.notes());
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("motif", id);
            fields.put("notes", Integer.toString(current.notes().size()));
            if (!steps.isEmpty()) {
                fields.put("transform", String.join("+", steps));
            }
            fields.put("identical", Boolean.toString(identical));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("motif", id);
            data.put("identical", identical);
            data.put("notes", current.notes().size());
            return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.motif/1",
                    data, List.of(), List.of());
        }

        private List<String> steps() {
            List<String> found = new ArrayList<>();
            for (String arg : spec.commandLine().getParseResult().originalArgs()) {
                if (arg.startsWith("--") && TRANSFORMS.contains(arg.substring(2))) {
                    found.add(arg.substring(2));
                }
            }
            return found;
        }
    }

    @CommandLine.Command(name = "import", description = "Read a MIDI melody and write motif YAML.")
    static class ImportMidi implements Callable<Integer> {
        @CommandLine.ParentCommand MotifCommand motifCommand;
        @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<file.mid>")
        Path file;
        @CommandLine.Option(names = "--out", paramLabel = "<file.yaml>")
        Path out;

        @Override
        public Integer call() {
            AgentCli parent = motifCommand.cli();
            parent.commandName = "motif import";
            if (file == null) {
                return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                        "usage: motif import <file.mid> --out <file.yaml>",
                        "Pass a MIDI melody such as samples/motifs/theme.mid.");
            }
            if (!Files.isRegularFile(file)) {
                return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]", "midi file not found",
                        "Pass a .mid file that contains a melody.");
            }
            if (out == null) {
                return parent.fail(ExitCode.USAGE, "USAGE", "--out", "import needs --out",
                        "Pass a YAML path, such as build/l7/m.yaml.");
            }
            try {
                Motif motif = MotifImport.read(file);
                byte[] yaml = MotifImport.yaml(motif).getBytes(StandardCharsets.UTF_8);
                AtomicFiles.WritePlan plan = AtomicFiles.write(out, yaml, parent.dryRun);
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("motifs", "1");
                fields.put("notes", Integer.toString(motif.notes().size()));
                fields.put("path", TextPaths.portable(out));
                fields.put("dryRun", Boolean.toString(plan.dryRun()));
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("motifs", 1);
                data.put("notes", motif.notes().size());
                data.put("path", TextPaths.portable(out));
                return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.motif/1",
                        data, List.of(), List.of());
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[2]", e.getMessage(),
                        "Check that the MIDI file is readable and --out is writable.");
            }
        }
    }
}
