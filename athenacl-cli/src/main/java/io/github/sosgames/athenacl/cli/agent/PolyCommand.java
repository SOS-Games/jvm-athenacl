// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.AtomicFiles;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.events.EventLog;
import io.github.sosgames.athenacl.rhythm.Polyrhythm;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Writes two even pulse trains into {@code events.jsonl}. {@code 3:2} puts three notes
 * against two in each bar. Both trains hit the downbeat, so {@code alignedAtBar} lists every bar.
 */
@CommandLine.Command(name = "poly", description = "Write a polyrhythm such as 3:2 into an event log.")
public class PolyCommand implements Callable<Integer> {
    @CommandLine.ParentCommand RhythmCommand rhythm;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<3:2>")
    String ratio;
    @CommandLine.Option(names = "--bars", paramLabel = "<n>", defaultValue = "1")
    int bars;
    @CommandLine.Option(names = "--out", paramLabel = "<dir>")
    Path out;

    @Override
    public Integer call() {
        AgentCli parent = rhythm.cli();
        parent.commandName = "rhythm poly";
        if (ratio == null || ratio.isBlank()) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]",
                    "usage: rhythm poly <3:2> --bars 2 --out <dir>",
                    "Pass a ratio such as 3:2.");
        }
        if (out == null) {
            return parent.fail(ExitCode.USAGE, "USAGE", "--out",
                    "usage: rhythm poly <3:2> --bars 2 --out <dir>",
                    "Pass --out build/l3p.");
        }
        Polyrhythm.Voices voices;
        try {
            int[] counts = Polyrhythm.ratio(ratio);
            voices = Polyrhythm.render(counts[0], counts[1], bars);
        } catch (IllegalArgumentException e) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "argv[2]", e.getMessage(),
                    "Use a ratio such as 3:2 and at least one bar.");
        }
        byte[] body = EventLog.encode(voices.score());
        AtomicFiles.WritePlan plan;
        try {
            plan = AtomicFiles.write(out.resolve("events.jsonl"), body, parent.dryRun);
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--out", e.getMessage(),
                    "Check that --out is a writable directory and try again.");
        }
        String hash = EventLog.sha256(body);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("layers", Integer.toString(voices.layers()));
        fields.put("alignedAtBar", voices.alignedAtBar());
        fields.put("notes", Integer.toString(voices.score().notes().size()));
        fields.put("hash", hash);
        fields.put("changed", Boolean.toString(plan.changed()));
        fields.put("dryRun", Boolean.toString(parent.dryRun));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("layers", voices.layers());
        data.put("alignedAtBar", voices.alignedAtBar());
        data.put("notes", voices.score().notes().size());
        data.put("hash", hash);
        data.put("changed", plan.changed());
        data.put("dryRun", parent.dryRun);
        return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.poly/1",
                data, List.of(), List.of());
    }
}
