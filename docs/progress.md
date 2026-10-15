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

`gradlew layerCheck -Player=L16` passes (3 checks).

## L17 — Textures and clones

`describe textures` lists the 14 texture modules, including LineGroove. `conformance run --subset texture,clone` checks those modules, the 22 public texture switches, the 2 clone switches, and the 15 clone filters. It prints `pass=53 fail=0`. The private bases are not switches or filters.

`gradlew layerCheck -Player=L17` passes (2 checks).

## L18 — Set classes, sieves, and generators

`describe setclass 3-11` prints `forte=3-11`, `pcs=0,3,7`, and `icv=0,0,1,1,1,0`. The table has 351 forms. `conformance run --subset sieve,generators` checks five sieves, four tunings, the logistic map, permutations, and three set classes. It prints `pass=14 fail=0`.

`gradlew layerCheck -Player=L18` passes (2 checks).

## L19 — Commands and project files

`describe commands` lists the first 55 command names, from `w` through `TCe`. The source has 104 names. `conformance run --subset commands.a,xml` checks that list and one project file. It prints `pass=56 fail=0`.

`gradlew layerCheck -Player=L19` passes (2 checks).

## L20 — Commands and the prompt

`describe commands --group b` lists the other 48 command names, from `TCcp` through `menu`. `describe commands --group all` lists all 103 distinct names. `AUpc` is assigned twice in the source. `athena prompt --line "tpsd 7"` prints `command=TPsd`. `help --json` lists every name. The line is resolved and not run.

`gradlew layerCheck -Player=L20` passes (6 checks).

## L21 — Other genres

`preset list` prints `presets=25`: fantasy, sci-fi, fourteen more genres, and nine world packs. `preset audition reggae --bars 8 --seed 1` prints `character=skank` and a note count above 0. Hijaz plays E phrygian dominant. The five-note packs play the seven-note scale that contains those notes.

`gradlew layerCheck -Player=L21` passes (3 checks). The full `gradlew layerCheck` passes (138 checks).

## L7c — Melodic engine

Step 0 moves the presets into `presets/genres` and `presets/states`. `theme resolve` merges genre defaults, the generic state, the genre-specific state, then the theme file. Lists replace and maps merge. Each value records which layer set it. StockLayers reads those density fields instead of testing the mode or the state name. Note hashes of existing themes can change where a state theme now supplies the pattern. `gradlew layerCheck -Player=L7c` is the resolve checks.

Step 1 maps a scale degree with `12 * (octave + 1) + tonic + step`, so the note after B in A is the C above it. Motif transforms keep a degree that walks past the mode instead of folding it back. The check is `TheoryTest.aeolianDegreesStayAboveTheTonic` (69, 71, 72, 74, 76, 77, 79, 81). No golden file changed.

Step 2 plays each bar from an eight-bar progression (`i VI iv V i VI V i`, half cadence on bar 4, authentic on bar 8). A minor-ish scale with `scale.leadingTone` turns `V` into `V7`. The bass, pad, harp, arp, brass, bell, drone, chords, stinger, and wind-down read that chord. A state with `harmony.style: pedal` may hold one bass pitch; every other shipped preset and theme must change bass pitch within four bars. No golden file changed. Note hashes move because the bass no longer stays on the tonic.

Step 3 voice-leads the pad, brass, chords, and bell with the same close voicing used by `theory progression --voice-lead`. A bare fifth is played only when `voicing.style` is `open5`. A theme file's state map is merged into that state's resolved settings, so a game can turn the fifth back on. No golden file changed.

Step 4 replaces the every-bar scale walk with a four-bar phrase. The form (`period`, `sentence`, or `aaba`) and the rhythm cells come from the resolved lead settings and `presets/rhythms.yaml`. Strong beats sit on the current chord, one leap steps back, and a cadence note is held. `motif.every` counts phrases between statements of the unturned idea. Lead notes do not overlap. No golden file changed. Note hashes move because the melody is new.

Step 5 turns hooks on for every preset (`hooks.enabled` and 64 candidates). A theme file that already sets `hooks:` keeps that block, so `themes/fantasy-dungeon.yaml` stays at 8 candidates. The hook shape picks a random interior inside the contour, one leap of three to six degrees, and a rhythm cell that can start later in its pattern. Strong beats snap to the chord of that moment. No golden file changed. Note hashes move where a preset theme now grows a hook.

Step 6 adds `melody score --mode phrase`. A phrase may span a twelfth. The cadence score looks only at a note that ends a four-bar phrase, so a line is not rejected for a note in the middle. Chord tones are read from the chord of the bar. `rhythmVariety` counts distinct durations and notes that are not quarters. `barPattern` is higher when some bars repeat a rhythm and some do not. In that mode the folk fixture scores above the old quarter-note scale lead, which scores above the random walk. Hook mode is unchanged. No golden file changed.

Step 7 retunes the presets in YAML. Fantasy peaceful and trading are tonal (major, or minor with a leading tone); sneaking stays a dorian pedal at 76 bpm and danger stays phrygian. Tempos split by state (peaceful 84, trading 96, partying 120, danger 96, combat 140). The base colour is strings, horn, piano, flute, and clarinet; harp and fiddle are no longer the base of every state. Other genres keep their mode and play a progression that fits it (dorian i–IV–VII–i, lydian I–II–vi–V, and the rest). Combat is a little faster and drops a colour layer. Lo-fi and every sci-fi state have a lead; calm sci-fi leads are quiet squares, and combat stays a saw at 100 bpm. `theme explain` and `adapt` now print `instruments=strings,piano,horn,flute,cello` for the peaceful state. The score's bpm stays the opening tempo, so a later state's tempo does not shift the loudness slices. No golden MIDI file changed.

Step 8 lets a layer name a parameter object. `source.lead.po` draws rhythm tokens, and `source.arp.po` keeps arp steps. Sci-fi uses a Markov pulse for the lead and a sieve for the arp, both with texture `LineGroove`. The seed is a fork of the theme seed. A sieve `3@2` still keeps remainder 2. No golden file changed.

Step 9 counts what a render actually did. `analyze` of a directory with `events.jsonl` prints `harmony.distinctRoots` (the smallest set of bass pitch classes in any eight bars), `harmony.dominantCadences`, `lead.rhythmValues`, `lead.leapsOver7`, `lead.overlaps`, and `lead.distinctBarPatterns`. `harmony.json` beside the log adds `harmony.style`. `preset audition --all --analyze` exits 6 when any preset stays under three bass notes or the lead overlaps. `render --genre` plays one state; a single `--bars` number there is the length. A theme render also writes `harmony.json` and `lead.mid`. `melody score --progression` reads that file. An eighth in that MIDI file stays an eighth, so the cadence is still at the end of the phrase. No golden file changed.

L22 starts with `batch`. A JSONL file lists commands, and this process runs each one with a fresh set of flags. The text under each `id=` is what that command prints on its own. `--stop-on-error` skips the rest. No golden file changed.

## L7d — Instrument analysis

`kit analyze` renders each instrument dry through Gervill and writes a profile. The CC0 fixture bank has seven presets. Three are bad: inharmonic partials, a clipped sine, and a loop that clicks. The saw stays in tune and is tagged bright. A second run reads the profile (`cached=true`) and keeps the same hash, including across two processes. `kit describe` prints the verdict. Thresholds live in `instruments/analysis.yaml`. No golden MIDI file changed.

## L7e — Instrument lint

`kit lint` reads that profile and checks a resolved theme. A bad lead exits 3 with `finding=INSTR_BAD` and a `fix=try` hint. A saw lead prints `errors=0 warnings=0`. `kit fit` scores a role. A pluck is a poor pad and a good arp. `kit compat` reports when two parts cover the same register. The finding names the theme file that chose the instrument. A missing GM profile is skipped so lint does not render the full bank.

## L7h — Seeded parameter sampling

A genre no longer has to name one key, one progression, and one tempo. `scale`, `tempo`, `harmony`, `voicing`, and `lead` can be a choice, a range, a probability, or a generator (Markov, grammar, sieve, or an athenaCL parameter object). `theme resolve --seed 1` draws them and prints `source=sampled`, `source=generated`, or `source=pin`, plus the seed path `1/s1/…`. The same seed draws the same values. A `pin:` on the game theme replaces any draw. With no seed, each field keeps the `fallback`, which is the value that genre used before. No golden MIDI file changed. Note hashes move where a render used to inherit a fixed genre tempo, key, or progression and now draws one.

## L7f — Ensemble selection

`ensemble select` fills each role from a profile. Fantasy and sci-fi ask for that. The same seed always picks the same instruments, chosen from the top few that stay inside the pair limits. A pinned role stays pinned. A missing profile keeps the fixed instrument names. `theme resolve --seed` prints those choices with `source=ensemble:auto`.
