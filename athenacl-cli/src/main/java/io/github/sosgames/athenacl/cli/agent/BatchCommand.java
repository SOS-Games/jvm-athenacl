// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Runs many one-shot commands in this JVM. Each line of the JSONL file is one
 * command, and the next command does not see the previous command's flags or files
 * except what that command already wrote. {@code --json} prints one JSON object per
 * command. {@code --stop-on-error} returns at the first failure.
 */
@CommandLine.Command(name = "batch", description = "Run a JSONL list of commands in one process.")
public class BatchCommand implements Callable<Integer> {
    private static final ObjectMapper JSON = new ObjectMapper();

    @CommandLine.ParentCommand AgentCli parent;
    @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<commands.jsonl>")
    Path file;
    @CommandLine.Option(names = "--stop-on-error", description = "Return at the first command that fails.")
    boolean stopOnError;

    @Override
    public Integer call() {
        parent.commandName = "batch";
        List<String> lines;
        try {
            lines = readLines();
        } catch (IOException e) {
            return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[1]", e.getMessage(),
                    "Check that the JSONL file is readable.");
        }
        if (lines == null) {
            return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[1]", "command list not found",
                    "Pass a JSONL file, or pipe one on stdin.");
        }
        int failed = 0;
        int ran = 0;
        int exit = ExitCode.OK.number();
        StringBuilder text = new StringBuilder();
        StringBuilder json = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            JsonNode node;
            try {
                node = JSON.readTree(line);
            } catch (IOException e) {
                return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "line " + (i + 1),
                        "line is not JSON",
                        "Each line is {\"id\":1,\"cmd\":\"version\",\"args\":[]}.");
            }
            if (!node.isObject() || node.path("cmd").asText("").isBlank()) {
                return parent.fail(ExitCode.INVALID_INPUT, "INVALID_INPUT", "line " + (i + 1),
                        "line needs a cmd",
                        "Each line is {\"id\":1,\"cmd\":\"version\",\"args\":[]}.");
            }
            List<String> argv = argv(node);
            ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
            ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
            int code;
            try (PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
                 PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8)) {
                code = AgentCli.execute(argv.toArray(String[]::new), out, err);
            }
            ran++;
            if (code != 0) {
                failed++;
                if (exit == 0) {
                    exit = code;
                }
            }
            String childErr = errBytes.toString(StandardCharsets.UTF_8);
            if (!childErr.isEmpty()) {
                parent.err.print(childErr);
                if (!childErr.endsWith("\n")) {
                    parent.err.println();
                }
            }
            String stdout = stripEnd(outBytes.toString(StandardCharsets.UTF_8));
            JsonNode idNode = node.get("id");
            String id = idNode == null || idNode.isNull() ? Integer.toString(i + 1) : idNode.asText();
            if (parent.json) {
                ObjectNode row = JSON.createObjectNode();
                if (idNode == null || idNode.isNull()) {
                    row.put("id", i + 1);
                } else {
                    row.set("id", idNode);
                }
                row.put("exit", code);
                row.put("ok", code == 0);
                row.put("stdout", stdout);
                json.append(row.toString()).append('\n');
            } else {
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("id", id);
                fields.put("exit", Integer.toString(code));
                text.append(KeyValue.line(fields)).append('\n');
                if (!stdout.isEmpty()) {
                    text.append(stdout).append('\n');
                }
            }
            if (stopOnError && code != 0) {
                break;
            }
        }
        if (!parent.json) {
            Map<String, String> summary = new LinkedHashMap<>();
            summary.put("commands", Integer.toString(ran));
            summary.put("failed", Integer.toString(failed));
            text.append(KeyValue.line(summary));
            parent.out.println(text);
        } else if (!json.isEmpty()) {
            parent.out.print(json);
        }
        return exit;
    }

    private List<String> readLines() throws IOException {
        if (file != null) {
            if (!Files.isRegularFile(file)) {
                return null;
            }
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        }
        try (InputStreamReader reader = new InputStreamReader(System.in, StandardCharsets.UTF_8)) {
            return new java.io.BufferedReader(reader).lines().toList();
        }
    }

    private static List<String> argv(JsonNode node) {
        List<String> argv = new ArrayList<>();
        String cmd = node.path("cmd").asText("").trim();
        if (!cmd.isEmpty()) {
            for (String part : cmd.split("\\s+")) {
                argv.add(part);
            }
        }
        JsonNode args = node.get("args");
        if (args == null || args.isNull()) {
            return argv;
        }
        if (args.isArray()) {
            for (JsonNode arg : args) {
                argv.add(arg.asText());
            }
            return argv;
        }
        if (args.isObject()) {
            args.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value.isBoolean()) {
                    if (value.booleanValue()) {
                        argv.add("--" + entry.getKey());
                    }
                    return;
                }
                argv.add("--" + entry.getKey());
                argv.add(value.asText());
            });
        }
        return argv;
    }

    private static String stripEnd(String text) {
        if (text.endsWith("\r\n")) {
            return text.substring(0, text.length() - 2);
        }
        if (text.endsWith("\n")) {
            return text.substring(0, text.length() - 1);
        }
        return text;
    }
}
