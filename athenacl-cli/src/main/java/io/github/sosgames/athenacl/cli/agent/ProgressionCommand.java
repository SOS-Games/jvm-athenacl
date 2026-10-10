// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.AtomicFiles;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.events.EventLog;
import io.github.sosgames.athenacl.theory.Key;
import io.github.sosgames.athenacl.theory.Progressions;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Realizes a roman-numeral progression as one chord per bar and writes {@code events.jsonl}.
 * {@code --voice-lead} keeps the leaps small and avoids parallel fifths and octaves.
 * The hash is that event log, the same hash {@code render} uses.
 */
@CommandLine.Command(name = "progression", description = "Voice-lead a roman-numeral progression into an event log.")
public class ProgressionCommand implements Callable<Integer> {
    @CommandLine.ParentCommand TheoryCommand theory;
    @CommandLine.Parameters(index = "0..*", arity = "1..*", paramLabel = "<roman numerals>")
    List<String> numerals;
    @CommandLine.Option(names = "--key", required = true, paramLabel = "<tonic mode>")
    String keyName;
    @CommandLine.Option(names = "--voice-lead", description = "Prefer small motion and no parallel fifths or octaves.")
    boolean voiceLead;
    @CommandLine.Option(names = "--out", paramLabel = "<dir>")
    Path out;

    @Override
    public Integer call() {
        AgentCli parent = theory.cli();
        parent.commandName = "theory progression";
        if (out == null) {
            return parent.fail(ExitCode.USAGE, "USAGE", "--out",
                    "usage: theory progression <roman numerals> --key <key> --out <dir>",
                    "Pass --out build/l2. Add --voice-lead to keep the voices close.");
        }
        Progressions.Realized realized;
        try {
            Key key = Key.parse(keyName);
            realized = Progressions.realize(String.join(" ", numerals), key, voiceLead);
        } catch (IllegalArgumentException e) {
            return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "argv[1]", e.getMessage(),
                    "Use numerals such as i bVII bVI and a key such as \"D aeolian\".");
        }
        byte[] body = EventLog.encode(realized.score());
        AtomicFiles.WritePlan plan;
        try {
            plan = AtomicFiles.write(out.resolve("events.jsonl"), body, parent.dryRun);
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--out", e.getMessage(),
                    "Check that --out is a writable directory and try again.");
        }
        String hash = EventLog.sha256(body);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("chords", Integer.toString(realized.chords()));
        fields.put("maxMotion", Integer.toString(realized.maxMotion()));
        fields.put("parallels", Integer.toString(realized.parallels()));
        fields.put("hash", hash);
        fields.put("changed", Boolean.toString(plan.changed()));
        fields.put("dryRun", Boolean.toString(parent.dryRun));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chords", realized.chords());
        data.put("maxMotion", realized.maxMotion());
        data.put("parallels", realized.parallels());
        data.put("hash", hash);
        data.put("changed", plan.changed());
        data.put("dryRun", parent.dryRun);
        return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.progression/1",
                data, List.of(), List.of());
    }
}
