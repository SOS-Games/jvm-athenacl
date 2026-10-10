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
                    "play",
                    "Play a note list, event log, or WAV on the speakers. --dry-run prints the length and does not open the device.",
                    List.of(
                            gradle("play samples/notes/c-major-scale.yaml"),
                            gradle("play samples/notes/c-major-scale.yaml --dry-run")),
                    false,
                    "athenacl.result.play/1"),
            new CommandInfo(
                    "describe-music",
                    "One bar= line per bar from an event log. --roll adds a piano roll (C4 |● is a downbeat quarter). Chord and key estimates are appended when the notes support them.",
                    List.of(gradle("describe-music build/l0/events.jsonl --roll")),
                    false,
                    "athenacl.result.describe-music/1"),
            new CommandInfo(
                    "debug",
                    "debug determinism hashes a note list twice. debug inspect filters notes with --where pitch=E4. debug check --stuck-notes counts notes that never turn off. debug bar explains one bar. debug trace --transitions lists state changes. debug replay hashes a control log.",
                    List.of(
                            gradle("debug determinism samples/notes/c-major-scale.yaml --runs 2"),
                            gradle("debug inspect build/l0/events.jsonl --where pitch=E4"),
                            gradle("debug bar samples/themes/poc.yaml --seed 42 --bar 9")),
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
                    "describe scale lists a mode. describe chord names a roman numeral. describe instruments counts presets.",
                    List.of(
                            gradle("describe scale D-dorian"),
                            gradle("describe chord Dm7 --key D-dorian"),
                            gradle("describe instruments --soundbank generaluser")),
                    false,
                    "athenacl.result.instruments/1"),
            new CommandInfo(
                    "analyze",
                    "EBU R128 loudness of a WAV or a render directory: integrated LUFS, true peak, short-term.",
                    List.of(gradle("analyze build/l0")),
                    false,
                    "athenacl.result.analyze/1"),
            new CommandInfo(
                    "theory",
                    "theory progression voice-leads roman numerals into events.jsonl. The hash is that event log.",
                    List.of(gradle(
                            "theory progression i bVII bVI bVII --key D-aeolian --voice-lead --out build/l2")),
                    true,
                    "athenacl.result.progression/1"),
            new CommandInfo(
                    "rhythm",
                    "rhythm pattern prints a drum grid. rhythm poly writes 3-against-2 into events.jsonl.",
                    List.of(
                            gradle("rhythm pattern x..x..x. --meter 6/8 --bars 2 --drum-grid"),
                            gradle("rhythm poly 3:2 --bars 2 --out build/l3p")),
                    true,
                    "athenacl.result.rhythm/1"),
            new CommandInfo(
                    "theme",
                    "theme validate checks states and transitions. theme explain prints the states a theme will play.",
                    List.of(
                            gradle("theme validate samples/themes/poc.yaml"),
                            gradle("theme explain samples/themes/poc.yaml")),
                    false,
                    "athenacl.result.validate/1"));

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
