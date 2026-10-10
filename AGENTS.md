# AGENTS

Instructions for AI coding agents working in **jvm-athenacl**.

## Project

GPL-3.0-or-later Java 21 rewrite of athenaCL as an adaptive game-music engine for libGDX desktop. Package `io.github.sosgames.athenacl`. Layer plan: [docs/research/05-project-plan.md](docs/research/05-project-plan.md). L3 (rhythm, meter, swing, ties) is in the tree. Do not start L4 until the user asks.

- Java 21 (`options.release = 21`). Versions live in `gradle.properties`.
- Groovy Gradle, same shape as SOS-Games/jvm-mw. Root `gradlew layerCheck`. CLI: `gradlew :athenacl-cli:agentCli --args="help"`.
- Do not commit `build/`, `local.properties`, soundbanks (`.sf2`), or rendered audio (`.wav`, `.mid`).
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
gradlew.bat :athenacl-cli:agentCli --args="schema athenacl.result.version/1"
gradlew.bat :athenacl-cli:agentCli --args="schema athenacl.result.version/1 --out build/schema --dry-run"
gradlew.bat :athenacl-cli:agentCli --args="schema athenacl.result.version/1 --out build/schema"
gradlew.bat :athenacl-cli:agentCli --args="render samples/notes/c-major-scale.yaml --out build/l0 --formats mid,wav,events,txt"
gradlew.bat :athenacl-cli:agentCli --args="play samples/notes/c-major-scale.yaml --dry-run"
gradlew.bat :athenacl-cli:agentCli --args="describe-music build/l0/events.jsonl --roll"
gradlew.bat :athenacl-cli:agentCli --args="debug determinism samples/notes/c-major-scale.yaml --runs 2"
gradlew.bat :athenacl-cli:agentCli --args="debug inspect build/l0/events.jsonl --where pitch=E4"
gradlew.bat :athenacl-cli:agentCli --args="soundbank install generaluser --dry-run"
gradlew.bat :athenacl-cli:agentCli --args="describe instruments --soundbank generaluser"
gradlew.bat :athenacl-cli:agentCli --args="analyze build/l0"
gradlew.bat :athenacl-cli:agentCli --args="describe scale D-dorian"
gradlew.bat :athenacl-cli:agentCli --args="theory progression i bVII bVI bVII --key D-aeolian --voice-lead --out build/l2"
gradlew.bat :athenacl-cli:agentCli --args="rhythm pattern x..x..x. --meter 6/8 --bars 2 --drum-grid"
gradlew.bat :athenacl-cli:agentCli --args="rhythm poly 3:2 --bars 2 --out build/l3p"
```

| Command | Use when |
| --- | --- |
| help | You need the example invocations, or you are checking that the CLI starts. |
| version | You need the tool version, the Java feature level, or whether Gervill is on this JDK. |
| schema | You need a published result schema on stdout, or you want an atomic write and `--dry-run` without rendering music. |
| render | You have a note-list YAML file and want `score.mid`, `score.wav`, `events.jsonl`, and `score.txt` under `--out`. |
| play | You want to hear a note list, an event log, or a WAV on the speakers. `--dry-run` does not open the device. |
| describe-music | You want one `bar=` line per bar from an event log, a piano roll (`--roll`), or the chord and key estimate. |
| debug | You want `debug determinism` (same event-log hash twice), `debug inspect` (filter notes with `--where`), `debug check --loudness`, or `debug check --stuck-notes`. |
| soundbank | You want to install, list, or show the GeneralUser GS soundbank. |
| describe | You want `describe scale`, `describe chord`, or `describe instruments` (287 presets and 13 drum kits in GeneralUser GS). |
| analyze | You want EBU R128 integrated loudness and true peak for a WAV or a render directory. A swung render also prints `swing.measured=`. |
| theory | You want `theory progression` to voice-lead roman numerals into `events.jsonl`. |
| rhythm | You want `rhythm pattern` to print a drum grid, or `rhythm poly` to write 3-against-2 into `events.jsonl`. |

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
- `athenacl.result.inspect/1`
- `athenacl.result.soundbank/1`
- `athenacl.result.instruments/1`
- `athenacl.result.analyze/1`
- `athenacl.result.check/1`
- `athenacl.result.scale/1`
- `athenacl.result.chord/1`
- `athenacl.result.progression/1`
- `athenacl.result.rhythm/1`
- `athenacl.result.poly/1`

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
| 4 | Not found. A missing schema id, note list, or event log. `render` reports that on `argv[1]`. `debug determinism` and `debug inspect` report it on `argv[2]`. |
| 5 | Runtime failure. A note list or `--out` directory that cannot be read or written, or a MIDI/WAV render that threw. |
| 6 | Check failed. `debug determinism` returns this when two runs do not share a hash. |
| 7 | Environment. `render` with `wav` returns this when Gervill's offline stream cannot be opened. `version` still reports a missing Gervill as `gervill=absent` and a warning, so that command can still answer. |

Unknown command stderr starts with `unknown command: <what you typed>`. A close name adds `Did you mean ...?` in the fix hint.

## Logs

`-v` is debug. `-vv` is trace. `--log-level` wins when both are set. Default is warn, so a normal command's stderr is empty.

Lines look like `TRACE athenacl.cli start command=version`. Categories reserved for later layers: `athenacl.director`, `athenacl.scheduler`, `athenacl.transition`, `athenacl.gen`, `athenacl.render`. L0a only uses `athenacl.cli`.

## Dry-run and atomic writes

`--dry-run` is for commands that write. `schema --out <dir> --dry-run` prints `action=write`, `path=`, `bytes=`, `changed=`, and `dryRun=true`, and does not create the directory. A real write fills a temp file in the destination folder and renames it into place. A second write of the same bytes prints `changed=false` and leaves the modified time alone.

`--out` is a directory the command owns. Inputs are not modified. Do not point it at the repo root.

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

`debug determinism <notes.yaml> --runs 2` hashes the event log that many times and does not write audio. `debug inspect <events.jsonl> --where pitch=E4` prints only the matching `note id=` lines.

The summary line also ends with `soundbank=`. A missing `--soundbank` file still exits 0 and adds `warning=SOUNDBANK_FALLBACK`. The WAV then uses the JDK default bank, so a second render of the same notes stays byte-for-byte the same.

`play <notes.yaml|events.jsonl|wav|dir>` sends that audio to the speakers and waits until it finishes. A directory uses `score.wav`. A note list or event log is rendered in memory first, with the same soundbank rules as `render`. `--dry-run` prints `action=play seconds= dryRun=true` and does not open the device. No speakers is exit 7. This is not the libGDX window.

## Soundbanks and loudness

`soundbank install generaluser` downloads GeneralUser GS into `~/.cache/athenacl-j/soundbanks/` (or `--cache <dir>`). The file is 32319396 bytes. The SHA-256 is pinned. `--dry-run` prints `action=download size=32319396 sha256=` and writes nothing. A second install prints `status=present changed=false`. `soundbank list` and `soundbank show generaluser` report that cache.

`describe instruments --soundbank generaluser` prints `instruments=287 drumkits=13`.

`analyze <dir>` reads `score.wav` and prints `loudness.integrated=` in LUFS, `truePeak=` in dBTP, and `shortTerm=`. `debug check <dir> --loudness` prints the same line. Integrated loudness stays within 0.2 LU of ffmpeg's `ebur128` filter.

The download URL is a pinned commit until this project hosts its own copy. Do not point the installer at schristiancollins.com.

## Theory

`describe scale "D dorian"` prints `scale=D-dorian pcs=D,E,F,G,A,B,C`. `D-dorian` is the same key. `major` means ionian and `minor` means aeolian.

`describe chord Dm7 --key "D dorian"` prints `chord=Dm7 roman=i7 pcs=D,F,A,C`. Roman numerals are counted from the major scale, so in D aeolian `bVII` is C major and `bVI` is B-flat major. Upper case is major and lower case is minor.

`theory progression "i bVII bVI bVII" --key "D aeolian" --voice-lead --out build/l2` writes `events.jsonl`, one whole-note chord per bar in 4/4 at 120 bpm. The summary is `chords= maxMotion= parallels= hash=`. `--voice-lead` keeps each leap within a few semitones and avoids parallel fifths and octaves. A second run of the same progression is `changed=false` with the same hash. `--dry-run` prints the plan and does not create `--out`.

`describe-music` on that log prints `chord=Dm` and `key=D-aeolian` on bar 1, with `conf=` at least 0.70.

## Rhythm

`rhythm pattern x..x..x. --meter 6/8 --bars 2 --drum-grid` repeats the pattern once per bar. `x` is a hit and `.` is a rest. The summary is `bars= onsets= meter= grouping=`. The grid row is `kick |` with the same piano-roll marks as `describe-music`. 6/8 groups as `3+3`.

`render samples/notes/tie-across-bar.yaml --out build/l3` is one note held for six quarter-beats. The summary appends `maxDurBeats=6` after `soundbank=`. The note's end can sit in a later bar. `describe-music` draws that hold as `─` in the next bar. `debug check <dir> --stuck-notes` prints `stuck=0` when every note has an end and nothing overlaps. Overlap exits 6.

`rhythm poly 3:2 --bars 2 --out build/l3p` writes `events.jsonl` only. Three pulses against two, both on the downbeat, so the summary has `layers=2 alignedAtBar=1,2`. `--dry-run` does not create `--out`.

`render samples/notes/swing.yaml --groove "swing=0.62" --out build/l3s` moves eighths that sat halfway through a quarter. `0.50` is straight. The summary and `analyze` of that directory print `swing.measured=` to two decimals. A directory with no off-beat pair leaves that field off.

## Not in L3

Themes, the interactive REPL, libGDX, and the adaptive director. Do not add them in a drive-by change.
