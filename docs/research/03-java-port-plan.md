# 03 — Porting athenaCL's engine to Java: analysis and plan

*Research note, Oct 9, 2026. No project was created. All numbers come from the clone at
`/workspace/athenaCL` (default branch HEAD `c5f042d`, "Port unit to rust", Sep 26, 2026) and a
worktree of its **`pregui`** tag at `/workspace/pregui` (`35b9869`, Feb 7, 2024). Experiments ran on
the research box: CPython 3.12.15 (via `uv`), GraalPy 3.12 25.2.4 community, OpenJDK 21.
Labels: **[verified]** = run or read by me; **[sourced]** = cited web page; **[estimate]** = my judgment.*

Companion files: [01](01-soundfont-microsoft-gs.md) (sound/soundfont), [02](02-soundfont-instruments.md)
(soundbank comparison), `tools/run_pregui_demos.py`, `tools/golden-pregui-manifest.txt`.

---

## TL;DR

1. **Port from the `pregui` tag, not HEAD.** `pregui` is the original athenaCL converted to pure
   Python 3 by 2to3. It runs on stock CPython 3.12. HEAD is partway through a migration to Rust: 12 modules
   (`drawer`, `error`, `unit`, `chaos`, `permutate`, `quantize`, `omde/*`, the RNG) now live in
   `src/libath/*.rs`, and HEAD imports Rust-only modules (`athenaObjExt`, `xmlToolsExt`, `dialogExt`,
   ...). So HEAD's `pysrc/` **doesn't import on CPython** (`ImportError: cannot import name 'drawer'`). **[verified]**
2. **The engine has no third-party dependencies**, only the standard library. The parts that need care are
   Python semantics: `eval` of user argument strings, discovery via `dir()`/`getattr`, CPython's
   Mersenne Twister RNG, `round()` (banker's rounding), `%`/`//` on negative numbers, and 156
   `deepcopy` calls. **[verified]**
3. **A golden test corpus already exists and is deterministic.** The 31 `demo/midi/script*.py` scripts
   with fixed seeds (`tpsd 7`, `tmsd 7`) produced byte-identical MIDI on repeated CPython runs, **and on
   GraalPy**. That gives an executable reference for the Java port. **[verified]**
4. **Options:**
   - **GraalPy embedding** works today. It produced identical output, but ran **~17× slower** cold
     (189 s vs 11 s for the corpus), adds about 120 MB, and keeps the code in Python.
   - **Jython** is out: it supports Python 2.7 only, and the code is Python 3.
   - **Automated transpilers** don't handle this dynamic code.
   - **Recommendation:** a manual, LLM-assisted port into Gradle modules, checked against the golden
     corpus. Use GraalPy only as a stopgap or as the reference oracle.
5. **First slice:** RNG + Python-literal parser + pitch paths + about 10 ParameterObjects + the
   `LineGroove` texture + the MIDI writer. That reproduces simple demo scripts such as `script02a`
   byte-for-byte. The Java library must be **GPL-2.0-or-later** (or GPL-3.0+), as covered in 01.

---

## 1. Which source to port: `pregui` vs HEAD

| | `pregui` tag (Feb 2024) | HEAD (Sep 2026) |
|---|---|---|
| What it is | Original athenaCL (Ariza, last upstream 2011) converted to Python 3 with 2to3 (`conversion.log` in the tag). Its README says "all tests of the main module (libATH) passed". | Rust/iced GUI. Python runs inside **RustPython**. Modules are being ported to Rust one at a time. |
| Python files / lines | 151 `.py` files, **85,693** lines (`find athenaCL -name '*.py' \| xargs wc -l`) | 134 files, **76,128** lines in `pysrc/`, plus Rust in `src/libath/` |
| Runs on CPython | **Yes, on 3.12** [verified]. 3.13 fails: `fileTools.py` imports `sndhdr`, which [PEP 594](https://peps.python.org/pep-0594/) removed (so are `aifc` and `audioop`). | **No.** It needs the Rust-provided `athenaObjExt`, `xmlToolsExt`, `dialogExt`, `figureExt`, `manualExt`, plus the Rust ports. |
| RNG | CPython `random` (Mersenne Twister) | `src/libath/rng.rs`: **ChaCha12 + SHA-256-folded seeds**, two streams (`parameters` for `TPsd`, `textures` for `TMsd`). Its doc comment says "Pieces saved before a stage re-render differently after it." |
| Dropped vs pregui | – | `libGfx/*`, `imageTools.py`, `fontLibrary.py`, `info.py`, `interpolate.py`, the `test/` package |

**Implication:** a seed in pregui and the same seed in the current GUI give **different music**.
Choose the reference behavior on purpose:

- **Reference A: pregui/CPython semantics (recommended first).** Easy to run (`python3.12`), a large
  corpus, matches 15 years of documented athenaCL behavior. Java needs a CPython-compatible MT19937.
- **Reference B: HEAD/Rust semantics.** Needed only if Java output must match the GUI app's renders
  for the same seed. Java would have to reimplement `rand_chacha::ChaCha12Rng` plus `rand 0.8`'s
  sampling exactly. Doable but fiddly. HEAD's own `tests/parity.py` (3,379 lines) and
  `tests/rng_tests.rs` (641 lines) are the spec for it.

Make the RNG a **pluggable interface** so both are possible (§6).

---

## 2. Module inventory (pregui), grouped by port tier

Lines are physical lines. "Code" excludes comments, docstrings and `Test` classes, counted with an
AST script. Doctests are counted separately: 1,752 `>>>` lines live in docstrings.

| Tier | Files | Lines | Code lines | Verdict |
|---|---|---|---|---|
| **A. Core engine** | 63 | 43,592 | **26,583** | Port |
| **B. Interpreter/state** | 9 | 15,001 | 9,649 | Port a slim subset |
| **C. Non-MIDI output backends** | 4 | 10,482 | 8,214 | Drop for "lite"; maybe later |
| **D. UI/docs/OS/graphics** | 16 | 14,728 | 10,936 | Drop |
| **E. Tests and demos** | 49 | 1,890 | 1,141 | Reuse as conformance corpus |

### Tier A: core engine (port)

| Area | Modules (lines) | Notes |
|---|---|---|
| **Textures** (14 TextureModules) | `libTM/baseTexture.py` (1478), `texture.py` (148, registry), `LineGroove` (147), `LineCluster` (191), `HarmonicAssembly` (241), `HarmonicShuffle` (222), `DroneArticulate` (179), `DroneSustain` (161), `IntervalExpansion` (317), `MonophonicOrnament` (265), `LiteralHorizontal` (154), `LiteralVertical` (191), `TimeFill` (186), `TimeSegment` (293), `InterpolateLine` (254), `InterpolateFill` (299) | `tmNames` in `texture.py:50` maps acronyms (`lg`, `ha`, ...) to classes. Events are dicts built in `baseTexture.makeEvent()` (`baseTexture.py:1326`): `inst, time, bpm, pulse, dur, sus, acc, amp, ps, pan, aux, comment`. |
| **ParameterObjects** (136 named) | `libPmtr/valueSingle.py` (3435), `valueSingleOmde.py` (1904), `rhythmSingle.py` (1251), `basePmtr.py` (1224), `parameter.py` (999, registry/factory), `cloneFilter.py` (850), `textureStatic.py` (661), `valueFile.py` (316), `cloneStatic.py` (123) | `parameter.py` name tables: `genPmtrNames` 84, `rthmPmtrNames` 13, `textPmtrNames` 22, `clonePmtrNames` 2, `filterPmtrNames` 15. |
| **Generators used by POs** | `markov` (864), `grammar` (945), `automata` (1180), `feedback` (1054), `genetic` (986), `dice` (383), `chaos` (203), `permutate` (121), `table` (360), `envelope` (259), `ornament` (881), `omde/{functional,rand,bpf,oscillator,miscellaneous}` (2,356) | Mostly pure math. `omde` is Maurizio Umberto Puxeddu's OMDE library, GPL-2+ (`omde/functional.py` header). |
| **Clones** | `clone.py` (988) + clone POs | Transform a texture's event stream. |
| **Pitch / path / set class** | `pitchTools` (991), `pitchPath` (445), `multiset` (1313), **`setTables` (3594, almost all data)**, `sieve` (1822), `temperament` (384), `spectral` (494) | `setTables.py` should be **generated** into a Java resource or class by script, not hand-ported. |
| **Rhythm/units/helpers** | `rhythm` (1314), `unit` (825), `quantize` (147), `drawer` (1699, a grab-bag of helpers), `error` (140), `interpolate` (62) | `drawer` mixes string, number and type-check utilities. Split it into small Java utility classes. |
| **Event output + MIDI** | `eventList.py` (3122), `midiTools.py` (441), `libOrc/{baseOrc,generalMidi,orc}` (730) | For "lite", keep only `EngineMidiFile` (`eventList.py:2334`) and the event modes `m`/`mp` (`eventModeNames`, `eventList.py:42`). `midiTools.MidiScore` writes the SMF at 960 PPQN. |

### Tier B: interpreter and state (port a slim subset)

`command.py` (9,131 lines, **~120 command classes**: `PIn`, `TIn`, `TIe`, `ELn`, `TPsd`, `TMsd`, ...),
`athenaObj.py` (1,783: `AthenaObject` holds `pathLib`/`textureLib`/`cloneLib`, plus the
`Interpreter` class), `argTools` (582), `typeset` (824, text formatting), `language` (1,210, UI strings),
`ioTools` (305) + `xmlTools` (451) (project XML), `prefTools` (407), `outFormat` (308).

The 31 MIDI demo scripts use only **8 distinct commands**: `tie` 147×, `tin` 48×, `emo` 31×,
`tmo` 14×, `ticp` 11×, `pin` 6×, `tio` 3×, `timode` 3× (plus `eln`, `tpsd`, `tmsd` from the harness)
**[verified]**. A lite command facade covering about 15 commands would replay all of them.

### Tier C: optional backends (drop for lite)

`libOrc/csoundNative.py` (8,734, mostly Csound instrument text), `csoundExternal.py` (211),
`superColliderNative.py` (357), `audioTools.py` (1,180, AIFF/WAV via `aifc`/`audioop`), plus the output
engines in `eventList.py` other than MIDI: `EngineCsoundNative/External/Silence`,
`EngineSuperColliderTask`, `EnginePureDataArray`, `EngineAudioFile`, `EngineText`
(`eventList.py:1587–2391`). Textures reach orchestras through `libOrc.orc.factory`
(`orc.py:49`), so a Java port needs an `Orchestra` interface with a General MIDI implementation only.

### Tier D: drop

`fontLibrary` (3,434), `imageTools` (2,734), `libGfx/*` (859), `htmlTools` (1,732), `fileTools`
(1,856), `osTools` (1,115), `dialog` (1,031, terminal I/O), `help` (819), `faq` (373), `info` (571),
`athenacl.py` (169, the interactive entry point). External-app launching (`APea`, `ELh` "hear")
is replaced by the Java sound layer from 01.

### Dependency graph (HEAD `pysrc`, from an `ast` import scan)

Most-imported modules: `drawer` (48 importers), `athenaObj` (44, mostly the demos), `language` (40),
`pitchTools` (27), `prefTools` (22), `error` (22), `libPmtr.basePmtr` (22), `unit` (17),
`libPmtr.parameter` (17), `typeset` (16), `libTM.baseTexture` (15).

```
                    command ──► athenaObj ──► (everything below)
                       │
            eventList ─┴──► midiTools          libOrc.orc ─► generalMidi | csound* | superCollider*
               ▲                                  ▲
   libTM.texture ──► libTM.<14 modules> ──► libTM.baseTexture ──► pitchPath ─► multiset ─► setTables, sieve, spectral
                                               │                      └► pitchTools
                                               ▼
   libPmtr.parameter ──► valueSingle ──► markov, grammar, automata, feedback, dice, chaos, sieve, table, quantize
                     ├─► valueSingleOmde ──► omde.{rand, bpf, oscillator, functional}, envelope
                     ├─► rhythmSingle ──► rhythm, genetic, markov, sieve
                     ├─► cloneFilter / cloneStatic / textureStatic / valueFile
                     └─► basePmtr ──► argTools, table, unit, typeset, language
   leaves used everywhere: drawer, error, unit, language, typeset, prefTools
```

Two couplings to cut in Java:

- **`baseTexture` → `eventList`/`libOrc`/`language`/`typeset`/`prefTools`.** The core reaches into
  UI strings and preferences. Inject these instead.
- **`libPmtr.valueSingle` → `libOrc.orc`.** A PO knows about orchestras. Hide that behind an interface.

---

## 3. Python-specific idioms that complicate porting [verified counts, pregui]

| Idiom | Where (examples) | Java approach |
|---|---|---|
| **`eval()` of user strings**: the ParameterObject argument language | `libPmtr/parameter.py:509` `rawArgs = eval(drawer.restringulator(rawArgs))`. `restringulator` quotes bare words, then `eval` turns `"pt,(c,2),(bg,oc,(7,5,2,1,1)),(c,1)"` into nested tuples. Also `rhythm.py:199/211`, `multiset.py:1002`, `sieve.py:1067`, `command.py:316/408/6160/8158` | Write a small **recursive-descent parser** for that grammar: nested `(...)` lists of numbers, bare words, quoted strings. That is the public "PO string" syntax and needs exact tests. |
| **`eval()` in sieve logic** | `sieve.py:1421` `seg = eval(evalStr)`: evaluates a set-algebra expression (`|`, `&`, `-`, complement) built from user sieve strings | A dedicated expression parser/AST. |
| **`eval()` of saved project values** | `ioTools.py:65` `data[key] = eval(data[key])`. Project XML stores Python literals in attributes, e.g. `<attr key="pcsPath" value="[(0,)]"/>` (`demo/midi/script02a.xml`) | A **Python-literal parser** (tuples, lists, dicts, str, int, float, `None`, `True`/`False`), shared with the PO parser. |
| **`eval()` as a factory** | `eventList.py:2652` `mod = eval(engine)`, `faq.py:32`, `pitchTools.py:981`, `athenaObj.py:187` (prefs) | Plain maps or switches. |
| **Plugin discovery through introspection** | `parameter.py:483–488` and `texture.py:86–93`: loop over modules, `reload(mod)`, `if objType in dir(mod)`, then `getattr(mod, objType)(args, refDict)`. Commands: `athenaObj.py:631` `dir(command)`, `:943` `getattr(command, cmd)`, `:963` `getattr(self, "proc_" + ...)`. Attribute dumps for `TIv`/`PIv` use `dir()`+`getattr` (`command.py:1644, 4621, 5982, 7389`) | An explicit **registry**: `Map<String, Factory>` built from annotated classes or `ServiceLoader`, with acronym expansion (`drawer.acronymExpand`) as a separate function. `reload()` is dev-only; drop it. |
| **Dynamic typing of PO args** | 233 `isinstance`/`drawer.isList/isStr/isNum...` checks. An argument may be a number *or* a nested PO (`(c,2)` vs `2`) | A sealed `Arg` type (`Num`, `Str`, `ListArg`), and POs that accept "number or PO" through a `ValueSource` interface. |
| **CPython RNG semantics** | `random.choice` 58×, `random.random` 28×, `random.randint` 22×, `shuffle` 1×. `omde/rand.py` uses `random.Random()` instances plus `expovariate`/`gauss`/beta wrappers (`omde/rand.py:167–338`) | Port MT19937 with **CPython's** `init_by_array` seeding, `random()` (53-bit from two 32-bit draws), `_randbelow` via `getrandbits` rejection (used by `randint`/`choice`/`shuffle`), and the exact `gauss`/`expovariate`/`betavariate`/`gammavariate` algorithms from `Lib/random.py`. java.util.Random is **not** compatible. |
| **`round()` = banker's rounding** | 101 `round(` calls, e.g. `midiTools.py:120` `durTicks = int(round((tickPerS * n), 0))` | A `Py.round(double)` helper (half-to-even, with Python's correctly-rounded `ndigits` behavior). Never use `Math.round`. Off-by-one MIDI ticks are the classic golden-test failure. |
| **`%` and `//` on negatives** | 10 `% 12` sites (pitch classes of negative pitch space values), about 79 `//` uses (approximate grep) | `Math.floorMod`, `Math.floorDiv`. |
| **`deepcopy`** | 156 uses (textures, clones, events) | Immutable value types or explicit `copy()` methods. |
| **Mutable dict "objects"** | events are `dict`s with string keys; `refDict` passed into POs | Java records (`Event`, `RefContext`) with builders. |
| **Float formatting / `repr`** | XML/text outputs depend on Python float `repr` | Only matters for XML/text output parity. Use `Double.toString` adjustments or skip byte-parity for XML. |
| **Pickle** | only legacy: `basePmtr.py:1155` "only used in ioTools for backwards compat with pickled data structs", `command.py:7071` | Not needed. Support XML only. |
| **Generators/lambdas** | small (7 `yield`, 8 `lambda`) | Trivial. |

---

## 4. Third-party dependencies

**None in the engine.** The import scan of HEAD `pysrc` found only stdlib modules: `unittest` 74,
`doctest` 63, `copy` 43, `random` 26, `os` 13, `time` 11, `urllib` 9, `sys` 8, `math` 8, ...
Python-2/Mac-era imports (`Carbon`, `macfs`, `whrandom`, `md5`, `sets`) also appear. I believe they sit
behind try/except or in dead branches, since the code imports on CPython 3.12 **[not checked line by line]**. Audio file helpers use `aifc`/`audioop`/`sndhdr` (removed in 3.13; only in Tier C/D).
The Rust GUI supplies HEAD's `*Ext` modules. The Java port therefore has nothing external to replace
except **javax.sound** for playback (see 01/02).

---

## 5. Existing tests usable as a conformance suite [verified]

| Source | Size | Result / use |
|---|---|---|
| pregui `python3.12 -m unittest discover athenaCL.libATH -p "*.py"` | **185 tests, 6.6 s** | 182 pass. The 3 errors (`athenaObj.testInterpreterPaths`, `testInterpreterTextures`, `command.testTPv`) are `curses` terminal-size failures on a headless box, not logic. |
| Doctests in docstrings | **1,752 `>>>` lines** | Many give concrete input→output pairs for helpers (pitch, sieve, unit, rhythm). Harvest them into JUnit parameterized tests by script. |
| `test/testDemo.py` | replays every `demo/midi/script*.py` | The basis of the golden corpus below. |
| **Golden MIDI corpus** (my harness `tools/run_pregui_demos.py`) | **31 scripts**, MIDI 653–7,797 bytes | Each script prefixed with `tpsd 7`, `tmsd 7` and written with `eln`. **Two CPython runs gave identical SHA-256 for all 31**, and so did **GraalPy 3.12**. Without seeds, hashes differ between runs, as expected. The only non-content byte is the file name, which is written into the MIDI track name, so use fixed output names. Manifest: `tools/golden-pregui-manifest.txt`. |
| HEAD `tests/parity.py` (3,379 lines), `tests/rng_tests.rs` (641) | – | The spec for HEAD/Rust semantics (Reference B). Also a good model of a differential test corpus. |

Coverage of the corpus: the demos use 2 TextureModules explicitly (`LineGroove` 9×, `HarmonicAssembly` 5×,
LineGroove is also the default) and **32 of the 136 ParameterObjects**, led by `constant` 88×,
`basketGen` 51×, `pulseTriple` 32×, `operatorCongruence` 30×, `randomUniform` 19×,
`grammarTerminus` 13×, `randomCauchy` 12×, `randomBeta` 11×, `markovValue` 9×, `convertSecond` 9×.
You'll need to **write more golden scripts** for the other 12 textures and ~100 POs, one
small script per PO/texture, generated from the PO docs. That is cheap with the pregui oracle.

---

## 6. Porting options compared

| Option | How | Pros | Cons | Verdict |
|---|---|---|---|---|
| **Jython** | Run Python inside the JVM | Pure JVM | **Python 2.7 only.** Current release 2.7.4; Jython 3 has no release or dates ([jython.org downloads](https://www.jython.org/download.html), [roadmap](https://github.com/jython/jython.github.io/blob/master/jython-3-roadmap.md)). pregui is Python 3 (2to3) and HEAD needs RustPython. Going back to the 2011 Py2 upstream would undo the fork's fixes. | **No.** |
| **GraalPy embedding** | `org.graalvm.python:python-embedding` + Gradle plugin `org.graalvm.python` (UPL license). Latest 25.4.4 is Python 3.13; 25.2.x is 3.12 ([releases](https://github.com/oracle/graalpython/releases), [Gradle plugin](https://central.sonatype.com/artifact/org.graalvm.python/org.graalvm.python.gradle.plugin), [embedding docs](https://github.com/oracle/graalpython/blob/master/docs/user/Embedding-Build-Tools.md)) | Ships *today*: the whole engine with no rewrite. **I verified pregui on GraalPy 3.12 25.2.4 produces byte-identical MIDI for all 31 demos.** Runs on stock JDKs through the polyglot API. | **Slow**: the corpus took **189 s vs 11 s** on CPython (~17×, native standalone launcher, cold; JVM-embedded warm-up performance not measured). **Heavy**: the standalone tarball is ~120 MB. Python 3.13 builds need shims for `sndhdr`/`aifc`/`audioop` (or pin 3.12). The API is stringly typed (`ath.cmd("tie r ...")`), the code stays Python, and the Java side can't extend POs/Textures natively. | **Good as a stopgap and as the oracle**, not as the end state of a "lite" library. |
| **CPython bridges (Jep via JNI, Py4J via sockets)** | Embed or talk to a real CPython | Exact CPython semantics, fast | Requires an installed CPython plus native libs per platform. Not "lite" or self-contained for embedding. | No (for this goal). |
| **Automated transpilers** (py2java-style tools) | Source-to-source translation | Quick first draft | No maintained tool handles this code's `eval`-driven argument language, `dir()`/`getattr` plugin discovery, duck-typed mixed args, CPython RNG/rounding semantics, or `deepcopy`-heavy state. The output would be non-idiomatic Java you'd rewrite anyway. **[estimate]** | No. |
| **LLM-assisted manual port** | Port module by module with an LLM drafting from the Python source plus doctests. A human designs the Java types; every module is gated by golden tests | Idiomatic Java API; can be **faster than hand-porting** for the large regular parts (136 PO classes follow one pattern: `__init__` parses args, `__call__(t, refDict)` returns a value) | LLM drafts reproduce subtle semantic bugs (rounding, modulo, RNG call order) unless tests catch them, so the golden corpus is mandatory. The PO/Texture files are long and need to be fed in chunks. | **Recommended**, combined with the golden corpus. |
| **Manual port (no LLM)** | Same, by hand | Full control | Slowest: ~36k code lines in Tiers A+B. | Fine but slower. |

**Size estimate [estimate]:** Tier A + slim Tier B is about **30k Python code lines → roughly 25–40k
Java lines**. Java is more verbose, but `setTables` becomes generated data and the UI strings go away.
LLM-assisted with a golden corpus, the first MVP slice (§7 Phase 1) is about **1–2 weeks**, the full
PO/Texture surface about **2–4 months** part-time. These are judgment calls, not measured.

**Hybrid path [recommended]:** keep **pregui on CPython (or GraalPy) as the test oracle**. Optionally
ship a GraalPy-backed `athenacl-graalpy` adapter early so users can run everything from Java while
native Java modules replace it piece by piece. Both implement the same Java `Engine` interface.

---

## 7. Phased Gradle multi-module plan

```
athenacl-java/                       settings.gradle.kts, Gradle Kotlin DSL, Java 21 toolchain
├─ athenacl-pylang/   Python semantics kit: CpythonRandom (MT19937), Py.round/floorMod/floorDiv,
│                     PyLiteralParser (tuples/lists/str/num/None), PoArgParser (restringulator grammar)
├─ athenacl-core/     model + engine: Pitch/Path/Multiset/SetClass(+generated tables), Sieve, Rhythm,
│                     ParameterObject SPI + registry, Texture SPI + registry, Clone, Event, EventList,
│                     Orchestra(GeneralMidi only). No javax.sound, no I/O except via interfaces.
├─ athenacl-midi/     SMF writer (port of midiTools.MidiScore, 960 PPQN) + javax.sound Sequence export,
│                     soundbank resolver, realtime play + offline WAV render (01 §4)
├─ athenacl-project/  athenaCL XML project load/save (xmlTools/ioTools), using PyLiteralParser
├─ athenacl-cli/      picocli app: replays athenaCL command scripts (TIn/TIe/ELn/...), REPL later
├─ athenacl-graalpy/  (optional) GraalPy-backed Engine implementation running pregui sources
└─ conformance/       golden corpus runner: *.ath scripts + expected .mid hashes produced by pregui
```

### Phase 0: oracle and harness (no Java engine yet)
- Pin the oracle: pregui `35b9869` + CPython 3.12. Script it like `tools/run_pregui_demos.py`.
- Turn each demo into a plain command script (`*.ath`: one command per line) plus expected `.mid`
  and an **event dump** (time, dur, ps, amp, pan per event as JSON). Add the dumper to the oracle so
  failures show *which* event diverged, not just a hash mismatch.
- Harvest doctests into JSON fixtures (`input`, `expected repr`).

### Phase 1: minimum viable slice
Goal: `script02a` ("Building a Basic Beat") and other simple LineGroove scripts match **byte-for-byte**. Pick the exact set by
checking which demos use only the Phase-1 POs.
- `pylang`: `CpythonRandom` (`seed(int)`, `random`, `randint`, `choice`, `shuffle`), `Py.round`,
  `PoArgParser`.
- `core`: Path from pitch strings (`PIn`) with only the pitch parts it needs, auto-paths for
  `emo mp`, the `LineGroove` texture, POs `constant`, `basketGen`, `pulseTriple`, `randomUniform`,
  `loop`, `operatorCongruence`, `iterateGroup`, `convertSecond`, `lineSegment`, `waveSine`.
- `midi`: `MidiScore` port, then `javax.sound.midi.Sequence`/file.
- `cli`: `emo`, `tin`, `tie`, `tmo`, `tpsd`, `tmsd`, `eln`.
- Exit criterion: those golden hashes equal, plus the RNG unit tests (first 10k outputs of
  CPython `random.seed(n)` match).

### Phase 2: breadth
All 136 POs (grouped by `valueSingle`, `valueSingleOmde`, `rhythmSingle`, `textureStatic`,
`cloneFilter`, `cloneStatic`, `valueFile`) and all 14 Textures. Clones (`TCn`, ...). Set-class tables
generated from `setTables.py` by script. Sieve parser. Grow the golden corpus to one script per PO/Texture.

### Phase 3: projects and CLI
XML load/save compatible with athenaCL `.xml` files. The rest of the useful commands (`TIv`, `TIls`,
`PIv`, `TImute`, `TImode`, ...). `ELh` → play through the soundbank layer.

### Phase 4 (optional)
Reference-B RNG (ChaCha12) for parity with the GUI app, other output formats (Csound score, text),
publishing to Maven Central.

---

## 8. Java public API sketch (embedding)

```java
// --- athenacl-core -------------------------------------------------------
public interface Engine {
    Paths paths();
    Textures textures();
    Clones clones();
    void seed(long parameterSeed, long textureSeed);        // TPsd / TMsd
    EventList render();                                      // all non-muted textures + clones
    static Engine create(EngineOptions opts) { ... }         // RNG flavor, event mode (midi | midiPercussion)
}

public interface Textures {
    TextureInstance create(String name, TextureType type, int gmProgram);   // TIn + TImode
    TextureInstance get(String name);
}

public interface TextureInstance {
    TextureInstance set(PmtrSlot slot, ParameterObject po);  // TIe with typed objects
    TextureInstance set(String slot, String poSpec);         // TIe with athenaCL syntax: "pt,(c,2),(bg,oc,(7,5,2,1,1)),(c,1)"
    TextureInstance path(String pathName);
    TextureInstance timeRange(double start, double end);
}

// ParameterObjects are a sealed SPI with a registry (replaces dir()/getattr discovery)
public interface ParameterObject {
    Object next(double t, RefContext ctx);                   // Python's __call__(t, refDict)
}
@AthenaPo(name = "basketGen", acronym = "bg", library = PoLibrary.GENERATOR)
final class BasketGen implements ParameterObject { ... }

public final class Po {                                      // typed builders mirror the CLI syntax
    public static ParameterObject constant(double v) { ... }
    public static ParameterObject basketGen(SelectMode mode, double... values) { ... }
    public static ParameterObject pulseTriple(ParameterObject divisor, ParameterObject multiplier,
                                              ParameterObject accent) { ... }
    public static ParameterObject parse(String athenaSyntax, PoLibrary lib) { ... }
}

public record Event(double time, double dur, double sus, double acc, double amp,
                    double ps, double pan, String inst, double bpm, List<Object> aux) {}
```

```java
// --- usage from a host app -------------------------------------------------
Engine eng = Engine.create(EngineOptions.defaults().eventMode(EventMode.MIDI_PERCUSSION));
eng.seed(7, 7);
eng.textures().create("a", TextureType.LINE_GROOVE, 36)
   .set("r", "pt,(c,2),(bg,oc,(7,5,2,1,1)),(c,1)");
eng.textures().create("b", TextureType.LINE_GROOVE, 37)
   .set(PmtrSlot.RHYTHM, Po.pulseTriple(Po.constant(2),
                                        Po.basketGen(SelectMode.ORDERED_CYCLIC, 3, 5),
                                        Po.basketGen(SelectMode.ORDERED_CYCLIC, 0, 1)));
EventList events = eng.render();

// --- athenacl-midi ---------------------------------------------------------
Sequence seq = MidiExport.toSequence(events, MidiExportOptions.athenaDefaults()); // 960 PPQN, tempo
MidiExport.write(events, Path.of("beat.mid"));
try (AthenaSynth synth = AthenaSynth.open(SoundbankResolver.defaultChain())) {   // see 01 §4
    synth.renderWav(seq, Path.of("beat.wav"));
}

// --- athenacl-cli / script compatibility ---------------------------------
CommandInterpreter ci = new CommandInterpreter(eng);
ci.run("emo mp");
ci.run("tin a 36");
ci.run("tie r pt,(c,2),(bg,oc,(7,5,2,1,1)),(c,1)");
ci.run("eln beat.xml");     // writes beat.mid (+ optional XML)
```

Design notes:
- **Event stream as data.** `EventList` is a plain list of immutable records, so host apps can map
  events to their own synths and skip MIDI entirely.
- **RNG is injected** (`RandomSource` with `CpythonRandom` and later `ChaChaRandom`). Keep two streams
  (parameters/textures) exactly as athenaCL seeds them through `TPsd`/`TMsd`.
- **No static global state.** Python's module-level `random` and `prefTools` globals become fields of
  `Engine`, so several engines can coexist in one JVM. That mirrors what HEAD's Rust RNG module does
  "per interpreter".

---

## 9. Test strategy: golden MIDI from Python vs Java

1. **Oracle generation** (CI job with Python 3.12 + the pregui checkout): for every `conformance/*.ath`
   script, write `expected/<name>.mid`, `expected/<name>.events.json` (rounded to 1e-9), and sha256.
   Commit the outputs so Java CI doesn't need Python.
2. **Java JUnit 5 parameterized test** per script: run through `CommandInterpreter`, then compare
   (a) event JSON field by field with tolerance 0 for ints and 1e-9 for floats, and (b) MIDI bytes
   exactly. Show the first diverging event on failure.
3. **Unit fixtures from doctests** (Phase 0 harvest) for helpers (pitch names, sieves, unit
   conversions, rhythm parsing).
4. **RNG conformance**: CPython `random.seed(s)` → first N values of `random()`, `randint(a,b)`,
   `choice(range(k))`, `gauss`, `expovariate`, saved as fixtures for `CpythonRandom`.
5. **Property and fuzz tests** for the parsers: random nested PO strings parsed by Python (`eval` of
   `restringulator` output) vs Java `PoArgParser` must give equal trees.
6. **Fixed names and seeds**: the MIDI track name embeds the output file stem (the only byte that
   differed between my unseeded-name runs), so the harness always uses fixed names.
7. Optional: run the same corpus against the `athenacl-graalpy` adapter as a cross-check of the harness itself.

---

## 10. GPL implications (summary; details in 01 §4.3)

- athenaCL is **"GPL ... version 2 of the License, or (at your option) any later version"**
  (`pysrc/athenaCL/libATH/help.py:69`; `omde/functional.py` header for OMDE). A Java translation is
  a derivative work, so **license it GPL-2.0-or-later or GPL-3.0-or-later**, keep Ariza's and
  Puxeddu's copyright notices, and note "Java port of athenaCL" with a link to the source.
- **Embedding**: any app that *distributes* the library linked in must comply with the GPL for the
  combined work. Internal or unreleased use is unaffected. That limits embedding in closed or
  permissive projects.
- **GraalPy route doesn't avoid this.** It still distributes athenaCL's GPL Python code. GraalPy itself
  is UPL (permissive), which is fine.
- **Clean-room alternative** (only if a permissive license is essential): reimplement the *ideas*
  from published papers and the manual without translating the code. That is much more work, and
  golden-matching athenaCL output would be hard to call clean-room. **[legal judgment; not legal
  advice]**
- Copying the CPython `random` algorithms into Java: CPython is PSF-licensed (GPL-compatible).
  Keep the attribution in the RNG class.
- Prefer **GPL-3.0-or-later** if you want Apache-2.0 dependencies such as picocli. Apache-2.0 is not
  compatible with GPL-2.0-only, but athenaCL's "or later" allows GPL-3.

---

## Appendix: commands to reproduce

```bash
git clone https://github.com/ales-tsurko/athenaCL && cd athenaCL
git fetch --depth 1 origin tag pregui && git worktree add ../pregui pregui
uv python install 3.12                                   # 3.13 lacks sndhdr/aifc/audioop
cd ../pregui && python3.12 -m unittest discover athenaCL.libATH -p "*.py"   # 185 tests
python3.12 tools/run_pregui_demos.py                     # 31 demos -> /tmp/goldens/*.mid + hashes
graalpy run_pregui_demos.py                              # same hashes (GraalPy 3.12 25.2.4)
```
