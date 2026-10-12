# Progress

Short log of layers that are in the tree. The layer plan is `docs/research/05-project-plan.md`. Commands and check-file rules live in `AGENTS.md`.

## L0a — CLI skeleton

`gradlew layerCheck -Player=L0a` passes (11 checks).

`athenacl` runs headless from `gradlew :athenacl-cli:agentCli`. Commands: `help`, `version`, `schema`. Text is `key=value` (help is a page of examples). `--json` prints one Result document. Exit codes are 0–7. Logs stay on stderr. `--dry-run` plans a write and does not touch the disk. A real write uses a temp file and a rename; identical bytes leave the file time alone.

## L0b — Events, render, describe

`gradlew layerCheck -Player=L0b` passes (8 checks).

A note list such as `samples/notes/c-major-scale.yaml` renders to `score.mid`, `score.wav`, `events.jsonl`, and `score.txt`. The hash is the event log. A second render of the same notes is `changed=false` with the same hash. `describe-music --roll` prints a bar summary and a piano roll. `debug determinism` compares that hash across runs. `debug inspect --where pitch=E4` prints the one matching note. A missing note list exits 4. `play` sends a note list, an event log, or a WAV to the speakers. `--dry-run` prints the length and does not open the device.

## L1 — Soundbanks and loudness

`soundbank install generaluser` downloads GeneralUser GS (32,319,396 bytes, pinned SHA-256) into a cache. A dry run writes nothing. Installing again reports `status=present changed=false`. `describe instruments` reports 287 instruments and 13 drum kits. `analyze` prints integrated loudness and true peak for `score.wav`. A missing soundbank file still exits 0, with `soundbank=emergency warning=SOUNDBANK_FALLBACK`. An SF3 file is rejected before Gervill loads it (`SOUNDBANK_SF3_UNSUPPORTED`, exit 3), and the fix tells you to use the SF2 edition.

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

`themes/fantasy-dungeon.yaml` uses the fantasy preset and the `gm-lite` kit. Layers name kit instruments (harp, strings, choir, cello, flute, horn, taiko, and the orchestral drums). Each of those is normalized before the mix, then `analyze --by state` measures each state within 1 LU of the contour, from sneaking at −1.5 LU to combat at +4.0, with the spread at or under 6. The event-log hash is unchanged by that trim.

## L1b — Instrument kits

`gradlew layerCheck -Player=L1b` passes (10 checks). `instruments/gm-lite.yaml` names instruments in GeneralUser GS. `instruments/gm-full.yaml` extends it with MuseScore_General (`soundbank install musescore-general`). Kits can set gain, transpose, key range, and fallbacks. Several soundfonts load into one synth, each instrument on an engine-assigned slot, and only the instruments in use are loaded. Each instrument is normalized to about -20 LUFS. `kit validate` checks checksums, licenses, and SF3. `credits` writes `CREDITS.md` for the sources a theme actually uses.

## L6 — Beat switches

`gradlew layerCheck -Player=L6` passes (6 checks). Combat and danger land on the next beat. Notes already queued after that beat are dropped, and a sounding note ends on the beat. `samples/timelines/ambush.json` asks for combat on beat 3 of bar 4. `debug fuzz-transitions` tries a thousand seeded changes. `adapt` prints the scheduler snapshot, including `kit=` and `instruments=`. `gradlew :athenacl-gdx:run` plays the ambush through the kit and shows that line. The next theme is preloaded off-thread and swaps at a bar line. F3 writes `build/athenacl-snapshot.txt`.

## L7 — Motifs

`gradlew layerCheck -Player=L7` passes (8 checks).

`motif generate` grows a 2–8 note idea from a seed. Inverting it twice restores the idea. The theme lead plays that idea every `motif.every` bars (`themes/fantasy-dungeon.yaml` uses 4) and keeps a scale line between statements; combat turns it upside down and shortens it. `analyze` prints `motif.reuse.per8bars=`. `motif import` reads `samples/motifs/theme.mid` into YAML.

## L7b — Hooks and the melody critic

`gradlew layerCheck -Player=L7b` passes (9 checks).

`melody generate --candidates 64 --explain` ranks seeded hook candidates and prints the same pick on a second run. A hook is 3–6 notes. Hard rules reject a leap that is too wide, a stuck pitch, an unresolved tritone, a cadence that does not land, and a line that copies a declared reference (`copyGuard`). `melody score` rates a MIDI file. `melody rate` writes one A/B row. `melody fit` turns those rows into weights.

`themes/fantasy-dungeon.yaml` has a `hooks:` block, so that theme's lead is a picked hook (still motif `m1`) instead of the L7 generator. The pick happens once per state, before the bar loop. `debug explain` on a lead note prints `hook.cand=` and `score=`. `themes/fantasy-combat.yaml` has no `hooks:` block, so its fight line is unchanged. Loudness, stuck notes, and the ambush still pass with the dungeon hooks on.

## L8 — Overlays and wind-downs

`gradlew layerCheck -Player=L8` passes (5 checks).

`lowHealth` is a quiet heartbeat on top of the current state. `--at "4 overlay lowHealth 1"` turns it on, and `analyze` prints `loudness.overlayDelta=` within 1 LU. Leaving combat waits for the bar and plays a falling wind-down. A jump larger than 4 LU ramps CC11 instead of stepping. Entering combat still lands on the beat. A render with no timeline still stays on the first state.

## L9 — Sci-fi space

`gradlew layerCheck -Player=L9` passes (6 checks).

`themes/scifi-space.yaml` is the space preset: Lydian pads, a synth bass, and an arp on the eighth-note grid. Combat stays at 100 bpm and doubles that arp into sixteenths, with an 808 kit. Trading uses celesta. There is no whole-tone scale, so the raised fourth stands in for it. `describe-music` prints `arp.grid=` and `programs=`. `render themes/fantasy-dungeon.yaml --at "8 theme themes/scifi-space.yaml"` prints `themeSwitch.bar=9`. The window still swaps the combat kit on its own. Ambush, Dungeon, Combat, Party, Trading, Space, and Space combat buttons each start that piece from the beginning, with the track length and how far through it you are. `themes/fantasy-party.yaml` holds the tavern, `themes/fantasy-trading.yaml` holds the market, and `themes/scifi-combat.yaml` holds the space fight.

## L10 — Harmony and meter

`gradlew layerCheck -Player=L10` passes (4 checks).

`theory modulate` plans a pivot, a direct jump, or a chord borrowed from the parallel key. A transition can keep the current key, move to the relative key, or use the state's own key. `samples/themes/modulation.yaml` stays in D dorian until bar 9, which is F lydian, and bar 12 is in 7/8. Chord phrases cadence on the tonic, and a Lydian tonic includes the raised fourth. The check render writes MIDI and the event log, not a WAV.

## L11 — Debug depth

`gradlew layerCheck -Player=L11` passes (10 checks).

`debug bisect` names the first bar where two renders split, and the control call that caused it. `diff` compares two themes after the preset is merged, or two render directories. `debug determinism --fork-jvm` hashes a theme in a fresh process for each run. `render --debug provenance` and `--debug trace` write a sidecar and keep the same event-log hash as `--debug off`.

## L12 — Port foundation

`compat random --seed 7 --n 5` prints the five CPython `random()` draws for that seed. The generator also matches `randint`, `choice`, and `shuffle`. `gauss`, `expovariate`, and `betavariate` stay within 1e-15 of CPython. `Py.round` is half-to-even, and integer division matches Python on negatives. Argument strings are quoted and read as Python values. A fuzz test round-trips random literals and argument strings. `conformance/demos` holds the 31 MIDI demo scripts. `conformance list` prints `scripts=31`. `conformance run` checks that each script has commands. MIDI bytes are compared once a script can be played.

`gradlew layerCheck -Player=L12` passes (3 checks).

## L13 — Path slice

`athena run conformance/paths/c-major.ath --out build/l13` writes MIDI for a path with no texture. Pitch names, pitch groups, and the event list are in. The file matches the golden MIDI beside the script (`bytesEqualGolden=true`). A script that sets an amplitude still exits 3. Set-class names are a later slice.

`gradlew layerCheck -Player=L13` passes (3 checks).

## L14 — Line groove

`po sample basketGen "oc,(1,2,3)" --n 6` prints `values=1,2,3,1,2,3`. `athena run conformance/demos/script02a.ath --out build/l14` plays the basic beat and matches the golden MIDI. `conformance run --subset slice` checks that beat and the shuffled snare (`script02b`) and prints `pass=2 fail=0`. `tpsd` and `tmsd` are separate generators. Each instrument is scored twice while it is built, and those notes are thrown away. A rest does not draw an amplitude.

`gradlew layerCheck -Player=L14` passes (3 checks).

## L15 — Value generators

`describe pos --group value` lists the 45 public value generators. The private operator base is not one of them. `conformance run --subset po.value` samples each generator six times at seed 7 and prints `pass=45 fail=0`. The samples stay off the wave and breakpoint generators.

`gradlew layerCheck -Player=L15` passes (2 checks).

## L16 — Waves, breakpoints, and rhythm

`describe pos --group omde` lists the 36 public wave, distribution, breakpoint, and envelope generators. The private bases are not generators. `describe pos --group rhythm` lists the 13 rhythm generators. `conformance run --subset po.omde,po.rhythm` samples each one six times at seed 7 and prints `pass=49 fail=0`.

`gradlew layerCheck -Player=L16` passes (3 checks). The full `gradlew layerCheck` passes (123 checks).
