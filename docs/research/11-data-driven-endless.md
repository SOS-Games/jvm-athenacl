# 11: Data-driven instruments, seeded parameters, endless form, opt-in states

Date: 2026-10-15. Repo `SOS-Games/jvm-athenacl` at `35776a3`, read-only. No audio renders, no kit testing.
Paths: `A` = `athenacl-adaptive/src/main/java/io/github/sosgames/athenacl/adaptive/`; `R` = `athenacl-adaptive/src/main/resources/presets/`.
This builds on 09 §6 (genre → state → game merge) and 10 (instrument analysis L7d–L7f).

## 1. Instruments and roles are hard-coded
**Now:**
- `A/StockLayers.java:84-126` dispatches on a **fixed set of layer names** (`pad, bass, lead, drums, drone, harp, taiko,
  brass, arp, bell, chords`). An unknown layer name is silently ignored (`default -> degree`).
- `StockLayers.channel` (`:178-188`) hard-codes MIDI channels per layer name, and `bell` shares channel 6 with `harp`.
- `StockLayers.program` (`:195-204`) hard-codes fallback GM programs per layer name.
- Genres repeat raw GM numbers (`programs: {pad: 48, harp: 46, bass: 43, lead: 73}` in `R/genres/fantasy.yaml`).
- Kits (`instruments/gm-lite.yaml`) name only the presets someone typed in. Nothing discovers the soundfont's other presets.
- So "harp" is a *behaviour* (a 0-2-4-2 arpeggio) and an *instrument* at once. A new instrument or role needs Java
  changes, and only roles from that list can sound.

**Fix (L7g):** split *role behaviour* from *instrument* and make both data.
- **Layers are data.** A theme/genre declares any number of layers with free names. Each layer picks a **behaviour
  module** from a registry (`chord-pad`, `bass-line`, `phrase-lead`, `arpeggio`, `drone`, `ostinato`, `drum-pattern`,
  `po-source`), its parameters, and an **instrument reference**.
- **Behaviours** are classes registered by id (ServiceLoader, or one registry file). Adding a *behaviour* is Java;
  adding an *instrument or layer* never is.
  ```yaml
  layers:
    shimmer: {behaviour: arpeggio, instrument: "gm-lite:celesta", params: {pattern: [0, 2, 4, 7], subdivision: 1/16}}
    low:     {behaviour: bass-line, instrument: "sf:MuseScore_General/0/32"}   # bank/program addressing
    ritual:  {behaviour: drum-pattern, instrument: "kit:gm-lite:drums.orchestral", params: {pattern: "x..x..x."}}
  ```
- **Instrument references:** `kit:name`, `<kit>:<name>`, `sf:<soundbank-id>/<bank>/<program>`, `sf:<id>/name:"Choir Aahs"`,
  or `ensemble:<role>` (auto-select, doc 10 L7f).
- **Discovery:** `athenacl kit scan <sf2>` lists every preset (bank, program, name, percussion flag) and can write a
  generated kit `instruments/<id>.scanned.yaml` with slug names (`choir-aahs`) and license fields copied from the
  source. `kit analyze` (L7d) then profiles any of them.
- **Channels** are allocated dynamically per layer (the patch allocator from 05 §6.12.2, plus a channel allocator that
  reserves channel 10 for kits), never by name. The `programs:` maps and `StockLayers.program/channel` go away.

## 2. Genre YAML pins values that should be generated
**Now:**
- `R/genres/fantasy.yaml` fixes `scale: {tonic: A, mode: aeolian}`, `progression: [i, VI, iv, V, i, VI, V, i]`,
  `rhythmCells: [lsS, sslL, dotted]`, `tempo: {bpm: 96}`, `density.pad.degrees: [0, 2, 4]`, `arp.degrees: [0, 2, 4, 2]`.
- `A/Resolver.java:20-31` only **merges** modules (`MODULES`, `STATE_KEYS`). No code samples them from the seed:
  `rg "random|seed" A/Resolver.java A/Composition.java` returns nothing, and `tempo.bpm` is read as a fixed number
  (`Resolver.java:92`).
- So every render of a genre uses the same key, progression and tempo. Variety comes only from the lead.

**Fix (L7h):** genre and state files declare **distributions and constraints**. The resolver **samples** them from the
seed into concrete values, deterministically. Any value can be pinned.
```yaml
# genre: fantasy
scale:   {tonic: {choose: [D, E, A, G], weights: [3, 2, 3, 1]}, mode: {choose: [aeolian, dorian, ionian], weights: [4, 2, 2]},
          leadingTone: {p: 0.8}}
tempo:   {bpm: {range: [84, 108], step: 2}}
harmony: {style: functional, progression: {generate: markov, model: minor-functional, length: 8,
          constraints: {startOn: i, endOn: [i], cadenceAt: {4: half, 8: authentic}}}, harmonicRhythm: {choose: [1, 2], weights: [3, 1]}}
lead:    {rhythmCells: {generate: po, po: "markovPulse, …", count: 3}, register: {span: [10, 14], centre: {range: [A4, D5]}}}
voicing: {style: {choose: [close, open], weights: [2, 1]}}
arp:     {pattern: {generate: sieve, sieve: "3@0|4@1", length: 8}}
---
# game theme: pin what matters
pin: {scale.tonic: D, tempo.bpm: 92}
```
- **Samplers:** `choose`, `range` (with `step`), `p` (boolean), `generate` (`markov` / `grammar` / `sieve` / `po`,
  which uses the ported athenaCL ParameterObjects via `LayerPo`). Constraints are applied with rejection sampling,
  bounded and deterministic, with a fallback to the first valid candidate.
- **Seed paths:** `seed / section / module.key`, e.g. `1/s3/harmony.progression`. The same seed and section always
  sample the same value. Sections can re-seed (§3).
- **Order:** merge first (genre → state → game, 09 §6), **then sample**, then apply pins. State themes can narrow a
  distribution (`tempo.bpm: {relative: 0.8}` or a narrower `range`) without pinning it.
- **`theme resolve` shows each value, its source and its seed path:**
  ```
  scale.tonic=D source=pin:theme:my-dungeon.yaml
  scale.mode=aeolian source=sampled:genre:fantasy choose seed=1/s1/scale.mode
  tempo.bpm=74 source=sampled:genre:fantasy range[84,108]*state:sneaking relative=0.8 seed=1/s1/tempo.bpm
  harmony.progression=i,VI,iv,V,i,iv,V,i source=generated:markov(minor-functional) seed=1/s1/harmony.progression
  ```

## 3. Tracks are 14 bars and then loop
**Now:**
- `A/Themes.java:80`: `bars` defaults to **14** with a preset, 16 without.
- `A/Director.java:119` renders `for (bar = 1; bar <= theme.bars(); …)` into one finite `Score` (`:255`).
- `A/LiveClock.java:25-32` pre-renders that whole performance, and `bar()` clamps to its length (`:45-52`).
- The libGDX demo turns the whole score into one WAV (`ThemeMix.wav(...)`, `athenacl-gdx/.../MusicDemo.java:267-278`,
  `:352`, `:371`) and replays it in a loop (`:376-400`).
- So music is a fixed-length clip on repeat, with no form beyond 8-bar periods.

**Fix (L7i):** a streaming **Conductor** that generates bar by bar ahead of the playhead.
- **`MusicStream.next(barIndex)`** produces the events for exactly one bar. It's a **pure function of
  (seed, barIndex, control log up to that bar, resolved params)**. It keeps state only in a bounded rolling window
  (current section plan, last N bars for voice leading and phrase continuity, hook cache).
- **Lookahead scheduler:** keep 1–2 bars generated ahead. State changes retract events past the quantize point
  (existing BAR/BEAT rules). Audio is pulled through Gervill into `AudioDevice` (libGDX), with no WAV clip.
- **Form planner** (data, per genre; sampled like §2):
  ```yaml
  form:
    sections: {choose: ["A A B A", "A B A C", "A A' B A''"], weights: [3, 2, 2]}
    sectionBars: {choose: [8, 16]}
    variation: {A': [transpose:+2, reharmonize], A'': [fragment, augment]}
    hook: {recur: true, every: section, transform: [repeat, sequence, invert]}
    reseed: {per: cycle}            # new material after each full form cycle; hook can persist across cycles
    development: {intensityArc: true, maxCyclesSameHook: 3}
  ```
  - A **section seed** is `seed/cycleN/sectionK`.
  - Returning sections (the second A) reuse the earlier section's seed, so repetition is exact or varied by design.
  - After `maxCyclesSameHook` cycles the hook is regenerated, so material is memorable but never a fixed loop.
- **Determinism:** to generate bar N cold, re-derive the section plan from (seed, N) in O(cycles), then replay the
  window. Event output for bar N is identical whether reached by streaming from bar 1 or jumped to directly.
- `bars:` becomes optional (offline render length only). `LiveClock` and the demo switch to the stream.

## 4. States are implicit and global
**Now:**
- `A/Presets.java:16-17` hard-codes `STATE_IDS = peaceful, sneaking, combat, danger, trading, partying`.
- `Presets.load` resolves **all six** for every genre (`:107-110`), with loudness offsets for all six (`:120-122`).
- `A/Themes.java:100-103` copies every preset state into every theme (`states.putAll(preset.states())`).
- `Themes.java:166-175` auto-adds a BEAT/retract rule when states named `combat`/`danger` exist.
- `Themes.java:463` and `:525` fall back to `"peaceful"`.
- `R/states/*.yaml` (generic `combat.yaml`, `sneaking.yaml`, …) apply to every genre.

**Fix (L7j):** states are **opt-in modifiers declared by the game**.
- A genre has **no states**. A theme without `states:` plays the genre (base) stream only.
- The game declares which states exist, what each modifies and how transitions behave. State-theme files from the
  library are only a starting point it can reference:
  ```yaml
  genre: fantasy
  states:                           # only these exist in this game
    explore: {}                     # = genre base
    stealth: {use: library:sneaking, modify: {drone: {enabled: true}, tempo: {relative: 0.85}}}
    boss:    {use: library:combat, modify: {lead: {instrument: "gm-lite:trumpet"}}, transition: {quantize: beat, retract: true}}
  initial: explore
  ```
- Remove `STATE_IDS`, the implicit `putAll`, the name-based urgent rule and the `"peaceful"` fallbacks. Loudness
  offsets live with each declared state (default 0). Unknown states in controls are errors listing the declared ones.

## 5. Ready-to-paste prompt for the coding agent
```text
Implement four layers, in order. Design: docs/research/11-data-driven-endless.md (copy it in), consistent with
09 §6 (genre -> state -> game merge) and 10 (L7d-f instrument analysis). jvm-mw CLI conventions (key=value, --json,
exit 0/2/3/4). Verify only with CLI + MIDI/event-log analysis; no audio renders. Each layer: checks/L7x.check,
tests green, goldens updated deliberately with a docs/progress.md note.

L7g: Data-driven layers and instruments
- Behaviour registry (chord-pad, bass-line, phrase-lead, arpeggio, drone, ostinato, drum-pattern, po-source);
  layers are free-named YAML entries {behaviour, instrument, params}. Port StockLayers' cases into behaviours.
- Instrument refs: kit name, <kit>:<name>, sf:<id>/<bank>/<program>, sf:<id>/name:"…", ensemble:<role>.
- Dynamic channel allocation (kits on 10); delete StockLayers.channel/program and genre `programs:` maps.
- `kit scan <sf2> [--write instruments/<id>.scanned.yaml]` lists all presets.
Exit checks:
  athenacl kit scan ~/.cache/athenacl-j/soundbanks/generaluser.sf2 → "presets=287 percussion=13", one line per preset
  athenacl render samples/themes/free-layers.yaml --bars 8 --seed 1 --out build/l7g → layers=shimmer,low,ritual; analyze shows 3 channels
  athenacl render samples/themes/sf-address.yaml … → program change for "sf:generaluser/0/52" present in events.jsonl
  rg -n 'case "(harp|taiko|bell)"' athenacl-adaptive/src/main/java → no matches
  athenacl theme validate samples/themes/unknown-behaviour.yaml → exit 3 "THEME_UNKNOWN_BEHAVIOUR … did you mean"

L7h: Seeded parameter sampling
- Sampler grammar: choose/weights, range/step, p, generate (markov|grammar|sieve|po via ported athenaCL POs),
  constraints with bounded deterministic rejection; pin:. Order: merge -> sample -> pin. Seed path seed/section/key.
- Rewrite R/genres/*.yaml to distributions (keep current values as the most likely choice).
- `theme resolve` prints value + source (pin|sampled|generated|fixed) + seed path; --explain shows the distribution.
Exit checks:
  athenacl theme resolve --genre fantasy --seed 1 → lines with "source=sampled" and "seed=1/s1/…"
  same --seed 1 twice → identical output; seeds 1..50 → ≥ 3 distinct scale.tonic and ≥ 5 distinct progressions
  athenacl theme resolve samples/themes/pinned.yaml --seed 7 → "scale.tonic=D source=pin:…"
  athenacl theme resolve --genre fantasy --seed 1 --json | jq '.harmony.progression | length' → 8, starts i, ends i
  athenacl render --genre fantasy --seed 1..20 --bars 16 + analyze → every render passes the L7c harmony/lead checks

L7i: Endless streaming with form
- MusicStream.next(bar) pure in (seed, bar, controls≤bar, params); bounded rolling window; lookahead scheduler
  (1–2 bars) with BAR/BEAT retraction; gdx pulls PCM via AudioDevice (no WAV loop); LiveClock uses the stream.
- Form planner from data (sections, sectionBars, variation, hook recurrence, reseed per cycle, maxCyclesSameHook).
Exit checks:
  athenacl stream --genre fantasy --seed 1 --bars 500 --out build/l7i --formats events
    → "bars=500 heap.maxMB<64 heap.growthMB<8 (bar 100→500)" (constant memory)
  athenacl analyze build/l7i --repetition → "longestExactRepeat.bars<=16 distinctSections>=6 hook.recurrences>=10"
    (no fixed loop; hooks recur)
  athenacl stream … --from-bar 377 --bars 4 → events identical to bars 377–380 of the full stream (hash per bar)
  athenacl debug determinism --stream --genre fantasy --bars 500 --runs 2 --fork-jvm → identical
  athenacl stream … --timeline samples/timelines/ambush.json → transitions at bar/beat, retracted>0, stuck=0
  athenacl debug perf build/l7i → "gen.msPerBar.p99<5"

L7j: Opt-in states
- Delete Presets.STATE_IDS, Themes' preset putAll, name-based urgent rule, "peaceful" fallbacks; genres have no
  states; game theme declares states (use: library:<id>, modify:, transition:, loudness offset), initial state.
Exit checks:
  athenacl render samples/themes/genre-only.yaml --bars 32 → "states=" empty, exit 0, music plays (notes>0)
  athenacl theme resolve samples/themes/genre-only.yaml → no state lines; --state combat → exit 3 "unknown state; declared: (none)"
  athenacl theme resolve samples/themes/custom-states.yaml --state stealth → drone.enabled=true from=theme:…/stealth (library:sneaking)
  rg -n "STATE_IDS|\"peaceful\"|\"combat\"" athenacl-adaptive/src/main/java → no matches
  athenacl stream samples/themes/custom-states.yaml --timeline … --bars 64 → transitions only between declared states
```

Rough effort: L7g ~1.5 wk, L7h ~1.5 wk, L7i ~2–2.5 wk, L7j ~0.5 wk. L7j is small and can go first if the user wants
quick relief from unwanted state effects.
