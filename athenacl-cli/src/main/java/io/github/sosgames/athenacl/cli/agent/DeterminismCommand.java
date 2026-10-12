// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.adaptive.Director;
import io.github.sosgames.athenacl.adaptive.Theme;
import io.github.sosgames.athenacl.adaptive.ThemeException;
import io.github.sosgames.athenacl.adaptive.ThemeIssue;
import io.github.sosgames.athenacl.adaptive.Themes;
import io.github.sosgames.athenacl.debug.Determinism;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.events.EventLog;
import io.github.sosgames.athenacl.events.ScoreException;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads the same note list or theme more than once and compares event-log hashes.
 * It does not render audio. {@code --fork-jvm} runs each pass in its own process.
 * A mismatch exits 6.
 */
@CommandLine.Command(name = "determinism", description = "Render the event log more than once and compare hashes.")
public class DeterminismCommand implements Callable<Integer> {
    private static final Pattern HASH = Pattern.compile("hash=([0-9a-f]+)");

    @CommandLine.ParentCommand DebugCommand debug;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<notes.yaml>")
    Path notes;
    @CommandLine.Option(names = "--runs", paramLabel = "<n>", defaultValue = "2")
    int runs;
    @CommandLine.Option(names = "--seed", paramLabel = "<long>", defaultValue = "0")
    long seed;
    @CommandLine.Option(names = "--fork-jvm")
    boolean forkJvm;

    private int parentCode;

    @Override
    public Integer call() {
        AgentCli parent = debug.cli();
        parent.commandName = "debug determinism";
        if (notes == null) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                    "usage: debug determinism <notes.yaml> --runs <n>",
                    "Pass a YAML note list such as samples/notes/c-major-scale.yaml.");
        }
        if (runs < 1) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "--runs",
                    "runs must be 1 or more", "Use --runs 2.");
        }
        if (!Files.isRegularFile(notes)) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]",
                    "note list not found",
                    "Pass a YAML note list such as samples/notes/c-major-scale.yaml.");
        }
        Determinism.Report report;
        try {
            if (forkJvm) {
                report = fork(parent);
                if (report == null) {
                    return parentCode;
                }
            } else {
                report = Determinism.run(runs, this::hashOnce);
            }
        } catch (IllegalStateException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof ScoreException score) {
                return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "argv[2]",
                        score.getMessage(), score.fix());
            }
            if (cause instanceof ThemeException theme) {
                ThemeIssue issue = theme.issues().get(0);
                return parent.fail(ExitCode.INVALID_INPUT, issue.code(), issue.path(),
                        issue.message(), issue.fix());
            }
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[2]", cause.getMessage(),
                    "Check that the note list is readable.");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("runs", Integer.toString(report.runs()));
        fields.put("identical", Boolean.toString(report.identical()));
        fields.put("hash", report.hash());
        if (forkJvm) {
            fields.put("fork", "true");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runs", report.runs());
        data.put("identical", report.identical());
        data.put("hash", report.hash());
        if (forkJvm) {
            data.put("fork", true);
        }
        ExitCode code = report.identical() ? ExitCode.OK : ExitCode.CHECK_FAILED;
        return parent.finish(code, KeyValue.line(fields), "athenacl.result.determinism/1",
                data, List.of(), List.of());
    }

    private String hashOnce() {
        try {
            if (Themes.looksLike(notes)) {
                Theme theme = Themes.load(notes);
                return EventLog.hash(Director.perform(theme, theme.timeline(), seed).score());
            }
            return EventLog.hash(NoteFiles.yaml(notes, seed));
        } catch (IOException | ScoreException | ThemeException e) {
            throw new IllegalStateException(e);
        }
    }

    private Determinism.Report fork(AgentCli parent) {
        String first = null;
        boolean identical = true;
        for (int i = 0; i < runs; i++) {
            Child child = spawn();
            if (child.exit != 0) {
                String message = child.stderr.isBlank() ? child.stdout : child.stderr;
                parentCode = parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[2]",
                        message.isBlank() ? "forked run failed" : message.strip(),
                        "Run debug determinism without --fork-jvm to see the in-process error.");
                return null;
            }
            Matcher matcher = HASH.matcher(child.stdout);
            if (!matcher.find()) {
                parentCode = parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[2]",
                        "forked run did not print a hash",
                        "Run debug determinism without --fork-jvm.");
                return null;
            }
            String hash = matcher.group(1);
            if (first == null) {
                first = hash;
            } else if (!first.equals(hash)) {
                identical = false;
            }
        }
        return new Determinism.Report(runs, identical, first == null ? "" : first);
    }

    private Child spawn() {
        String javaName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", javaName);
        List<String> cmd = new ArrayList<>();
        cmd.add(java.toString());
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Dstdout.encoding=UTF-8");
        cmd.add("-Dstderr.encoding=UTF-8");
        cmd.add("-Djava.awt.headless=true");
        cmd.add("-Dpicocli.ansi=false");
        cmd.add("--add-exports=java.desktop/com.sun.media.sound=ALL-UNNAMED");
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("io.github.sosgames.athenacl.cli.agent.AgentCli");
        cmd.add("debug");
        cmd.add("determinism");
        cmd.add(notes.toString());
        cmd.add("--runs");
        cmd.add("1");
        cmd.add("--seed");
        cmd.add(Long.toString(seed));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(Path.of("").toAbsolutePath().toFile());
        try {
            Process process = pb.start();
            byte[] out = process.getInputStream().readAllBytes();
            byte[] err = process.getErrorStream().readAllBytes();
            boolean finished = process.waitFor(180, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new Child(-1, "", "forked run timed out");
            }
            return new Child(process.exitValue(),
                    new String(out, StandardCharsets.UTF_8),
                    new String(err, StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Child(-1, "", "fork interrupted");
        } catch (IOException e) {
            return new Child(-1, "", e.getMessage() == null ? "fork failed" : e.getMessage());
        }
    }

    private record Child(int exit, String stdout, String stderr) {
    }
}
