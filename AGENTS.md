# AGENTS

Instructions for AI coding agents working in **jvm-athenacl**.

## Project

GPL-3.0-or-later Java 21 rewrite of athenaCL as an adaptive game-music engine for libGDX desktop. Package `io.github.sosgames.athenacl`. Layer plan: [docs/research/05-project-plan.md](docs/research/05-project-plan.md). L0a–L21, L1b (instrument kits), L7b (hook generator and melody critic), L7c (melodic engine), L7d (instrument analysis), L7e (instrument lint), and L7f (ensemble selection) are in the tree. Do not start L6b or L9b until the user asks.

- Java 21 (`options.release = 21`). Versions live in `gradle.properties`.
- Groovy Gradle, same shape as SOS-Games/jvm-mw. Root `gradlew layerCheck`. CLI: `gradlew :athenacl-cli:agentCli --args="help"`.
- Do not commit `build/`, `local.properties`, soundbanks (`.sf2`), or rendered audio (`.wav`, `.mid`). `samples/motifs/*.mid` and `samples/melodies/*.mid` are composed fixtures, not renders.
- Do not add a server, a socket, or an MCP mode. Agents use one-shot commands.
- Do not port Csound, SuperCollider, or athenaCL's GUI. Those are later optional layers.
- Generation must not read the wall clock, iterate an unordered map, or draw randomness except through the future `RandomSource`. `meta.durationMs` is wall-clock and is not part of a musical hash.

```bat
gradlew.bat test
gradlew.bat layerCheck
gradlew.bat layerCheck -Player=L0a
gradlew.bat :athenacl-cli:agentCli --args="help"
```

`gradlew` is the same entry on macOS and Linux. After `gradlew.bat :athenacl-cli:installDist`, the launcher is `athenacl-cli\build\install\athenacl\bin\athenacl.bat`.

## CLI (headless)

No window. The working directory is the repo root. Stdout is the result. Logs go to stderr only.

```bat
gradlew.bat :athenacl-cli:agentCli --args="help"
gradlew.bat :athenacl-cli:agentCli --args="help --json"
gradlew.bat :athenacl-cli:agentCli --args="help version"
gradlew.bat :athenacl-cli:agentCli --args="version"
gradlew.bat :athenacl-cli:agentCli --args="version -vv"
gradlew.bat :athenacl-cli:agentCli --args="version --json"
gradlew.bat :athenacl-cli:agentCli --args="batch checks/batch.jsonl"
gradlew.bat :athenacl-cli:agentCli --args="schema athenacl.result.version/1"
gradlew.bat :athenacl-cli:agentCli --args="schema athenacl.result.version/1 --out build/schema --dry-run"
gradlew.bat :athenacl-cli:agentCli --args="schema athenacl.result.version/1 --out build/schema"
gradlew.bat :athenacl-cli:agentCli --args="render samples/notes/c-major-scale.yaml --out build/l0 --formats mid,wav,events,txt"
gradlew.bat :athenacl-cli:agentCli --args="play samples/notes/c-major-scale.yaml --dry-run"
gradlew.bat :athenacl-cli:agentCli --args="describe-music build/l0/events.jsonl --roll"
gradlew.bat :athenacl-cli:agentCli --args="debug determinism samples/notes/c-major-scale.yaml --runs 2"
gradlew.bat :athenacl-cli:agentCli --args="debug determinism themes/fantasy-dungeon.yaml --runs 2 --fork-jvm"
gradlew.bat :athenacl-cli:agentCli --args="debug bisect --good build/l11/a --bad build/l11/b"
gradlew.bat :athenacl-cli:agentCli --args="diff themes/fantasy-dungeon.yaml themes/scifi-space.yaml"
gradlew.bat :athenacl-cli:agentCli --args="compat random --seed 7 --n 5"
gradlew.bat :athenacl-cli:agentCli --args="conformance list"
gradlew.bat :athenacl-cli:agentCli --args="conformance run --subset slice"
gradlew.bat :athenacl-cli:agentCli --args="conformance run --subset po.value"
gradlew.bat :athenacl-cli:agentCli --args="describe pos --group value"
gradlew.bat :athenacl-cli:agentCli --args="describe pos --group omde"
gradlew.bat :athenacl-cli:agentCli --args="describe textures"
gradlew.bat :athenacl-cli:agentCli --args="describe setclass 3-11"
gradlew.bat :athenacl-cli:agentCli --args="conformance run --subset po.omde,po.rhythm"
gradlew.bat :athenacl-cli:agentCli --args="conformance run --subset texture,clone"
gradlew.bat :athenacl-cli:agentCli --args="conformance run --subset sieve,generators"
gradlew.bat :athenacl-cli:agentCli --args="describe commands"
gradlew.bat :athenacl-cli:agentCli --args="conformance run --subset commands.a,xml"
gradlew.bat :athenacl-cli:agentCli --args="describe commands --group b"
gradlew.bat :athenacl-cli:agentCli --args="conformance run --subset commands.b"
gradlew.bat :athenacl-cli:agentCli --args="athena prompt --line \"tpsd 7\""
gradlew.bat :athenacl-cli:agentCli --args="po sample basketGen \"oc,(1,2,3)\" --n 6"
gradlew.bat :athenacl-cli:agentCli --args="athena run conformance/paths/c-major.ath --out build/l13"
gradlew.bat :athenacl-cli:agentCli --args="athena run conformance/demos/script02a.ath --out build/l14"
gradlew.bat :athenacl-cli:agentCli --args="debug inspect build/l0/events.jsonl --where pitch=E4"
gradlew.bat :athenacl-cli:agentCli --args="soundbank install generaluser --dry-run"
gradlew.bat :athenacl-cli:agentCli --args="describe instruments --soundbank generaluser"
gradlew.bat :athenacl-cli:agentCli --args="analyze build/l0"
gradlew.bat :athenacl-cli:agentCli --args="describe scale D-dorian"
gradlew.bat :athenacl-cli:agentCli --args="theory progression i bVII bVI bVII --key D-aeolian --voice-lead --out build/l2"
gradlew.bat :athenacl-cli:agentCli --args="theory modulate D-dorian F-lydian --method pivot"
gradlew.bat :athenacl-cli:agentCli --args="rhythm pattern x..x..x. --meter 6/8 --bars 2 --drum-grid"
gradlew.bat :athenacl-cli:agentCli --args="rhythm poly 3:2 --bars 2 --out build/l3p"
gradlew.bat :athenacl-cli:agentCli --args="theme validate samples/themes/poc.yaml"
gradlew.bat :athenacl-cli:agentCli --args="theme resolve --genre fantasy --state sneaking"
gradlew.bat :athenacl-cli:agentCli --args="render samples/themes/poc.yaml --timeline samples/timelines/poc.json --seed 42 --out build/l4"
gradlew.bat :athenacl-cli:agentCli --args="preset list"
gradlew.bat :athenacl-cli:agentCli --args="preset show fantasy"
gradlew.bat :athenacl-cli:agentCli --args="preset show scifi-space"
gradlew.bat :athenacl-cli:agentCli --args="preset audition reggae --bars 8 --seed 1"
gradlew.bat :athenacl-cli:agentCli --args="preset audition fantasy --bars 8 --seed 1 --analyze"
gradlew.bat :athenacl-cli:agentCli --args="render --genre fantasy --state sneaking --bars 16 --seed 1 --out build/l7c-s --formats events"
gradlew.bat :athenacl-cli:agentCli --args="theme explain themes/fantasy-dungeon.yaml"
gradlew.bat :athenacl-cli:agentCli --args="render themes/fantasy-dungeon.yaml --timeline samples/timelines/dungeon-tour.json --seed 1 --out build/l5 --formats mid,wav,events,txt"
gradlew.bat :athenacl-cli:agentCli --args="analyze build/l5 --by state"
gradlew.bat :athenacl-cli:agentCli --args="motif generate --key D-dorian --seed 3"
gradlew.bat :athenacl-cli:agentCli --args="motif transform m1 --invert --invert"
gradlew.bat :athenacl-cli:agentCli --args="motif import samples/motifs/theme.mid --out build/l7/m.yaml"
gradlew.bat :athenacl-cli:agentCli --args="melody generate --key E-minor --candidates 64 --seed 3 --explain"
gradlew.bat :athenacl-cli:agentCli --args="kit analyze samples/kits/analysis.yaml --out build/l7d"
gradlew.bat :athenacl-cli:agentCli --args="kit describe saw --kit analysis --out build/l7d"
gradlew.bat :athenacl-cli:agentCli --args="kit lint samples/themes/lint-clean.yaml --all-states"
gradlew.bat :athenacl-cli:agentCli --args="ensemble select --genre fantasy --state sneaking --seed 1 --kit analysis"
gradlew.bat :athenacl-cli:agentCli --args="debug probe flute --kit gm-lite --pitch C4 --velocity 80"
```

| Command | Use when |
| --- | --- |
| help | You need the example invocations, or you are checking that the CLI starts. |
| version | You need the tool version, the Java feature level, or whether Gervill is on this JDK. |
| schema | You need a published result schema on stdout, or you want an atomic write and `--dry-run` without rendering music. |
| render | You have a note-list YAML file and want `score.mid`, `score.wav`, `events.jsonl`, and `score.txt` under `--out`. `render --genre fantasy --state sneaking` plays that preset state; a single `--bars` number there is the bar count. |
| play | You want to hear a note list, an event log, or a WAV on the speakers. `--dry-run` does not open the device. |
| describe-music | You want one `bar=` line per bar from an event log, a piano roll (`--roll`), or the chord and key estimate. |
| debug | You want `debug determinism` (same event-log hash twice, optionally `--fork-jvm`; `--analyze` hashes a kit profile), `debug bisect` (first bar where two renders split), `debug inspect` (filter notes with `--where`), `debug check --loudness`, `debug check --stuck-notes`, `debug bar`, `debug trace --transitions`, `debug replay`, `debug fuzz-transitions`, `debug overhead`, or `debug probe` (one dry note). |
| diff | You want a semantic diff of two themes, or the first bar where two render directories split. `--fail-on-diff` exits 6 when they differ. |
| compat | You want CPython `random()` draws. `compat random --seed 7 --n 5` prints the five values the port must match. |
| conformance | You want `conformance list` to count the 31 pregui MIDI demo scripts, `conformance run` to check that each script has commands, `conformance run --subset slice` to compare the LineGroove beats to their golden MIDI, `conformance run --subset po.value` to sample the value generators, `conformance run --subset po.omde,po.rhythm` to sample the wave, breakpoint, and rhythm generators, `conformance run --subset texture,clone` to check the texture modules and the clone filters, `conformance run --subset sieve,generators` to check sieves, tunings, and generator fixtures, `conformance run --subset commands.a,xml` to check the first 55 commands and one project file, or `conformance run --subset commands.b` to check the other 48 command names. |
| po | You want `po sample basketGen "oc,(1,2,3)" --n 6` to walk a basket and print `values=1,2,3,1,2,3`. |
| athena | You want `athena run` to play a path or a LineGroove beat and check its MIDI against the golden file beside it, or `athena prompt --line` to resolve one command line. |
| batch | You want `batch checks/batch.jsonl` to run each JSONL command in one process. `--stop-on-error` returns at the first failure. `--json` prints one object per command. |
| soundbank | You want to install, list, or show GeneralUser GS or MuseScore_General. An `.sf3` file is rejected. |
| describe | You want `describe scale`, `describe chord`, `describe instruments` (287 presets and 13 drum kits in GeneralUser GS), `describe pos --group value` (45 value generators), `describe pos --group omde` (36 wave and breakpoint generators), `describe pos --group rhythm` (13 rhythm generators), `describe textures` (14 texture modules), `describe setclass 3-11` (a Forte set class), `describe commands` (the first 55 athenaCL commands), or `describe commands --group b` (the other 48). |
| analyze | You want EBU R128 integrated loudness and true peak for a WAV or a render directory. A swung render also prints `swing.measured=`. `analyze <dir> --by state` prints each state's offset from the contour. A motif render also prints `motif.reuse.per8bars=`. A directory with `events.jsonl` also prints `harmony.distinctRoots=`, `lead.overlaps=`, and the other phrase counts. `harmony.json` adds `harmony.style=`. |
| theory | You want `theory progression` to voice-lead roman numerals, or `theory modulate` to plan a key change. |
| rhythm | You want `rhythm pattern` to print a drum grid, or `rhythm poly` to write 3-against-2 into `events.jsonl`. |
| theme | You want `theme validate` to check a theme file, `theme explain` to print its states, or `theme resolve --genre fantasy --state sneaking` to print one merged state. |
| preset | You want `preset list` for the genre presets, `preset show fantasy` or `preset show scifi-space` for instruments and GM limits, or `preset audition reggae --bars 8 --seed 1` to play eight bars and print the character. `preset audition --all --bars 16 --seed 1 --analyze` exits 6 when any preset stays on fewer than three bass notes or the lead overlaps. |
| motif | You want `motif generate` to grow a short idea, `motif transform` to turn it over, or `motif import` to read a MIDI melody. |
| melody | You want `melody generate` to rank hook candidates, `melody score` to rate a MIDI line, `melody rate` to write an A/B row, or `melody fit` to turn ratings into weights. `melody score --progression harmony.json` reads that file's key and roman numerals. |
| adapt | You want the scheduler snapshot: `state=`, `intensity=`, `bar=`, `sched.lookaheadMs=`, and `cpu.msPerBar=`. |
| instruments | You want `instruments list`, `show`, `audition`, or `compare` for a named instrument in a kit. |
| kit | You want `kit validate` to check sources, licenses, and slots, `kit analyze` to probe a kit and write a profile, `kit describe` to print one instrument's verdict, `kit lint` to check a resolved theme, `kit fit` to score one role, or `kit compat` to compare a pair. |
| ensemble | You want `ensemble select` to fill each role from a kit profile. The same seed picks the same instruments. |
| credits | You want `credits` to write `CREDITS.md` for the soundfonts a theme uses. |

Shared flags, in front of the command or after it: `--json`, `--dry-run`, `--strict`, `-v`, `-vv`, `--log-level <error|warn|info|debug|trace>`, `-h`.

`schema <id>` with no `--out` prints the schema document on stdout (so a redirect works). `--json` wraps that document in a Result instead. `--out <dir>` prints one `key=value` line and writes `<id-with-slash-as-underscore>.json` under that directory.

Published schema ids:

- `athenacl.result.help/1`
- `athenacl.result.version/1`
- `athenacl.result.schema/1`
- `athenacl.result.error/1`
- `athenacl.result.render/1`
- `athenacl.result.play/1`
- `athenacl.result.describe-music/1`
- `athenacl.result.determinism/1`
- `athenacl.result.bisect/1`
- `athenacl.result.diff/1`
- `athenacl.result.overhead/1`
- `athenacl.result.inspect/1`
- `athenacl.result.soundbank/1`
- `athenacl.result.instruments/1`
- `athenacl.result.analyze/1`
- `athenacl.result.check/1`
- `athenacl.result.scale/1`
- `athenacl.result.chord/1`
- `athenacl.result.progression/1`
- `athenacl.result.modulate/1`
- `athenacl.result.rhythm/1`
- `athenacl.result.poly/1`
- `athenacl.result.validate/1`
- `athenacl.result.explain/1`
- `athenacl.result.resolve/1`
- `athenacl.genre/1`
- `athenacl.state/1`
- `athenacl.theme/1`
- `athenacl.result.trace/1`
- `athenacl.result.bar/1`
- `athenacl.result.replay/1`
- `athenacl.result.preset/1`
- `athenacl.result.preset-list/1`
- `athenacl.result.preset-audition/1`
- `athenacl.result.motif/1`
- `athenacl.result.melody/1`
- `athenacl.result.fuzz/1`
- `athenacl.result.adapt/1`
- `athenacl.result.instrument/1`
- `athenacl.result.kit/1`
- `athenacl.result.ensemble/1`
- `athenacl.instrument-profile/1`
- `athenacl.result.credits/1`
- `athenacl.result.compat/1`
- `athenacl.result.conformance/1`
- `athenacl.result.conformance-run/1`
- `athenacl.result.po/1`
- `athenacl.result.pos/1`
- `athenacl.result.textures/1`
- `athenacl.result.setclass/1`
- `athenacl.result.commands/1`
- `athenacl.result.prompt/1`
- `athenacl.result.athena/1`
- `athenacl.result.batch/1`

When you add a command, add it to `CommandCatalog`, add a row to the table above, and add a block to the layer's `checks/L*.check`. `LayerCheckTest` fails if the table is missing a catalog command.

## Output

Text is the default. One record per line, `key=value`, with a leading kind word when the line is an error or a warning (`error code=NOT_FOUND path=argv[1] ...`). Values with spaces or punctuation are double-quoted. Paths use forward slashes. Stdout and stderr are UTF-8, including the piano-roll marks.

`help` without `--json` is the human page (examples, then one line per command), same idea as jvm-mw's debug CLI. It is not `key=value`.

`--json` prints exactly one Result document: `schema`, `ok`, `command`, `data`, `warnings`, `errors`, `meta`. `meta` is `version`, `seed` (always `0`; the note-list seed is in the event log and in `render` data), and `durationMs`. Logs never go on stdout. `help --json` puts the command list at `data.commands`. The event-log hash is SHA-256 of `events.jsonl` and is not `durationMs`.

A warning still exits 0. `--strict` turns any warning into exit 3 and sets `ok` to false.

## Exit codes

| Code | Meaning |
| --- | --- |
| 0 | Success. Warnings may be present. |
| 1 | Unexpected error. Stack trace on stderr. |
| 2 | Usage. Unknown command or bad flag. Message on stderr, help on stdout. |
| 3 | Invalid input. Also `--strict` when a warning was present. |
| 4 | Not found. A missing schema id, note list, or event log. `render` reports that on `argv[1]`. `debug determinism` and `debug inspect` report it on `argv[2]`. `debug bisect` and `diff` report a missing directory or theme the same way. |
| 5 | Runtime failure. A note list or `--out` directory that cannot be read or written, or a MIDI/WAV render that threw. |
| 6 | Check failed. `debug determinism` returns this when two runs do not share a hash. `diff --fail-on-diff` returns this when the two sides differ. `render --debug provenance` returns this when the observer changes the event log. |
| 7 | Environment. `render` with `wav` returns this when Gervill's offline stream cannot be opened. `version` still reports a missing Gervill as `gervill=absent` and a warning, so that command can still answer. |

Unknown command stderr starts with `unknown command: <what you typed>`. A close name adds `Did you mean ...?` in the fix hint.

## Logs

`-v` is debug. `-vv` is trace. `--log-level` wins when both are set. Default is warn, so a normal command's stderr is empty.

Lines look like `TRACE athenacl.cli start command=version`. Categories reserved for later layers: `athenacl.director`, `athenacl.scheduler`, `athenacl.transition`, `athenacl.gen`, `athenacl.render`. L0a only uses `athenacl.cli`.

## Dry-run and atomic writes

`--dry-run` is for commands that write. `schema --out <dir> --dry-run` prints `action=write`, `path=`, `bytes=`, `changed=`, and `dryRun=true`, and does not create the directory. A real write fills a temp file in the destination folder and renames it into place. A second write of the same bytes prints `changed=false` and leaves the modified time alone.

`--out` is a directory the command owns. Inputs are not modified. Do not point it at the repo root.

## Batch

`batch checks/batch.jsonl` reads one command per line: `{"id":1,"cmd":"version"}` or `{"id":2,"cmd":"describe","args":["scale","D-dorian"]}`. `cmd` may include spaces. `args` is a list of strings, or an object of flags (`{"seed":1}` becomes `--seed 1`). Each command starts clean. The text is `id=` `exit=` and then that command's own stdout, then `commands=` `failed=`. `--json` prints one object per command (`id`, `exit`, `ok`, `stdout`) and skips the summary. `--stop-on-error` does not run the rest. A missing file is exit 4. A line that is not JSON is exit 3. The batch exit code is the first command that failed, or 0.

## Layer checks

`gradlew layerCheck` runs every `checks/L*.check`. `-Player=L0a` (or `0a`) runs one file. The runner starts the CLI in a child process from the repo root. A failing check fails the Gradle task.

Blocks are split by a line that is only `---`. Blank lines and `#` comments are ignored. A directive is `key: value`. These flags take no value: `stderr-empty`, `stdout-empty`, `json-document`.

| Directive | Meaning |
| --- | --- |
| name | Label printed as PASS or FAIL. |
| args | Arguments after `athenacl`. Required once. Double quotes keep a value that contains a space, such as `"D dorian"`. |
| exit | Expected process status. Required once. |
| stdout-contains | Stdout must contain this literal. |
| stdout-not-contains | Stdout must not contain this literal. |
| stdout-regex | `find()` on stdout. |
| stdout-same-as | Run these args and require the same stdout. Use a command that does not write. |
| stderr-contains / stderr-regex / stderr-empty | Same idea for stderr. |
| json-document | Stdout is exactly one JSON value. |
| json-array-nonempty | Dot path to an array that is non-empty (`data.commands`). |
| json-equals | `path value`. Compared with the node's text. |
| json-regex | `path regex`. |
| stdout-count | `N literal`. Stdout must contain that literal exactly N times, non-overlapping. |
| capture | `name regex`. Stores regex group 1. The map lasts for one check file. |
| stdout-contains-capture | A later block's stdout must contain the captured value. |
| file-exists / file-absent | Path relative to the repo. `file-absent` may be a directory. |
| delete-before | Delete this path before the command. Must stay under `build/` and must not be `build` itself. |

`checks=` and `failed=` are the runner's last line.

## Later-layer rules (not wired in L0a)

Config uses the same triple as jvm-mw: a `local.properties` key, a `-D` property, and an env var. The soundbank path is `athenacl.soundbank`, `-Dathenacl.soundbank`, `ATHENACL_SOUNDBANK`. Debug knobs will be `athenacl.debug.NAME`, `-Dathenacl.debug.NAME`, `ATHENACL_DEBUG_NAME`. Do not invent a second scheme. Debug knobs are not read yet.

Copyright for themes and presets: borrow style (genre, tempo, meter, mode, groove, instrumentation, common progression types). Do not copy a melody, a distinctive riff, or lyrics. This is guidance, not legal advice.

## Class comments

Large classes get a short block immediately above `public class`. Not per line, and not on tiny records that carry no gotcha. Write for a person learning the tool: what the type does, where it sits, and the gotcha that bites. Plain English. Names the user types (`schema`, `--dry-run`, `version`) are fine. Do not write "port of" or "maps to" a Python module; that belongs in a future `docs/NAME_MAP.md`.

## Note lists and render

`samples/notes/c-major-scale.yaml` is eight quarter notes, two bars of 4/4, 120 bpm. Pitches are names (`C4` is MIDI 60). `beat` is a 1-based quarter-note position in the bar. `dur: "1/4"` is a quarter note at 960 ticks per quarter.

`render <file> --out <dir> --formats mid,wav,events,txt` writes exactly the files you asked for:

- `mid` → `score.mid`
- `wav` → `score.wav` (Gervill, reverb and chorus off)
- `events` → `events.jsonl` (canonical JSONL; this is the hash)
- `txt` → `score.txt`

The summary line is `files= notes= bars= changed= hash=` plus `dryRun=`. `changed=true` when any requested file's bytes differ. A second render of the same notes is `changed=false` and the same hash. `--seed` defaults to 0. `--dry-run` plans the writes and does not create `--out`.

`describe-music <events.jsonl> --roll` prints `bar= notes= range=` and a roll where one column is a 16th note: `●` starts a note, `─` holds it, `·` is empty. A quarter note on beat 1 starts `C4 |●`. Channel 9 is a drum grid under that roll. When the notes form a chord, the summary also ends with `chord=`, `key=`, and `conf=` (cosine similarity of the whole piece against a mode profile).

`debug determinism <notes.yaml> --runs 2` hashes the event log that many times and does not write audio. `--fork-jvm` does each run in a new process. `debug inspect <events.jsonl> --where pitch=E4` prints only the matching `note id=` lines. `render --debug provenance` writes `provenance.jsonl` beside the event log and prints the same `hash=` as `--debug off`. `--debug trace` writes `trace.jsonl` the same way.

The summary line also ends with `soundbank=`. A missing `--soundbank` file still exits 0 and adds `warning=SOUNDBANK_FALLBACK`. The WAV then uses the JDK default bank, so a second render of the same notes stays byte-for-byte the same.

`play <notes.yaml|events.jsonl|wav|dir>` sends that audio to the speakers and waits until it finishes. A directory uses `score.wav`. A note list or event log is rendered in memory first, with the same soundbank rules as `render`. `--dry-run` prints `action=play seconds= dryRun=true` and does not open the device. No speakers is exit 7. This is not the libGDX window.

## Soundbanks and loudness

`soundbank install generaluser` downloads GeneralUser GS into `~/.cache/athenacl-j/soundbanks/` (or `--cache <dir>`). The file is 32319396 bytes. The SHA-256 is pinned. `--dry-run` prints `action=download size=32319396 sha256=` and writes nothing. A second install prints `status=present changed=false`. `soundbank list` and `soundbank show generaluser` report that cache.

`describe instruments --soundbank generaluser` prints `instruments=287 drumkits=13`.

`analyze <dir>` reads `score.wav` and prints `loudness.integrated=` in LUFS, `truePeak=` in dBTP, and `shortTerm=`. `debug check <dir> --loudness` prints the same line. Integrated loudness stays within 0.2 LU of ffmpeg's `ebur128` filter. A directory that has `events.jsonl` and no WAV still prints the phrase counts. `harmony.distinctRoots` is the smallest set of bass pitch classes in any eight bars. `lead.overlaps` is how many lead notes start before the previous one ends.

The download URL is a pinned commit until this project hosts its own copy. Do not point the installer at schristiancollins.com.

## Theory

`describe scale "D dorian"` prints `scale=D-dorian pcs=D,E,F,G,A,B,C`. `D-dorian` is the same key. `major` means ionian and `minor` means aeolian.

`describe chord Dm7 --key "D dorian"` prints `chord=Dm7 roman=i7 pcs=D,F,A,C`. Roman numerals are counted from the major scale, so in D aeolian `bVII` is C major and `bVI` is B-flat major. Upper case is major and lower case is minor.

`theory progression "i bVII bVI bVII" --key "D aeolian" --voice-lead --out build/l2` writes `events.jsonl`, one whole-note chord per bar in 4/4 at 120 bpm. The summary is `chords= maxMotion= parallels= hash=`. `--voice-lead` keeps each leap within a few semitones and avoids parallel fifths and octaves. A second run of the same progression is `changed=false` with the same hash. `--dry-run` prints the plan and does not create `--out`.

`describe-music` on that log prints `chord=Dm` and `key=D-aeolian` on bar 1, with `conf=` at least 0.70. When the log has a meter event, the bar line also ends with `meter=` and `grouping=`.

`theory modulate "D dorian" "F lydian" --method pivot` prints `pivot=F` and `bars=2`. `direct` lands on the next bar. `mixture` borrows one chord from the parallel key. A transition `keyPolicy` is `themeKey`, `keep`, `relative`, or `parallel`. `relative` on a major key is its relative minor. On another mode it is the major key with the same notes. A state `meter` replaces the theme meter from the bar that state starts, and `harmony.phraseBars` is how often the chord phrase cadences. `samples/themes/modulation.yaml` is D dorian until bar 9 (F lydian) and 7/8 from bar 12.

## Rhythm

`rhythm pattern x..x..x. --meter 6/8 --bars 2 --drum-grid` repeats the pattern once per bar. `x` is a hit and `.` is a rest. The summary is `bars= onsets= meter= grouping=`. The grid row is `kick |` with the same piano-roll marks as `describe-music`. 6/8 groups as `3+3`.

`render samples/notes/tie-across-bar.yaml --out build/l3` is one note held for six quarter-beats. The summary appends `maxDurBeats=6` after `soundbank=`. The note's end can sit in a later bar. `describe-music` draws that hold as `─` in the next bar. `debug check <dir> --stuck-notes` prints `stuck=0` when every note has an end and nothing overlaps. Overlap exits 6.

`rhythm poly 3:2 --bars 2 --out build/l3p` writes `events.jsonl` only. Three pulses against two, both on the downbeat, so the summary has `layers=2 alignedAtBar=1,2`. `--dry-run` does not create `--out`.

`render samples/notes/swing.yaml --groove "swing=0.62" --out build/l3s` moves eighths that sat halfway through a quarter. `0.50` is straight. The summary and `analyze` of that directory print `swing.measured=` to two decimals. A directory with no off-beat pair leaves that field off.

## Director

`theme validate samples/themes/broken.yaml` exits 3 when a transition names a missing state. The line includes `error code=THEME_UNKNOWN_STATE`, `path=/transitions/rules/0/to`, and `fix=`.

`render samples/themes/poc.yaml --timeline samples/timelines/poc.json --seed 42 --out build/l4` plays sixteen bars. The summary is `bars=16 states=peaceful,sneaking,combat transitions=2 hash=`. It also writes `control.jsonl`. State changes wait for the next bar line. `debug trace build/l4 --transitions` prints `appliedTick=` for each one.

`render samples/themes/poc.yaml --seed 42 --bars 9-10 --at "9 state combat 0.6"` prints the same events as bars 9–10 of that full render. `debug bar samples/themes/poc.yaml --seed 42 --bar 9` prints `bar=9 state=combat` and one `note id= layer= seed= why=` line per note. `debug replay build/l4/control.jsonl` prints the same `hash=` as the render.

## Presets and loudness

`preset show fantasy` prints `preset=fantasy` and the GM instruments (harp, choir, horn, flute, fiddle, taiko). `theme explain themes/fantasy-dungeon.yaml` prints `preset=fantasy` and the design contour, including `loudness.combat=+4.0`. `theme validate` on that file prints `states=6` and `warnings=0`.

`render themes/fantasy-dungeon.yaml --timeline samples/timelines/dungeon-tour.json --seed 1 --out build/l5` plays fourteen bars at one tempo. The summary lists `states=sneaking,peaceful,danger,combat,trading,partying`. The WAV is trimmed so `analyze build/l5 --by state` prints each state's measured offset within 1 LU of the contour, and `loudness.spread=` stays at or under 6.0. The event-log hash does not include that trim.

`describe-music build/l5/events.jsonl --bars 1-4` estimates the key from those bars only. The line ends with `layers=`, and the sneaking bars include `drone`. The mode is dorian, aeolian, or phrygian.

## Motifs

`motif generate --key "D dorian" --seed 3` prints `motif=m1`, `notes=` between 2 and 8, and `contour=`. The same seed grows the same idea. `motif transform m1 --invert --invert` prints `identical=true`.

The lead is a four-bar phrase. The motif is that phrase's opening idea, fitted to the chord of the bar. `motif.every` is how many phrases pass between statements of the unturned idea. `1` states it every phrase. `themes/fantasy-dungeon.yaml` sets it to 4. A period's answer starts with the same idea as its question. Coming back to a state starts the phrase over. `debug explain` on a lead note prints `motif=m1:`. `analyze` of that render prints `motif.reuse.per8bars=`.

`motif import samples/motifs/theme.mid --out build/l7/m.yaml` reads the composed melody and prints `motifs=1` and `notes=`.

## Hooks

`melody generate --key "E minor" --chords "i VI VII i" --candidates 64 --seed 3 --explain` prints one `cand=` line per candidate and then `chosen=`, `hook=h1`, `contour=` (arch, fall, rise-and-resolve, or call-response), and `form=` (`sentence` or `period`). The same seed prints the same text. `melody score` rates a MIDI file. `--mode phrase` allows a twelfth and checks the cadence only at the end of a four-bar phrase; the line then includes `rhythmVariety=` and `barPattern=`. `--progression harmony.json` uses that file's key and the roman numeral of each bar. An eighth in the MIDI file stays an eighth. `melody rate --choose a` writes one ratings line without asking. `melody fit` turns those rows into weights; the header date is `1970-01-01` and the hash covers the weight lines only.

A preset turns hooks on unless the theme file has its own `hooks:` block, which replaces that default. The picked hook is still motif `m1`, so `PhrasePlan` keeps building the sentence, and `debug explain` adds `hook.cand=` and `score=` on those lead notes. The choice is made once per state before the bar loop. `themes/fantasy-dungeon.yaml` sets the block and asks for 8 candidates. A preset theme with no block uses 64.

`samples/melodies/` holds short public-domain lines transcribed for the critic: Amazing Grace, Twinkle, and a random walk. They are fixtures, not renders.

## Instrument kits

`instruments/gm-lite.yaml` is the default kit (GeneralUser GS). `instruments/gm-full.yaml` extends it and plays every named instrument from MuseScore_General, falling back to GeneralUser when a patch is missing. `soundbank install musescore-general` downloads that SF2. An SF3 file is rejected with `SOUNDBANK_SF3_UNSUPPORTED` before Gervill opens it. `kit validate` checks the checksum, the license, and that two instruments did not receive the same slot. `instruments compare` prints `loudness.probe=` after each instrument is brought to about -20 LUFS, and `raw.deltaLu=` for the gap before that. `render --kit gm-full` puts each named instrument on its own slot.

`themes/fantasy-dungeon.yaml` names `instruments.kits: [gm-lite]` and the preset maps each layer to a kit instrument (`strings`, `piano`, `horn`, `flute`, `cello`, and the rest). A render of that theme normalizes those instruments, then trims each state onto the loudness contour. The event-log hash stays the program-change log, not the trim.

## Instrument analysis

`kit analyze samples/kits/analysis.yaml` probes the CC0 fixture bank and writes a profile. The summary is `analyzed=7 bad=3`. A second run prints `cached=true` and the same `hash=`. The profile lives under `--out`, or in `instruments/profiles/` when `--out` is left off. It is not recomputed while the game is playing. `kit describe inharmonic --kit analysis` prints `verdict=bad` and `reasons=inharmonicity`. `kit describe saw` prints `verdict=ok`, `tags=bright`, and `pitch.errorCents` within 2 cents. `clipped` names `clipping` and `loopclick` names `loopClicks`. `kit describe pad --text` is at most eight lines and says the attack is slow. An unknown name exits 4 and suggests a close name. `debug determinism --analyze` hashes that profile, including with `--fork-jvm`. `debug probe flute --kit gm-lite --pitch C4` prints one probe's measurements. Thresholds are in `instruments/analysis.yaml`. Tags are listed in `docs/instrument-tags.md`.

`kit lint samples/themes/lint-clean.yaml --all-states` checks the resolved instruments and prints `errors=0 warnings=0`. A bad instrument prints `finding=INSTR_BAD` and `fix=try`, and the command exits 3. `from=` names the file that chose the instrument. `kit fit pluck --role pad --kit analysis` prints a fitness below 0.5. The same pluck with `--role arp` is at least 0.7. `kit compat saw saw --kit analysis --registers C4..C5,C4..C5` prints `verdict=clash`. `theme resolve --with-lint` adds the same findings when a theme file is given. A kit that has no profile is skipped (`lint.skipped=no-profile`) instead of rendering GeneralUser GS.

`ensemble select --genre fantasy --state sneaking --seed 1 --kit analysis` fills each declared role from that profile. None of the picks is a bad instrument, and `pairs.maxRoughness<=` stays inside the constraint. The same seed prints the same text. `--seed 2` may pick another instrument from the top few. `ensemble select samples/themes/pin.yaml --state combat` prints `from=pin` for the pinned lead. A kit with no profile prints `ensemble.fallback=fixed` and keeps the fixed names. `theme resolve --seed 1 --explain` adds `source=ensemble:auto` on the chosen instruments. Fantasy and sci-fi ask for `select: auto`. The other genres stay on their fixed instruments until a profile exists.

## Beat switches

Entering combat or danger waits for the next beat and drops notes already queued after that beat. Other states still wait for the bar. `render themes/fantasy-dungeon.yaml --timeline samples/timelines/ambush.json --seed 1 --out build/l6` lands on beat 3 of bar 4. `debug trace build/l6 --transitions` prints `quantize=beat`, `retracted=`, and `latency.ms=`. `debug check build/l6 --stuck-notes` prints `stuck=0`. `debug fuzz-transitions themes/fantasy-dungeon.yaml --runs 1000 --seed 1` prints `lateTransitions=0`.

`adapt themes/fantasy-dungeon.yaml --seed 1` prints `state=`, `intensity=`, `bar=`, `sched.lookaheadMs=150`, `cpu.msPerBar=`, `kit=gm-lite`, and `instruments=`. `gradlew :athenacl-gdx:run` opens a window that plays the ambush, then a button for each other track and each genre preset. The window renders through `gm-full` (MuseScore_General). The combat theme's kit is rendered on a background thread and takes over at the next bar from bar 8. F3 writes `build/athenacl-snapshot.txt`. F4 hides the line.

## Overlays and wind-downs

`--at "4 overlay lowHealth 1"` turns on an overlay at that bar. It does not replace a state change at the same time. `themes/fantasy-dungeon.yaml` defines `lowHealth` as a quiet heartbeat, capped at 1 LU. `analyze` of that render prints `loudness.overlayDelta=`.

A named `from` beats a wildcard, so entering combat or danger still waits for the next beat. Leaving combat waits for the bar and plays `bridge=windDown`. `debug trace --transitions` prints `bridge=` on that line. A jump larger than 4 LU writes a CC11 ramp; `debug check --loudness` exits 6 when that ramp is missing. A render with no control log only measures the WAV.

`clock.frameMs` snaps a request onto a fixed frame before the musical grid. It is 0 unless the theme sets it. A stinger with `degree` follows the state's key, and `cooldownBars` skips a repeat inside that many bars. A pitch-only stinger is unchanged. The window still preloads the next kit and swaps it at a bar line.

## Sci-fi

`preset show scifi-space` prints `preset=scifi-space` and the synth set (pad, square, saw, bass, celesta, 808). `themes/scifi-space.yaml` is that preset at 100 bpm in Lydian. There is no whole-tone scale, so the raised fourth stands in for it. Combat stays at 100; the drive is a sixteenth-note arp, not a tempo jump. Other states arpeggiate in eighths. The lead rhythm is a Markov pulse and the arp keeps the steps a sieve allows. Both name the texture `LineGroove`. `describe-music` prints `arp.grid=` and `programs=` for the bars you asked about.

`render themes/fantasy-dungeon.yaml --at "8 theme themes/scifi-space.yaml" --bars 1-16` prints `themeSwitch.bar=9`. A theme change asked for on a bar line still waits until the next bar. The window still preloads the combat kit and swaps that at a bar until a track button is pressed. Ambush, Dungeon, Combat, Party, Trading, Space, and Space combat each start that piece from the beginning and repeat it until another button is pressed. The window shows where you are in the current pass.

## Debug depth

`debug bisect --good build/l11/a --bad build/l11/b` prints `firstDivergentBar=` for the first bar whose notes differ, and `firstControl=` when the bad control log's shortest prefix is what makes that bar match the bad render. `diff themes/fantasy-dungeon.yaml themes/scifi-space.yaml` prints one `changed=` line per resolved leaf, including `changed=/states/combat/key`. Combat tempo is 100 in both presets, so that leaf is the key, not the tempo. `diff` of two render directories prints `firstDivergentBar=` as well. `debug overhead` prints `identical=` and `ratio=` for one encode with debug off and one with a provenance observer. The times are wall-clock and are not part of a hash.

## Port foundation

`compat random --seed 7 --n 5` prints `values=` as five CPython `random()` draws. The same seed also matches `randint`, `choice`, and `shuffle`. `gauss`, `expovariate`, and `betavariate` stay within 1e-15 of CPython. `Py.round` is half-to-even. `//` and `%` round toward negative infinity. Argument strings such as `pt,(c,2)` are quoted and then read as a Python row. `conformance/demos` holds the 31 MIDI demos, each seeded with `tpsd 7` and `tmsd 7` and closed with `eln`. `conformance list` prints `scripts=31`. `conformance run` prints `missing=0` when every file has commands. `conformance run --subset slice` plays the LineGroove beats and prints `pass=` and `fail=0` when each MIDI file matches the golden beside the script.

## Path slice

`athena run conformance/paths/c-major.ath --out build/l13` plays a path with no texture. `pin` names the path and lists pitch groups (`c4`, `d$4` for D flat, `f#4`, `g~4` for a quarter tone). Each group is a chord. `total` is the length in seconds; without it, each group lasts one second. `pidf` sets the weights. `eln` writes `<stem>.mid`. The summary includes `bytesEqualGolden=true` when that file matches the golden MIDI beside the script. A script that sets an amplitude, such as `conformance/demos/script01a.ath`, still exits 3 with `error code=ATHENA_TEXTURE`. Set-class names are not in this slice.

## Line groove

`po sample basketGen "oc,(1,2,3)" --n 6` prints `values=1,2,3,1,2,3`. `oc` walks the list and then starts again. `athena run conformance/demos/script02a.ath --out build/l14` plays the basic beat: three drums on MIDI channel 10, twenty seconds, tempo 120. `tpsd` seeds the shuffle basket. `tmsd` seeds the sustain draw and the amplitude. Choosing from a one-item list still draws bits. Creating each instrument scores it twice and throws those notes away, first with the default pulse and then with the rhythm from `tie r`. `eln` scores it a third time, and that pass is the file. The generators are not reseeded between those passes. A pulse with accent 0 is a rest and does not draw an amplitude. The note in the file lasts the sustain. `conformance run --subset slice` checks `script02a` and `script02b` and prints `pass=2 fail=0`. `script02b` shuffles the snare multipliers with `rp`.

## Value generators

`describe pos --group value` prints `count=45` and one `po=` line per generator. The private operator base is not a generator, so the list is the public 45. `conformance run --subset po.value` samples each one six times with both generators seeded to 7 and prints `pass=45 fail=0`. The golden is `conformance/po/value.txt`. `iterateHold` and `iterateSelect` refresh on the first event the way Python 2 did, when a missing refresh counted as less than every number.

## Waves, breakpoints, and rhythm

`describe pos --group omde` prints `count=36`. The plan's 44 counted eight private bases; those bases are not generators. `describe pos --group rhythm` prints `count=13`. `conformance run --subset po.omde,po.rhythm` samples each public generator six times with both streams seeded to 7 and prints `pass=49 fail=0`. The goldens are `conformance/po/omde.txt` and `conformance/po/rhythm.txt`. Gauss draws on the parameter stream. The other distributions draw on the texture stream. `iterateRhythmHold` treats a missing refresh as less than the event count, the way Python 2 did. A pulse compared with nothing uses identity.

## Textures and clones

`describe textures` prints `count=14`, one line per texture module, including LineGroove. `conformance run --subset texture,clone` checks those modules, the 22 public texture-static switches, the 2 public clone-static switches, and the 15 public clone filters. It prints `pass=53 fail=0`. The golden is `conformance/po/texture-clone.txt`. The plan's 27, 3, and 17 counted private bases; those bases are not switches or filters. A selector written `oc` is stored as ordered cyclic. Clone filters see the values 1 through 6. An anchor multiply shifts by the lowest or highest value, scales, then shifts back.

## Set classes, sieves, and generators

`describe setclass 3-11` prints `forte=3-11`, `pcs=0,3,7`, and `icv=0,0,1,1,1,0`. That is the major triad. `3-11B` is the inversion, `0,4,7`. The table has 351 forms. `conformance run --subset sieve,generators` checks five sieves over the integers 0 through 23, four tunings, six steps of the logistic map, the permutations of `1,2,3`, and three set classes. It prints `pass=14 fail=0`. The golden is `conformance/po/sieve-generators.txt`. A sieve `3@2` keeps the remainder 2. `&` is intersection and `|` is union.

## Commands and project files

`describe commands` prints `count=55` and one `command=` line per name, from `w` through `TCe`. The source lists 104 command names. This half is the ones that come first. `conformance run --subset commands.a,xml` checks that list and one project file. It prints `pass=56 fail=0`. The goldens are `conformance/po/commands-a.txt` and `conformance/po/project.xml`. The file has three sections under `athenaObject`. Keys are alphabetical. A `&` in a value is written as `&amp;`. A voice library is tagged `pi`.

## Commands and the prompt

`describe commands --group b` prints `count=48`, from `TCcp` through `menu`. `describe commands --group all` prints `count=103`. The source assigns a name 104 times, and `AUpc` is assigned twice, so the distinct names are 103. `conformance run --subset commands.b` prints `pass=48 fail=0`. The golden is `conformance/po/commands-b.txt`. `athena prompt --line "tpsd 7"` prints `command=TPsd args=7`. `?` is help, `!` is shell, `q` is quit, `man` is help, and `pi` opens the path menu. `help --json` lists every name under `athenaCommands`. The line is resolved and not run.

## Genre presets

`preset list` prints `presets=25`, the two starter presets plus fourteen genres and nine world packs. `preset audition reggae --bars 8 --seed 1` prints `character=skank` and `notes=` above 0. `preset audition --all --bars 16 --seed 1 --analyze` prints `presets=25` and `fail=0` when every peaceful state moves to at least three bass notes and the lead does not overlap. `render --genre fantasy --state sneaking --bars 16` writes `events.jsonl` and `harmony.json`; `analyze` of that directory prints `drone.present=true` and `harmony.style=pedal`. Combat prints `drone.present=false` and at least one dominant cadence. `hijaz` plays E phrygian dominant, a 12-tone stand-in for that maqam. Hirajoshi, yo, pentatonic, pelog, and slendro name the five-note set and play the seven-note scale that contains it. These packs are not a performance of that tradition.

## Not in L21

Playing the other demo scripts, the interactive prompt, and the command bodies. Filter sweeps, sidechain, bitcrush, vinyl crackle, and sung lyrics. Noise temperaments, spectral pitch, sieve complement and exclusive-or, and the event loops of the other textures. Markov, grammar, and automata stay on the value-generator samples. Do not add them in a drive-by change.
