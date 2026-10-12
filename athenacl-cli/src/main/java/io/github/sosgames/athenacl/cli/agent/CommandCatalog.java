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
                    "debug determinism hashes a note list or theme twice. --fork-jvm uses a fresh process for each run. debug bisect finds the first bar where two renders split. debug inspect filters notes with --where pitch=E4. debug check --stuck-notes counts notes that never turn off. debug bar explains one bar. debug trace --transitions lists state changes. debug replay hashes a control log. debug fuzz-transitions tries seeded state changes. debug overhead times provenance against debug off.",
                    List.of(
                            gradle("debug determinism samples/notes/c-major-scale.yaml --runs 2"),
                            gradle("debug determinism themes/fantasy-dungeon.yaml --runs 2 --fork-jvm"),
                            gradle("debug bisect --good build/l11/a --bad build/l11/b"),
                            gradle("debug inspect build/l0/events.jsonl --where pitch=E4"),
                            gradle("debug bar samples/themes/poc.yaml --seed 42 --bar 9")),
                    false,
                    "athenacl.result.determinism/1"),
            new CommandInfo(
                    "diff",
                    "diff of two themes prints the resolved leaves that differ. diff of two render directories prints the first bar that split. --fail-on-diff exits 6 when anything differs.",
                    List.of(gradle("diff themes/fantasy-dungeon.yaml themes/scifi-space.yaml")),
                    false,
                    "athenacl.result.diff/1"),
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
                    "describe scale lists a mode. describe chord names a roman numeral. describe instruments counts presets. describe pos lists value, wave, or rhythm generators. describe textures lists the texture modules.",
                    List.of(
                            gradle("describe scale D-dorian"),
                            gradle("describe chord Dm7 --key D-dorian"),
                            gradle("describe instruments --soundbank generaluser"),
                            gradle("describe pos --group value"),
                            gradle("describe pos --group omde"),
                            gradle("describe textures")),
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
                    "theory progression voice-leads roman numerals into events.jsonl. theory modulate plans a key change.",
                    List.of(
                            gradle("theory progression i bVII bVI bVII --key D-aeolian --voice-lead --out build/l2"),
                            gradle("theory modulate D-dorian F-lydian --method pivot")),
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
                    "athenacl.result.validate/1"),
            new CommandInfo(
                    "preset",
                    "preset show prints a built-in genre preset: instruments and the GM limits.",
                    List.of(
                            gradle("preset show fantasy"),
                            gradle("preset show scifi-space")),
                    false,
                    "athenacl.result.preset/1"),
            new CommandInfo(
                    "motif",
                    "motif generate grows a short idea. motif transform turns it over. motif import reads a MIDI melody.",
                    List.of(
                            gradle("motif generate --key D-dorian --seed 3"),
                            gradle("motif transform m1 --invert --invert")),
                    true,
                    "athenacl.result.motif/1"),
            new CommandInfo(
                    "melody",
                    "melody generate ranks hook candidates. melody score rates a MIDI line. melody rate writes an A/B row. melody fit turns ratings into weights.",
                    List.of(
                            gradle("melody generate --key E-minor --candidates 64 --seed 3 --explain"),
                            gradle("melody score samples/melodies/folk-pd-01.mid --key G-major")),
                    true,
                    "athenacl.result.melody/1"),
            new CommandInfo(
                    "adapt",
                    "adapt prints the scheduler snapshot: state, intensity, bar, lookahead, and build time.",
                    List.of(gradle("adapt themes/fantasy-dungeon.yaml --seed 1")),
                    true,
                    "athenacl.result.adapt/1"),
            new CommandInfo(
                    "instruments",
                    "instruments list, show, audition, and compare named instruments from a kit. gm-lite is the default.",
                    List.of(
                            gradle("instruments list --kit gm-lite"),
                            gradle("instruments show harp --kit gm-lite")),
                    true,
                    "athenacl.result.instrument/1"),
            new CommandInfo(
                    "kit",
                    "kit validate checks sources, licenses, SF3 files, and slot assignment.",
                    List.of(gradle("kit validate instruments/gm-lite.yaml")),
                    false,
                    "athenacl.result.kit/1"),
            new CommandInfo(
                    "credits",
                    "credits writes CREDITS.md for the soundfonts a theme's kits use.",
                    List.of(gradle("credits samples/themes/two-banks.yaml --out build/credits")),
                    true,
                    "athenacl.result.credits/1"),
            new CommandInfo(
                    "compat",
                    "compat random prints CPython random() draws for a seed.",
                    List.of(gradle("compat random --seed 7 --n 5")),
                    false,
                    "athenacl.result.compat/1"),
            new CommandInfo(
                    "conformance",
                    "conformance list counts the 31 pregui MIDI demo scripts. conformance run checks that each script has commands. conformance run --subset slice compares the LineGroove beats to their golden MIDI. conformance run --subset po.value samples the value generators. conformance run --subset po.omde,po.rhythm samples the wave, breakpoint, and rhythm generators. conformance run --subset texture,clone checks the texture modules and the clone filters.",
                    List.of(
                            gradle("conformance list"),
                            gradle("conformance run --subset po.value"),
                            gradle("conformance run --subset po.omde,po.rhythm"),
                            gradle("conformance run --subset texture,clone")),
                    false,
                    "athenacl.result.conformance/1"),
            new CommandInfo(
                    "po",
                    "po sample draws the next values from a parameter object. basketGen with oc walks the list and then starts over.",
                    List.of(gradle("po sample basketGen \"oc,(1,2,3)\" --n 6")),
                    false,
                    "athenacl.result.po/1"),
            new CommandInfo(
                    "athena",
                    "athena run plays a path or a LineGroove beat and checks the MIDI against the golden file beside it.",
                    List.of(gradle("athena run conformance/paths/c-major.ath --out build/l13")),
                    true,
                    "athenacl.result.athena/1"));

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
