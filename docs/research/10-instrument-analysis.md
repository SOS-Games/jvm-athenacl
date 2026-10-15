# 10: Instrument analysis, compatibility lint, and genre auto-selection (design)

Date: 2026-10-15. Context: `SOS-Games/jvm-athenacl` after L7c (genre × state composition model, instrument kits from
05 §6.12). Problem: some kit instruments from GeneralUser GS or MuseScore_General sound inharmonic or painful, and
one bad instrument ruins a track. The coding agent picks instruments blind.
**This is a design only.** No soundfont was rendered or measured for this document. All thresholds below are
starting values to be calibrated in L7d.

## 1. Goals
- Give every kit instrument a **measured, versioned profile**: descriptors, tags and a quality verdict. The profile is
  readable by people (key=value), agents (`--json`, text summary) and the engine.
- **Lint** themes and resolved states for bad instruments, clashing pairs and poor role fit before anyone listens.
- Later, let a genre **declare what it wants per role**, and pick a compatible ensemble deterministically from the seed.
- Fit the existing models: kits (05 §6.12), genre → state → per-game merge (09 §6), and jvm-mw CLI conventions
  (key=value stdout, `--json`, exit codes 0/1/2 plus the project's 3 = validation and 4 = not found, `help` with examples).

## 2. L7d: Instrument analysis
### 2.1 Probe render (deterministic)
- Gervill offline (`AudioSynthesizer.openStream`), 48 kHz float, **dry**: reverb, chorus and AGC off, `interpolation=sinc`.
  One fresh synth per probe and fixed probe order, so the result is bit-stable per JDK.
- **Probe grid per instrument:**
  - **Pitches:** every 6 semitones across the declared or detected `keyRange`, plus the role registers used by
    presets (e.g. bass C1–C3).
  - **Velocities:** 40, 80, 120.
  - **Timing:** hold 2.0 s, release 1.5 s.
  - **Percussion kits:** each mapped key at velocities 40, 80, 120.
- **Interval probes** (for roughness against itself): unison+octave, fifth, major third, minor second, at mid register.
- **Cache key:** `sha256(soundbank) + bank + program (+ drumkit/key) + probeVersion + jdkMajor`. Profiles are
  recomputed only when the key changes.

### 2.2 Descriptors (per probe, aggregated per instrument and register band)
| Descriptor | How (summary) | Flags |
|---|---|---|
| `pitch.errorCents` | f0 estimate (YIN or harmonic-sum) in the sustain window vs nominal | \|err\| > 15 c |
| `inharmonicity` | spectral peaks (Hann, zero-padded FFT) matched to k·f0, amplitude-weighted cent deviation of partials 2–16; plus `inharmonicEnergy` = share of energy > 1.5 % from any harmonic | > 20 c or > 0.25 (unless tagged bell/metal) |
| `roughness` | Sethares/Plomp–Levelt sensory dissonance over the top N partials, single note and interval probes, normalized by loudness | top decile of kit |
| `centroid.hz` / `centroid.ratio` | spectral centroid, absolute and relative to f0 (brightness) | — |
| `flatness` | geometric/arithmetic mean of the power spectrum (noisiness) | > 0.3 on pitched |
| `envelope` | attack (10→90 %), decay to sustain, sustain level, release time (RMS, 5 ms frames) | — |
| `artifacts.loopClicks` | sustain-window transient detector (spectral flux / HF energy spikes > k·σ at loop period) | > 0 |
| `artifacts.dcOffset`, `noiseFloor.db` | mean sample value; level in the tail after release | — |
| `peak.dbfs`, `clipping` | sample peak and true peak, count ≥ −0.1 dBFS | any clip |
| `loudness.lufs` | EBU R128 integrated per probe; feeds the existing per-instrument normalization (05 §6.12.4) | spread > 6 LU across range |
| `range.usable` | the range where pitch, inharmonicity and artifacts all pass | narrower than the role needs |
| `stereo` | L/R correlation, width, pan centre | correlation < 0 |
| `velocity.response` | loudness and centroid change from velocity 40 to 120 | flat (< 3 LU) |

### 2.3 Tags and verdict
- **Tags** come from rules over descriptors and are documented in `docs/instrument-tags.md`:
  - `warm`: low centroid ratio, low roughness.
  - `bright`: high centroid.
  - `harsh`: high roughness or high-frequency energy.
  - `plucky`: fast attack, decay without sustain.
  - `pad`: slow attack, long sustain.
  - `percussive`: fast attack, high flatness or a kit.
  - `bell`: inharmonic but stable partials.
  - `noisy`, `thin`, `boomy`.
- **Verdict:**
  - `ok`: all flags pass.
  - `caution`: one soft flag, or a range restriction.
  - `bad`: a hard flag (clipping, `pitch.errorCents` > 30, clicks, inharmonicity on an instrument not tagged bell or
    metal, roughness above the kit's 95th percentile).
  - Each verdict lists its reasons, e.g. `reasons=inharmonicity@C6..C7,loopClicks@A2`.
- **Manual overrides** in the kit YAML (`analysis: {verdict: ok, note: "intentional detune"}`) take priority and are
  shown as `source=override`.

### 2.4 Profile storage
`instruments/profiles/<kit>.profile.json` (plus an optional `.yaml` mirror), schema `athenacl.instrument-profile/1`:
```json
{"schema":"athenacl.instrument-profile/1","kit":"gm-lite","probeVersion":1,"jdk":21,
 "instruments":{"flute":{"source":{"sha256":"9575…","bank":0,"program":73},
   "verdict":"ok","reasons":[],"tags":["warm","breathy"],
   "range":{"usable":["C4","C7"]},
   "descriptors":{"pitch.errorCents":{"max":4.1},"inharmonicity":{"median":3.2,"max":7.9},
     "roughness":{"median":0.11},"centroid.ratio":{"median":3.4},"envelope":{"attackMs":38,"sustain":0.82},
     "loudness.lufs":{"min":-24.1,"max":-20.3},"stereo":{"corr":0.97}},
   "bands":[{"range":["C4","B4"],"…":"…"}]}}}
```
These are generated files with a hash header, deterministic, and diffable in git. `kit describe` reads them. They are
never recomputed at game runtime.

### 2.5 CLI (L7d)
```
athenacl kit analyze instruments/gm-lite.yaml [--instruments flute,harp] [--probe-version 1] [--dry-run]
   → per instrument: "instrument=flute verdict=ok tags=warm,breathy range=C4..C7 lufs=-22.0 cached=false"
     final "kit=gm-lite analyzed=<n> bad=<n> caution=<n> profile=instruments/profiles/gm-lite.profile.json"
athenacl kit describe flute --kit gm-lite [--json] [--text]
   → key=value descriptors; --text prints a 5–8 line plain-English summary an LLM can read
athenacl kit describe --kit gm-lite --where "verdict=bad"          → list with reasons
athenacl debug probe flute --kit gm-lite --pitch C6 --velocity 120 → one probe's raw descriptors (for calibration)
```

## 3. L7e: Compatibility and lint
### 3.1 Pair and role checks (from profiles, no new renders except optional pair probes)
- **Pairwise roughness:** a combined Plomp–Levelt dissonance of A's and B's partials at the registers their roles
  use, against the chord intervals of the state's harmony style.
- **Masking:** overlap of register ranges and of the spectral energy bands (ERB/Bark bands, centroid ± spread) when
  two roles play at the same time. Reported as `mask=0..1` per pair.
- **Role fitness** (each 0–1, from descriptors):
  - `lead`: clear pitch, mid-to-high centroid, moderate attack, stable through the lead register, low roughness.
  - `pad`: slow attack, sustain, low flatness, warm.
  - `bass`: usable range down to E1–C2, strong fundamental share, low inharmonicity at low pitches.
  - `arp`/`pluck`: fast attack, short decay.
  - `perc`: percussive tag or a kit.
- **Loudness/velocity sanity:** normalized loudness spread across the roles of one state stays within the state's
  contour (05 §6.4.1).

### 3.2 Lint
`kit lint` runs over a **resolved** state (genre → state theme → per-game override, 09 §6), so it sees exactly what
plays. Findings have a code, the path in the merged config, the layer that set the value, and a fix hint:
- `INSTR_BAD`: a role uses a `bad` instrument.
- `INSTR_RANGE`: the role's register is outside the usable range.
- `ROLE_FIT_LOW`: role fitness < 0.5.
- `PAIR_ROUGH`: pair roughness above threshold.
- `PAIR_MASK`: masking > 0.7 between lead and another sustained role.
- `LOUDNESS_SPREAD`: loudness outside the contour.
- **Severity:** `error` fails with exit 3; `warn` is printed but exits 0 unless `--strict` is set. Every fix hint
  names the best-scoring alternatives in the same kit.

### 3.3 CLI (L7e)
```
athenacl kit lint themes/fantasy-dungeon.yaml [--state combat|--all-states] [--strict] [--json]
   → "finding=PAIR_MASK severity=warn state=combat roles=lead,brass instruments=trumpet,horn mask=0.78
      from=genre:fantasy fix=try lead=flute (mask=0.31)"; final "errors=<n> warnings=<n>"
athenacl kit compat trumpet horn --kit gm-lite [--registers C4..C6,C3..C5]   → "roughness= mask= verdict="
athenacl kit fit flute --role lead --kit gm-lite                            → "role=lead fitness=0.82 limits=…"
athenacl theme resolve --genre fantasy --state combat --with-lint          → merged params + lint findings
```

## 4. L7f: Genre auto-selection
- Genres (and optionally state themes) declare **what they want per role**, not which program. This is a new
  overridable module, `ensemble`, in the 09 §6 merge chain:
```yaml
# presets/genres/fantasy.yaml (excerpt)
ensemble:
  select: auto            # auto | fixed (fixed = use `instruments:` as today)
  kits: [gm-lite]
  roles:
    lead: {tags: {any: [warm, breathy, reed]}, none: [harsh, noisy], fit: ">=0.7", centroidRatio: [2, 6]}
    pad:  {tags: {any: [pad, warm]}, attackMs: ">=80", fit: ">=0.6"}
    bass: {range: {lowest: "<=C2"}, fit: ">=0.7"}
    arp:  {tags: {any: [plucky]}, fit: ">=0.6"}
  constraints: {maxPairRoughness: 0.4, maxMask: {lead: 0.6}, verdict: [ok]}
---
# presets/states/sneaking.yaml: state theme narrows it
ensemble: {roles: {lead: {tags: {any: [breathy]}}, pad: {centroidRatio: [0, 3]}}}
---
# themes/my-dungeon.yaml: per-game override pins a role
states: {combat: {ensemble: {roles: {lead: {pin: trumpet}}}}}
```
- **Selector:**
  1. Filter the candidates for each role (verdict, tags, ranges, fit).
  2. Score each role (fit plus closeness to the target ranges).
  3. Search ensembles under the pair constraints: beam search, beam 16, deterministic tie-break by instrument name.
  4. Draw among the top K (default 3) with a seeded RNG (`seedPath …/ensemble/<state>`), so variety is reproducible.
- **Caching:** the result is cached by (profiles hash, resolved ensemble spec, seed).
- **Explain:** `--explain` prints why each pick won, and why the runners-up lost.
- Fixed `instruments:` keeps working and always wins over `auto` for a pinned role. No selection at game runtime
  without profiles: a missing profile falls back to fixed instruments and logs `ensemble.fallback`.

```
athenacl ensemble select --genre fantasy --state sneaking --seed 1 [--explain] [--json]
   → "role=lead instrument=flute score=0.84 from=auto" … "pairs.maxRoughness=0.22 maxMask.lead=0.41"
athenacl theme resolve --genre fantasy --state sneaking --seed 1   → includes "instruments.lead=flute source=ensemble:auto"
```

## 5. Testing (no audio listening, no MP3s)
- **Synthetic fixtures:** a tiny generated SF2 under `samples/kits/analysis/` (script-built, CC0):
  - a pure harmonic saw
  - a deliberately detuned or inharmonic partial set
  - a clipped sample
  - a sample with a loop click
  - a noise burst
  - a slow-attack pad
  - a pluck

  Every descriptor and flag is unit-tested against these known answers.
- **Determinism:** `kit analyze` twice gives an identical profile hash. A fork-JVM run matches as well.
- **Golden profile** of the fixture kit is checked in; changes need a `probeVersion` bump.
- **Lint and selector:** table tests on synthetic profiles (no rendering), covering a bad instrument, out-of-range,
  clashing pairs, pins and overrides. Same seed → same ensemble. Changing only the seed → a different pick among the
  top K.
- Real kits (GeneralUser GS, MuseScore_General) are analyzed only when the user runs `kit analyze`. Their profiles are
  generated artifacts, not test oracles.

## 6. Risks
- **Descriptors are proxies.** Bells, gongs and detuned pads are meant to be inharmonic. Mitigations: tag-aware rules
  and manual overrides.
- **Thresholds need calibration on fixtures and a few hand-judged instruments.** Expose them in
  `instruments/analysis.yaml`, and keep the defaults conservative (`caution` rather than `bad`).
- **Analysis time.** A full GM kit is about 128 programs × ~30 probes. Mitigations: run it offline only, cache by hash,
  and allow `--instruments` subsets.
- **Gervill-specific results** may not transfer to other synths. Profiles record the renderer and JDK.

## 7. Ready-to-paste prompt for the coding agent
```text
Implement instrument analysis in three layers. Design: docs/research/10-instrument-analysis.md (copy it in).
Follow jvm-mw CLI conventions (key=value stdout, --json with versioned schemas, exit 0 ok / 2 usage / 3 validation /
4 not found, help with examples), the kit model (05 §6.12) and the genre -> state -> per-game merge model (09 §6).
Do NOT hand-pick or judge instruments by listening; everything must be checkable from the CLI. Each layer adds
checks/L7x.check and keeps `gradlew test` + determinism green.

L7d: Instrument analysis (module athenacl-instruments, package …instruments.analysis)
1. Build a CC0 fixture SF2 via a script (samples/kits/analysis/fixtures.sf2 + build script): pure saw, inharmonic
   partials, clipped, loop-click, noise burst, slow pad, pluck. Fixture kit YAML samples/kits/analysis.yaml.
2. ProbeRenderer: dry deterministic Gervill renders per the probe grid (pitches every 6 st in keyRange, vel 40/80/120,
   hold 2 s, release 1.5 s; interval probes; kits per mapped key). One fresh synth per probe; fixed order.
3. Descriptors (§2.2): pitch.errorCents, inharmonicity + inharmonicEnergy, roughness (Sethares), centroid hz/ratio,
   flatness, envelope (attack/decay/sustain/release), loopClicks, dcOffset, noiseFloor, peak/clipping, LUFS,
   usable range, stereo, velocity response. Pure functions with unit tests on fixtures.
4. Tags + verdict rules (§2.3), thresholds in instruments/analysis.yaml, kit-level overrides `analysis:`.
5. Profile JSON (§2.4, schema athenacl.instrument-profile/1), cached by sha256+bank+program+probeVersion+jdk.
6. CLI: kit analyze, kit describe (--json, --text, --where), debug probe.
Exit checks:
  athenacl kit analyze samples/kits/analysis.yaml → exit 0; "analyzed=7 bad=3"; rerun → "cached=true" and same profile hash
  athenacl kit describe inharmonic --kit analysis → "verdict=bad reasons=inharmonicity…"
  athenacl kit describe saw --kit analysis → "verdict=ok tags=bright" and pitch.errorCents within ±2
  athenacl kit describe clipped --kit analysis → "reasons=clipping"; loopclick → "reasons=loopClicks"
  athenacl kit describe pad --kit analysis --text → ≤ 8 lines, mentions "slow attack"
  athenacl kit describe nosuch --kit analysis → exit 4 with a did-you-mean hint
  athenacl debug determinism --analyze samples/kits/analysis.yaml --runs 2 --fork-jvm → identical hashes

L7e: Compatibility + lint
1. Pair metrics from profiles: pairwise roughness at role registers, masking (register + Bark-band overlap).
2. Role fitness functions for lead/pad/bass/arp/perc (§3.1).
3. kit lint over RESOLVED states (genre -> state -> per-game), findings with code, merged-config path, provenance
   layer, severity, fix hint naming better same-kit alternatives. --strict, --all-states, --json.
4. CLI: kit lint, kit compat, kit fit, theme resolve --with-lint.
Exit checks:
  athenacl kit lint samples/themes/lint-bad.yaml --all-states → exit 3; "finding=INSTR_BAD … fix=try …"
  athenacl kit lint samples/themes/lint-clean.yaml --all-states → exit 0; "errors=0 warnings=0"
  athenacl kit compat saw saw --kit analysis --registers C4..C5,C4..C5 → "mask=" near 1 and "verdict=clash"
  athenacl kit fit pluck --role pad --kit analysis → fitness < 0.5; --role arp → fitness >= 0.7
  athenacl kit lint samples/themes/lint-override.yaml --state combat → finding shows "from=theme:lint-override.yaml"

L7f: Genre auto-selection
1. New overridable module `ensemble` (select auto|fixed, kits, roles with tags/ranges/fit, constraints, pin) in the
   genre/state/theme merge chain; schema + validation.
2. Selector: filter -> score -> deterministic beam search under pair constraints -> seeded top-K draw
   (seed path …/ensemble/<state>); cache; --explain; fallback to fixed instruments when profiles are missing.
3. theme resolve shows the chosen instruments with source=ensemble:auto|fixed|pin.
4. Migrate fantasy and scifi-space genres to `ensemble: {select: auto}` with role specs; others stay fixed until
   profiled.
Exit checks:
  athenacl ensemble select --genre fantasy --state sneaking --seed 1 --kit analysis → every role filled, no bad verdicts,
    "pairs.maxRoughness<=" constraint; rerun → identical
  … --seed 2 → may differ but stays within constraints (property test over 100 seeds)
  athenacl ensemble select samples/themes/pin.yaml --state combat → "role=lead instrument=<pinned> from=pin"
  athenacl ensemble select --genre fantasy --state sneaking --kit nosuchprofile → exit 0, "ensemble.fallback=fixed"
  athenacl theme resolve --genre fantasy --state sneaking --seed 1 --explain → instruments.* lines with source=ensemble:auto
  athenacl kit lint --genre fantasy --all-states --seed 1 → errors=0 (auto-selected ensembles pass their own lint)
```

Effort (rough): L7d 1.5–2 weeks (fixtures, DSP descriptors, profiles), L7e about 1 week, L7f about 1 week.
