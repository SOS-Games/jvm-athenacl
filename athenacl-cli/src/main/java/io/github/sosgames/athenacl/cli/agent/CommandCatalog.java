// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.sosgames.athenacl.cli.agent;

import java.util.List;

/**
 * Every command the skeleton knows. Help text and {@code help --json} are built from this
 * list, so a new command has to be added here or it will not show up.
 */
public final class CommandCatalog {
    public record CommandInfo(
            String name,
            String summary,
            List<String> examples,
            boolean writesFiles,
            String schemaId) {
    }

    private static String gradle(String args) {
        return "gradlew.bat :athenacl-cli:agentCli --args=\"" + args + "\"";
    }

    public static final List<CommandInfo> ALL = List.of(
            new CommandInfo(
                    "help",
                    "Commands and copy-pasteable examples. --json prints one Result document.",
                    List.of(gradle("help"), gradle("help --json")),
                    false,
                    "athenacl.result.help/1"),
            new CommandInfo(
                    "version",
                    "One line version= java= gervill=. -v, -vv, and --log-level log on stderr only.",
                    List.of(gradle("version"), gradle("version -vv")),
                    false,
                    "athenacl.result.version/1"),
            new CommandInfo(
                    "schema",
                    "Print a published schema. --out writes it atomically. --dry-run plans that write and touches nothing.",
                    List.of(
                            gradle("schema athenacl.result.version/1"),
                            gradle("schema athenacl.result.version/1 --out build/schema --dry-run")),
                    true,
                    "athenacl.result.schema/1"),
            new CommandInfo(
                    "render",
                    "Write MIDI, WAV, events.jsonl, and a text dump from a note-list YAML file. The hash is the event log.",
                    List.of(gradle(
                            "render samples/notes/c-major-scale.yaml --out build/l0 --formats mid,wav,events,txt")),
                    true,
                    "athenacl.result.render/1"),
            new CommandInfo(
                    "describe-music",
                    "One bar= line per bar from an event log. --roll adds a piano roll (C4 |● is a downbeat quarter).",
                    List.of(gradle("describe-music build/l0/events.jsonl --roll")),
                    false,
                    "athenacl.result.describe-music/1"),
            new CommandInfo(
                    "debug",
                    "debug determinism hashes a note list twice. debug inspect filters notes with --where pitch=E4.",
                    List.of(
                            gradle("debug determinism samples/notes/c-major-scale.yaml --runs 2"),
                            gradle("debug inspect build/l0/events.jsonl --where pitch=E4")),
                    false,
                    "athenacl.result.determinism/1"),
            new CommandInfo(
                    "soundbank",
                    "soundbank install downloads GeneralUser GS and checks the pinned SHA-256. list and show report the cache.",
                    List.of(
                            gradle("soundbank install generaluser --dry-run"),
                            gradle("soundbank install generaluser")),
                    true,
                    "athenacl.result.soundbank/1"),
            new CommandInfo(
                    "describe",
                    "describe instruments counts presets. GeneralUser GS is instruments=287 drumkits=13.",
                    List.of(gradle("describe instruments --soundbank generaluser")),
                    false,
                    "athenacl.result.instruments/1"),
            new CommandInfo(
                    "analyze",
                    "EBU R128 loudness of a WAV or a render directory: integrated LUFS, true peak, short-term.",
                    List.of(gradle("analyze build/l0")),
                    false,
                    "athenacl.result.analyze/1"));

    private CommandCatalog() {
    }

    public static List<CommandInfo> all() {
        return ALL;
    }

    public static List<String> names() {
        return ALL.stream().map(CommandInfo::name).toList();
    }

    public static CommandInfo byName(String name) {
        if (name == null) {
            return null;
        }
        for (CommandInfo info : ALL) {
            if (info.name().equalsIgnoreCase(name)) {
                return info;
            }
        }
        return null;
    }
}
