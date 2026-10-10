// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.AtomicFiles;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.cli.model.SchemaRegistry;
import io.github.sosgames.athenacl.cli.model.Suggestions;
import io.github.sosgames.athenacl.cli.model.TextPaths;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Prints a published schema. With no {@code --out}, stdout is the schema document itself
 * so it can be redirected. With {@code --out}, stdout is one key=value line and the document
 * is written under that directory. {@code --dry-run} reports the same line and does not
 * create the file or its parent folder. {@code --json} always wraps a Result instead.
 */
@CommandLine.Command(name = "schema", description = "Print or write a published JSON schema.")
public class SchemaCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<id>")
    String id;
    @CommandLine.Option(names = "--out", paramLabel = "<dir>")
    Path outDir;

    @Override
    public Integer call() {
        parent.commandName = "schema";
        if (id == null || id.isBlank()) {
            return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]", "usage: schema <id>",
                    "Known schemas: " + String.join(", ", SchemaRegistry.ids()));
        }
        Optional<byte[]> body = SchemaRegistry.bytes(id);
        if (body.isEmpty()) {
            String near = Suggestions.nearest(id, SchemaRegistry.ids());
            String fix = near == null
                    ? "Known schemas: " + String.join(", ", SchemaRegistry.ids())
                    : "Did you mean " + near + "?";
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[1]", "no schema " + id, fix);
        }
        if (outDir == null) {
            if (parent.json) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("id", id);
                data.put("document", SchemaRegistry.node(id));
                return parent.finish(ExitCode.OK, null, "athenacl.result.schema/1", data, List.of(), List.of());
            }
            parent.out.writeBytes(body.get());
            parent.out.flush();
            return ExitCode.OK.number();
        }
        Path file = outDir.resolve(SchemaRegistry.fileName(id));
        try {
            AtomicFiles.WritePlan plan = AtomicFiles.write(file, body.get(), parent.dryRun);
            String shown = TextPaths.portable(plan.path());
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("schema", id);
            fields.put("action", "write");
            fields.put("path", shown);
            fields.put("bytes", Integer.toString(plan.bytes()));
            fields.put("changed", Boolean.toString(plan.changed()));
            fields.put("dryRun", Boolean.toString(plan.dryRun()));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", id);
            data.put("action", "write");
            data.put("path", shown);
            data.put("bytes", plan.bytes());
            data.put("changed", plan.changed());
            data.put("dryRun", plan.dryRun());
            return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.schema/1",
                    data, List.of(), List.of());
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--out", e.getMessage(),
                    "Check that --out is a writable directory and try again.");
        }
    }
}
