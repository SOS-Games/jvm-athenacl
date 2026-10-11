// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.cli.model.TextPaths;
import io.github.sosgames.athenacl.instruments.CreditsWriter;
import io.github.sosgames.athenacl.instruments.KitException;
import io.github.sosgames.athenacl.render.soundbank.SoundbankPaths;
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
 * Writes CREDITS.md for the soundfonts a theme's kits use, plus a copy of each license file.
 * Only sources that a resolved instrument points at are included.
 */
@CommandLine.Command(name = "credits", description = "Write credits for the soundfonts a theme uses.")
public class CreditsCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<theme.yaml>")
    Path theme;
    @CommandLine.Option(names = "--out", paramLabel = "<dir>")
    Path out;
    @CommandLine.Option(names = "--cache", paramLabel = "<dir>")
    Path cache;

    @Override
    public Integer call() {
        parent.commandName = "credits";
        if (theme == null || out == null) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]",
                    "usage: credits <theme.yaml> --out <dir>",
                    "Try credits themes/fantasy-dungeon.yaml --out build/credits.");
        }
        if (!Files.isRegularFile(theme)) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[1]", "theme not found",
                    "Pass a theme YAML file.");
        }
        try {
            CreditsWriter.Written written = CreditsWriter.write(List.of(theme), out, SoundbankPaths.cache(cache),
                    parent.dryRun);
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("sources", Integer.toString(written.sources()));
            fields.put("licenses", String.join(",", written.licenses()));
            fields.put("path", TextPaths.portable(written.file()));
            fields.put("dryRun", Boolean.toString(parent.dryRun));
            return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.credits/1",
                    new LinkedHashMap<>(fields), List.of(), List.of());
        } catch (KitException e) {
            KitException.Issue issue = e.issues().get(0);
            return parent.fail(ExitCode.INVALID_INPUT, issue.code(), issue.path(), issue.message(), issue.fix());
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--out", e.getMessage(),
                    "Check that the theme is readable and --out is writable.");
        }
    }
}
