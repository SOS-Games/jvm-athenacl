// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.core.CommandPrompt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The human help page and the {@code commands} array inside {@code help --json}. */
final class HelpText {
    private HelpText() {
    }

    static String full() {
        StringBuilder sb = new StringBuilder();
        sb.append("athenaCL CLI (no window). Cwd is the repo root. Stdout is data. Logs go to stderr.\n\n");
        for (CommandCatalog.CommandInfo info : CommandCatalog.all()) {
            for (String example : info.examples()) {
                sb.append(example).append('\n');
            }
        }
        sb.append('\n');
        for (CommandCatalog.CommandInfo info : CommandCatalog.all()) {
            sb.append(blurb(info)).append('\n');
        }
        sb.append("\nFlags on every command: --json  --dry-run  --strict  -v  -vv  --log-level <error|warn|info|debug|trace>  -h\n");
        sb.append("Exit codes: 0 ok, 1 unexpected, 2 usage, 3 invalid, 4 not found, 5 runtime, 6 check failed, 7 environment\n");
        return sb.toString();
    }

    static String one(CommandCatalog.CommandInfo info) {
        StringBuilder sb = new StringBuilder();
        for (String example : info.examples()) {
            sb.append(example).append('\n');
        }
        sb.append('\n');
        sb.append(blurb(info)).append('\n');
        return sb.toString();
    }

    static Map<String, Object> data(List<CommandCatalog.CommandInfo> commands) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (CommandCatalog.CommandInfo info : commands) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", info.name());
            row.put("summary", info.summary());
            row.put("examples", info.examples());
            row.put("writesFiles", info.writesFiles());
            row.put("schema", info.schemaId());
            list.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("commands", list);
        data.put("athenaCommands", CommandPrompt.names());
        return data;
    }

    private static String blurb(CommandCatalog.CommandInfo info) {
        return String.format("%-10s %s", info.name(), info.summary());
    }
}
