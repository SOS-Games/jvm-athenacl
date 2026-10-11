// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.instruments.KitException;
import io.github.sosgames.athenacl.instruments.KitSession;
import io.github.sosgames.athenacl.render.soundbank.SoundbankPaths;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Checks a kit file: checksums, licenses, SF3, missing names, and whether two instruments
 * were given the same synth slot. {@code --allow-unlicensed} turns a denied license into a warning.
 */
@CommandLine.Command(name = "kit", description = "Validate an instrument kit.",
        subcommands = {KitCommand.Validate.class})
public class KitCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;

    @Override
    public Integer call() {
        parent.commandName = "kit";
        return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]",
                "usage: kit validate <kit.yaml>",
                "Try kit validate instruments/gm-lite.yaml.");
    }

    AgentCli cli() {
        return parent;
    }

    @CommandLine.Command(name = "validate", description = "Check sources, licenses, and slot assignment.")
    public static class Validate implements Callable<Integer> {
        @CommandLine.ParentCommand KitCommand kit;
        @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<kit.yaml>")
        Path file;
        @CommandLine.Option(names = "--cache", paramLabel = "<dir>")
        Path cache;
        @CommandLine.Option(names = "--allow-unlicensed")
        boolean allowUnlicensed;

        @Override
        public Integer call() {
            AgentCli parent = kit.cli();
            parent.commandName = "kit validate";
            if (file == null) {
                return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]", "usage: kit validate <kit.yaml>",
                        "Try kit validate instruments/gm-lite.yaml.");
            }
            try {
                KitSession session = KitSession.open(file, SoundbankPaths.cache(cache), allowUnlicensed);
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("kit", session.kit().id());
                fields.put("sources", Integer.toString(session.sourceCount()));
                fields.put("instruments", Integer.toString(session.instruments().size()));
                fields.put("slots.collisions", Integer.toString(session.collisions()));
                fields.put("fallbacksTaken", Integer.toString(session.fallbacksTaken()));
                fields.put("warnings", Integer.toString(session.warnings().size()));
                return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.kit/1",
                        new LinkedHashMap<>(fields), List.of(), List.of());
            } catch (KitException e) {
                KitException.Issue issue = e.issues().get(0);
                return parent.fail(ExitCode.INVALID_INPUT, issue.code(), issue.path(), issue.message(), issue.fix());
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[2]", e.getMessage(),
                        "Check that the kit file and the soundfont cache are readable.");
            }
        }
    }
}
