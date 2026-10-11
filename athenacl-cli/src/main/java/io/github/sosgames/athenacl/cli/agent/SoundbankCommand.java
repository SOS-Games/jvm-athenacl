// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import io.github.sosgames.athenacl.cli.model.ExitCode;
import io.github.sosgames.athenacl.cli.model.KeyValue;
import io.github.sosgames.athenacl.cli.model.TextPaths;
import io.github.sosgames.athenacl.render.soundbank.SoundbankCatalog;
import io.github.sosgames.athenacl.render.soundbank.SoundbankInstaller;
import io.github.sosgames.athenacl.render.soundbank.SoundbankPaths;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Installs, lists, and shows catalog soundbanks. {@code install --dry-run} prints the
 * pinned size and hash and does not write the cache.
 */
@CommandLine.Command(name = "soundbank", description = "Install or show a catalog soundbank.",
        subcommands = {SoundbankCommand.Install.class, SoundbankCommand.ListBanks.class, SoundbankCommand.Show.class})
public class SoundbankCommand implements Callable<Integer> {
    @CommandLine.ParentCommand AgentCli parent;

    @Override
    public Integer call() {
        parent.commandName = "soundbank";
        return parent.fail(ExitCode.USAGE, "USAGE", "argv[1]",
                "usage: soundbank install <id> | soundbank list | soundbank show <id>",
                "Known ids: generaluser, musescore-general.");
    }

    AgentCli cli() {
        return parent;
    }

    @CommandLine.Command(name = "install", description = "Download a catalog soundbank and check its SHA-256.")
    public static class Install implements Callable<Integer> {
        @CommandLine.ParentCommand SoundbankCommand soundbank;
        @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<id>")
        String id;
        @CommandLine.Option(names = "--cache", paramLabel = "<dir>")
        Path cache;

        @Override
        public Integer call() {
            AgentCli parent = soundbank.cli();
            parent.commandName = "soundbank install";
            if (id == null || id.isBlank()) {
                return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]", "usage: soundbank install <id>",
                        "Known ids: generaluser, musescore-general.");
            }
            if (SoundbankCatalog.byId(id) == null) {
                return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]", "unknown soundbank " + id,
                        "Known ids: generaluser, musescore-general.");
            }
            try {
                SoundbankInstaller.Plan plan = SoundbankInstaller.install(id, SoundbankPaths.cache(cache), parent.dryRun);
                String text = installLine(plan);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("soundbank", plan.id());
                data.put("action", plan.action());
                data.put("status", plan.status());
                data.put("size", plan.size());
                data.put("sha256", plan.sha256());
                data.put("changed", plan.changed());
                data.put("dryRun", plan.dryRun());
                data.put("path", TextPaths.portable(plan.path()));
                return parent.finish(ExitCode.OK, text, "athenacl.result.soundbank/1",
                        data, List.of(), List.of());
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "argv[2]", e.getMessage(),
                        "Check the network and the cache directory, then run install again.");
            }
        }
    }

    private static String installLine(SoundbankInstaller.Plan plan) {
        SoundbankCatalog.Entry entry = SoundbankCatalog.byId(plan.id());
        String license = entry == null || entry.license() == null ? "" : " license=" + entry.license();
        if (plan.dryRun() && "download".equals(plan.action())) {
            return "action=download size=" + plan.size() + " sha256=" + plan.sha256()
                    + " soundbank=" + plan.id() + " status=" + plan.status() + " dryRun=true" + license;
        }
        if ("installed".equals(plan.status())) {
            return "soundbank=" + plan.id() + " status=installed sha256=" + plan.sha256()
                    + " changed=true size=" + plan.size() + license;
        }
        return "soundbank=" + plan.id() + " status=present changed=false sha256=" + plan.sha256() + license;
    }

    @CommandLine.Command(name = "list", description = "List catalog soundbanks and whether each is installed.")
    public static class ListBanks implements Callable<Integer> {
        @CommandLine.ParentCommand SoundbankCommand soundbank;
        @CommandLine.Option(names = "--cache", paramLabel = "<dir>")
        Path cache;

        @Override
        public Integer call() {
            AgentCli parent = soundbank.cli();
            parent.commandName = "soundbank list";
            StringBuilder text = new StringBuilder();
            try {
                for (SoundbankCatalog.Entry entry : SoundbankCatalog.all()) {
                    Path file = SoundbankPaths.cache(cache).resolve(entry.fileName());
                    boolean present = SoundbankInstaller.matches(file, entry);
                    if (!text.isEmpty()) {
                        text.append('\n');
                    }
                    Map<String, String> fields = new LinkedHashMap<>();
                    fields.put("soundbank", entry.id());
                    fields.put("status", present ? "present" : "absent");
                    fields.put("size", Long.toString(entry.size()));
                    text.append(KeyValue.line(fields));
                }
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--cache", e.getMessage(),
                        "Check that the cache directory is readable.");
            }
            return parent.finish(ExitCode.OK, text.toString(), "athenacl.result.soundbank/1",
                    Map.of("soundbanks", SoundbankCatalog.all().stream().map(SoundbankCatalog.Entry::id).toList()),
                    List.of(), List.of());
        }
    }

    @CommandLine.Command(name = "show", description = "Show one catalog soundbank.")
    public static class Show implements Callable<Integer> {
        @CommandLine.ParentCommand SoundbankCommand soundbank;
        @CommandLine.Parameters(index = "0", arity = "0..1", paramLabel = "<id>")
        String id;
        @CommandLine.Option(names = "--cache", paramLabel = "<dir>")
        Path cache;

        @Override
        public Integer call() {
            AgentCli parent = soundbank.cli();
            parent.commandName = "soundbank show";
            if (id == null || id.isBlank()) {
                return parent.fail(ExitCode.USAGE, "USAGE", "argv[2]", "usage: soundbank show <id>",
                        "Known ids: generaluser, musescore-general.");
            }
            SoundbankCatalog.Entry entry = SoundbankCatalog.byId(id);
            if (entry == null) {
                return parent.fail(ExitCode.NOT_FOUND, "NOT_FOUND", "argv[2]", "unknown soundbank " + id,
                        "Known ids: generaluser, musescore-general.");
            }
            try {
                Path file = SoundbankPaths.cache(cache).resolve(entry.fileName());
                boolean present = SoundbankInstaller.matches(file, entry);
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("soundbank", entry.id());
                fields.put("file", entry.fileName());
                fields.put("size", Long.toString(entry.size()));
                fields.put("sha256", entry.sha256());
                fields.put("status", present ? "present" : "absent");
                fields.put("path", TextPaths.portable(file));
                return parent.finish(ExitCode.OK, KeyValue.line(fields), "athenacl.result.soundbank/1",
                        new LinkedHashMap<>(fields), List.of(), List.of());
            } catch (IOException e) {
                return parent.fail(ExitCode.RUNTIME, "IO_ERROR", "--cache", e.getMessage(),
                        "Check that the cache directory is readable.");
            }
        }
    }
}
