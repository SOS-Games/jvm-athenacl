# Progress

Short log of layers that are in the tree. The layer plan is `docs/research/05-project-plan.md`. Commands and check-file rules live in `AGENTS.md`.

## L0a — CLI skeleton

`gradlew layerCheck -Player=L0a` passes (11 checks).

`athenacl` runs headless from `gradlew :athenacl-cli:agentCli`. Commands: `help`, `version`, `schema`. Text is `key=value` (help is a page of examples). `--json` prints one Result document. Exit codes are 0–7. Logs stay on stderr. `--dry-run` plans a write and does not touch the disk. A real write uses a temp file and a rename; identical bytes leave the file time alone.

## L0b — Events, render, describe

`gradlew layerCheck -Player=L0b` passes (8 checks).

A note list such as `samples/notes/c-major-scale.yaml` renders to `score.mid`, `score.wav`, `events.jsonl`, and `score.txt`. The hash is the event log. A second render of the same notes is `changed=false` with the same hash. `describe-music --roll` prints a bar summary and a piano roll. `debug determinism` compares that hash across runs. `debug inspect --where pitch=E4` prints the one matching note. A missing note list exits 4. `play` sends a note list, an event log, or a WAV to the speakers. `--dry-run` prints the length and does not open the device.

## L1 — Soundbanks and loudness

`soundbank install generaluser` downloads GeneralUser GS (32,319,396 bytes, pinned SHA-256) into a cache. A dry run writes nothing. Installing again reports `status=present changed=false`. `describe instruments` reports 287 instruments and 13 drum kits. `analyze` prints integrated loudness and true peak for `score.wav`. A missing soundbank file still exits 0, with `soundbank=emergency warning=SOUNDBANK_FALLBACK`.

## L2 — Theory

`gradlew layerCheck -Player=L2` passes (5 checks).

`describe scale "D dorian"` lists `D,E,F,G,A,B,C`. `describe chord Dm7 --key "D dorian"` prints roman `i7`. `theory progression "i bVII bVI bVII" --key "D aeolian" --voice-lead` writes a four-bar event log whose largest leap is at most 4 semitones and whose parallel fifths and octaves are 0. `describe-music` on that log reads bar 1 as `Dm` in `D-aeolian`.

## L3 — Rhythm and time

`gradlew layerCheck -Player=L3` passes (6 checks). A six-beat tie renders as one note with `maxDurBeats=6`, and `debug check --stuck-notes` reports `stuck=0`. `rhythm poly 3:2` writes two layers that meet on each downbeat. `render --groove "swing=0.62"` and `analyze` both print `swing.measured=0.62`.

## L4 — Adaptive director, offline

`gradlew layerCheck -Player=L4` passes (6 checks).

`theme validate` rejects a transition that names a missing state. `render` of `samples/themes/poc.yaml` with the poc timeline plays 16 bars through peaceful, sneaking, and combat, with both state changes on a bar line. Bars 9–10 of that render match a window that asks for combat at intensity 0.6. `debug replay` of `control.jsonl` prints the same hash.

## L5 — Presets and loudness

`gradlew layerCheck -Player=L5` passes (6 checks).

`themes/fantasy-dungeon.yaml` uses the fantasy preset: six states, a drone under the sneaking bars, and a loudness contour from sneaking at −1.5 LU to combat at +4.0. `analyze --by state` measures each state within 1 LU of that contour, with the spread at or under 6. The event-log hash is unchanged by the WAV trim.

## L7 — Motifs

`gradlew layerCheck -Player=L7` passes (8 checks). The full `gradlew layerCheck` passes (58 checks).

`motif generate` grows a 2–8 note idea from a seed. Inverting it twice restores the idea. The theme lead plays that idea every `motif.every` bars (`themes/fantasy-dungeon.yaml` uses 4) and keeps a scale line between statements; combat turns it upside down and shortens it. `analyze` prints `motif.reuse.per8bars=`. `motif import` reads `samples/motifs/theme.mid` into YAML.
