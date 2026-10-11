# 05 — Project plan: athenaCL-J, an athenaCL rewrite in Java with an adaptive game-music engine

*Plan document, Oct 9, 2026. No project was created. This consolidates [01](01-soundfont-microsoft-gs.md)
(soundbanks/Gervill), [02](02-soundfont-instruments.md) (soundfont comparison), [03](03-java-port-plan.md)
(port analysis of the `pregui` code) and [04](04-adaptive-game-music-design.md) (adaptive design + PoC),
applying the decisions the user settled on Oct 9, 2026 (updated 1:07 PM with genre presets and the
agent-authoring non-goal, at 1:10 PM with the agentic CLI and debug tools, and at 1:25 PM: no server/MCP modes, conventions aligned
with the user's [jvm-mw](https://github.com/SOS-Games/jvm-mw) repo, see [06](06-jvm-mw-cli-patterns.md); and at 1:35 PM: loudness contour,
fantasy/sci-fi genre priority, transition defaults; and at 4:55 PM: layered plan with tools first, §7; Oct 10, 9:58 AM: audio effects stage and sampled layers, §6.11; 10:17 AM: multi-soundfont instrument kits and SFZ loader, §6.12, from survey [07](07-soundfont-upgrade.md); 9:40 PM: hook generator + melody critic, §6.13, layer L7b after the already-built L7). File and line references point to the `pregui`
tag (`35b9869`) checked out at `/workspace/pregui/athenaCL/` unless marked HEAD. Effort figures are
**estimates**. "athenaCL-J" is a working name.*

---

## 0. Settled decisions (input to this plan)

| Topic | Decision |
|---|---|
| Scope | **Complete Java rewrite of all of athenaCL** (from the `pregui` tag), extended into an **adaptive game-music engine**. Engine, MIDI output and CLI are core. **Csound/SuperCollider output comes last and is optional.** |
| License | **GPL-3.0-or-later** (allowed by athenaCL's "GPL v2 or later", 03 §10) |
| Build | Gradle multi-module, built locally first, GitHub later |
| Platform | **libGDX, desktop (LWJGL3) only**. Web skipped for now. **Java 21 minimum** (matches jvm-mw). |
| Synth | Java's built-in **Gervill** on all platforms. **Several soundfonts loaded at once, with specific instruments chosen from each via reusable instrument kits** (`instruments/*.yaml`, §6.12). Default kit `gm-lite` = GeneralUser GS; optional `gm-full` = MuseScore_General SF2 (MIT, 206 MB). SF3 is rejected; SFZ via an in-house loader (L6b). |
| Game states | Defined per game, with a **starter set of 6** (peaceful, sneaking, combat, danger, trading, partying). **One main state plus layered overlays**, continuous **intensity**, **stingers**. |
| Transitions | Next **bar** by default. **Combat and danger switch on the next beat, with retraction** of already-queued notes. Immediate is available but not a default. **Each theme can override** this (§6.4). |
| Loudness | **Designed relative contour**: peaceful/trading/sneaking quieter, partying/danger mid, combat a few LU louder but never jarring, **total spread ≤ ~6 LU**. Set per theme with defaults, **calibrated automatically** (§6.4.1). |
| Genre priority | **Fantasy first** (for the user's dungeon crawler), **then sci-fi space** (another of their projects). These are the first two presets and starter themes. Other genres come later (§6.7.2; layers L5, L9, L21). |
| Themes | Swappable per game, **YAML**, instruments per state |
| Material | Fully generated, composer-supplied, or mixed. The engine must **generate its own motifs and develop them** (repetition, transposition, inversion, augmentation, fragmentation). |
| Musical capabilities | Polyrhythm with independent drum/percussion layers; notes longer than a beat (ties, sustains, varied durations, rests); chords, progressions, harmonic rhythm; voice leading; key/mode changes and modulation; dynamics and articulation; swing/groove and humanization; meter changes. |
| Determinism | **Same seed (+ same control log) → same music** |
| Genre presets | **Add a preset library**: drum patterns, groove/swing templates, chord vocabularies and progressions, song-form presets, typical tempo/meter/mode, GM/GS instrumentation suggestions, for broad genres plus world/ethnic modes. Themes **reference and override** presets (`preset: celtic`). |
| Agentic CLI | **AI agents and scripts are first-class CLI users**: non-interactive one-shot commands, grep-stable `key=value` text by default (jvm-mw style) plus `--json` everywhere with stable versioned schemas, meaningful exit codes, structured errors with fix hints, introspection, idempotent file ops, dry-run, deterministic seeds, JSONL batch mode, headless bar stepping as **stateless commands** (no server, no MCP; aligned with jvm-mw's headless debug CLI, doc 06), text/ASCII descriptions of rendered music, analysis metrics, a shipped agent guide (AGENTS.md / skill file) |
| Debug tools | Event-log inspector, timeline replay + bisect, step-by-bar debugger with **note provenance**, state-machine/transition trace, scheduler/latency monitor, stuck-note detection, loudness meters, theme/render diff, live libGDX debug overlay + hooks, log levels, determinism checks |
| Song → theme | **Not built into the library.** External agents do it. The project supports them with a published JSON schema, thorough format docs, a validation command, and a deterministic preview/render CLI. |

---

## 1. Goals and non-goals

### Goals
1. **Faithful port of the full athenaCL system** (`pregui`, ~85.7k Python lines, 03 §2): ParameterObjects (136),
   TextureModules (14), Clones, Paths and set classes, sieves, generators (Markov, grammar, automata,
   feedback, genetic, OMDE), the event list and MIDI output, project XML, and the command interpreter (~110
   user commands in `command.py`). Ported generators must reproduce pregui MIDI **byte-for-byte** in
   compatibility mode.
2. **Adaptive engine** for games: a director that generates music bar by bar in real time from game
   state, overlays, intensity and stingers, with quantized transitions and retraction.
3. **Music theory and rhythm layers** that athenaCL lacks: functional harmony, progressions and
   harmonic rhythm, voice leading, modulation, meter changes, groove/swing/humanize, polyrhythmic layers, ties.
4. **Motif engine**: generate motifs and develop them (repeat, transpose, invert, retrograde,
   augment/diminish, fragment, sequence, ornament) across phrases for coherence. Accept composer-supplied
   motifs and progressions.
5. **Rendering**: Gervill real-time and offline WAV with a pluggable soundbank (GeneralUser GS default),
   plus standard MIDI file output.
6. **libGDX integration** for desktop games, plus a CLI covering athenaCL's commands, adaptive previews
   and offline renders.
7. **Determinism**: reproducible output for a seed and a recorded control log, in both offline and
   real-time modes (§6.6).
8. **Genre presets**: a built-in, data-driven library of genre presets (§6.7) that themes reference and
   override, so a game can get "celtic tavern" or "lo-fi menu" music from a few lines of YAML.
9. **A theme format external tools can target**: published JSON schema, format docs, `theme validate`, and a
   deterministic `preview`/`render` CLI, so external agents (human or AI) can author themes (§6.8).
10. **Agentic CLI** (§6.9): every command usable non-interactively by AI agents and scripts. Grep-stable `key=value`
    text by default (jvm-mw convention) and optional `--json` with versioned schemas, plus introspection, a batch mode,
    stateless bar-by-bar stepping, and "eyes for agents": text/ASCII descriptions and analysis metrics of rendered music.
11. **Debug tools** (§6.10): explain why every note was chosen, trace and replay what the director did, find
    where two renders diverge, and watch the scheduler live, both from the CLI and in a running libGDX game.

### Non-goals (for now)
- Web/GWT, Android, iOS backends (Android lacks `javax.sound.midi`'s synth; GWT lacks threads/javax.sound).
- Audio-stem middleware features (recorded audio stems, DSP chains, 3D spatial music), and
  replacing FMOD/Wwise.
- ML/neural music generation.
- Bundling or reading Microsoft `gm.dls` (01 §2). An opt-in system soundbank lookup can come later.
- SF3 (Ogg) soundfonts: Gervill plays their compressed samples as noise without an error (07 §2), so they're **rejected with a clear error** (§6.12.5). Use SF2 editions.
- Byte-exact parity with the **HEAD** fork's Rust RNG (ChaCha12) (03 §1). Only pregui/CPython parity is targeted.
- A GUI editor (a later candidate; the CLI preview and YAML hot-reload cover authoring first).
- **A song-to-theme tool** (analyzing a reference song/recording and producing a theme). External agents
  do this outside the library. The project only provides the schema, docs, validator and preview CLI they
  need (§6.8).
- Vocals with lyrics, and genre-authentic professional mixes/mastering. These are limits of GM/GS soundfonts through
  Gervill. Some modern-production tools (filter sweeps, sidechain ducking, saturation, 808 glides, loops/crackle)
  arrive later through the effects stage and sampled layers (§6.11, L9b–L9d), but they don't make GM sound like
  a produced record (§6.7.3).
- A graphical debugger/IDE plugin. Debug tools are CLI, JSON, ASCII and an in-game overlay; a GUI can build on
  the same JSON later.
- **Server modes of any kind:** no JSON-RPC session server, no MCP server, no socket for attaching to a running game.
  Agents use plain one-shot commands (like jvm-mw's `debugCli`, doc 06), and state lives in files: timelines/control
  logs in, event logs and snapshots out (§6.9.3). Live-game inspection goes through the F3 snapshot dump, as in jvm-mw.

---

## 2. Key technical choices

| Choice | Decision and rationale |
|---|---|
| Java baseline | **17** (records, sealed types, switch patterns, `java.util.random.RandomGenerator` incl. `L64X128MixRandom` [JEP 356]). Test on 17, 21 and 25. |
| Synth | Gervill (`MidiSystem.getSynthesizer()`), real time through `Receiver` with µs timestamps. Offline and libGDX pull-mode through `com.sun.media.sound.AudioSynthesizer.openStream`, which needs `--add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED` (verified on JDK 21, 01 §3.3). Wrap this in **one class** (`GervillStreams`) so a future JDK change touches one place. The fallback path when it's not exported is Gervill on its own `javax.sound.sampled` line instead of libGDX's OpenAL. |
| Soundbank default | **Instrument kits** (§6.12): default `gm-lite` = **GeneralUser GS 2.0.3** (32,319,396 bytes, 287 instruments, 13 GS kits; 02 §1); optional `gm-full` = **MuseScore_General v0.2 SF2** (206 MB, MIT, 309 instruments / 37 kits; 07). See §2.1. |
| RNG | Two implementations of one `RandomSource` interface: **`CompatRandom`** (CPython MT19937 algorithms, for the ported athenaCL core and golden tests) and **`DefaultRandom`** (`L64X128MixRandom`, splittable, used by the adaptive engine, seeded per (seed, scope, bar, layer)). |
| Time model | **Ticks at 960 PPQ** (athenaCL's own `midiTools.MidiScore.tickPerBeat = 960`) plus a tempo map. athenaCL's seconds-based events are converted at the boundary. Exact rational musical time (bars/beats/ticks) inside the adaptive engine. |
| Config | YAML through **SnakeYAML Engine** (Apache-2.0) or Jackson-YAML (Apache-2.0), mapped to records. JSON schema published for editor validation. |
| Build conventions | **Aligned with jvm-mw** (doc 06): Groovy DSL `build.gradle`/`settings.gradle`, `foojay-resolver-convention`, versions in `gradle.properties` (`gdxVersion`, `projectVersion`), `configure(subprojects)` applying `java-library`, a fat-jar launcher with manifest attributes (here also `Add-Exports`), a Gradle `JavaExec` task for the headless CLI run from the repo root, and config through the `local.properties` key / `-D` / env-var triple (`athenacl.soundbank`, `-Dathenacl.soundbank`, `ATHENACL_SOUNDBANK`). Java 21, same as jvm-mw (resolved, Q20). |
| CLI | **picocli** (Apache-2.0) + **JLine 3** (BSD-3) for athenaCL's interactive REPL. **Difference from jvm-mw:** jvm-mw's `DebugCli` is a hand-rolled `switch` with positional args. That's fine for its 14 commands, but athenaCL has ~110 commands with typed options. Agent/debug commands still follow jvm-mw's shape: short noun commands, positional args first, `help` with copy-pasteable examples. |
| JSON | **Jackson** (Apache-2.0) for the optional `--json` output and event logs (JSONL). Schemas are JSON Schema 2020-12, generated from records. jvm-mw has no JSON at all. Here it's opt-in because theme validation needs JSON-pointer error paths and event logs need a machine format. |
| Logging | `java.lang.System.Logger` (no dependency; games can bridge it to SLF4J/Log4j). Logs go to **stderr only**, so stdout stays pure data. Inside libGDX it forwards to `Gdx.app.log`, and slow work is logged as `perf …` lines like jvm-mw's `PerfTrace`. |
| Tests | JUnit 5 (EPL-2.0) and jqwik (EPL-2.0) property tests, test scope only |
| libGDX | 1.14.2 (May 2026, LWJGL 3.3.3; [libGDX news](https://libgdx.com/news/2026/05/gdx-1-14-1)), Apache-2.0 |
| License compatibility | Apache-2.0, BSD-3 and MIT dependencies are GPL-3 compatible. EPL-2.0 is used only for tests (not distributed). OpenJDK's Gervill is GPL-2.0 + Classpath Exception. |

### 2.1 GeneralUser GS: bundle or download? [decision proposal]

License text ([LICENSE.txt v2.0](https://github.com/mrbumpy409/GeneralUser-GS/blob/main/documentation/LICENSE.txt), fetched Oct 9, 2026):
- *"Please feel free to use it in your software projects, and to modify the SoundFont bank or its
  packaging to suit your needs."* So **bundling is allowed**.
- *"If you plan to feature GeneralUser GS on your own website, please do not link directly to my
  download files. Either link to my website, or provide your own local copy instead."* So **don't
  download from the author's URLs at runtime**. Host our own copy.
- Sample provenance caveat: the author "cannot be 100% sure where all of the samples originated". This is a
  known risk for commercial games; surface it in docs.
- It is a separate data work, not GPL code. Shipping it alongside GPL-3 code is aggregation. Keep its
  license file next to it.

**Decision proposal:**
1. Publish an optional artifact **`athenacl-soundbank-generaluser`** containing the `.sf2` (32 MB) and
   `LICENSE-GeneralUser-GS.txt`. Games that ship offline add it as a dependency or copy it into their
   `assets/`. This is the **recommended path for shipped games** (no network at first run).
2. The CLI and dev tooling **download on first run** from **our own GitHub release asset** (mirror,
   pinned SHA-256, like athenaCL's own `make init` for FluidR3, 01 §1.1) into
   `~/.cache/athenacl-j/soundbanks/`.
3. The resolver chain stays pluggable (01 §4.2): explicit path → kit source → cache/bundled
   GeneralUser GS → Gervill emergency bank (never silent).
4. **Since Oct 10, soundbanks are reached through instrument kits** (§6.12). The same mirror also hosts
   **MuseScore_General v0.2 SF2** (MIT) for the optional `gm-full` kit, downloaded with `soundbank install
   musescore-general` and shipped with its License.md. GeneralUser GS stays the small default because the user heard
   no difference (07).

---

## 3. Module layout (Gradle, Groovy DSL as in jvm-mw)

```
athenacl-j/
├─ settings.gradle      foojay toolchain resolver, include list (jvm-mw style)
├─ build.gradle         configure(subprojects): java-library, Java 21 release, UTF-8, license headers,
│                       --add-exports for compile/test; gradle.properties holds gdxVersion/projectVersion
├─ athenacl-events/      [L0b] event model (NoteEvent/ControlEvent, ticks, tempo map basics), canonical JSONL event
│                        log + hash, RandomSource + seed paths, DebugSink/Provenance hook interfaces, OutputSink
├─ athenacl-compat/      [L12] Python-semantics kit: CompatRandom (CPython MT19937 + randint/choice/shuffle/gauss/
│                        expovariate/betavariate…), Py.round/floorDiv/floorMod/repr, PyLiteralParser,
│                        PoArgParser (athenaCL "restringulator" grammar), acronym expansion
├─ athenacl-theory/      [L2,L10,L18] pitch, pitch class, pitch space, temperament/tuning, set classes (generated tables),
│                        multiset, sieve, scale/mode, key, chord, progression, harmonic rhythm, voice leading,
│                        modulation planner
├─ athenacl-rhythm/      [L3] durations/pulses (athenaCL rhythm), meter + meter changes, tempo map, ticks,
│                        groove templates, swing, humanize, polyrhythmic layer clocks, ties
├─ athenacl-core/        [L13–L20] athenaCL engine port: ParameterObjects (libPmtr), generators (markov, grammar, automata,
│                        feedback, genetic, dice, chaos, permutate, table, envelope, ornament, omde), TextureModules
│                        (libTM), Clones, PolyPath, AthenaObject/session, EventList, orchestras (GM, GM percussion),
│                        project XML I/O, command layer (headless)
├─ athenacl-motif/       [L7] Motif model, transformations, development planner, phrase forms, composer-material import
│                        [L7b] `…motif.hook` (hook generator) and `…motif.critic` (features, hard rules, selection,
│                        Bradley–Terry weight fit); extends L7 without changing its public API
├─ athenacl-presets/     [L5,L9,L21] Genre preset library: data files (YAML, on the classpath under presets/) for drum patterns,
│                        grooves, chord vocabularies/progressions, song forms, tempo/meter/mode ranges, GM/GS
│                        instrumentation; preset model, loader, inheritance (`extends`) and override merge,
│                        world/ethnic mode tables
├─ athenacl-adaptive/    [L4–L8] Director, state machine, overlays, intensity, transition rules, stingers, musical clock,
│                        lookahead scheduler with retraction, theme model + YAML loader (resolves `preset:`),
│                        theme validator (schema + semantic checks), loudness calibration, control log/replay
├─ athenacl-instruments/ [L1b,L6b] Instrument kits: kit YAML model/loader/resolver (extends, overrides, fallbacks),
│                        canonical instrument names, multi-soundbank loading + deterministic patch allocator (remap via
│                        SimpleInstrument), selective loading, normalization cache, license policy + credits, SF3/format
│                        checks, SFZ loader (L6b)
├─ athenacl-render/      [L0b,L1] SMF writer (port of midiTools.MidiScore), javax.sound Sequence export, Gervill real-time +
│                        offline (GervillStreams), soundbank resolver/cache, EBU R128 loudness meter, MIDI-device output
├─ athenacl-fx/          [L9b–L9d] Pure-Java DSP: bus mixer (one Gervill instance per bus), filters/EQ/compressor+
│                        sidechain/saturation/bitcrush/reverb/delay/chorus/limiter, automation on the musical clock,
│                        sampler for `type: sample` layers (WAV, OGG via JOrbis), meters, CPU budget
├─ athenacl-gdx/         [L6] libGDX desktop integration: pull-mode PCM into AudioDevice, lifecycle, main-thread callbacks,
│                        `…gdx.debug`: F4 debug overlay (scene2d), debug hooks, F3 snapshot + log dump for repro
├─ athenacl-analysis/    [L0b+] Music analysis over event logs: metrics (loudness from WAV or a velocity proxy, density,
│                        polyphony, pitch range, pitch-class histogram, key estimate + consistency, chord-tone ratio,
│                        repetition/motif reuse, syncopation, transition latency), text descriptions (per-bar
│                        chord/key/state summary, ASCII piano roll, drum grid)
├─ athenacl-debug/       [L0b+] Provenance capture, trace recorder (bars, transitions, scheduler samples), event-log
│                        inspector/query, replay, bisect, step-by-bar debugger engine, stuck-note checker,
│                        theme/render diff, determinism checker, latency/scheduler monitor (no UI; JSON + text)
├─ athenacl-cli/         [L0a+] One module, one `athenacl` launcher, three packages:
│                        `…cli.model`: typed commands, results, error model (codes, paths, fix hints), exit codes,
│                          key=value and JSON renderers, schema registry, `describe`, dry-run planner, atomic writer
│                        `…cli.human`: picocli + JLine REPL for athenaCL's commands, `adapt` live audio preview
│                        `…cli.agent`: non-interactive one-shot commands (render/describe/analyze/validate/debug …),
│                          JSONL batch; also exposed as Gradle task `:athenacl-cli:agentCli --args="…"` (jvm-mw `debugCli` style)
├─ athenacl-graphics/    [L24] (late) Java2D port of athenaCL's map/plot commands (TPmap, TImap, TCmap, AUca, …)
├─ athenacl-csound/      [L25] (last, optional) Csound orchestras/score engines (csoundNative/External/Silence)
├─ athenacl-supercollider/ [L26] (last, optional) SuperCollider task output
├─ athenacl-soundbank-generaluser/  (asset artifact) GeneralUser GS .sf2 + license
├─ schema/              theme, preset, timeline, eventlog, result/<command>, error schemas (generated from records,
│                        versioned, published with docs)
├─ docs/                jvm-mw layout: phaseN-*.md slice specs, next-topics.md, big-topics.md, NAME_MAP.md (athenaCL
│                        Python → Java), setup.md; plus format reference (themes, presets, timeline, event log),
│                        CLI output reference (key=value keys + JSON), debugging guide, authoring guide
├─ AGENTS.md             main agent guide, jvm-mw style: project rules, determinism rules, "CLI (headless)" examples +
│                        `Command | Use when` table, F3/F4 keys, music gotchas, copyright guidance
├─ agent-guide/          (optional) SKILL.md generated from AGENTS.md's CLI section; printed by `athenacl agent guide`
├─ conformance/          golden corpus (pregui oracle outputs), runners, harvested doctest fixtures
└─ samples/
   ├─ gdx-demo/          small libGDX desktop game switching states (keyboard), overlays, stingers
   └─ themes/            fantasy-dungeon.yaml, scifi-space.yaml (first two starter themes; evolved from 04 §4.2's fantasy/sci-fi
                         examples), later preset-based examples (celtic-tavern.yaml, …)
```

Dependency graph (bottom-up, so the tool modules exist before the music modules; `[Lnn]` = the layer that introduces each, §7):
```
events [L0b] ← instruments [L1b,L6b] ← render [L0b,L1]
events ← analysis [L0b] ← debug [L0b]
events ← theory [L2] ← rhythm [L3] ← motif [L7] ← presets [L5] ← adaptive [L4]
compat [L12] ← core/athenaCL port [L13–L20] → used by theory/rhythm (sieves, set classes) and adaptive (`po:` generators)
events, rhythm ← fx [L9b] ← render (the PCM path runs Gervill → fx → sink once L9b exists)
{adaptive, render, debug} ← gdx [L6]
everything ← cli [L0a, extended by every layer]
```
`adaptive` writes to the `OutputSink` interface in `events`, so it doesn't depend on `render`. `analysis` uses `theory`
for chord/key estimates once L2 exists (a pitch-class-only fallback before that). The `cli` module grows a command
group per layer.
Debug *hooks* (`DebugSink`, event ids, seed paths) live in `events` as tiny interfaces with a no-op default.
The `debug` module only consumes them, so games don't pay for tooling they don't load.
`presets` is mostly data. Its Java code only models and merges presets, so new genres need no code.
`core` has **no** `javax.sound` dependency (pure model and generation). `render` is the only module touching `com.sun.media.sound`.
`fx` works on float PCM blocks only (no `javax.sound`), so it's testable with synthetic signals.

---

## 4. Mapping athenaCL (pregui) modules to Java packages

Base package `org.athenaclj` (placeholder). Line counts are physical lines from 03 §2.

| pregui module(s) | Lines | Java module / package | Notes | Layer |
|---|---|---|---|---|
| `libPmtr/basePmtr.py` (`Parameter` :293, `__call__` :463, `Selector` :106, `RhythmParameter`) | 1224 | core `…core.pmtr` (`ParameterObject`, `RhythmParameterObject`, `Selector`) | Pull-based `next(t, RefContext)` | L14 |
| `libPmtr/parameter.py` (factory/registry, name tables) | 999 | core `…core.pmtr.registry` | Explicit registry + annotations replaces `dir()`/`getattr` (03 §3) | L14 |
| `libPmtr/valueSingle.py` (46 POs) | 3435 | core `…core.pmtr.value` | `Constant` :225, `BasketGen` :257, `SieveList` :516, `MarkovValue` :2846, `GrammarTerminus` :3037, … | L15 |
| `libPmtr/valueSingleOmde.py` (44) | 1904 | core `…core.pmtr.omde` | Waves, break-points, random distributions | L16 |
| `libPmtr/rhythmSingle.py` (13) | 1251 | core `…core.pmtr.rhythm` | `PulseTriple` :412, `Loop` :224, `MarkovPulse` :726, `GaRhythm` :96 | L16 |
| `libPmtr/textureStatic.py` (27), `cloneStatic.py` (3), `cloneFilter.py` (17), `valueFile.py` (4) | 1950 | core `…core.pmtr.{texture,clone,file}` | | L17 |
| `libTM/baseTexture.py`, `texture.py`, 14 modules | 4726 | core `…core.texture` | Ported 1:1 for offline/compat. Adaptive gets incremental wrappers (§6.3). | L14 (LineGroove), L17 |
| `clone.py` | 988 | core `…core.clone` | | L17 |
| `pitchTools.py` | 991 | theory `…theory.pitch` | Pitch names, ps/pc/midi/fq conversions | L13 (L2 for theory API) |
| `pitchPath.py` (`PolyPath`) | 445 | core `…core.path` | | L13 |
| `multiset.py` (`Multiset` :555), `setTables.py` (3594, data) | 4907 | theory `…theory.setclass` | `setTables` **generated** into a resource/class by a build script | L13, L18 (setTables) |
| `sieve.py` (`Sieve` :797, `SievePitch` :1557) | 1822 | theory `…theory.sieve` | Own expression parser (replaces `eval`, `sieve.py:1421`) | L18 |
| `temperament.py`, `spectral.py` | 878 | theory `…theory.tuning`, `…theory.spectral` | Microtonal output through MIDI Tuning/pitch bend (Gervill supports MIDI Tuning, 01 §3.1) | L18 |
| `rhythm.py`, `quantize.py` | 1461 | rhythm `…rhythm.pulse`, `…rhythm.quantize` | | L14, L16 |
| `markov.py`, `grammar.py`, `automata.py`, `feedback.py`, `genetic.py`, `dice.py`, `chaos.py`, `permutate.py`, `table.py`, `envelope.py`, `ornament.py`, `interpolate.py` | 7,349 | core `…core.gen.*` | Shared by POs, motif engine and adaptive layers | L18 (markov/grammar earlier if L4 needs them) |
| `omde/{functional,rand,bpf,oscillator,miscellaneous}.py` | 2356 | core `…core.omde` | | L16 |
| `unit.py`, `drawer.py`, `error.py` | 2664 | compat `…compat`, core `…core.util` | Split the `drawer` grab-bag into typed helpers | L12 |
| `eventList.py` (engines), `outFormat.py` | 3430 | core `…core.event` + render `…render.midi` | `EngineMidiFile` (`eventList.py:2334`), text engines; Csound/SC/PD engines → optional modules | L13 |
| `midiTools.py` (`MidiScore`, 960 PPQ) | 441 | render `…render.smf` | Byte-exact writer | L13 (byte-exact; L0b has a plain SMF writer) |
| `libOrc/baseOrc.py`, `orc.py`, `generalMidi.py` | 730 | core `…core.orc` | GM + GM percussion. Instrument names reused by themes. | L13 |
| `libOrc/csoundNative.py`, `csoundExternal.py`; `EngineCsound*` | ~9,000 | athenacl-csound | Last, optional | L25 |
| `libOrc/superColliderNative.py`; `EngineSuperColliderTask` | ~460 | athenacl-supercollider | Last, optional | L26 |
| `audioTools.py` (AIFF/WAV via `aifc`/`audioop`) | 1180 | render `…render.audio` (javax.sound.sampled) | Only the parts commands need | L1/L13 |
| `athenaObj.py` (`AthenaObject`, `Interpreter`) | 1783 | core `…core.session` + cli | Session state headless in core, REPL in cli | L14, L20 |
| `command.py` (~110 commands) | 9131 | core `…core.command` (logic) + cli `…cli.model` (typed args/results) + `…cli.human`/`…cli.agent` | Each command = class with typed args **and a typed result record**, rendered as athenaCL text (`typeset`-compatible, human REPL), `key=value` lines (agent default) or JSON (`--json`) | L14, L19, L20 |
| `argTools.py`, `typeset.py`, `language.py` | 2616 | core `…core.command.text`; `language` → `ResourceBundle` | | L14 |
| `ioTools.py`, `xmlTools.py`, `prefTools.py` | 1163 | core `…core.project` (XML), `…core.prefs` | Python-literal values parsed with `PyLiteralParser` (03 §3) | L19 |
| `dialog.py`, `osTools.py`, `fileTools.py` | 4002 | cli (JLine), `java.nio` | Mostly replaced, not ported | L20 |
| `help.py`, `faq.py`, `info.py` | 1763 | cli `…cli.help` resources | Text content ported as resources; also served as `help --json` | L20 |
| `imageTools.py`, `fontLibrary.py`, `libGfx/*`, `htmlTools.py` | ~8,560 | athenacl-graphics (Java2D) | Late. `htmlTools` dropped unless a command needs it. | L24 |
| `test/*`, `demo/*` | 1890 | conformance | Oracle corpus | L12 |

---

## 5. Domain model and API sketches

### 5.1 Key types

```java
// ---- compat ----
public interface RandomSource { double nextDouble(); int nextInt(int bound); RandomSource fork(long... scope); }
public final class CompatRandom implements RandomSource { /* CPython MT19937, seed(int) via init_by_array, _randbelow */ }
public final class Py { static long round(double x); static double round(double x, int nd); static int floorMod(int a,int b); }

// ---- theory ----
public record PitchClass(int pc) {}
public record Pitch(double ps) { int midi(); PitchClass pc(); }          // athenaCL "pitch space", microtonal
public record SetClass(int card, int index, String forte) {}            // from generated setTables
public record Scale(Pitch tonic, Mode mode) { Pitch degree(int d); boolean contains(Pitch p); }
public record Key(PitchClass tonic, Mode mode) {}
public record Chord(Pitch root, ChordQuality quality, List<Integer> extensions, int inversion) {}
public record RomanNumeral(int degree, ChordQuality quality, Optional<Key> applied) {}
public record Progression(List<HarmonicEvent> events) {}                 // chord + duration (harmonic rhythm)
public interface VoiceLeader { List<Pitch> voice(Chord next, List<Pitch> previous, VoicingRules rules); }
public interface Modulation { List<HarmonicEvent> bridge(Key from, Key to, Meter meter); } // pivot/direct/common-tone

// ---- rhythm ----
public record Meter(int numerator, int denominator, int[] grouping) {}  // 7/8 = 2+2+3
public record MusicalTime(long bar, Rational beat) {}                   // exact position
public record TempoMap(List<TempoChange> changes) { long toMicros(long tick); }
public record Groove(double swing, double[] microTimingTicks, double[] velocityAccents) {}
public interface Humanizer { NoteEvent apply(NoteEvent n, RandomSource rng); }
public record RhythmLayer(String name, Meter meter, int subdivision) {} // independent layer clocks → polyrhythm 3:2, 4:3…

// ---- events (shared by core, motif, adaptive, render) ----
public record NoteEvent(long id /* deterministic: (bar, layer, index) */, long startTick, long durationTicks,              // durations > 1 beat, ties are just long durations
                        Pitch pitch, int velocity, Articulation art, String layer, int channel) {}
public sealed interface ControlEvent permits ProgramChange, ControlChange, PitchBend, TempoEvent, MeterEvent {}

// ---- core (athenaCL) ----
public interface ParameterObject { Object next(double t, RefContext ctx); }   // port of __call__(t, refDict)
public interface Texture { EventSequence score(TextureContext ctx); }          // offline athenaCL semantics
public final class AthenaSession { PathLibrary paths(); TextureLibrary textures(); CloneLibrary clones();
                                   EventList render(); void seed(long tp, long tm); }

// ---- motif ----
public record Motif(List<MotifNote> notes, Meter meter, Optional<String> name) {} // scale degrees + rhythm + accents
public sealed interface MotifTransform permits Repeat, Transpose, Invert, Retrograde, Augment, Diminish,
                                               Fragment, Sequence, Ornament, Displace {}
public interface DevelopmentPlanner { List<MotifTransform> planPhrase(PhraseForm form, Intensity i, RandomSource r); }
public enum PhraseForm { SENTENCE, PERIOD, AABA, ABAC, FREE }

// ---- adaptive ----
public record Theme(String id, InstrumentSelection instruments /* kits + overrides, §6.12 */, Map<String, StateDef> states, Map<String, OverlayDef> overlays,
                    Map<String, StingerDef> stingers, List<TransitionRule> transitions, Map<String, InstrumentDef> instruments) {}
public record StateDef(String id, TempoRange tempo, Meter meter, KeySpec key, HarmonySpec harmony,
                       List<LayerDef> layers, MotifSpec motifs, LoudnessTarget loudness) {}
public record OverlayDef(String id, List<LayerDef> layers, Map<String, Object> modifiers) {} // e.g. "rain", "lowHealth"
public record TransitionRule(String from, String to, Quantize quantize, boolean retract,
                             Optional<String> stinger, Optional<BridgeSpec> bridge, int crossfadeBars, int minDwellBars) {}
public enum Quantize { IMMEDIATE, BEAT, BAR, PHRASE }
public interface LayerGenerator { List<NoteEvent> generate(BarContext ctx, LayerState st, RandomSource rng); }

// ---- effects stage (fx, L9b–L9d; §6.11) ----
public record BusDef(String id, List<String> layers, List<FxUnitDef> fx) {}
public sealed interface FxUnit permits Biquad, Eq, Compressor, Saturator, Bitcrusher, Reverb, Delay, Chorus, Limiter {
    void process(float[][] block, int frames, AutomationContext ctx); }      // allocation-free, 64-frame blocks
public sealed interface Automation permits Constant, ByIntensity, ByState, Lfo, Sweep, BeatEnvelope, Combined {
    double valueAt(MusicalTime t, DirectorView v); }                        // musical clock, never wall clock
public record SlideSpec(String mode /* bend|portamento */, Rational time, int bendRange, double probability) {}
public record SampleClip(Path file, ClipKind kind /* LOOP|ONE_SHOT|BED */, Optional<Double> bpm, OptionalInt bars, String license) {}
// ---- instrument kits (instruments, L1b/L6b; §6.12) ----
public record InstrumentKit(String id, Optional<String> extendsKit, Map<String, SoundbankSource> sources, Map<String, InstrumentDef> instruments) {}
public record SoundbankSource(String key, Optional<String> id, Optional<Path> path, SoundbankFormat format /* SF2|DLS|SFZ */,
                              String sha256, LicenseInfo license) {}
public record LicenseInfo(String spdx, String attribution, URI url, boolean redistribute, boolean commercial, Optional<Path> file) {}
public record InstrumentDef(String name, String source, OptionalInt bank, OptionalInt program, Optional<String> preset,
                            OptionalInt drumkit, Optional<Path> sfz, double gainDb, int transpose, KeyRange keyRange,
                            OutOfRange outOfRange, List<InstrumentRef> fallbacks) {}
public record InstrumentRef(String kit, String name) {}                       // "gm-lite:harp" or "gm:46"
public record SynthSlot(int bank, int program, boolean percussion) {}         // allocated, never the source's own patch
public interface KitResolver { ResolvedInstruments resolve(Theme t, List<InstrumentKit> kits, LicensePolicy p); } // + fallbacks taken
// ---- hooks + critic (motif, L7b; §6.13) ----
public record HookSpec(IntRange length, List<RhythmCell> cells, LeapSpec leap, Map<Contour, Double> contours,
                       StrongBeatRule strongBeats, Map<PhraseForm, Double> forms, CadenceSpec cadence, RepetitionSpec repetition) {}
public record CriticSpec(int candidates, Map<String, Double> weights, DoubleRange noveltyTarget, List<HardRule> hardRules,
                         Optional<Path> weightsFile, List<Path> references) {}
public record FeatureScores(Map<String, Double> values, double total, Optional<String> rejectedBy) {}
public record Candidate(int index, String seedPath, Motif motif, FeatureScores scores) {}
public interface MelodyFeature { String id(); double score(Motif m, HarmonyContext h, StyleModel s); }   // 0..1, pure
public interface MelodyCritic { CriticResult choose(List<Candidate> cs); }   // deterministic: max total, ties → lowest index
public interface HookGenerator extends MotifSource { List<Motif> candidates(HookSpec spec, HarmonyContext h, RandomSource rnd, int n); }
public interface MixEngine { void renderBlock(float[][] out, int frames); FxMeters meters(); }  // Gervill buses → fx → master

// ---- presets ----
public record GenrePreset(String id, List<String> extendsIds, TempoRange tempo, List<Meter> meters, List<ModeRef> modes,
                          Map<String, DrumPattern> drums, Map<String, Groove> grooves, ChordVocabulary chords,
                          List<ProgressionTemplate> progressions, List<SongForm> forms,
                          Map<String, List<InstrumentSuggestion>> instrumentation, // role → GM/GS program candidates
                          Map<String, StateHints> stateHints) {}           // optional per-state defaults (combat, trading…)
public record DrumPattern(Meter meter, int stepsPerBar, Map<String, String> lanes, Map<Double, DrumPattern> intensityTiers) {}
public interface PresetLibrary { GenrePreset get(String id); Set<String> ids(); }
public final class ThemeResolver { Theme resolve(RawTheme raw, PresetLibrary lib); } // preset → theme overrides → state overrides
public record ValidationReport(List<Issue> errors, List<Issue> warnings) {}         // JSON-pointer paths, messages, hints

// ---- agent CLI contract (cli …cli.model) ----
public record Result<T>(String schema /* "athenacl.result.render/1" */, boolean ok, String command, T data,
                        List<Issue> warnings, List<Issue> errors, Map<String, String> meta /* version, seed, durationMs */) {}
public record Issue(String code /* THEME_UNKNOWN_PRESET */, Severity severity, String path /* JSON pointer */,
                    String message, Optional<String> fix, Optional<String> docRef) {}
public enum ExitCode { OK(0), INTERNAL(1) /* uncaught, as in jvm-mw */, USAGE(2) /* jvm-mw: unknown command */,
                       INVALID_INPUT(3), NOT_FOUND(4), RUNTIME(5), CHECK_FAILED(6), ENVIRONMENT(7); }
public interface CommandSpec<A, R> { String name(); Class<A> args(); Class<R> result(); boolean mutatesFiles(); Plan plan(A a); R run(A a, Ctx c); }

// ---- stateless offline rendering (agent + debug; no session server) ----
public record RenderRequest(Path theme, Timeline timeline /* file + inline --at controls */, long seed,
                            BarRange bars /* window to emit, e.g. 17-18 */, Set<OutputFormat> formats, DebugOptions debug) {}
public interface OfflineRenderer { RenderResult render(RenderRequest r); }  // always generates deterministically from bar 1
public record RenderResult(EventLog events, List<BarTrace> bars, Map<String, Path> files, String canonicalHash) {}

// ---- debug hooks (in adaptive/core, no-op by default) and records (debug) ----
public interface DebugSink { default void bar(BarTrace t) {} default void transition(TransitionTrace t) {}
                             default void scheduler(SchedulerSample s) {} default void warning(DebugWarning w) {}
                             DebugSink NOOP = new DebugSink() {}; }
public record Provenance(long eventId, String layer, String generator /* e.g. "po:markovValue", "texture:LineGroove" */,
                         List<String> motifTransforms /* ["motif:m3", "invert", "augment x2"] */,
                         String seedPath /* "42/combat/lead/bar17/pitch#3" */, String reason /* "chord tone (Dm7 3rd), voice-led from F4" */,
                         Map<String, Object> inputs) {}
public record BarTrace(long bar, String state, float intensity, List<String> overlays, Key key, Chord chord, Meter meter,
                       double bpm, List<LayerTrace> layers, long generationNanos) {}
public record TransitionTrace(long requestTick, long appliedTick, String from, String to, Quantize q, String ruleId,
                              int retractedEvents, int releasedNotes, Optional<String> stinger, Duration requestToAudible) {}
public record SchedulerSample(long tick, Duration lookaheadMargin, int queuedEvents, int lateEvents,
                              Duration generationTime, int bufferUnderruns, double cpuMsPerBar) {}
public interface EventLog { Stream<LoggedEvent> stream(); String canonicalHash(); } // JSONL, wall-clock fields excluded from hash
```

### 5.2 Adaptive API

```java
AdaptiveMusic music = AdaptiveMusic.builder()
        .theme(Themes.load(Path.of("themes/fantasy-dungeon.yaml")))
        .soundbank(SoundbankSource.chain(SoundbankSource.themeDefault(), SoundbankSource.cachedGeneralUser(),
                                         SoundbankSource.emergency()))
        .output(Outputs.gervillRealtime())             // or Outputs.midiDevice("loopMIDI"), Outputs.events(sink)
        .seed(42L)
        .lookahead(Duration.ofMillis(150))
        .recordControlLog(true)                        // for deterministic replay (§6.6)
        .build();

music.start("peaceful", 0.2f);
music.setIntensity(0.55f);                             // smoothed per bar; layer thresholds with hysteresis
music.addOverlay("rain", 0.6f);                        // layered overlay on top of the main state
music.setState("combat", 0.8f);                        // rule: quantize=BEAT, retract=true, stinger=combatStart
music.trigger("victory");                              // stinger, quantized per its definition
music.removeOverlay("rain");
music.switchTheme(Themes.load(Path.of("themes/scifi-space.yaml")));    // soundbank preloaded off-thread, swapped at a bar line
music.listeners().onBar(e -> {});                      // delivered on the game thread via poll()
music.close();

// offline (CI, previews, trailers): same director, no wall clock
OfflineSession s = AdaptiveMusic.offline(theme, 42L);
s.at(0).start("peaceful", 0.3f).at(16.4).setState("sneaking", 0.4f).at(29.1).setState("combat", 0.6f);
s.renderWav(Path.of("preview.wav"), Duration.ofSeconds(60));
s.writeMidi(Path.of("preview.mid"));
s.writeControlLog(Path.of("preview.ctl.json"));
```

### 5.3 athenaCL API (ported core) and CLI

```java
AthenaSession a = AthenaSession.create(SessionOptions.compat());   // CompatRandom, athenaCL semantics
CommandProcessor cp = new CommandProcessor(a);
cp.run("emo mp"); cp.run("tin a 36"); cp.run("tie r pt,(c,2),(bg,oc,(7,5,2,1,1)),(c,1)"); cp.run("eln beat.xml");
// typed equivalent
a.textures().create("b", TextureType.LINE_GROOVE, 37)
 .set(Slot.RHYTHM, Po.pulseTriple(Po.constant(2), Po.basketGen(Select.ORDERED_CYCLIC, 3, 5), Po.constant(1)));
```

```
$ athenacl                       # REPL with the athenaCL command set (tin, tie, eln, tpsd, …)
$ athenacl run script.ath -o out.mid
$ athenacl adapt themes/fantasy-dungeon.yaml --seed 42      # keyboard: 1-6 states, +/- intensity, o overlay, s stinger
$ athenacl render themes/fantasy-dungeon.yaml --script timeline.json --wav out.wav --mid out.mid
$ athenacl soundbank install generaluser            # download from our mirror, verify SHA-256
$ athenacl theme validate themes/tavern.yaml --format json   # schema + semantic checks, machine-readable report
$ athenacl theme schema > theme.schema.json         # emit the published JSON schema
$ athenacl theme explain themes/tavern.yaml         # print the fully resolved theme (preset + overrides)
$ athenacl preset list
$ athenacl preset show celtic                     # fields, limits note, example YAML
$ athenacl preset audition reggae --bars 16 --seed 1 --wav r.wav

# agent-oriented (every command also accepts --json, --dry-run where it writes, --seed where it generates)
$ athenacl describe theme themes/tavern.yaml --json   # resolved states, overlays, layers, stingers, transition rules
$ athenacl describe presets|pos|textures|instruments|commands --json   # instruments: per soundbank, bank/program/name
$ athenacl help --json render                       # args, types, defaults, exit codes, result schema id
$ athenacl schema result.render                     # any published schema by id
$ athenacl render themes/tavern.yaml --timeline t.json --seed 42 --out out/ --formats mid,wav,events,txt --json
$ athenacl describe-music out/events.jsonl --bars 9-16 --roll --json   # chords/key per bar + ASCII piano roll
$ athenacl analyze out/ --json                      # metrics (§6.9.4)
$ athenacl render themes/tavern.yaml --timeline t.json --seed 42 --bars 17-18 --at "17 state combat 0.8"
                                                    # "step": re-run with a later window + more --at controls
$ athenacl batch < cmds.jsonl                       # one command per line → one result per line (one JVM, no state)
$ gradlew :athenacl-cli:agentCli --args="describe theme themes/tavern.yaml"   # same, via Gradle (jvm-mw style)

# debug
$ athenacl debug inspect out/events.jsonl --layer lead --bars 17-18 --where 'velocity>100'
$ athenacl debug explain out/events.jsonl --event 81234      # provenance of one note
$ athenacl debug bar themes/tavern.yaml --timeline t.json --seed 42 --bar 17   # one bar: BarTrace + per-note provenance
$ athenacl debug trace out/ --transitions                    # state-machine/transition trace
$ athenacl debug replay session-2026-10-09.ctl.json --wav replay.wav   # replay a log dumped from the game
$ athenacl debug bisect --good runA/ --bad runB/             # first divergent bar/event (or first bad control call)
$ athenacl debug check out/ --stuck-notes --loudness         # exit 6 on findings
$ athenacl diff themes/a.yaml themes/b.yaml               # semantic diff of resolved configs
$ athenacl diff runA/ runB/ --fail-on-diff                # render diff per bar
$ athenacl debug determinism themes/tavern.yaml --timeline t.json --seed 42 --runs 2 --fork-jvm
```

### 5.4 libGDX usage (desktop/LWJGL3)

```java
public class MyGame extends ApplicationAdapter {
    private GdxAdaptiveMusic music;
    private GdxMusicDebugOverlay debugOverlay;

    @Override public void create() {
        Theme theme = Themes.load(Gdx.files.internal("music/fantasy-dungeon.yaml").read());
        music = GdxAdaptiveMusic.builder(theme)
                .soundbank(SoundbankSource.file(Gdx.files.internal("music/GeneralUser-GS.sf2").file()))
                .seed(1234L)
                .bufferMillis(80)                 // AudioDevice pull size; writeSamples blocks, so it runs on its own thread
                .debug(DebugOptions.dev()          // provenance + traces + session recording; DebugOptions.off() in release
                        .dumpKey(Input.Keys.F3))   // jvm-mw convention: F3 writes build/athenacl-snapshot.txt (key=value,
                                                   // also to clipboard) + control/event logs for `athenacl debug replay`
                .build();                         // starts director + PCM pump threads
        debugOverlay = new GdxMusicDebugOverlay(music, font);   // from athenacl-gdx …gdx.debug
        music.start("peaceful", 0.2f);
    }

    @Override public void render() {
        float threat = world.threatLevel();
        if (world.inCombat())      music.setState("combat", threat);
        else if (threat > 0.4f)    music.setState("danger", threat);
        else if (player.sneaking()) music.setState("sneaking", 0.3f);
        else                       music.setState("peaceful", 0.2f);
        music.setOverlay("lowHealth", player.health() < 0.25f ? 1f : 0f);
        if (world.enemyKilledThisFrame()) music.trigger("hit");
        music.poll();                             // deliver onBar/onBeat callbacks on the GL thread (libGDX isn't thread-safe)
        // ... draw
        debugOverlay.draw(batch);                 // F4 toggles (jvm-mw convention): state, intensity, overlays, active layers, bar:beat,
                                                  // key/chord, queue/lookahead, latency, CPU ms per bar, underruns
    }

    @Override public void pause()   { music.pause(); }
    @Override public void resume()  { music.resume(); }
    @Override public void dispose() { music.dispose(); }
}
// Desktop launcher: add JVM arg --add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED
// (or Add-Exports manifest attribute in the runnable jar; 01 §3.3)
```

Implementation note: the PCM pump thread reads Gervill's `openStream` in blocks and calls
`AudioDevice.writeSamples(...)` (blocking, interleaved stereo float/short;
[libGDX PCM docs](https://libgdx.com/wiki/audio/playing-pcm-audio)). The music clock is derived from
frames written, so the engine and the audible output share one clock. Setting OpenAL buffer
size/count through the LWJGL3 configuration should be checked for underflow (libGDX issue
[#4859](https://github.com/libgdx/libgdx/issues/4859) notes default buffers are tight for `AudioDevice`).

---

## 6. Design details for the new requirements

### 6.1 Theory and harmony
- **Chords and progressions:** Roman-numeral grammar per mode, functional transition tables
  (T → PD → D → T) as weighted Markov chains (reusing `markov.py`). The progression is generated per
  phrase with a **cadence slot** at phrase end.
- **Harmonic rhythm:** per state and intensity, measured in chords per bar or beats per chord. It may
  accelerate toward cadences.
- **Voice leading:** greedy/DP minimal-motion voicing with constraints (common tones held, no parallel
  5ths/8ves in outer voices, range limits per layer, spacing rules). Pads and brass use it; bass uses
  root/inversion logic.
- **Key/mode changes and modulation:** direct modulation at phrase boundaries, pivot-chord modulation
  (common chords between keys), and mode mixture. State transitions can request `keyPolicy: keep | relative |
  parallel | themeKey`. Set-class Paths remain available as an alternative harmony source (athenaCL style).
- **Microtonality:** athenaCL temperaments stay possible through MIDI Tuning / pitch bend.

### 6.2 Rhythm, meter and groove
- **Independent rhythm layers:** each layer has its own subdivision and pattern length. Polyrhythm comes
  from layer clocks (e.g. 3 against 4) aligned at bar or phrase lines. Drum kit pieces (kick, snare,
  hats, toms) and auxiliary percussion are separate layers with their own generators (athenaCL
  rhythm POs, Euclidean patterns, Markov pulses).
- **Long notes, ties and rests:** `NoteEvent.durationTicks` is independent of the bar. The scheduler
  keeps **pending note-offs** across bars. Retraction and transitions emit note-offs or fade CC11 for
  held notes. Rests are explicit in rhythm patterns (athenaCL's accent=0 already means rest).
- **Meter changes:** only at bar lines, through `MeterEvent` in the tempo/meter map. Grouping (2+2+3)
  drives accents.
- **Groove and humanization:** swing ratio per layer, micro-timing templates, velocity accent maps, and
  seeded Gaussian jitter (timing ±N ticks, velocity ±M) applied **after** generation, so the
  quantized structure stays testable.
- **Dynamics and articulation:** velocity curves per intensity, CC11 expression ramps (crescendo),
  articulation → duration factor + velocity + optional keyswitch or program variation (GS banks, 02 §1).

### 6.3 Motif engine
- **Generation:** a motif is 2–8 notes as scale degrees, durations, accents, contour. It is generated from
  athenaCL generators (Markov or grammar POs for contour, rhythm POs for durations) under constraints
  (range, leap limits, a strong-beat chord tone).
- **Development:** the planner assigns transformations per phrase slot by form: e.g. sentence =
  basic idea, repeat, fragment+sequence, cadence; period = antecedent, consequent with altered
  ending. Transformations: repetition, real/diatonic **transposition**, **inversion**, retrograde,
  **augmentation/diminution**, **fragmentation**, sequence, ornamentation (`ornament.py`, used by
  `MonophonicOrnament`), rhythmic displacement.
- **Theme memory:** each state keeps a small motif bank (seeded), so returning to a state brings
  back recognizable material. A "leitmotif" can be shared across states and transformed (e.g. the peaceful
  motif inverted and diminished in combat).
- **Composer-supplied material:** import motifs, progressions and drum patterns from MIDI files (and
  inline YAML note lists). Per layer, choose `source: generated | composed | mixed` (mixed means composed motifs
  developed by the engine).

### 6.4 Adaptive director
- **Main state + overlays:** one main state defines key, meter, tempo and base layers. Overlays add layers or
  modifiers (e.g. "lowHealth": heartbeat layer + filter/velocity modifiers). Overlays have their own
  intensity and quantization.
- **Intensity:** continuous values smoothed per bar. Discrete switches (layers, pattern tiers) use
  hysteresis thresholds (04 §2).
- **Transitions:** a rule matrix (specific beats generic; 04 §4.3). **Defaults (decided Oct 9):** entering any state
  waits for the next **bar**. Entering **`combat` or `danger` waits for the next beat with `retract: true`**. Leaving them
  (e.g. combat → peaceful) goes back to the bar default, optionally with a wind-down. `IMMEDIATE` exists (e.g. for
  stingers or a game's own rules) but no starter state uses it. Custom states default to `BAR`. Themes override per
  state or per from→to pair:
  ```yaml
  transitions:
    defaults: {quantize: bar}
    rules:
      - {to: [combat, danger], quantize: beat, retract: true, releaseMs: 100}
      - {from: combat, to: "*", quantize: bar, bridge: windDown}
  ```
  `BEAT`/`IMMEDIATE`
  with **retraction**: the scheduler keeps generated-but-unreleased events in its own queue and releases to the
  output only `lookahead` ahead (100–200 ms). On an urgent change it drops queued events after the switch
  point and sends note-offs or short release fades for sounding notes (default release ~100 ms, configurable per
  rule). This fixes the PoC's full-bar delay (04 §7: combat applied 2.44 s late). At 144 BPM a beat is ~0.42 s, so a
  combat request is heard within about one beat plus lookahead.
- **Stingers:** defined per theme, quantized (beat/bar/immediate), with key/chord awareness (tonal
  stingers transposed to the current harmony), cooldowns, and an optional "mask the transition" role.
- **Loudness balancing (automatic, designed contour):** see §6.4.1. This replaces the PoC's hand tuning
  (04 §7: 20 dB spread before tuning).

#### 6.4.1 Loudness contour (decided Oct 9)
Each state sits at a comfortable loudness **relative to the others**. It isn't flat, and it never jumps.
Default contour (LU relative to the theme's `reference`; every theme can override any value):

| State | Default offset | Feel |
|---|---|---|
| sneaking | −1.5 LU | quietest, sparse but still present |
| peaceful | −1.0 LU | calm background |
| trading | −1.0 LU | calm, conversational |
| partying | +1.5 LU | lively |
| danger | +2.0 LU | tense, mid |
| combat | +4.0 LU | a few LU louder, never jarring |
| *custom states* | 0 LU unless set (or `like: <state>` to copy a starter state's offset) | |

That's a **5.5 LU spread** by default. The validator warns above **6 LU** (`maxSpread`) and errors above 9 LU.
```yaml
loudness:
  reference: -23          # LUFS integrated for the 0-LU point [placeholder default; games often mix louder, set per game]
  contour: {sneaking: -1.5, peaceful: -1, trading: -1, partying: 1.5, danger: 2, combat: 4}
  maxSpread: 6            # LU, warn if exceeded
  maxStepPerTransition: 4 # LU; larger jumps ramp over the transition (CC11) instead of stepping
  intensityRange: 1.5     # LU a state may move between intensity 0 and 1 (energy comes mostly from density/register)
  truePeakMax: -1         # dBTP
```
- **Never jarring:** a jump over `maxStepPerTransition` is ramped with CC11 across the transition beat or bar.
  Overlays may add at most +1 LU, and stingers are capped at +3 LU short-term above the current state.
- **Calibration (automatic):** at theme load (cached by theme + soundbank hash), render each state at
  intensity {0, 0.5, 1} over several seeds offline, measure EBU R128 integrated and short-term loudness in Java, and
  solve per-state/per-layer CC7 gains so each state lands on `reference + contour[state]` within **±1 LU**. The
  `theme explain` and `analyze` commands print the calibrated contour as `loudness.<state>=… LU` lines. Once the effects
  stage exists (L9b), calibration measures **after effects**, before the limiter (§6.11.7). It runs after per-instrument
  normalization (§6.12.4), and its cache key includes the resolved kits, because banks differ by ~7 LU (07).

### 6.5 Threading and latency (04 §4.7)
Game thread → lock-free queue → **director thread** (generation, scheduling, retraction) → output
(Gervill receiver with µs timestamps, or PCM pump into libGDX `AudioDevice`). Soundbank loading runs on a
background executor and swaps at a bar boundary. Budget: quantization wait + lookahead (100–200 ms) +
device buffer (~50–150 ms).

### 6.6 Determinism
- All randomness flows from `seed` through `RandomSource.fork(scope…)` keyed by
  (theme, state/overlay, layer, bar index, purpose). It never depends on wall-clock time or thread timing.
- **Control log:** every game call is applied at a quantized musical time (tick) and recorded as
  `(tick, command)`. Same seed + same log → identical events (hashable) and, offline, identical audio.
  This was verified in the PoC (04 §7).
- **Real-time caveat:** when the call lands relative to the lookahead depends on frame timing. The
  *recorded* log is deterministic and replayable, but two live sessions with the same inputs at slightly
  different times may differ. Optionally a "frame-quantized" mode snaps API calls to the game's
  fixed-timestep tick for lockstep and replay games.
- Audio bytes may differ across JDK versions if Gervill changes, so **event logs**, not audio, are the
  canonical snapshot.
- **Effects (§6.11.7):** DSP is a deterministic function of the event log, theme, soundbank and clips. It uses fixed
  64-frame blocks and fixed bus order. The offline render with FX is bit-stable on a given JDK.

### 6.7 Genre presets

A **preset** is a reusable, data-only bundle of genre conventions. A theme says `preset: <id>` (or a list,
merged left to right) and overrides any field at theme or state level. Presets are resolved at load time
into a plain `Theme`, so the director never sees presets and determinism is unaffected.

#### 6.7.1 What a preset contains
| Part | Content |
|---|---|
| Tempo/meter/mode | Typical BPM range, meters (with groupings, e.g. 12/8, 7/8 = 2+2+3), modes/scales (incl. world modes) |
| Drum patterns | Step-grid lanes per kit piece (`x . . x`), several intensity tiers, fills, GS kit suggestion (Standard, Room, Power, Electronic, 808/909, Dance, Jazz, Brush, Orchestral: all present in GeneralUser GS, 02 §1) |
| Grooves | Swing ratio, micro-timing offsets, accent maps, humanize ranges per layer |
| Harmony | Chord vocabulary (triads/7ths/9ths/power chords/sus/quartal), progression templates with weights (e.g. 12-bar blues, ii–V–I, i–♭VII–♭VI–♭VII, I–V–vi–IV), typical harmonic rhythm, cadence types |
| Song forms | Section patterns (AABA, verse/chorus, 12-bar, loop-based build/drop, ABAC) mapped onto phrase forms (§6.3) and intensity curves |
| Melody hints | Range, step/leap profile, rhythmic density, ornament style (e.g. celtic cuts/rolls via `ornament.py`, blues bends via pitch bend) |
| Instrumentation | Roles (lead, pad, bass, comp, drums, perc) → ranked GM/GS program suggestions (bank/program), with fallbacks to GM programs |
| State hints | Optional defaults per starter state (e.g. `combat` in `metal` = faster tier + double-kick lane) |

#### 6.7.2 Genre set and priority (priority decided Oct 9)
**First: `fantasy`** (for the user's dungeon crawler). It builds on orchestral/cinematic + folk/celtic + modal writing:
Dorian/Aeolian/Phrygian/Mixolydian; drones and low strings, harp, choir pads and sparse percussion for dungeon
exploration (sneaking/danger); taiko, brass and ostinato strings for combat; lute/fiddle/flute tavern feel for
trading/partying. GM/GS: Orchestral Harp 46, String Ensembles 48/49, Choir Aahs 52, French Horn 60, Flute 73, Fiddle
110, Taiko 116, Orchestral kit. Starter theme: `themes/fantasy-dungeon.yaml`.

**Second: `scifi-space`** (another of the user's projects). It builds on ambient + electronic/synth + cinematic: Lydian,
whole-tone and chromatic-mediant harmony; synth pads and arpeggiators, pulsing bass, sparse electronic percussion
(Electronic or 808/909 kit) rising to driving sequenced combat; bell/glass textures for trading. GM/GS: Pad 1 New Age
88, Pad 2 Warm 89, Pad 8 Sweep 95, Lead 1 Square 80, Lead 2 Saw 81, Synth Bass 38/39, FX 4 Atmosphere 99, Celesta 8.
Starter theme: `themes/scifi-space.yaml`.

**Later (L21, cuttable to post-1.0):**
orchestral/cinematic, ambient, folk/celtic, jazz/swing, blues, rock, funk, latin/bossa nova, reggae,
house/techno, hip-hop/trap, lo-fi, chiptune, metal, plus **world/ethnic mode packs**: e.g. maqam-inspired
(Hijaz, approximated in 12-TET by default), Indian raga-inspired scales (as scales, not full raga grammar),
Japanese (in/yo, hirajoshi), pentatonic families, klezmer (freygish), flamenco (Phrygian dominant), gamelan-like
(pelog/slendro approximations). Microtonal versions depend on open question 11 (MIDI Tuning). Mode packs
describe *scales and stock textures*, not claims of authentic traditional practice. Docs say so.

#### 6.7.3 GM/GS soundfont limits (documented per preset)
- **No vocals:** GM/GS has only Choir Aahs/Concert Choir (52), Voice Oohs (53) and Synth Voice (54). No lyrics or
  rap. Hip-hop/trap presets are instrumental beats.
- **Modern production: limited before L9b, partly covered after.** Without the effects stage (§6.11) there's no sidechain
  pumping, no filter sweeps, no saturation and no loops/crackle. Lo-fi falls back to slow tempo, swing, pitch-bend detune,
  Rhodes/EP and soft drums. **With L9b–L9d:**
  - automatable filters/sweeps, kick- or beat-keyed ducking, saturation/bitcrush, tempo-synced delay, reverb, chorus
    and a master limiter;
  - 808 glides via generated pitch bend;
  - optional sampled loops, one-shots and vinyl crackle (user-supplied clips).

  Still missing: real vocals, mastering-grade processing, and the sound of modern synths and samples beyond what GM/GS
  plus clips give. Presets declare `needs: fx` / `needs: samples` and fall back gracefully when those are off.
- **Electric guitar realism** for rock/metal is limited (GM 29 Overdriven/30 Distortion Guitar). Metal relies on
  power-chord voicing, palm-mute articulation (short durations) and double-kick patterns.
- **Ethnic instruments** are few (Sitar 104, Shamisen 106, Koto 107, Kalimba 108, Bagpipes 109, Fiddle 110, Steel
  Drums 114, Taiko 116, Agogo 113, etc.). Presets suggest the closest available ones.
- Genres that gain most from the effects stage: house/techno, hip-hop/trap and lo-fi (ducking, filters, 808 glides,
  crackle), sci-fi space (sweeps, delay), chiptune (bitcrush) and metal (saturation on guitars).
- **Instrument kits (§6.12) widen the palette:** presets name roles → canonical instruments, so a theme can pull a
  better harp or choir from another SF2, or orchestral sections from SFZ libraries (L6b). No free bank has a true lute
  (07), so fantasy uses nylon guitar/harpsichord until one is found (Q29).
- Each preset carries a `limits:` note shown by `preset show` and in the docs, and can name a better custom
  soundbank as an option (pluggable soundbank, §2.1).

#### 6.7.4 YAML example
```yaml
id: tavern
preset: celtic                 # or [celtic, folk] (merged left to right)
instruments: {kits: [gm-lite]}   # instrument kits (§6.12); default kit
overrides:                     # theme-wide overrides of preset fields
  tempo: [104, 124]
  modes: [dorian, mixolydian]
  instrumentation:
    lead: [{gm: 110, name: Fiddle}, {gm: 73, name: Flute}]
    drums: {kit: room}
states:
  peaceful:
    preset: celtic             # inherits theme preset; state-level overrides below
    form: AABB                 # reel-like tune form
    groove: {swing: 0.12}
    layers:
      drums: {pattern: bodhran-light}
  partying:
    preset: celtic
    meter: "6/8"               # jig
    intensity_map: {drums: [0.2, 1.0], lead_ornament_rate: [0.1, 0.5]}
  combat:
    preset: [celtic, orchestral]   # borrow cinematic percussion and brass on top
    tempo: [136, 150]
    transition: {quantize: beat, retract: true}
```
Merge rule: built-in defaults → preset(s) in order → theme `overrides` → state block. Maps merge deeply,
lists replace (an explicit `+append` form is available). `athenacl theme explain` prints the result.

### 6.8 Supporting external authoring agents (song → theme is out of scope)

The library does **not** analyze songs. External agents (AI or human tools) may listen to or read a
reference and write a theme. The project makes that reliable through:
1. **Published JSON schema** (`theme.schema.json`, `preset.schema.json`, `timeline.schema.json`), versioned
   (`schemaVersion: 1`), generated from the Java records so it never drifts. It has descriptions, enums
   (preset ids, GM program names, kits, modes) and examples on every field.
2. **Thorough format documentation:** the field reference, merge rules, worked examples per genre, a "how
   the director interprets intensity" chapter, and the GM/GS limits list.
3. **`athenacl theme validate`:** schema errors plus semantic checks (unknown preset/instrument, pattern length
   vs meter, ranges outside an instrument's playable range, unreachable states, missing loudness targets).
   Output as JSON (paths as JSON pointers, `fix` hints) so agents can iterate automatically. Exit codes follow
   the shared table in §6.9.2 (0 valid, 3 invalid; warnings exit 0 unless `--strict`).
4. **Deterministic preview/render CLI:** `athenacl render theme.yaml --timeline t.json --seed N --wav/--mid
   --events events.json`. Same inputs give the same events, so an agent can render, inspect (events JSON,
   loudness report) and refine without listening in real time.

**Copyright guidance (in the authoring docs):** borrow *style*: genre, tempo, meter, mode, groove, instrumentation,
progression *types* (common progressions and drum patterns aren't protected), and form. **Don't copy melodies,
distinctive riffs or lyrics verbatim** from a reference song, and don't transcribe a specific recording into
composer-supplied motifs unless the user owns or licenses it. Agents should use generated motifs or original
composer material. This is guidance, not legal advice. The tool doesn't police it.

### 6.9 Agentic CLI (agents and scripts as first-class users)

One command model (`…cli.model`) and two front ends in the same `athenacl-cli` module and launcher: the **human**
front end (athenaCL REPL, colors, live `adapt` preview) and the **agent** front end (plain one-shot commands). There
is **no server mode**. This mirrors jvm-mw, whose agent tooling is a headless one-shot debug CLI plus in-game dumps
(doc 06). Everything works **headless** (no audio device, `java.awt.headless=true`): offline rendering uses Gervill's
stream API and a virtual clock.

#### 6.9.1 Contract rules
- **Non-interactive by default** when stdin isn't a TTY or `--json` is given: never prompt, never page, no colors.
  Missing input is an error with a fix hint, not a question.
- **Default output: grep-stable `key=value` lines** (jvm-mw convention, doc 06). One record per line, led by a record
  kind (`bar=17 state=combat …`, `note id=… layer=lead …`), a closing summary line of counters, and long lists capped
  with `... truncated, N more`. Example:
  ```
  bar=17 state=combat i=0.72 meter=4/4 bpm=144 key=D-dorian chord=Dm7 layers=drums,bass,pad,lead,brass gen.ms=1.8
  note id=81234 bar=17 beat=1 layer=lead pitch=A5 dur=1/4 vel=96 po=markovValue motif=m3:invert seed=42/combat/lead/bar17/pitch#3 why=chord-tone(Dm7.5th)
  bars=2 notes=57 retracted=0 stuck=0 hash=9f3c…
  ```
- **`--json` on every command** (difference from jvm-mw, which is text only): stdout then carries exactly one JSON
  document (`Result` envelope, §5.1), or one per line in batch mode. Logs and progress always go to stderr.
- **Stable, versioned schemas:** each result type has an id (`athenacl.result.render/1`). Additive changes keep the
  version. Breaking changes bump it, and the old version stays available for one major release via
  `--schema-version`. CI diffs schemas against the last release.
- **Structured errors:** `code` (stable enum), `path` (JSON pointer into the input file, or `argv[3]`), `message`,
  `fix` hint, `docRef`. Unknown names come with "did you mean" suggestions from `describe` data.
- **Deterministic:** commands that generate take `--seed` (default 0 when `--json`, never time-based), and echo the
  seed, theme hash, soundbank hash and tool version in `meta`.
- **Idempotent file ops:** outputs are written atomically (temp file + rename) into `--out` with a `manifest.json`
  (input hashes → output hashes). A re-run with identical inputs is a no-op (`"changed": false`) unless `--force`.
  Inputs are never modified. `soundbank install` is idempotent (hash check).
- **`--dry-run`:** for any command that writes or downloads, return the plan (files to create or overwrite, downloads,
  estimated duration) and touch nothing.

#### 6.9.2 Exit codes (0/1/2 match jvm-mw; 3–7 are additions for validation and checks, which jvm-mw signals only by exception)
| Code | Meaning |
|---|---|
| 0 | Success (warnings may be present in the output; `--strict` turns warnings into 3) |
| 1 | Unexpected error: an uncaught exception with a stack trace on stderr (same as jvm-mw) |
| 2 | Usage error: unknown command/flag or bad argument type. Message on stderr, help on stdout (same as jvm-mw) |
| 3 | Invalid input (schema or semantic validation failed) |
| 4 | Not found (file, preset, state, event id) |
| 5 | Runtime failure (render failed, I/O error) |
| 6 | Check failed (determinism mismatch, stuck notes, diff found with `--fail-on-diff`, loudness out of tolerance) |
| 7 | Environment problem (no soundbank, missing `--add-exports`, unsupported JDK) |

#### 6.9.3 Introspection, batch and stateless stepping
- **`describe`**: themes (resolved states, overlays, layers, stingers, transition rules), presets, POs (name,
  arguments, types, defaults, examples from athenaCL docs), textures, GM/GS instruments of the active soundbank
  (bank, program, name, range hint), commands. **`schema <id>`** emits any schema. **`help`** prints copy-pasteable
  example invocations and one or two lines per command (jvm-mw's `DebugCli.help()` format). **`help --json`** is
  generated from the command specs (args, defaults, exit codes, result schema id, examples).
- **Batch mode:** `athenacl batch` reads JSONL commands from stdin (`{"id":1,"cmd":"render","args":{…}}`) and writes one
  result per command with the same `id`. Commands share nothing but the JVM (no session state), so batch is just
  cheaper startup. `--stop-on-error` is optional. jvm-mw has no batch mode; this one is cuttable (§7).
- **Stepping without a server:** generation is deterministic from bar 1, so "step to bar N" is a pure function of
  (theme, seed, timeline, N). `render --bars A-B` emits only that window, and `--at "<bar> <command>"` appends control
  calls inline. An agent "steps" by re-running with a later window and more `--at` flags, or by editing the
  timeline file. `debug bar --bar N` prints one bar's `BarTrace` and per-note provenance. `debug explain --event ID`
  works on a saved event log. `debug replay` re-runs a saved control log. At a target of < 5 ms per bar, regenerating
  300 bars from the start costs about 1.5 s, so no session state is needed. A `--checkpoint` snapshot file is an
  optional later optimization, still file-based and stateless.

#### 6.9.4 Letting agents "see" the music
- **Text description** (`describe-music`): per bar, state, intensity, meter, tempo, key, chord
  symbol/Roman numeral, active layers, events per layer, transitions and stingers. Plus an **ASCII piano roll** per
  layer (pitch rows × 16th columns; `●` onset, `─` sustain, `·` rest) and a **drum grid** (kit-piece rows). The JSON form
  contains the same data structurally.
  ```
  bar 17  combat  i=0.72  4/4  ♩=144  D dorian   Dm7 (i7) → G7 (IV7)   layers: drums bass pad lead brass
  lead   A5 |●───·   ●─● ●───────·   |
         G5 |    ●─·                 |
  kick      |●   ·   ●   ·   ●   ·   |
  snare     |    ●       ●       ●  ●|
  ```
- **Analysis metrics** (`analyze`), per bar/section/state: integrated and short-term LUFS and true
  peak (from WAV; a velocity-based proxy when no WAV was rendered, labelled as such), note density (onsets per beat)
  and polyphony, pitch range/register per layer, pitch-class histogram and **key estimate with confidence**
  (Krumhansl-Schmuckler style) vs the declared key (key consistency), chord-tone ratio, **repetition/motif reuse**
  (planned reuse from provenance plus measured interval/rhythm n-gram self-similarity), syncopation index, rest ratio,
  velocity range, transition latency stats, and stuck/overlapping-note counts. Estimates are labelled as estimates.

#### 6.9.5 Agent guide (modelled on jvm-mw's AGENTS.md)
- **`AGENTS.md`** (repo root) is the main guide, structured like jvm-mw's (doc 06):
  - project rules: pregui pin, what not to commit (soundbanks, renders), and the determinism rules (no wall clock, no
    unordered-map iteration in generation, RNG only via `RandomSource.fork`);
  - a **"CLI (headless)"** section with copy-pasteable invocations and a **`Command | Use when`** table (e.g.
    `debug bar`: "a note sounds wrong at bar N, see why it was chosen"; `debug bisect`: "two renders differ, find the
    first bar");
  - in-game keys (F3 dump, F4 overlay) and how to read the snapshot (e.g. wait until the overlay's `n=` sample count
    settles before trusting CPU numbers);
  - music-domain gotchas and the copyright guidance from §6.8.
- **Docs layout copied from jvm-mw:** phase specs `docs/phaseN-*.md` (Goal / Why this slice / In scope / Out of scope / Test,
  including the exact `key=value` lines a slice must print), `docs/next-topics.md` and `docs/big-topics.md`, and
  **`docs/NAME_MAP.md`** mapping athenaCL Python names to Java classes (kept out of class comments, as in jvm-mw).
- **Optional skill file** (`agent-guide/SKILL.md`, printed by `athenacl agent guide`): a short excerpt of AGENTS.md's CLI
  section for agents that only *use* the tool. jvm-mw doesn't have one. It's generated from AGENTS.md so the two
  can't drift.

### 6.10 Debug tools

| Tool | What it does | Surfaces |
|---|---|---|
| **Event-log inspector** | Query and filter the canonical JSONL event log (bar range, layer, channel, pitch, velocity, event type), show stats, jump from an event to its provenance | CLI `debug inspect` |
| **Timeline replay** | Re-run a control log (from `render` or a game's F3 dump) offline, with identical output | `debug replay`, `OfflineSession.replay(log)` |
| **Bisect** | Given good and bad renders/configs/versions: find the first divergent bar and event. Given one control log and a failing check (e.g. stuck note): find the first control call that causes it (delta-debugging over log prefixes). Exit codes are compatible with `git bisect run`. | `debug bisect` |
| **Step-by-bar debugger (stateless)** | Show any bar (`--bar N`), or the first bar matching a breakpoint (`--until state=combat`, `--until transition`, `--until 'velocity>110'`). It prints the `BarTrace` and, per note, the **provenance**: layer, generator (PO/texture/motif), transform chain, seed path, and the reason (chord tone, voice-led from X, passing tone, groove offset). Stepping means re-running with the next bar (§6.9.3). | `debug bar`, `debug explain` |
| **State-machine/transition trace** | Every request, chosen rule, quantization point, retracted/released events, stinger, bridge and latency, with "why this rule matched" | `debug trace --transitions`, overlay, `TransitionTrace` |
| **Scheduler/latency monitor** | Lookahead margin, queue depth, late events, generation time per bar, CPU ms per bar, request→audible latency, buffer underruns (libGDX) | `adapt --monitor`, overlay, F3 snapshot `sched.*` keys, `SchedulerSample` in event logs, `perf` log lines over budget (jvm-mw `PerfTrace` style) |
| **Stuck-note detection** | Runtime watchdog (note older than its scheduled duration + tolerance gets a forced note-off + warning) and an offline checker on event logs (unmatched note-on/off, notes surviving retraction) | director, `debug check --stuck-notes` |
| **Loudness meters** | Live momentary/short-term LUFS and peak (real-time renders and the libGDX pump), offline integrated per state/section | overlay, `analyze`, `debug check --loudness` |
| **Theme diff** | Semantic diff of two resolved configs (after preset merge), and render diff (event logs per bar: added/removed/changed notes, metric deltas, first divergence) | `diff` |
| **libGDX debug overlay/hooks** | Overlay with current state, intensity (target vs smoothed), overlays, active layers, bar:beat, key/chord, queue/lookahead, latency, CPU per bar, underruns, last transition. `DebugListener` hooks let games draw their own UI. **F4** toggles it and **F3** writes `build/athenacl-snapshot.txt` (`key=value`, also copied to the clipboard) plus control + event logs for replay (jvm-mw key conventions). Tuning knobs follow jvm-mw's `DebugVars` pattern: `MusicDebugVars` fields overridable via `athenacl.debug.NAME` in `local.properties`, `-Dathenacl.debug.NAME` or env `ATHENACL_DEBUG_NAME`, and printed in every dump (e.g. `lookaheadMs`, `muteLayers`, `slowBarMs`). | `athenacl-gdx …gdx.debug` |
| **Log levels** | Categories `athenacl.director`, `.scheduler`, `.transition`, `.gen`, `.render`, `.cli`. ERROR…TRACE via `System.Logger`. CLI `-v/-vv/--log-level`, `--log-json` to stderr. | everywhere |
| **Determinism check** | Render N times (optionally in separate JVMs, with different thread pools/`-XX:hashCode` settings) and compare canonical hashes. On mismatch, report the first divergent event plus its two provenance records. | `debug determinism`, CI |

**Design rules:** tracing is an **observer only**. It never draws from `RandomSource` or changes iteration order, so
output with debug on equals output with debug off (tested, §8.7). Provenance is captured through a scoped
generation context (not extra parameters on every generator). Event ids are deterministic. Wall-clock fields
(latency, CPU) live in separate trace records that are excluded from canonical hashes. Debug off is a no-op
`DebugSink` (target: < 1% overhead); full provenance in dev builds should stay under ~15% generation time [estimate].

### 6.11 Audio effects stage and sampled layers (approved Oct 10; layers L9b–L9d)

A **pure-Java DSP chain** sits between Gervill's PCM output (`AudioSynthesizer.openStream`) and the two sinks: the
libGDX `AudioDevice` pump (real time) and the WAV writer (offline). It lives in a new module, **`athenacl-fx`**.
The MIDI/event side is unchanged; effects only shape audio.

#### 6.11.1 Buses and Gervill's limits
- **Gervill has no per-channel audio taps.** I checked OpenJDK 21's `SoftMainMixer`: all 16 MIDI channels are mixed
  into one internal set of left/right/mono buffers (plus Gervill's own reverb/chorus sends), and the output format
  is mono or stereo only. So one synth instance gives one stereo mix. Gervill's info properties (`SoftSynthesizer`)
  do let us turn off its built-in `reverb`, `chorus` and `auto gain control`. We do that when our FX chain is active,
  so loudness and dynamics are under our control and calibration is predictable.
- **Default: one Gervill instance per bus.** Each bus (e.g. `drums`, `bass`, `music`, `fx`) gets its own `SoftSynthesizer`
  opened with `openStream`, receives only its layers' MIDI events, and renders in lock-step blocks on the director's
  audio thread. All instances load the **same `Soundbank` object**. Whether Gervill then shares the sample data in
  memory instead of copying it is **unverified**; L9b measures it. Cost scales with the number of buses: voices only
  play on one instance, but each instance has fixed per-block overhead. Default cap: 4 buses [estimate].
- **Alternatives**, selectable per theme:
  1. **Single-bus mode:** one instance, master FX only. Cheapest; no sidechain or per-layer filters.
  2. **Render-twice ("stem by muting"):** offline only. Render each bus separately with other channels muted, then mix.
     Exact, but slower. Useful for `render --stems` and tests.
  3. **Per-channel tap by patching Gervill:** vendoring and patching `SoftMainMixer` (GPL-2.0 + Classpath Exception)
     to expose per-channel buffers. Most efficient, but we'd own a fork of a JDK internal. Not planned; noted as an
     option if CPU becomes the problem.
- **Signal flow:** `bus synths → per-layer/bus FX → bus faders (calibrated) → master FX (EQ → glue compressor →
  limiter) → sink`. Sampled layers (§6.11.5) enter as their own buses.

#### 6.11.2 Effect set (in-house, allocation-free, block-based)
| Effect | Notes |
|---|---|
| **Filters** | LP/HP/BP/notch, RBJ-cookbook biquads (12 dB) and cascades (24 dB), resonance (Q), smoothed automatable cutoff for sweeps; optional state-variable filter for fast modulation |
| **EQ** | Low/high shelf + 2–3 peaking bands; master and per bus |
| **Compressor / sidechain ducking** | Feed-forward RMS/peak detector, attack/release/ratio/knee. **Key sources:** another bus (e.g. `kick` lane), or a **beat-synced envelope** generated from the musical clock (duck on every beat/quarter without needing a real kick) |
| **Reverb** | Freeverb/Schroeder-style (comb + allpass), room/damping/width/mix |
| **Delay** | Tempo-synced (`1/8`, `3/16`, `1/4.`) from the tempo map, feedback with LP in the loop, ping-pong |
| **Distortion** | Saturation (tanh/soft clip), hard clip, **bitcrush** (bit depth + sample-rate reduction), 2× oversampled where aliasing matters |
| **Chorus** | 2–3 modulated delay voices, tempo-synced or Hz rate |
| **Limiter** | Master look-ahead (≈1.5 ms) brickwall limiter with 4× oversampled true-peak detection, ceiling −1 dBTP |
| **Meters** | Per-bus and master peak/RMS/short-term LUFS and gain reduction (debug, §6.11.6) |

**Libraries are optional.** The set above is small (well-known public-domain formulas such as the RBJ cookbook and
Freeverb) and written in-house for determinism, zero allocation and a stable API. Candidates if we want more:
- **TarsosDSP**, GPL-3.0 (compatible with GPL-3.0-or-later; [LICENSE](https://github.com/JorenSix/TarsosDSP/blob/master/LICENSE)).
  Useful mainly for **WSOLA time-stretch / pitch-shift** for sampled loops (§6.11.5). It has its own dispatcher
  model, so we'd call individual processors, not its pipeline.
- **JSyn**, Apache-2.0 ([license](https://jsyn.com/jsyn/developers/)). Filters/delays as unit generators, but it runs
  its own engine thread, which fights our lock-step determinism.
- **Beads**, GPL-3.0 ([repo](https://github.com/orsjb/beads)). Same concern, and it pulls in mp3/JACK dependencies.

Recommendation: in-house core FX, TarsosDSP only if time-stretch is needed.

#### 6.11.3 Automation (musical clock, intensity, state)
Every numeric FX parameter can be a constant or an automation source, evaluated per DSP block (64 frames) from
the **musical clock**, so it's sample-accurate and deterministic:
- `byIntensity`: a curve over intensity 0–1 (e.g. the pad filter opens with intensity).
- `byState`: values per state (e.g. combat adds saturation); changes glide over the transition's quantization unit.
- `lfo`: tempo-synced LFO (`rate: 1bar`, sine/triangle/saw/square/random-step seeded).
- `sweep`: a one-shot ramp triggered by an event (transition, stinger, bar N, phrase start), `over: 4bars`.
- `envelope`: beat-synced envelope (e.g. for ducking).
- Automation sources combine (`mul`/`add`), and the result is clamped to the parameter range.

#### 6.11.4 808 slides
A **bass layer option** generates monophonic legato lines with **pitch glides between notes**:
- **Generated pitch bend** (default): set RPN 0 bend range (e.g. ±12 semitones), then emit pitch-bend ramps at control
  rate over `slide.timeMs` or a musical length (`1/16`). Deterministic and works on any GM program.
- **Portamento CC5/CC65**: only if Gervill honors it, which is **unverified**; L9c tests it and falls back to bend.
- A sub-bass sound comes from a GM program (Synth Bass 38/39, or a sine-like lead) plus saturation, or from a **sampled
  808 one-shot** played by the sampler (§6.11.5) with the same glide curve applied to its playback rate.

#### 6.11.5 Sampled-audio layers (separate sub-feature, L9d)
A new layer type, `type: sample`, plays user-supplied clips outside Gervill:
- **Formats:** WAV through `javax.sound.sampled`; OGG Vorbis through **JOrbis** (LGPL, [jcraft.com/jorbis](https://www.jcraft.com/jorbis/),
  usable from GPL-3.0 code) for offline/CLI. In-game, libGDX's own OGG decoding is an alternative. All clips are decoded
  and resampled to the engine rate **at theme load**, never during playback.
- **Kinds:**
  - **loops:** declare `bpm` and `bars`; they start on bar lines. A small tempo mismatch (≤ ±6%) uses varispeed, which
    also changes pitch. Larger mismatches use beat-slice retriggering (slices at beat markers). Optional true
    time-stretch via TarsosDSP WSOLA.
  - **one-shots:** triggered by generated note events (pitched by playback rate for 808s, or fixed).
  - **beds:** free-running textures such as vinyl crackle or room tone, with crossfaded loop points.
- **Determinism:** clip starts are sample-accurate from ticks; random clip choice uses `RandomSource`; no wall clock.
- **Licensing:** themes must list each clip's source and license (`license:` field, validated). The project bundles
  only CC0 example clips.

#### 6.11.6 YAML syntax
```yaml
buses:                          # omit for single-bus mode
  drums: {layers: [kick, snare, hats, perc]}
  bass:  {layers: [bass808]}
  music: {layers: [pad, lead, arp]}
  beds:  {layers: [crackle]}
layers:
  bass808:
    program: {gm: 38}
    mono: true
    slide: {mode: bend, time: "1/16", bendRange: 12, probability: 0.4}
    fx:
      - saturate: {drive: {byState: {combat: 0.6, danger: 0.4, default: 0.15}}}
  pad:
    fx:
      - filter: {type: lowpass, slope: 24, resonance: 0.35,
                 cutoff: {byIntensity: [[0, 600], [0.5, 2500], [1, 9000]], curve: exp,
                          mul: {lfo: {rate: 2bars, shape: sine, depth: 0.25}}}}
      - duck: {key: bus:drums.kick, depthDb: 6, attackMs: 4, releaseMs: 140}   # or key: {envelope: {every: "1/4"}}
      - delay: {time: "3/16", feedback: 0.35, mix: {byIntensity: [[0, 0.1], [1, 0.25]]}}
      - reverb: {room: 0.7, damping: 0.4, mix: 0.25}
  crackle:
    type: sample
    clip: {file: samples/vinyl-crackle.ogg, kind: bed, license: CC0}
    gainDb: -24
    fx: [{filter: {type: highpass, cutoff: 300}}]
  loopTop:
    type: sample
    clip: {file: samples/top-loop-90bpm.wav, kind: loop, bpm: 90, bars: 2, license: "own work"}
    activeAbove: 0.6            # intensity threshold, like other layers
master:
  fx:
    - eq: {lowShelf: {freq: 80, gainDb: 1}, highShelf: {freq: 10000, gainDb: 0.5}}
    - compressor: {ratio: 2, thresholdDb: -18, attackMs: 20, releaseMs: 200}
    - limiter: {ceilingDbtp: -1}
  sweeps:
    - {on: "transition to=combat", target: music.pad.filter.cutoff, from: 400, to: 8000, over: 1bar}
fxBudget: {cpuPercentOfBuffer: 25, onExceed: warn}   # warn | degrade (live only; see 6.11.7)
```
`sci-fi space` uses these first (L9c): filter sweeps on pads, tempo-synced ping-pong delay on arps, light chorus,
ducking pads to the kick in combat. `fantasy-dungeon` gets reverb sized per state (bigger in dungeon exploration,
dry in combat) and a slight master glue compressor.

#### 6.11.7 Determinism, loudness and CPU
- **Determinism:** the DSP is a pure function of (event log, theme, soundbank, sample clips, block size). Fixed 64-frame
  internal blocks are independent of the device buffer size. Buses are processed in a fixed order on one thread,
  denormals are flushed explicitly, and there are no wall-clock or unordered-map reads. The offline render is
  **bit-stable on a given JDK**. Gervill or JDK updates may change bytes, so event logs stay the canonical snapshot
  (§6.6). Live output equals the offline render for the same recorded log, **unless** `fxBudget.onExceed: degrade`
  kicks in. That mode is logged and is off by default.
- **Loudness calibration measures after effects:** the §6.4.1 calibration renders through the full FX chain and
  measures post-master, before the limiter. It also checks that the limiter's gain reduction stays ≤ 1 dB on average,
  so the contour isn't achieved by squashing. FX changes invalidate the calibration cache (the hash includes the FX
  config and clip hashes).
- **CPU budget:** DSP time per audio buffer is measured (e.g. 512 frames at 48 kHz ≈ 10.7 ms; default budget 25% ≈ 2.7 ms
  for FX, Gervill excluded). `fx.cpu.pct` and `fx.cpu.maxMs` appear in the F3 snapshot, the F4 overlay and
  `debug perf --fx`. Over budget: `warn` (default) or `degrade`, which bypasses reverb/chorus first.

#### 6.11.8 CLI and debug
```
athenacl render themes/scifi-space.yaml --seed 1 --bars 1-16 --out build/fx --fx on|off   # A/B with and without FX
athenacl render … --bypass music.pad.filter,master.limiter          # bypass selected units
athenacl render … --stems                                            # per-bus WAVs (render-twice mode)
athenacl analyze build/fx --by bus  → "bus=drums lufs=… peak=… gr.maxDb=…" per bus + master
athenacl debug fx themes/scifi-space.yaml   → chain listing: "unit=music.pad.filter type=lowpass cutoff.auto=byIntensity…"
athenacl debug fx-curve themes/scifi-space.yaml --param music.pad.filter.cutoff --bars 1-8   → per-bar values
athenacl debug perf themes/scifi-space.yaml --fx --buffer 512   → "fx.cpu.pct.p99=… budget=25 ok=true"
```
F4 overlay gains per-bus meters and gain-reduction bars, and F3 snapshots gain `bus.<name>.lufs`, `fx.cpu.*` and
`limiter.grDb`.

### 6.12 Instrument kits: several soundfonts, chosen instruments (decided Oct 10; layers L1b, L6b)

The user couldn't hear a difference between GeneralUser GS and MuseScore_General ([07](07-soundfont-upgrade.md)), so the
decision is not "pick one bank". Instead the engine **loads several soundfonts at once and selects specific instruments
from each, defined in config**. That config is a reusable **instrument kit** (`instruments/*.yaml`), separate from themes
and presets.

#### 6.12.1 Kit format
A kit declares **sources** (soundbanks) and **named instruments**:
```yaml
# instruments/gm-lite.yaml (DEFAULT kit, small)
kit: gm-lite
version: 1
sources:
  gu:
    id: generaluser-gs-2.0.3          # resolved via the soundbank cache/mirror (§2.1); or path: ...
    format: sf2                        # sf2 | dls | sfz (L6b). sf3 is rejected (§6.12.5)
    sha256: 0f3c…                      # verified on load
    license: {spdx: LicenseRef-GeneralUser-GS-2.0, url: "https://www.schristiancollins.com/generaluser",
              attribution: "GeneralUser GS by S. Christian Collins", redistribute: true, commercial: true,
              file: licenses/GeneralUser-GS-LICENSE.txt}
instruments:                           # canonical names (docs/instrument-names.md), GM-derived
  piano:          {source: gu, bank: 0, program: 0}
  harp:           {source: gu, bank: 0, program: 46, gainDb: -1.5, keyRange: [C1, G7], outOfRange: fold}
  strings:        {source: gu, bank: 0, program: 48}
  choir:          {source: gu, bank: 0, program: 52}
  flute:          {source: gu, bank: 0, program: 73}
  fiddle:         {source: gu, bank: 0, program: 110}
  taiko:          {source: gu, bank: 0, program: 116, transpose: -12}
  pad.halo:       {source: gu, bank: 0, program: 94}
  bass.synth:     {source: gu, bank: 0, program: 38}
  drums.orchestral: {source: gu, drumkit: 48}
  drums.808:      {source: gu, drumkit: 25}
```
```yaml
# instruments/gm-full.yaml (OPTIONAL larger kit)
kit: gm-full
extends: gm-lite                       # inherit names; override where MSG is preferred
sources:
  msg:
    id: musescore-general-0.2
    format: sf2                        # the 206 MB SF2 edition; the 38 MB SF3 edition is rejected
    sha256: 9a1e…
    license: {spdx: MIT, attribution: "MuseScore_General (S. Christian Collins), based on FluidR3Mono / FluidR3",
              redistribute: true, commercial: true, file: licenses/MuseScore_General_License.md}
instruments:
  piano:   {source: msg, bank: 0, program: 0, fallback: [gm-lite:piano]}
  choir:   {source: msg, preset: "Choir Aahs Expr.", fallback: [gm-lite:choir]}   # select by preset name; exact MSG names to be checked against a dump in L1b
  drums.808: {source: msg, drumkit: 25}
```
- **Instrument fields:** `source` + `bank`/`program` (or `preset:` by name, or `drumkit:` for percussion, or `sfz:` in
  L6b); `gainDb` (manual trim on top of automatic normalization); `transpose` (semitones); `keyRange` + `outOfRange:
  fold|clamp|drop`; `velocityCurve` (optional); `fallback:` (ordered list of `kit:name` or `gm:<program>`). A fallback
  is used when a source is missing, fails its checksum or license policy, or is an unsupported format. `kit validate`
  reports every fallback taken.
- **References from presets and themes:** presets name **roles → canonical instrument names** (e.g. `lead: [fiddle, flute]`).
  Themes pick kits and may override single instruments:
  ```yaml
  # themes/fantasy-dungeon.yaml (excerpt)
  instruments:
    kits: [fantasy-orchestra, gm-lite]   # lookup order; first kit defining a name wins (fantasy-orchestra arrives in L6b)
    override:
      lead:  {use: gm-full:flute, gainDb: -2}
      pad:   {use: strings}
  layers:
    harp:  {instrument: harp}
    drums: {instrument: drums.orchestral}
  ```
  A state block may override instruments too (e.g. `combat: {instruments: {drums: drums.808}}` in sci-fi).
- **Default:** `gm-lite` (GeneralUser GS, 31 MB) is the default kit everywhere. `gm-full` (MuseScore_General SF2, MIT,
  206 MB) is optional. It's installed with `soundbank install musescore-general` from **our own mirror** (MIT allows
  redistribution; its License.md must ship alongside), and can be bundled by games that want it.

#### 6.12.2 Several soundbanks in one Gervill synth (verified Oct 10)
Tested on this box with OpenJDK 21 (harness `research/tools/MultiBank.java`):
- `loadInstrument` accepts instruments from **different `Soundbank` objects in one synth**. A GeneralUser piano and a
  MuseScore_General piano loaded together render as their own references: lag-tolerant correlation 1.000 and 0.972,
  RMS matching within 1%.
- **Bank/program collisions:** loading a second instrument at the same patch silently **replaces** the first. Only one
  instrument stayed loaded, and it rendered as the last-loaded bank. So collisions must be handled before loading.
- **Remapping works:** wrapping an instrument in `com.sun.media.sound.SimpleInstrument` with a new `Patch` (bank 1,
  program 0) let both pianos coexist and be selected by bank select (MSB/LSB). This uses the same internal package as
  `openStream`, so it needs no new `--add-exports`.
- **Not yet verified (L1b):** remapping **percussion kits** (`ModelPatch` with the percussion flag) to non-colliding slots,
  and whether `preset:`-by-name lookup is unambiguous in MSG.

**Patch allocator:** the engine never uses the source's own bank/program in the synth. Every resolved instrument gets a
**synth slot** allocated deterministically (sorted by kit order, then name): melodic slots in banks 1…n (128 programs
per bank), percussion kits in percussion slots. Layers then send bank select + program change for their slot. SMF export
writes either the slots (exact replay with the same kits) or the instruments' GM program hints (`--midi-programs gm`,
for external synths). With buses (§6.11.1), each bus's Gervill instance loads only its layers' instruments.

#### 6.12.3 Memory: load only what's referenced
`getSoundbank` is lazy, and sample data is pulled in when instruments are loaded (07 §2). The resolver collects the
instruments a theme can reach (all states, overlays, stingers and fallbacks actually needed), then calls `loadInstrument`
for those only. Measured: two pianos from two banks added **≈6.7 MB** of heap, versus **≈183 MB** for `loadAllInstruments`
on MuseScore_General. `athenacl instruments show --theme … --memory` reports per-instrument sample memory, and theme
switching preloads the next theme's set off-thread.

#### 6.12.4 Per-instrument loudness normalization
Banks differ by about 7 LU (07 §2). The same piano measured 0.0132 vs 0.0413 RMS (≈10 dB) between GeneralUser and MSG.
So before the per-state contour (§6.4.1) runs, each instrument is normalized:
- **Probe:** render a fixed phrase per instrument (melodic: C3–C5 notes at velocities 64/100, 1 s each; kits: a standard
  groove), measure EBU R128 integrated loudness (L1 meter), and store a correction so every instrument hits the kit's probe
  reference (default −20 LUFS) ±0.5 LU. Manual `gainDb` is added on top.
- **Cache key:** source sha256, bank/program/preset, transpose, probe version.
- **Applied as** the channel's CC7 baseline (or bus gain with the effects stage).
- The state calibration then runs per resolved instrument set, so its cache key includes the kit hashes. Swapping kits
  re-calibrates automatically.

#### 6.12.5 License, credits, formats
- **License policy** (project-level `athenacl.yaml`, with defaults): `allow: [MIT, CC0-1.0, CC-BY-3.0, CC-BY-4.0,
  LicenseRef-GeneralUser-GS-2.0]`, `warn: [LicenseRef-CC-Sampling-Plus-1.0, CC-BY-SA-3.0, CC-BY-SA-4.0]`, `deny:
  everything without redistribute+commercial`. `kit validate` and `theme validate` fail (exit 3,
  `KIT_LICENSE_DENIED`) on denied or missing licenses. Banks that 07 §3 rules out (SGM, Timbres of Heaven, Arachno, …)
  can be used locally only with `--allow-unlicensed` and are never written into credits as redistributable.
- **Credits:** `athenacl credits <theme…>` writes `build/credits/CREDITS.md` plus copies of the license files for **only
  the sources actually used**. A Gradle task (`athenaclCredits`) does the same for game packaging. Attribution licenses
  (CC-BY, Sampling+, BY-SA) appear with their required text.
- **SF3 is rejected with a clear error** (exit 3, `SOUNDBANK_SF3_UNSUPPORTED`). Gervill would otherwise play its Vorbis
  data as noise (07 §2: 9 LU louder, 0 dBFS peaks). Detection reads the RIFF `ifil` version (major 3) and the `shdr`
  sample-type compression bit, before Gervill sees the file. Fix hint: "use the SF2 edition or convert (e.g. Polyphone)".
  Unreadable files give `SOUNDBANK_UNREADABLE` (e.g. Timbres of Heaven's Vorbis build in 07). DLS and SF2 are accepted.

#### 6.12.6 SFZ loader (L6b)
SFZ libraries (VSCO 2 CE: CC0; Sonatina: CC Sampling+ 1.0; Virtual Playing Orchestra: mixed CC; Salamander piano:
CC-BY 3.0; 07 §3) need an in-house loader, because Gervill reads only SF2/DLS:
- **Parse the opcode subset** from 07 §6: `<group>/<region>`, `sample`, `lokey/hikey/key`, `lovel/hivel`,
  `pitch_keycenter`, `tune`, `transpose`, `volume`, `pan`, `ampeg_attack/decay/sustain/release`, `loop_mode/start/end`,
  `seq_length/seq_position`, `default_path`, `#define`/`#include`. Unknown opcodes produce warnings listed by `kit validate`.
- **Build Gervill instruments in memory** (`SimpleInstrument` + `ModelPerformer` + `ModelByteBufferWavetable`), so no new
  synth is needed. Same internal API as above.
- **Decode WAV at load** (FLAC/OGG later: OGG via JOrbis when L9d exists, FLAC decoder TBD). Load only regions whose
  key and velocity ranges the theme's layers can reach.
- **Round-robin** (`seq_position`): Gervill has no native round-robin. The plan is to load each RR set as a separate slot
  and have the engine alternate slots deterministically (seeded). This is unverified until L6b.
- **Optional `athenacl bank convert in.sfz out.sf2`** writes the same instrument as SF2 for inspection in Polyphone.
- **First user:** `instruments/fantasy-orchestra.yaml` built from **VSCO 2 CE (CC0)**: section strings, horns, timpani.
  SSO chorus/harp is added only after the license question (Q28) is answered. All entries fall back to `gm-lite`.

#### 6.12.7 CLI and debug
```
athenacl instruments list [--kit gm-lite] [--theme themes/fantasy-dungeon.yaml]   → "instrument=harp kit=gm-lite source=gu bank=0 program=46 slot=1:3"
athenacl instruments show harp --kit gm-lite        → source, license, keyRange, normalization gain, sample memory, fallbacks
athenacl instruments audition harp --kit gm-full --notes C4,E4,G4 --out build/aud   → WAV + "loudness.probe=-20.1 LUFS"
athenacl instruments compare piano --kits gm-lite,gm-full --out build/cmp           → A/B WAVs + loudness/spectral summary
athenacl kit validate instruments/gm-full.yaml      → checksums, licenses, SF3/format checks, unresolved names, fallbacks taken
athenacl credits themes/fantasy-dungeon.yaml --out build/credits   → "sources=2 licenses=MIT,LicenseRef-GeneralUser-GS-2.0"
```

### 6.13 Hook generator + melody critic (decided Oct 10; layer L7b)

**Context:** the user's agent **has already built L7** (motif engine) from the earlier plan. L7b **extends it without
rewriting it**. The hook generator is one more motif *source* behind L7's existing generator entry point (if L7 has no
such interface, L7b adds a thin adapter and leaves L7's model, transforms and development planner unchanged). A hook is
an ordinary `Motif` tagged `role: hook`, so L7's transforms (repeat, transpose, invert, augment, fragment) and
`debug explain` work on it unchanged.

#### 6.13.1 Hook generator
- **Shape:** 3–6 notes (configurable), 1–2 bars, with a **clear rhythmic identity**: one rhythm cell drawn from a
  per-genre cell library (e.g. long-short-short, syncopated anticipation) and kept fixed when the hook repeats.
- **Motion:** mostly stepwise, with **one signature leap** (size range per style, default a 4th–6th), normally
  recovered by step in the opposite direction (gap-fill).
- **Contour archetypes:** `arch`, `rise-and-resolve`, `fall`, `call/response` (2 sub-phrases, the answer inverts or
  completes the call). Weighted per style.
- **Harmony:** chord tones on strong beats (against the current chord from the progression); passing/neighbour tones on
  weak beats.
- **Phrase forms:** the hook is placed into `AABA`, `ABAB`, `sentence` (a a' b) or `period` (antecedent → half cadence,
  consequent → full cadence). This uses L7's development planner with the hook as the `a` material.
- **Cadences:** phrase ends land on stable degrees (1, 3, 5; 1 for final cadences), on a strong beat, lengthened.
- **Controlled repetition:** share of exact vs varied restatements per phrase (`repetition.exact`, `.varied`), with a cap
  on unvaried repeats in a row. This keeps it catchy but not grating (Margulis 2014).
- **Per-genre / per-state YAML** (presets set defaults, themes and states override):
```yaml
hooks:
  length: [3, 6]
  rhythmCells: [long-short-short, short-short-long, anticipation]
  leap: {count: 1, size: [5, 9], recover: step-opposite}
  contours: {arch: 0.5, rise-and-resolve: 0.3, call-response: 0.2}
  strongBeats: chord-tone
  forms: {sentence: 0.5, AABA: 0.3, period: 0.2}
  cadence: {degrees: [1, 3, 5], final: [1], lengthen: true}
  repetition: {exact: 0.5, varied: 0.4, maxExactInRow: 2}
critic:
  candidates: 64
  weights: {chordTone: 1.0, tonalStability: 0.6, implication: 0.6, leapRecovery: 0.8, range: 0.5,
            novelty: 0.7, rhythm: 0.6, contour: 0.6, closure: 0.9, distinctiveness: 0.2}
  noveltyTarget: [0.35, 0.60]          # normalized information-content band (inverted U), not "max" or "min"
  hardRules: [inRange, cadenceStable, maxLeap: 12, maxSamePitchRun: 3, noUnresolvedTritoneLeap, copyGuard]
  weightsFile: weights/fantasy-dungeon.yaml   # optional, from the rating loop (§6.13.3)
states:
  combat:   {hooks: {length: [3, 4], rhythmCells: [driving-8ths]}, critic: {weights: {rhythm: 0.9}}}
  peaceful: {hooks: {contours: {arch: 0.7}}, critic: {noveltyTarget: [0.25, 0.5]}}
```

#### 6.13.2 Melody critic
**Generate-and-test:** draw N seeded candidates (seed path `…/hook/<motifId>/cand/<k>`). Reject any that break a
**hard rule**. Score the rest with a **weighted sum of normalized features (0–1)**, then pick the highest. Ties go to the
lowest `k`, so the result is fully deterministic.

| Feature | Measures | Grounding (sources from the literature; not re-checked online for this edit) |
|---|---|---|
| `chordTone` | share of strong-beat notes that are chord tones; weak-beat dissonances resolved by step | Temperley, *The Cognition of Basic Musical Structures* (MIT 2001) and *Music and Probability* (MIT 2007) |
| `tonalStability` | mean key-profile weight of the pitches, stronger on metric accents | Krumhansl & Kessler (1982) / Krumhansl, *Cognitive Foundations of Musical Pitch* (OUP 1990) |
| `implication` | fit to implication-realization expectations (pitch proximity, reversal after large intervals) | Narmour, *The Analysis and Cognition of Basic Melodic Structures* (Chicago 1990); Schellenberg's simplified two-factor model (1997) |
| `leapRecovery` | leaps followed by a change of direction / gap-fill; stepwise share | von Hippel & Huron, "Why do skips precede reversals?" (*Music Perception* 2000); Huron, *Sweet Anticipation* (MIT 2006) |
| `range` | ambitus within the style's band (hooks: about ≤ a 10th) and the instrument's range | Huron 2006 (tessitura/proximity); FANTASTIC `p.range` (Müllensiefen 2009) |
| `novelty` | information content under a simple variable-order model trained on the style corpus, plus an LZ compression ratio as a cheap proxy; scored as distance to the target **band** | Huron 2006 (predictability and reward); Pearce, IDyOM (2005/2018); Margulis, *On Repeat* (OUP 2014) |
| `rhythm` | metric alignment, syncopation index within the style band, durational consistency | Longuet-Higgins & Lee (1984) syncopation; Temperley meter model |
| `contour` | match to the requested archetype, few direction changes, clear peak | Huron, "The melodic arch in Western folksongs" (*Computing in Musicology* 1996); FANTASTIC contour features |
| `closure` | final note on a stable degree, on a strong beat, lengthened; antecedent/consequent cadence types | Huron 2006 (closure, "tonic" ending); Meyer, *Emotion and Meaning in Music* (1956) |
| `distinctiveness` | **small weight:** common global contour combined with less common interval patterns relative to the corpus | Jakubowski, Finkel, Stewart & Müllensiefen, "Dissecting an earworm" (*Psychology of Aesthetics, Creativity, and the Arts* 2017); FANTASTIC (Müllensiefen 2009 technical report) |

**Hard rules:** outside the instrument/tessitura range, an unstable cadence note, a leap over an octave (unless the style
allows it), more than N repeated same pitches, an unresolved tritone leap, and **`copyGuard`**. `copyGuard` rejects a
candidate whose interval+rhythm n-grams overlap too much with a theme's declared `references:` (e.g. a melody the agent
was told *not* to copy). That backs up 05's copyright guidance.

**Honesty:** the critic **flags bad melodies and ranks the rest by learned or hand-set proxies**. It does not measure
beauty or guarantee a "good" hook. Some features are correlational findings from specific corpora (Jakubowski et al.
studied a pop earworm corpus, at song level), so they get low default weights. Listening, or the rating loop, is still
the final judge.

#### 6.13.3 Rating loop (optional)
- `athenacl melody rate` renders pairs of candidates (A/B WAVs + ASCII piano rolls) and records choices to
  `ratings/<name>.jsonl` (`{"a":…, "b":…, "winner":"a", "features":{…}}`). It's interactive for humans, and
  `--choose a|b` makes it scriptable for agents.
- `athenacl melody fit ratings/x.jsonl --out weights/<theme>.yaml` fits a **Bradley–Terry model with a linear feature
  predictor**, i.e. logistic regression on feature differences (Bradley & Terry 1952). It uses L2 regularization toward
  the default weights, a fixed iteration count and a fixed order, so there's no randomness. Absolute ratings (1–5) can
  instead be fit with ordinal/simple logistic regression.
- The weights file is a versioned input with a hash, n, and the fit date in its header. Same weights + same seed → same
  music. The hash is in the event log header.

#### 6.13.4 Performance
Hooks are chosen at **motif-creation time** (theme load, state entry, or the phrase planner's look-ahead one phrase
early), on the director thread, **not inside the per-bar generation budget**. Target: 64 candidates × up to 2 bars
generated and scored in **< 10 ms** on desktop (features are O(notes); the n-gram model is a small precomputed table).
If over budget, the director uses the cached best hook from the previous seed path and logs `critic.deferred`.
Results are cached by (seed path, settings hash, weights hash).

#### 6.13.5 CLI and debug
```
athenacl melody generate --theme themes/fantasy-dungeon.yaml --state peaceful --candidates 64 --seed 3 --explain
   → one line per candidate: "cand=17 score=0.812 chordTone=0.92 closure=1.00 novelty=0.48 … rejected=-"
     and "cand=4 rejected=maxLeap" ; final "chosen=17 hook=h1 notes=5 contour=arch form=sentence"
athenacl melody score lead.mid --track 2 --key "E minor" [--chords prog.yaml]  → same feature breakdown for any MIDI
athenacl melody rate build/pairs --out ratings/user.jsonl [--choose a]
athenacl melody fit ratings/user.jsonl --out weights/fantasy-dungeon.yaml       → "n=<n> accuracy.cv=<x> weightsHash=<h>"
athenacl debug explain build/l7b/events.jsonl --note <id>   → "... motif=h1:transpose(+2) hook.cand=17/64 score=0.812 top=closure,chordTone"
```

---

## 7. Layered plan: tools first, then bottom-up layers [estimates: full-time-equivalent weeks, one experienced developer + coding agent]

*Restructured Oct 9, 4:55 PM at the user's request. The old milestones M0–M11 are folded into finer layers
(mapping in §7.4).*

### 7.1 Rules for every layer
1. **Tools first.** Layer 0 builds the agent CLI and debug foundations before any music code, so the coding agent
   can test each later piece from the command line as it builds it.
2. **Each layer ships with its CLI commands, debug views and tests.** A layer isn't done until its new
   types are reachable through `athenacl …` commands (key=value + `--json`), show up in `describe`/`describe-music`/
   `debug …` output, have JUnit/property tests, and have an AGENTS.md `Command | Use when` row.
3. **Agent-testable exit check.** Each layer ends with concrete CLI commands and expected outputs. They're committed as
   `checks/LNN.check` (command, expected exit code, and required `key=value` lines or regexes) and run by
   `gradlew layerCheck -Player=NN` and in CI, so later layers can't silently break earlier ones. `<h>` stands for a
   hash that must be identical across repeated runs. Numbers in brackets are tolerances.
4. **Small, 1–3 weeks each.** The phase-spec format follows jvm-mw: `docs/phaseNN-*.md` with Goal / Why / In scope /
   Out of scope / Exit check (doc 06).
5. **Order:** tools (L0–L1) → instrument kits (L1b) → music foundations (L2–L4) → **fantasy-dungeon playable in-game (L6)** →
   SFZ loader + fantasy-orchestra kit (L6b) → fantasy depth (L7, **L7b hooks + critic**, L8) → **sci-fi space (L9)** → **effects stage + sampled layers (L9b–L9d)** → harmony/debug depth (L10–L11) → athenaCL port track (L12–L20, which can run
   in parallel after L1) → remaining genres and release (L21–L23) → optional outputs (L24–L26).

Commands are written as `athenacl …` (the `installDist` launcher). The same commands work through the jvm-mw-style
Gradle task, e.g. `gradlew :athenacl-cli:agentCli --args="describe-music build/l0/events.jsonl"`.

### 7.2 Layers

**L0a: CLI skeleton (1–2 wk).** Gradle layout (jvm-mw style, §2), `athenacl-cli` dispatcher, key=value and `--json`
renderers, result envelope, error model with fix hints, exit codes (§6.9.2), `help` with copy-pasteable examples,
log levels (`-v/-vv/--log-level`, stderr only), atomic writes, `--dry-run`, AGENTS.md skeleton, `checks/` + `layerCheck` task.
```
athenacl help                          → exit 0; stdout lists commands with example invocations
athenacl help --json                   → exit 0; valid JSON, .commands[] non-empty
athenacl bogus                         → exit 2; stderr "unknown command: bogus"; help on stdout
athenacl version                       → exit 0; "version=0.1.0 java=21… gervill=present"
athenacl version -vv 2>/dev/null       → stdout identical to the line above (logs only on stderr)
```

**L0b: Events, render, describe, determinism (1–2 wk).** `athenacl-events` (NoteEvent/ControlEvent, ticks at 960 PPQ,
tempo map basics, canonical JSONL event log + hash, `RandomSource` + seed paths, `DebugSink` hooks), `athenacl-render`
(javax.sound Sequence → SMF, offline Gervill WAV through `GervillStreams`), `athenacl-analysis` v0 (`describe-music`:
per-bar summary, ASCII piano roll, drum grid; density, range, velocity), `athenacl-debug` v0 (`debug determinism`,
`debug inspect`). The input is a hand-written note list (`samples/notes/*.yaml`).
```
athenacl render samples/notes/c-major-scale.yaml --out build/l0 --formats mid,wav,events,txt
   → exit 0; "files=4 notes=8 bars=2 changed=true hash=<h>"
athenacl render samples/notes/c-major-scale.yaml --out build/l0 --formats mid,wav,events,txt
   → exit 0; "… changed=false hash=<h>"            (idempotent)
athenacl describe-music build/l0/events.jsonl --roll
   → "bar=1 notes=4 range=C4..F4", a roll row starting "C4 |●"
athenacl debug determinism samples/notes/c-major-scale.yaml --runs 2
   → exit 0; "runs=2 identical=true hash=<h>"
athenacl debug inspect build/l0/events.jsonl --where "pitch=E4"
   → exactly one "note id=… pitch=E4" line
athenacl render missing.yaml                       → exit 4; "error code=NOT_FOUND path=argv[1] fix=…"
```

**L1: Soundbanks and loudness metering (1–2 wk).** Resolver chain, `soundbank install|list|show` (our mirror, SHA-256),
`athenacl-soundbank-generaluser` artifact, `describe instruments`, EBU R128 meter (integrated, short-term, true peak)
in `analyze`, `debug check --loudness`. *(old M2)*
```
athenacl soundbank install generaluser --dry-run   → "action=download size=32319396 sha256=…"; nothing written
athenacl soundbank install generaluser             → "soundbank=generaluser status=installed sha256=<h>"
athenacl soundbank install generaluser             → "status=present changed=false"
athenacl describe instruments --soundbank generaluser → "instruments=287 drumkits=13"
athenacl analyze build/l0/                         → "loudness.integrated=<x> LUFS truePeak=<y> dBTP"
                                                     (x within ±0.2 LU of ffmpeg ebur128, checked by test)
athenacl render samples/notes/c-major-scale.yaml --soundbank /nonexistent.sf2
   → exit 0; "soundbank=emergency warning=SOUNDBANK_FALLBACK"
```

**L1b: Instrument kits (1–2 wk).** `athenacl-instruments`: kit YAML (sources, named instruments, gain/transpose/keyRange/
fallbacks), canonical instrument-name list, multi-soundbank loading into one synth, deterministic patch allocator (remap via
`SimpleInstrument`; percussion remap verified here), selective loading, per-instrument loudness normalization, license
policy + `credits`, SF3/unreadable rejection, `instruments list|show|audition|compare`, `kit validate`. Default kit
`gm-lite` (GeneralUser GS); optional `gm-full` (MuseScore_General) via `soundbank install musescore-general`. *(new, §6.12)*
```
athenacl kit validate instruments/gm-lite.yaml      → exit 0; "kit=gm-lite sources=1 instruments=<n> fallbacksTaken=0"
athenacl soundbank install musescore-general         → "status=installed sha256=<h> license=MIT"
athenacl kit validate instruments/gm-full.yaml      → exit 0; "sources=2 slots.collisions=0"
athenacl kit validate samples/kits/sf3.yaml         → exit 3; "error code=SOUNDBANK_SF3_UNSUPPORTED path=/sources/x fix=use the SF2 edition…"
athenacl kit validate samples/kits/denied.yaml      → exit 3; "error code=KIT_LICENSE_DENIED"
athenacl instruments compare piano --kits gm-lite,gm-full --out build/l1b
   → both probes "loudness.probe=-20.0[±0.5] LUFS" after normalization; raw difference reported ("raw.deltaLu=<≈10>")
athenacl instruments show piano --kit gm-full --memory → "sampleMB=<small>" (selective load, not ~183 MB)
athenacl render samples/notes/two-banks.yaml --kit gm-full --out build/l1b2  → both pianos audible on separate slots ("slots=2")
athenacl credits samples/themes/two-banks.yaml --out build/credits → CREDITS.md lists exactly the used sources
```

**L2: Theory (2–3 wk).** Pitch/pitch class/scale/mode/key, chords + Roman numerals, progressions + harmonic rhythm,
voice leader. `describe-music` gains per-bar chord and key estimates. *(old M4, harmony part)*
```
athenacl describe scale "D dorian"                 → "scale=D-dorian pcs=D,E,F,G,A,B,C"
athenacl describe chord Dm7 --key "D dorian"       → "chord=Dm7 roman=i7 pcs=D,F,A,C"
athenacl theory progression "i bVII bVI bVII" --key "D aeolian" --voice-lead --out build/l2
   → "chords=4 maxMotion=<=4 parallels=0 hash=<h>"
athenacl describe-music build/l2/events.jsonl      → "bar=1 chord=Dm key=D-aeolian(conf>=0.7)"
```

**L3: Rhythm and time (2–3 wk).** Meter + groupings, tempo map, long notes/ties/rests, pending note-offs, drum lanes,
groove/swing/humanize (seeded), polyrhythmic layer clocks, `debug check --stuck-notes`. *(old M4, rhythm part)*
```
athenacl rhythm pattern "x..x..x." --meter 6/8 --bars 2 --drum-grid
   → "bars=2 onsets=6" + grid rows "kick |●  ●  ●…"
athenacl render samples/notes/tie-across-bar.yaml --out build/l3 → "notes=1 maxDurBeats=6"
athenacl rhythm poly 3:2 --bars 2 --out build/l3p   → "layers=2 alignedAtBar=1,2"
athenacl render samples/notes/swing.yaml --groove "swing=0.62" --seed 7 --out build/l3s
   → analyze: "swing.measured=0.62[±0.01]"
athenacl debug check build/l3 --stuck-notes        → exit 0; "stuck=0"
```

**L4: Adaptive director, offline (2–3 wk).** Musical clock, main state machine, intensity (smoothed, hysteresis),
BAR transitions, stingers, stock layer generators (scale-walk/Markov melody, chord pad, bass, drum lanes),
theme YAML + `theme validate` (JSON-pointer paths + fix hints) + `theme explain`, `render --timeline/--bars/--at`,
control log, provenance v1 + `debug bar`/`debug explain`/`debug trace --transitions`/`debug replay`. *(old M3)*
```
athenacl theme validate samples/themes/broken.yaml → exit 3; "error code=THEME_UNKNOWN_STATE path=/transitions/rules/0/to fix=…"
athenacl render samples/themes/poc.yaml --timeline samples/timelines/poc.json --seed 42 --out build/l4
   → "bars=16 states=peaceful,sneaking,combat transitions=2 hash=<h>"
athenacl debug trace build/l4 --transitions        → every "transition … appliedTick=" is on a bar line
athenacl render samples/themes/poc.yaml --seed 42 --bars 9-10 --at "9 state combat 0.6" → same events as bars 9–10 of a full render
athenacl debug bar samples/themes/poc.yaml --seed 42 --bar 9
   → "bar=9 state=combat …" + "note id=… layer=… seed=42/combat/…/bar9/… why=…" per note
athenacl debug replay build/l4/control.jsonl       → "hash=<h>" (equals the render hash)
```

**L5: Fantasy-dungeon v1, offline (2–3 wk).** Presets framework minimum (preset model, merge, `preset show`, `theme explain`),
the `fantasy` preset, `themes/fantasy-dungeon.yaml` with all 6 starter states (modes, drones, harp/strings/choir, taiko
combat, tavern trading/partying), automatic loudness calibration to the contour (§6.4.1). *(old M6b first half, M6 calibration)*
```
athenacl theme validate themes/fantasy-dungeon.yaml → exit 0; "states=6 warnings=0"
athenacl theme explain themes/fantasy-dungeon.yaml  → "preset=fantasy" + "loudness.combat=+4.0" …
athenacl render themes/fantasy-dungeon.yaml --timeline samples/timelines/dungeon-tour.json --seed 1 --out build/l5 --formats mid,wav,events,txt
   → "bars=<n> states=6 hash=<h>"
athenacl analyze build/l5 --by state
   → "loudness.<state>=<lu>" each within ±1 LU of contour; "loudness.spread=<=6.0"
athenacl describe-music build/l5/events.jsonl --bars 1-4
   → modes in {dorian, aeolian, phrygian}; a drone layer present in sneaking bars
```
Plus a human listening review of `build/l5/*.wav`.

**L6: Real time + retraction + libGDX → fantasy playable in-game (2–3 wk).** Lookahead scheduler, Gervill real-time
output, BEAT transitions with retraction (default rules: bar; combat/danger next beat with retraction), runtime stuck-note
watchdog, latency monitor, `adapt` live preview, `athenacl-gdx` (PCM pump, `poll()`, F3 snapshot + logs, F4 overlay,
`MusicDebugVars`), `samples/gdx-demo` dungeon demo on `fantasy-dungeon`. **First playable music for the dungeon crawler.**
*(old M6 retraction/defaults, M7)*
```
athenacl render themes/fantasy-dungeon.yaml --timeline samples/timelines/ambush.json --seed 1 --out build/l6
   → debug trace: "transition to=combat quantize=beat retracted=<n> latency.ms=<=<beat+lookahead>"
athenacl debug check build/l6 --stuck-notes        → "stuck=0"
athenacl debug fuzz-transitions themes/fantasy-dungeon.yaml --runs 1000 --seed 1 → "runs=1000 stuck=0 lateTransitions=0"
gradlew :samples:gdx-demo:run   (manual) F3 → build/athenacl-snapshot.txt has "state= intensity= bar= sched.lookaheadMs= cpu.msPerBar="
athenacl debug replay build/athenacl-control.jsonl → "hash=" equal to build/athenacl-events.jsonl hash
```

**L6b: SFZ loader + fantasy-orchestra kit (2–3 wk).** SFZ opcode subset → Gervill `ModelInstrument`s in memory, WAV decode at
load, region-level selective loading, seeded round-robin via alternating slots, `bank convert sfz→sf2`,
`instruments/fantasy-orchestra.yaml` from VSCO 2 CE (CC0) with `gm-lite` fallbacks, adopted by `fantasy-dungeon`. *(new, §6.12.6)*
```
athenacl kit validate instruments/fantasy-orchestra.yaml → exit 0; "format=sfz regions=<n> unknownOpcodes=<list or 0>"
athenacl instruments audition violins --kit fantasy-orchestra --notes G3,D4,A4,E5 --out build/l6b → WAV; "regionsLoaded=<subset>"
athenacl bank convert libs/VSCO-2-CE/Strings/Violin\ Section\ Sus.sfz build/l6b/vln.sf2 → "presets=1 samples=<n>"
athenacl render themes/fantasy-dungeon.yaml --seed 1 --bars 1-16 --out build/l6b-t → "kits=fantasy-orchestra,gm-lite"; contour within ±1 LU
athenacl debug inspect build/l6b-t/events.jsonl --where "layer=strings" → round-robin slots alternate deterministically (same on rerun)
athenacl kit validate instruments/fantasy-orchestra.yaml --without-source vsco → "fallbacksTaken=<n>" exit 0 (all resolve to gm-lite)
```

**L7: Motif engine → fantasy v2 (2–3 wk).** Motif model, 10 transforms, development planner (sentence/period), motif
banks per state (leitmotif across states), composer MIDI/YAML import, mixed mode. `analyze` gains motif reuse,
`debug explain` shows transform chains. *(old M5)*
```
athenacl motif generate --key "D dorian" --seed 3          → "motif=m1 notes=<2..8> contour=…"
athenacl motif transform m1 --invert --invert              → equals m1 ("identical=true")
athenacl render themes/fantasy-dungeon.yaml --seed 1 --bars 1-16 --out build/l7
athenacl analyze build/l7 → "motif.reuse.per8bars>=<N>"; debug explain on a lead note shows "motif=m1:…"
athenacl motif import samples/motifs/theme.mid --out build/l7/m.yaml → "motifs=1 notes=<n>"
```

**L7b: Hook generator + melody critic (2–3 wk).** *Extends the already-built L7; no rewrite.* Hook source behind the
motif generator entry point, rhythm-cell and contour libraries, chord-tone/cadence placement, phrase forms via L7's
planner, critic features + hard rules + deterministic selection, `copyGuard`, per-genre/state YAML, the rating loop
(`melody rate|fit`), critic provenance in `debug explain`, a small license-clean evaluation corpus. *(new, §6.13)*
```
athenacl melody generate --key "E minor" --chords "i VI VII i" --candidates 64 --seed 3 --explain
   → 64 "cand=" lines; "chosen=<k>"; rerun → identical output (hash equal)
athenacl melody generate … --candidates 64 --seed 3 --json | jq '.chosen.features.closure' → >= 0.8
athenacl melody score samples/melodies/random-walk.mid --key "C major" → "score=<low> rejected=<rule or ->"
athenacl melody score samples/melodies/folk-pd-01.mid --key "G major" → "score=<higher than random-walk>"
athenacl render themes/fantasy-dungeon.yaml --seed 1 --bars 1-16 --out build/l7b
athenacl debug explain build/l7b/events.jsonl --where "layer=lead" --first → contains "hook.cand=" and "score="
athenacl debug perf build/l7b → "critic.ms.p99<10 critic.deferred=0"; per-bar budget unchanged vs L7
athenacl melody fit samples/ratings/synthetic.jsonl --out build/w.yaml → recovers the planted weight ordering; twice → same hash
athenacl melody generate … --references samples/melodies/protected.mid → candidates near it "rejected=copyGuard"
```

**L8: Adaptive complete → fantasy v3 (2–3 wk).** Overlays (e.g. `lowHealth`), bridges/wind-downs, crossfades, stinger
polish, theme switching with preloaded soundbanks, frame-quantized mode, per-theme transition overrides. *(old M6 remainder)*
```
athenacl render themes/fantasy-dungeon.yaml --at "4 overlay lowHealth 1" --bars 1-8 --seed 1 --out build/l8
   → "overlays=lowHealth"; analyze "loudness.overlayDelta=<=1.0"
athenacl debug trace build/l8 --transitions → "from=combat to=peaceful quantize=bar bridge=windDown"
athenacl debug check build/l8 --loudness   → exit 0 (no step > maxStepPerTransition without ramp)
```

**L9: Sci-fi space (1–2 wk).** `scifi-space` preset + `themes/scifi-space.yaml` (6 states), theme switching
fantasy ↔ sci-fi in the demo. *(old M6b second half)*
```
athenacl theme validate themes/scifi-space.yaml   → "states=6 warnings=0"
athenacl render themes/scifi-space.yaml --timeline samples/timelines/dungeon-tour.json --seed 1 --out build/l9
athenacl analyze build/l9 --by state → contour within ±1 LU; describe-music shows only scifi program set, arps on grid
athenacl render themes/fantasy-dungeon.yaml --at "8 theme themes/scifi-space.yaml" --bars 1-16 → "themeSwitch.bar=9"
```

**L9b: Effects core (2–3 wk).** `athenacl-fx`: bus model (one Gervill instance per bus, Gervill reverb/chorus/AGC off),
filters with automatable cutoff, EQ, compressor + sidechain ducking (bus key or beat-synced envelope), saturation/
bitcrush, master limiter, automation sources (`byIntensity`, `byState`, `lfo`, `sweep`, `envelope`), post-FX loudness
calibration, per-bus meters, `--fx on|off`, `--bypass`, `--stems`, `debug fx`, `debug fx-curve`, `debug perf --fx`,
F3/F4 bus meters. *(new, §6.11)*
```
athenacl render themes/fantasy-dungeon.yaml --seed 1 --bars 1-8 --out build/l9b --fx off   → "hash=<h0>"
athenacl render samples/themes/fx-null.yaml --seed 1 --bars 1-8 --out build/l9b0 --fx on
   → PCM identical to --fx off ("nullTest=true"; all units at neutral settings)
athenacl debug fx-curve samples/themes/fx-sweep.yaml --param music.pad.filter.cutoff --at "1 intensity 0" --at "5 intensity 1"
   → "bar=1 value=600" … "bar=5 value=9000[±1%]"
athenacl analyze build/l9b --by bus → "bus=drums …", "bus=music …", "master truePeak<=-1.0"
athenacl analyze build/l9b --by state → contour within ±1 LU (post-FX); "limiter.gr.avgDb<=1.0"
athenacl render … --fx on (twice) → identical "hash=<h1>" (bit-stable on this JDK)
athenacl debug perf themes/fantasy-dungeon.yaml --fx --buffer 512 → "fx.cpu.pct.p99<=25 ok=true"
```

**L9c: Time-based FX + 808 slides + sci-fi/fantasy FX passes (1–2 wk).** Reverb, tempo-synced delay, chorus; 808 slide
generator (pitch-bend glides; CC5/CC65 tested); FX settings for `scifi-space` (sweeps, ping-pong delay, ducking) and
`fantasy-dungeon` (per-state reverb). *(new, §6.11)*
```
athenacl debug fx samples/themes/delay.yaml → "unit=music.arp.delay time=3/16 samples=<exact for bpm>"
athenacl render samples/themes/808.yaml --seed 1 --bars 1-4 --out build/l9c
   → debug inspect: pitch-bend ramps between slid notes; "slides=<n> bendRange=12"
athenacl render themes/scifi-space.yaml --timeline samples/timelines/dungeon-tour.json --seed 1 --out build/l9c-s
   → "fx=on buses=4"; analyze --by state within ±1 LU; listening review
```

**L9d: Sampled-audio layers (2–3 wk).** `type: sample` layers: WAV + OGG (JOrbis) decode at load, loops (bpm/bars,
varispeed or beat-slice), one-shots (pitched, with glides), beds (vinyl crackle), clip license validation, optional
TarsosDSP time-stretch. *(new, §6.11.5)*
```
athenacl theme validate samples/themes/loops-nolicense.yaml → exit 3; "error code=CLIP_LICENSE_MISSING path=/layers/loopTop/clip"
athenacl render samples/themes/loops.yaml --seed 1 --bars 1-8 --out build/l9d
   → "clips=3 loopStarts=bar1,bar3,bar5,bar7 offsetSamples=0"
athenacl debug inspect build/l9d/events.jsonl --where "type=clip" → each start on a bar line, sample-accurate
athenacl render samples/themes/loops.yaml --tempo 96 (clip at 90 bpm) → "loopTop.mode=varispeed ratio=1.067"
```

**L10: Harmony and time depth (2–3 wk).** Pivot/direct modulation and mode mixture, key policies across state
changes, meter changes inside themes, harmonic-rhythm planner, cadences at phrase ends. *(old M4 remainder)*
```
athenacl theory modulate "D dorian" "F lydian" --method pivot → "pivot=<chord> bars=<n>"
athenacl render samples/themes/modulation.yaml --seed 2 --out build/l10
   → describe-music "bar=9 key=F-lydian"; "bar=12 meter=7/8 grouping=2+2+3"
```

**L11: Debug depth (2–3 wk).** Bisect (renders + control-log delta-debugging), theme/render `diff`, multi-JVM determinism,
full provenance coverage, observer-effect test harness, overhead benchmarks. *(old M6d remainder)*
```
athenacl debug bisect --good build/l11/a --bad build/l11/b  → "firstDivergentBar=<injected bar>"
athenacl diff themes/fantasy-dungeon.yaml themes/scifi-space.yaml → "changed=/states/combat/tempo …"
athenacl debug determinism themes/fantasy-dungeon.yaml --runs 2 --fork-jvm → "identical=true"
athenacl render … --debug provenance vs --debug off → same "hash=<h>"
```

**L12–L20: athenaCL port track** (can start after L1, in parallel with L2–L11, e.g. by a second agent). Every layer adds
`describe po|texture`, `po sample`, and `athena run <script>`-level commands for what it ports, and extends the golden suite.
| Layer | Scope (old milestone) | Agent-testable exit check | Effort |
|---|---|---|---|
| **L12** Port foundation | `athenacl-compat` (CompatRandom, Py.*, PyLiteralParser, PoArgParser), pregui oracle scripts, the 31-demo golden corpus, `conformance run` command (old M0) | `athenacl compat random --seed 7 --n 5` matches CPython values; `athenacl conformance list` → "scripts=31"; parser fuzz test green | 2–3 |
| **L13** Port slice A | pitchTools, multiset, PolyPath, EventList, `MidiScore` byte-exact writer, `athena run` (old M1) | `athenacl athena run conformance/demos/<simple>.ath --out build/p` → "bytesEqualGolden=true" for the path-only demos | 2–3 |
| **L14** Port slice B | `LineGroove`, ~12 POs, first commands (`emo tin tie tmo ticp pin tio timode tpsd tmsd eln`); ported POs usable as `po:` generators in themes (old M1) | `athenacl conformance run --subset slice` → "pass=<n> fail=0"; `athenacl po sample basketGen "oc,(1,2,3)" --n 6` → "values=1,2,3,1,2,3" | 2–3 |
| **L15** valueSingle POs | 46 POs (old M8) | `conformance run --subset po.value` → "fail=0"; `describe pos --group value` → "count=46" | 2–3 |
| **L16** OMDE + rhythm POs | 44 + 13 POs (old M8) | `conformance run --subset po.omde,po.rhythm` → "fail=0" | 2–3 |
| **L17** Textures + Clones | remaining 13 TextureModules, clones, clone filters, textureStatic/cloneStatic POs (old M8) | `conformance run --subset texture,clone` → "fail=0"; `describe textures` → "count=14" | 2–3 |
| **L18** Theory/generators | set-class tables (generated), sieves, temperaments, markov/grammar/automata/feedback/genetic/… (old M8) | `athenacl describe setclass 3-11` → "forte=3-11 …"; `conformance run --subset sieve,generators` → "fail=0"; doctest fixtures green | 2–3 |
| **L19** Commands A + project XML | ~55 commands, project XML load/save (old M8) | `conformance run --subset commands.a,xml` → "fail=0" | 2–3 |
| **L20** Commands B + REPL | remaining ~55 commands, JLine REPL, help text, typed results for every command (old M8) | `conformance run` → "scripts=<all> fail=0" (all 31 demos + ~150 per-PO/texture scripts byte-identical); `help --json` lists every command | 2–3 |

**L21–L23: finish.**
| Layer | Scope (old milestone) | Agent-testable exit check | Effort |
|---|---|---|---|
| **L21** Other genres (cuttable to post-1.0) | ~14 genre presets + world mode packs, example themes (old M8b) | `athenacl preset list` → "presets=<16+>"; `athenacl preset audition <each> --bars 8 --seed 1` exit 0 with genre-character checks green | 2–3 |
| **L22** Hardening | final schemas + docs + agent guide, JSONL batch mode, perf (< 5 ms/bar), scripted agent e2e (old M6c remainder, M10) | `athenacl batch < checks/batch.jsonl` → same results as one-by-one; `athenacl debug perf themes/fantasy-dungeon.yaml` → "gen.msPerBar.p99<5"; `gradlew layerCheck` (all layers) green | 2–3 |
| **L23** Publish | GitHub repo, CI, release artifacts, soundbank mirror (old M10) | CI green on Windows/macOS/Linux; `athenacl version` from the release zip | 1–2 |

Note: L9b–L9d come right after sci-fi, so neither fantasy nor sci-fi waits for them. They can also slide after
L10–L11 without affecting anything else. Before L9b, all renders are dry Gervill output, and every later layer's exit
checks run with `--fx off` and `--fx on`.

**Optional (last):** **L24** graphics commands (old M9, 2–4 wk; may split), **L25** Csound output (old M11, 2–3 wk), **L26**
SuperCollider output (old M11, 1–2 wk), each with golden text-output checks.

### 7.3 Totals and timeline [estimates]
- **Sum of core layers L0a–L23 incl. L1b, L6b, L7b and L9b–L9d:** about **55–86 FTE-weeks**. L7b (hooks + critic) adds
  2–3, the instrument kits add 3–5 (L1b 1–2, L6b 2–3), and the effects stage adds 5–8 (L9b 2–3, L9c 1–2, L9d 2–3). With a
  **~10% integration buffer** that's **about 61–95**. With optional L24–L26: **60–95** raw, **about 66–105** with the
  buffer. Earlier figures: 53–83 (58–91) before L7b, 50–78 (55–86) before the kits, 45–70 (50–77) before the effects stage.
  L7 is already built by the user's agent, so its 2–3 weeks are spent; the remaining effort is about 2–3 weeks lower.
- Compared with the previous 56–80, the main saving is that the CLI/debug tooling (old M6c/M6d, 7–9 wk) is now built
  once in L0 and extended inside each layer, instead of being a separate pass. The per-layer CLI/test work is included in
  each layer's estimate.
- **Milestones that matter to the user:**
  - **Fantasy-dungeon offline renders:** end of L5, about **12–20 weeks** in.
  - **Fantasy playable in the dungeon crawler:** end of L6, about **14–23 weeks**.
  - **Fantasy with the SFZ orchestra kit:** end of L6b, about **16–26 weeks**. L6b can also be deferred until after L9
    if sci-fi matters more; nothing else depends on it.
  - **Fantasy hooks + critic:** end of L7b, 2–3 weeks after the (already finished) L7.
  - **Fantasy complete (motifs, hooks, overlays):** end of L8, about **22–35 weeks** (from project start).
  - **Sci-fi space:** end of L9, about **23–37 weeks**.
  - **Effects + 808 slides (sci-fi enriched):** end of L9c, about **26–42 weeks**; **sampled layers** end of L9d, about **28–45**.
  - Running the port track (L12–L20, 18–27 wk) in parallel with L2–L11 shortens the calendar but not the effort.
- **Cheapest cuts:** the L7b rating loop (`melody rate|fit`, ~0.5 wk; hand-set weights still work), L6b (SFZ loader, 2–3 wk; fantasy keeps `gm-lite`), L9d (sampled layers, 2–3 wk), L21 (other genres, 2–3 wk), batch mode, bisect delta-debugging, multi-JVM determinism (about 1.5–2.5 wk
  together). L0's contract, event log, provenance hooks and determinism check shouldn't be cut.

### 7.4 Where the old milestones went
| Old | New layers |
|---|---|
| M0 Foundations/oracle | L0a (Gradle), L12 (compat + oracle) |
| M1 Ported slice | L13, L14 |
| M2 Render/soundbanks | L0b (render), L1 |
| M3 Adaptive MVP + agent/debug foundations | L0a/L0b (foundations), L4 |
| M4 Theory/rhythm | L2, L3, L10 |
| M5 Motif engine | L7 |
| M6 Adaptive complete | L5 (calibration), L6 (retraction, defaults), L8 |
| M6b Fantasy + sci-fi | L5, L9 |
| M6c Agentic CLI | L0a, L0b, plus every layer's commands, L22 (batch, e2e) |
| M6d Debug tools | L0b, L4, L6, L11 |
| M7 libGDX | L6 |
| M8 Port breadth | L15–L20 |
| M8b Other genres | L21 |
| M9 Graphics | L24 |
| M10 Hardening/publish | L22, L23 |
| M11 Csound/SC | L25, L26 |
| *(new, Oct 10)* Effects stage | L9b, L9c, L9d |
| *(new, Oct 10)* Instrument kits + SFZ | L1b, L6b |
| *(new, Oct 10)* Hook generator + melody critic | L7b |

---

## 8. Testing strategy

### 8.0 Layer exit checks (agent-runnable)
Every layer's exit check (§7.2) is committed as `checks/LNN.check` and run by `gradlew layerCheck` (all layers) in CI.
The suites below sit underneath: the checks prove a layer works from the command line, and the JUnit/property/golden
suites prove it in depth. A layer is done only when both are green and its AGENTS.md rows exist.

### 8.1 Golden conformance for the ported core (compat mode)
- **Oracle:** the pregui tag on CPython 3.12. Verified deterministic with fixed seeds; GraalPy produced
  identical hashes (03 §5). The corpus is the 31 MIDI demos (manifest
  `tools/golden-pregui-manifest.txt`) plus generated per-PO/per-texture scripts.
- **Artifacts:** per script, `.mid`, `events.json` (inst, time, dur, sus, acc, amp, ps, pan per event),
  and sha256. Committed, so Java CI needs no Python.
- **JUnit:** parameterized over the corpus, comparing events field by field (report the first divergence),
  then MIDI bytes. Fixed output file names (the MIDI track name embeds the file stem; 03 §5).
- **Unit fixtures:** harvested from the 1,752 doctest lines; plus RNG and parser fixtures (L12).

### 8.2 Deterministic adaptive renders
- **Scenario files** (`timeline.json`: seed + control calls at times) for each starter theme. Snapshots
  are **event-log hashes** (canonical JSON of NoteEvents/ControlEvents). Offline WAV renders are
  smoke-checked by loudness/peak tolerance, not bytes (Gervill may change across JDKs).
- **Replay test:** record a live-style session's control log, replay offline, require identical events.

### 8.3 Musical property tests (jqwik)
- Pitches belong to the active scale/chord (allowing declared chromatic passing tones); melody range and
  leap limits.
- Voice leading: no parallel perfect 5ths/8ves between outer voices; common tones held when available;
  total motion ≤ bound.
- Harmonic rhythm matches the spec; cadences at phrase ends.
- Rhythm: layer patterns sum to the bar length in every meter; polyrhythm alignment at declared points;
  swing applied only to off-beats; humanization bounded.
- Transitions happen only at the requested quantization grid. After retraction, **no events remain past the
  switch point** from the old state, and **no stuck notes** (every note-on has a note-off), fuzzed over
  random control sequences.
- Motif transforms satisfy algebraic identities; development reuses motif material at the planned rate.
- Loudness contour: per-state integrated loudness within ±1 LU of `reference + contour[state]`; spread ≤ `maxSpread`
  (6 LU); every transition step ≤ `maxStepPerTransition` or ramped; true peak ≤ −1 dBTP; overlays ≤ +1 LU, stingers ≤ +3 LU
  short-term.
- Transition defaults: entering combat/danger lands on the next beat with no old-state events after the switch;
  all other entries land on bar lines; theme overrides are honored.
- Determinism: the same seed and log give an equal hash; a different seed changes the hash (sanity).

### 8.4 Performance and soak
- Bar generation time and allocation (JMH), scheduler jitter, a 30-minute soak in the libGDX demo with
  underrun counting, and memory for large soundbanks (FluidR3 148 MB vs GeneralUser 32 MB, 02 §1).

### 8.5 Listening reviews
- Scheduled human listening sessions per layer (L4, L5, L6, L7, L8, L9, L21) with rendered state/transition reels.
  Numbers alone can't judge musicality (02 §3, 04 §7).

### 8.6 Genre presets and authoring tooling
- **Schema:** every built-in preset and example theme validates. The schema generated from the records equals the
  committed schema (drift test). The validator agrees with the loader (whatever loads, validates; whatever
  fails, gets a JSON-pointer error), using fuzzed and mutated YAML.
- **Merge semantics:** golden `theme explain` outputs for override cases (deep map merge, list replace,
  `+append`, multi-preset order, state-level overrides).
- **Fantasy/sci-fi character (first, L5 and L9):** fantasy stays in its declared modes with drones/pedals present in
  sneaking/danger and no swing; fantasy combat uses ostinato + taiko/orchestral kit lanes; sci-fi uses only its
  synth/pad/bell program set, keeps arpeggio layers on their subdivision grid, and uses Lydian/whole-tone material where
  declared; both themes cover all 6 starter states.
- **Genre-character properties:** drum lanes fill exactly one bar of the preset's meter; swing presets
  delay off-beats by the declared ratio; reggae one-drop accents beat 3 with no kick on 1; bossa
  patterns contain the declared clave/partido-alto onsets; 12-bar blues follows I–IV–I–V–IV–I-type
  templates; metal power chords are root+5th(+8ve); chiptune uses only square/saw/noise-style programs; tempo,
  meter and mode stay inside preset ranges at every intensity.
- **Determinism:** each preset × starter state × seed set has a committed event-log hash.
- **CLI:** `theme validate` exit codes and JSON report shape; `render` gives identical events for repeated runs.
- **Listening:** an audition reel per genre is reviewed by a human (fantasy in L5, sci-fi in L9, the rest in L21), since property tests can't judge
  whether "funk" sounds like funk.

### 8.7 Agentic CLI and debug tools
- **Contract:** golden JSON outputs per command validated against the published schemas; schema-compatibility diff
  against the last release in CI (breaking change without a version bump fails the build).
- **Exit-code matrix:** one test per command × failure class (usage, invalid, not found, environment, check failed).
- **stdout purity:** in `--json` mode stdout parses as exactly one JSON document (JSONL in batch), even with
  `-vv`. In text mode stdout holds only `key=value` records. All logs are on stderr.
- **Exit 1/2 parity with jvm-mw:** unknown command → stderr message + help + exit 2; an injected uncaught exception →
  exit 1. Note that jvm-mw itself has no automated tests (it runs self-checks at CLI start, doc 06). athenaCL-J keeps
  JUnit/golden suites because byte-exact athenaCL parity needs them.
- **Idempotency and dry-run:** a second identical `render` reports `changed:false` and leaves file hashes and mtimes
  unchanged. `--dry-run` leaves the filesystem snapshot unchanged and its plan matches what a real run writes.
- **Stateless stepping:** concatenating `render --bars 1-8`, `9-16` and `17-24` gives exactly the events of
  `render --bars 1-24`, and `--at` inline controls equal the same calls in a timeline file. Batch output equals the
  same commands run one by one. All of it runs headless in CI with no sound card.
- **Text output stability:** golden `key=value` outputs per command (grep-stable keys are a contract, as in jvm-mw's
  phase specs). A test checks that every key in AGENTS.md examples still exists.
- **Agent e2e:** a scripted agent (deterministic, non-LLM) follows the AGENTS.md workflow on broken example themes
  and must reach valid renders using only `fix` hints. Optional periodic evals with real LLM agents are tracked but
  don't gate CI.
- **Analysis correctness:** synthetic fixtures with known answers (C-major scale → key C, high confidence; known
  density; an exactly repeated motif → reuse 1.0; a swung pattern → measured swing ratio) and golden
  `describe-music` snapshots (ASCII roll, chords per bar).
- **Debug observer effect:** each starter theme renders with debug off, traces on, and full provenance on. All
  canonical hashes must be equal.
- **Provenance completeness/re-derivation:** every NoteEvent has provenance, and re-running its seed path in isolation
  reproduces the same pitch/duration/velocity.
- **Bisect/diff/stuck notes:** injected divergences (changed seed scope at bar N) are found at bar N. Injected missing
  note-offs and retraction bugs are caught, with no false positives over 10k fuzzed control sequences. Golden
  outputs for theme diffs.
- **Determinism checker self-test:** mutation tests inject nondeterminism (`HashMap` iteration, `System.nanoTime`,
  thread race). The checker must flag each one (exit 6).
- **Overhead:** JMH, debug-off < 1% and full provenance ≤ ~15% generation-time overhead [targets].

---

### 8.8 Effects stage (L9b–L9d)
- **DSP unit tests on synthetic signals:** biquad magnitude response vs the analytic curve (±0.1 dB); compressor gain
  reduction on step signals; limiter never exceeds the ceiling (4× oversampled true peak) over fuzzed input; delay time
  equals the tempo-synced sample count exactly; bitcrush/saturation transfer curves; reverb tail decay within
  tolerance; no NaN/denormal blow-ups (fuzzed parameters, extreme automation).
- **Null test:** an FX chain at neutral settings is bit-identical to `--fx off`; `--bypass` of a unit equals removing it.
- **Determinism:** two offline FX renders are bit-identical on the same JDK; the live block pipeline fed the recorded
  log equals the offline render (with `degrade` off); internal 64-frame blocks give identical output for device buffers
  of 256/512/1024.
- **Automation:** `debug fx-curve` goldens for `byIntensity`/`byState`/`lfo`/`sweep`; state changes glide over the
  transition's unit.
- **Loudness:** post-FX contour within ±1 LU, average limiter gain reduction ≤ 1 dB, master true peak ≤ −1 dBTP.
- **808 slides:** pitch-bend curve matches the requested glide time and range; no stuck bends after retraction.
- **Sampled layers:** loop starts are sample-accurate on bar lines; varispeed ratio correct; slice retrigger aligned to
  beats; clips without a license fail validation; OGG and WAV decodes equal reference PCM.
- **CPU:** JMH per effect and per typical chain; `debug perf --fx` p99 under budget on a reference machine; bus-count
  scaling measured (and soundbank memory sharing across Gervill instances verified).

### 8.9 Instrument kits and SFZ (L1b, L6b)
- **Multi-bank:** the `MultiBank` harness as a JUnit test. Instruments from two banks in one synth each correlate
  ≥ 0.95 with their single-bank reference. The allocator never produces two instruments in one slot (property test over
  random kits). Percussion-kit remap gets the same test.
- **Determinism:** slot allocation is identical across runs and machines for the same kit list; renders with the same kits
  give the same hash.
- **Selective loading:** heap delta for a theme's instruments stays below a set fraction of `loadAllInstruments`
  (e.g. two pianos < 20 MB vs ~183 MB).
- **Normalization:** after normalization, probe loudness for the same instrument across `gm-lite` and `gm-full` is within
  ±0.5 LU; the cache invalidates when the checksum changes.
- **Validation:** fixtures for an SF3 file (exit 3, `SOUNDBANK_SF3_UNSUPPORTED`), an unreadable file, a checksum mismatch,
  a denied license, a missing source (fallback taken and reported), unknown names (did-you-mean hint), and `extends`/override
  merge goldens.
- **Credits:** golden CREDITS.md for themes using one or two kits. Only the used sources appear, with their license files
  copied.
- **SFZ:** opcode parser goldens (incl. `#define`/`#include`), region mapping (key/velocity), envelope and loop opcodes,
  round-robin alternation that is deterministic and seeded, `bank convert` round-trip (SFZ → SF2 → load → same regions),
  render sanity (non-silent, pitch matches `pitch_keycenter`), unknown-opcode warnings.

### 8.10 Hook generator + melody critic (L7b)
- **Separation:** on a license-clean corpus of public-domain folk/hymn phrases (transcribed in-house, or a dataset whose
  license is checked first; Q33), the critic ranks corpus phrases above **random walks** (AUC ≥ 0.85) and above
  **pitch-shuffled** versions with the same rhythm (AUC ≥ 0.70). The thresholds are starting estimates.
- **Feature unit tests:** hand-made melodies with known values (all chord tones on strong beats → `chordTone`=1; final
  note on degree 2 → closure low; one unrecovered 9th → `leapRecovery` low; an exact 8× repeat → novelty below band).
- **Hard rules:** each rule has a minimal failing fixture. `copyGuard` rejects transposed or rhythm-preserved copies of a
  reference and accepts unrelated melodies.
- **Generator properties:** over 1,000 seeds, hooks have 3–6 notes, ≤ 1 leap > a 4th (unless configured), strong-beat
  chord-tone share ≥ the configured minimum, and cadences on allowed degrees.
- **Determinism:** same seed + settings + weights → identical candidate list, scores (bit-equal doubles; fixed summation
  order) and choice. Hashes checked across two JVM runs.
- **Rating fit:** synthetic ratings generated from known weights → the fit recovers the weight ordering; identical input →
  identical weights file.
- **Non-regression with L7:** existing L7 tests and goldens pass unchanged when `hooks:` is absent.
- **Performance:** a JMH-style micro-benchmark asserts the 64-candidate budget (§6.13.4); the render perf trace shows no
  per-bar regression.

## 9. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Port breadth larger than estimated (`command.py` 9.1k lines, 136 POs, Python quirks: `round`, `%`, RNG call order; 03 §3) | Schedule slip | Golden corpus first (L12). LLM-assisted drafting gated by per-PO goldens. Run the port track (L12–L20) in parallel with the adaptive layers. |
| Generated music sounds aimless or repetitive | Product failure | Motif development + phrase forms (L7), composer-supplied mixed mode, theme memory, listening reviews |
| `com.sun.media.sound` internal API (`AudioSynthesizer`) changes or gets locked down in a future JDK | Breaks offline/libGDX pull mode | Isolate it in `GervillStreams`. Fallback to Gervill's own output line. Watch JDK releases. Long-term option: a pure-Java SF2 synth (e.g. vendoring Gervill's GPL+CE sources into the project). |
| Gervill quality limits (no per-channel FX, loudness variance between programs) | Sound quality | Automatic loudness calibration, curated themes, optional external MIDI output to better synths |
| Real-time timing (GC pauses, OpenAL buffer underruns in libGDX `AudioDevice`) | Audible glitches | Lookahead + pre-generation, allocation discipline, buffer tuning, soak tests |
| Retraction complexity (held notes, ties, sustain pedal) | Stuck or cut notes | A single owner thread, explicit pending-note-off table, fuzz tests |
| Determinism in live sessions depends on call timing | Replays/multiplayer differ | Control log + frame-quantized mode |
| GeneralUser GS sample-provenance caveat | Commercial games' legal comfort | Document it. Pluggable soundbank. Let games choose FluidR3 (MIT) or their own. |
| GPL-3.0-or-later for embedding | Limits closed-source games using the library | Settled by the user (their projects are GPLv3). Document it clearly for third parties. |
| Scope creep (graphics, Csound/SC, editors) | Delays core | Explicitly late/optional (L24–L26) |
| Genre presets sound stereotyped or "MIDI-cheesy" given GM/GS limits | Disappointing genre results | Documented `limits:`, curated intensity tiers, per-genre listening reviews, custom soundbank option |
| World/ethnic mode packs misrepresent traditions | Cultural/credibility issues | Label them as "inspired" scale packs. Invite review by practitioners. No authenticity claims. |
| Preset content or agent-authored themes copy protected material | Copyright exposure | Presets use only generic patterns/progressions written from scratch (no transcriptions of specific recordings). Authoring docs give the borrow-style/no-verbatim guidance (§6.8). |
| Preset scope creep (more genres, sub-genres) | Delays L5/L9/L21 | Fixed initial list. New presets are data-only and can come after 1.0. |
| The JSON/CLI contract becomes a public API that agents depend on | Breaking changes break agent workflows | Versioned schema ids, CI schema-compat diff, `--schema-version` for one major release, deprecation notes in results |
| Provenance/debug plumbing complicates generators or alters output | Slower development, nondeterminism | Hooks designed in L0b; scoped context instead of extra params; observer-effect hash tests; no-op default |
| Debug/analysis tooling grows without bound | Delays the music engine | Must-have vs later split (§7 totals note); tools reuse one event-log format |
| Text descriptions and key/motif metrics mislead agents (lossy roll, key-estimation errors) | Bad automated decisions | Confidence values, labelled estimates, structured JSON alongside ASCII, provenance-based (exact) reuse figures where possible |
| CLI writes files on an agent's behalf | Accidental overwrites, path escapes | Outputs only under `--out` (default `build/`); inputs never modified; atomic writes; `--dry-run`; no network fetches except `soundbank install` |
| JVM startup cost for many small agent CLI calls (no server to amortize it) | Slow agent loops | Batch mode keeps one JVM; AppCDS archive in the launcher; native-image as an open question. jvm-mw accepts Gradle-task startup for its debug CLI, which suggests it's tolerable. |
| Several Gervill instances (one per bus) cost CPU/memory; soundbank sample sharing across instances unverified | Frame drops or high RAM with many buses | Cap buses (default 4); measure in L9b; single-bus mode; render-twice for offline stems; patched-mixer option as last resort |
| Gervill has no per-channel audio taps (verified in OpenJDK 21 `SoftMainMixer`) | Per-layer FX need a bus split | Per-bus instances (§6.11.1); route by layer, not by MIDI channel |
| DSP CPU spikes (reverb, oversampling, denormals) on low-end machines | Audio underruns in game | 25% buffer budget with metering, flush denormals, `degrade` mode (logged), quality settings per effect |
| FX hide or exaggerate loudness differences | Contour drifts | Calibrate post-FX, before the limiter; cap limiter gain reduction; cache keyed by FX config |
| Sampled clips: licensing, decode cost, tempo-sync artifacts | Legal risk or poor sound | Required `license:` field, CC0-only bundled examples, decode at load, varispeed/slice by default, time-stretch optional |
| OGG decoder (JOrbis, LGPL) and TarsosDSP (GPL-3.0) add dependencies | Maintenance | Both optional; WAV-only and no-time-stretch builds work |
| Kit remapping relies on `com.sun.media.sound.SimpleInstrument` (internal), and the percussion-kit remap is unverified | Breaks on a JDK change; kits from two banks collide | Same isolation as `GervillStreams` (one class); verified melodic remap (§6.12.2); percussion verified in L1b; fallback to one kit per bus instance |
| Silent patch collisions (a later `loadInstrument` replaces an earlier one, verified) | Wrong instrument plays | The engine never loads at source patches; deterministic slot allocator; validator checks `slots.collisions=0` |
| Large banks/SFZ libraries exhaust heap (MSG full load ≈183 MB; SFZ libraries 1–3 GB on disk) | OOM or long loads in game | Load only referenced instruments/regions; per-instrument memory report; preload off-thread; budget check in `kit validate` |
| SFZ coverage gaps (opcodes, round-robin, FLAC) | Libraries sound wrong or fail to load | Opcode subset with warnings; RR via slots; WAV first; fallbacks to `gm-lite`; `bank convert` for inspection in Polyphone |
| Licenses of mixed sources (CC Sampling+ 1.0 untested with GPL; BY-SA share-alike; GeneralUser provenance caveat; MSG "v0.2 work in progress") | Legal risk for commercial games | License policy with allow/warn/deny, credits generation, banks kept as separate data (aggregation); user/legal check before release (Q28) |
| Per-bank loudness differences (~7 LU, ~10 dB on one piano) | Unbalanced mixes when kits change | Per-instrument normalization + per-kit state calibration |
| Critic rewards "textbook" melodies; output becomes bland or samey | Hooks lack character | Novelty is a target **band**, distinctiveness feature, style-specific corpora, per-theme weights, the rating loop, listening reviews |
| Features are proxies; research findings are correlational and corpus-specific (Western tonal, folk/pop) | False confidence in "quality" | Documented as flags + ranking, not beauty; low weights for earworm features; modal/world styles get their own key profiles or turn features off |
| L7 already exists, and its internals may not match this design | Rework or regressions | Integrate via the generator entry point/adapter only; L7 goldens must pass unchanged; read L7's code before L7b's spec |
| Rating data is small and noisy (one user) | Overfitted weights | L2 prior toward defaults, cross-validated accuracy reported, minimum n before `fit` writes weights, weights are versioned and revertible |
| Critic CPU at state changes | Late hook at a transition | Choose ahead at phrase look-ahead, cached; deferral fallback with a logged counter; benchmark in CI |
| `copyGuard` false negatives (melodies altered beyond the n-gram matching) | Copyright exposure remains | Guard is a safety net, not clearance; keep the style-only authoring rule (05) and human review |
| Stateless stepping gets slow for long timelines (always regenerating from bar 1) | Slow bar-by-bar debugging | Per-bar generation budget (< 5 ms); optional file-based `--checkpoint` later |

---

## 10. Remaining open questions

1. **Project name, Maven group ID and Java package** (`athenacl-j`/`org.athenaclj` are placeholders). Is
   keeping "athenaCL" in the name OK with the upstream authors?
2. **athenaCL project XML compatibility:** must the Java version load pregui-saved `.xml` projects (03 §3,
   Python literals in attributes), or only scripts?
3. **Graphics commands** (TPmap/TImap/TCmap/AUca …): needed in the rewrite (L24), or can they be dropped?
4. **Composer input formats:** MIDI + YAML enough, or also MusicXML / tracker formats?
5. **Overlay semantics:** may overlays change key/tempo, or only add layers and modifiers? Max simultaneous overlays?
6. ~~Urgent-transition defaults?~~ **Resolved (Oct 9):** next bar by default; combat and danger on the next beat with
   retraction (release ~100 ms); per-theme overrides (§6.4).
7. ~~Loudness targets?~~ **Resolved (Oct 9):** designed relative contour per theme with defaults (quiet states −1 to −1.5 LU,
   partying/danger +1.5 to +2, combat +4; spread ≤ ~6 LU), calibrated automatically (§6.4.1). Still open: the absolute
   `reference` level for the user's games (−23 LUFS is a placeholder).
8. **Determinism scope:** is the control-log replay guarantee enough, or is lockstep (frame-quantized)
   determinism needed for multiplayer?
9. **CPU and memory budget** on minimum target hardware, and the maximum simultaneous layers/voices.
10. **Distribution of GeneralUser GS:** confirm the plan in §2.1 (optional bundled artifact for shipped games +
    CLI download from our mirror). Is the sample-provenance caveat acceptable for the user's games? *(Since Oct 10 it's
    the default `gm-lite` kit; see also Q27.)*
11. **Microtonal/temperament support** in the adaptive engine (athenaCL has it; adds MIDI Tuning complexity)?
12. **Licensing of starter themes and demo assets** (GPL-3.0 like the code, or CC-BY for themes so games can
    reuse them freely?)
13. ~~Genre priority?~~ **Resolved (Oct 9):** fantasy first (dungeon crawler), then sci-fi space; the other genres
    later in L21 (§6.7.2). Still open: the order of the later genres and world/ethnic packs.
14. **Preset granularity:** are broad genres enough, or are sub-genres needed (e.g. bebop vs swing, drum'n'bass vs house)?
    Should presets be selectable **per state** (shown in §6.7.4) or only per theme?
15. **Preset licensing:** distribute presets under GPL-3.0 with the code, or a permissive license (e.g. CC0/CC-BY) so
    themes derived from them can be shared freely?
16. ~~Authoring docs for AI agents?~~ **Resolved (Oct 9):** ship `AGENTS.md` + an agent skill file (§6.9.5).
17. **Debug defaults in shipped games:** compile/strip `athenacl-debug` from release builds, or keep the overlay and
    session dump available behind a flag for player bug reports?
18. **CLI distribution for agents:** fat jar + `java -jar`, a jpackage launcher, or GraalVM native-image for fast
    startup? Gervill/`javax.sound` support under native-image is unverified and likely needs work.
19. **Analysis depth:** are the listed metrics enough, or should agents also get audio-derived features (spectral
    balance per section, masking between layers)? Those would need DSP code beyond the R128 meter.
20. ~~Java 17 vs 21~~ **Resolved (Oct 9, 2026): Java 21 minimum, matching jvm-mw.**
21. ~~Is `--json` needed?~~ **Resolved (Oct 9, 2026): keep `--json` as an optional flag; `key=value` stays the default.**
22. ~~Package/group naming~~ **Resolved (Oct 9, 2026): the user is fine with any name; use `io.github.sosgames.athenacl` as the working default (refines Q1).**
23. **Effects budget and buses:** max buses (Gervill instances) and FX CPU budget on the dungeon crawler's minimum hardware?
    Is the default `warn` (keep bit-identical) right, or should live play `degrade` under load?
24. **Sampled layers:** which clips do the user's games need (loops, 808 one-shots, crackle, ambiences)? Who supplies them,
    and under which licenses? WAV only, or OGG too (adds JOrbis)?
25. **Time-stretch:** is varispeed/beat-slicing enough for loops, or is true time-stretch (TarsosDSP WSOLA) needed?
26. **Effects scope per starter theme:** should fantasy-dungeon use effects at all beyond reverb/glue (more "authentic"
    GM orchestral), or only sci-fi space by default?
27. **`gm-full` distribution:** should games ship MuseScore_General (206 MB) inside the build, download it on first run
    from our mirror, or use it only for development? Its "v0.2 work in progress" status and FluidR3 provenance caveat (07)
    apply.
28. **SFZ library licenses:** OK to use Sonatina (CC Sampling+ 1.0, GPL compatibility untested) and VPO's BY-SA parts as
    separate data next to GPL-3 code in commercial games? VSCO 2 CE (CC0) is fine. Legal confirmation before release?
29. **Lute and other folk instruments for fantasy:** no license-clean free lute/oud/hurdy-gurdy was found (07). Accept
    nylon guitar/harpsichord, build a CC0 bank from freesound recordings, or commission one?
30. **SFZ sample formats:** WAV only, or also FLAC/OGG (needs decoders; FLAC library and license TBD)?
31. **Canonical instrument names:** OK to standardize on GM-derived names (`docs/instrument-names.md`) that presets and kits
    share, with kit-specific extras namespaced (`fantasy-orchestra:violins.spiccato`)?
32. **L7 integration point:** what generator/extension interface did the user's agent build in L7? L7b's spec should be
    written after reading that code (names in §6.13 are placeholders).
33. **Evaluation corpus:** which license-clean melody corpus for critic tests and novelty models (in-house transcriptions
    of public-domain tunes, or a dataset such as the Essen collection or the Nottingham folk set after a license check)?
    Per genre (fantasy folk first, then sci-fi/synth)?
34. **Default candidate count and weights** per starter theme (64? 128?), and should states differ (combat: rhythm-heavy;
    peaceful: closure/contour-heavy)?
35. **Rating loop UX:** will the user rate pairs by ear (`melody rate` with WAV playback), and how many pairs per theme
    are realistic (≥ 50 suggested before `fit`)?
36. **Should the critic also run on composer-supplied or imported motifs**, as a report only (never altering them)?
(Former Q17 on MCP priority and Q18 on attaching to a running game were removed on Oct 9, 1:25 PM: no server or MCP modes.)

---

## Appendix A: evidence carried over from 01–04

- athenaCL HEAD plays SMF output through `midi-player`/`rustysynth` with FluidR3_GM (01 §1). The Python engine
  only writes MIDI (`eventList.EngineMidiFile`, `midiTools.MidiScore`, 960 PPQ, no bank select).
- Gervill loads DLS/SF2 (not SF3). Its default bank order is `java.home/lib/audio` → Linux `default.sf2` →
  Windows `gm.dls` → emergency (01 §3.2). Offline render needs `--add-exports` (01 §3.3, verified JDK 21).
- GeneralUser GS: 287 instruments, all 13 GS kit slots, most even per-section balance in the demo render
  (5.2 dB spread) (02).
- pregui runs on CPython 3.12 (not 3.13). 185 unit tests (182 pass headless). The 31 demos are deterministic
  with seeds, and GraalPy gives identical output but is ~17× slower (03 §5–6).
- Adaptive PoC: bar-quantized transitions, intensity layering, a stinger, a deterministic render. It showed the
  lookahead full-bar delay and the 20 dB loudness spread that L5's calibration and L6's retraction address (04 §7).
