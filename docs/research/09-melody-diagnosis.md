# 09: Why the generated music sounds non-melodic and "medieval"

Date: 2026-10-12. Repo: `github.com/SOS-Games/jvm-athenacl` at `8c8e59c` ("Add genre and world presets…"), cloned
read-only to `/workspace/jvm-athenacl`. Nothing was pushed. A local prototype lives on the branch
`local/melodic-prototype` (uncommitted diff saved to `research/tools/melody-diag/prototype-stocklayers.diff`).
Paths below are relative to the repo. `SL` = `athenacl-adaptive/src/main/java/io/github/sosgames/athenacl/adaptive/StockLayers.java`.
Line numbers refer to `8c8e59c`.

## TL;DR
Nothing the user built is broken in the sense of failing tests. The layers just **were never asked to make melodic
music.** The adaptive engine plays the music from one hand-coded file, `StockLayers.java`, which:
1. **Has no chord changes in practice.** Bass, pad, harp, brass and bell all play the **tonic chord or root every
   bar**. The progression code (`HarmonicRhythm`) is only used by the optional `chords` layer, which no fantasy or
   sci-fi state lists. Every fantasy/sci-fi render measured has **one bass note for the entire piece**.
2. **Folds every melody at the note C** (an octave bug in `scaleMidi`). In A aeolian the "scale" played is A5 B5 **C5**
   D5…, so a step from B to C becomes an 11-semitone drop. This turns any line into random-sounding leaps.
3. **Generates the lead as a random walk of equal note values** (all quarters or all eighths), restarting the same
   1-bar idea every bar, with no phrase or cadence. In themes without `motif.every` the idea is longer than a bar and
   **overlaps itself** (60–104 overlapping lead notes per render).
4. **Uses modal keys everywhere** (aeolian/dorian/phrygian/lydian, never a raised leading tone, never V→i), **open
   fifths** (brass and bell play root + fifth only), **drones**, and a **harp/flute/fiddle/choir/taiko** palette. Static
   modal harmony + open fifths + drones + harp/recorder-like timbres is close to a textbook description of "medieval".
5. **The hook generator and critic (L7b) are wired in, but only for one theme** (`themes/fantasy-dungeon.yaml`, the
   only file with `hooks:`). There the hook is a near-fixed template, stated once every 4 bars, with the scale-walk
   in between. No preset has `hooks:`, so `preset audition` and the in-window presets never use it.
6. **The ported athenaCL engine (ParameterObjects, Textures, Markov, grammars, sieves) is not used by the adaptive
   engine at all.** `athenacl-adaptive` has no dependency on `athenacl-core`. Those generators are only reachable
   through the ported CLI (`athena run`, `po sample`, conformance). So "all athena components are built" is true, but
   the game music path never calls them.

A ~250-line local prototype that adds functional progressions, voice-led chords, a register fix and a phrase-based
lead changed the measured output from "one chord, one bass note" to i–VI–iv–V / V7→i progressions with real
cadences. The critic's score for the first 8 dungeon bars went from **rejected** to **0.895** (see §5). One-off before/after MP3s are in `research/audio/melody-diag/`, as reference only (**not listened to**).
Testing from here on uses CLI and event-log analysis, not audio.

## 1. Is L7b (hooks + critic) implemented and wired into playback?
**Implemented: yes. Wired: partially.**
- Code: `athenacl-motif/.../motif/hook/HookGenerator.java`, `HookCache.java`, `HookSettings.java`, `.../motif/critic/MelodyCritic.java`
  (465 lines), `WeightFit.java`. The CLI covers `melody generate|score|rate|fit`.
- Playback: `Director.perform` calls `StockLayers.prepareHooks` (Director.java:70). `SL.lead` uses
  `HookCache.select(...)` **only when `theme.hooks() != null`** (SL:163-168). Otherwise it falls back to
  `MotifGenerator.generate` (SL:167).
- Themes: only `themes/fantasy-dungeon.yaml` has a `hooks:` block (lines 10-22, `candidates: 8`). `fantasy-party.yaml`
  and `fantasy-combat.yaml` don't, and **no preset in `Presets.java` sets hooks** (`Presets.audition` builds a YAML with
  just `preset:`; Presets.java:55). docs/progress.md:63 says so explicitly.
- Even where it's used, the hook is weak:
  - `HookGenerator.shape` (HookGenerator.java:70-118) is a handful of fixed templates (`0, 4, 3, …, 0`, `4, 0, 1, 2, 0`,
    `0, 2, 4, …, 0`). With `length: [4,4]` the candidates barely differ, and 8 candidates is too few to matter.
  - Strong-beat snapping uses the **tonic triad (0, 2, 4) regardless of the current chord** (HookGenerator.java:166-204).
  - The hook lasts one bar. `motif.every: 4` states it once, and then `scaleLead` fills 3 bars with a ±1-step random
    walk in quarter notes (SL:184-203).
  - `SL.lead` maps degrees with `Math.floorMod(tone.degree(), motif.steps())` at octave 5 (SL:175). That discards the
    octave information `HookGenerator.midi` tries to keep (HookGenerator.java:207-212), and then hits the C-fold bug (§3.2).

## 2. Are athenaCL's ported generators used by the adaptive engine?
**No.** `athenacl-adaptive/build.gradle` depends on events, theory, motif and rhythm only, with **no `athenacl-core`**.
`rg` finds no `athenacl.core` import in adaptive, motif, theory or rhythm. ParameterObjects (`po sample basketGen …`),
Textures (`LineGroove`…), Markov/grammar/sieve generators and Clones all run only from the CLI (`athena run`,
`conformance run`). This was the plan's design (05 §6.x: `po:` generator references in themes were a later item). It
just
hasn't been connected, and it explains the user's expectation gap.

## 3. Why it sounds medieval: root causes, ranked by impact
1. **Static tonic harmony: the biggest cause.**
   - `pad` plays scale degrees 0-2-4 (plus the 6th in dorian) every bar (SL:137-146).
   - `bass` plays degree 0 on beats 1 and 3 every bar (SL:148-157). `drone` plays degree 0 as a whole note (SL:239-245).
   - `harp` plays the tonic arpeggio 0-2-4-2 every bar (SL:247-256).
   - `bell` plays root + fifth (SL:349-357).
   - `HarmonicRhythm.at` (`athenacl-theory/.../HarmonicRhythm.java:16-32`) does compute i–iv–v–i, but only `chords`
     (SL:337-346) uses it, and no fantasy/sci-fi state includes `chords`.
   - Measured result: "bass first note per bar" is the same pitch for all 14–16 bars in every render (§4).
2. **The octave fold at C (bug).** `scaleMidi` builds a note name like `"C" + octave` from the scale letter (SL:363-366).
   Scientific octaves restart at C, so in any key not starting on C, scale degrees above B drop by an octave.
   - Measured: the dungeon lead goes `A5 B5 C5`, and its interval histogram has 7- and 11-semitone jumps, 8 of 35
     intervals ≥ 7 semitones.
   - It affects every layer (the pad voicing A4-C4-E4 is an accidental inversion).
3. **Lead rhythm and form.**
   - `MotifGenerator.generate` makes 2–8 notes, **all `1/4`**, each ±1 step from the last, starting on the tonic
     (MotifGenerator.java:24-36).
   - `PhrasePlan.sentence` turns it into idea / repeat / fragment+sequence / retrograde (PhrasePlan.java:69-76). That
     is what the user's agent described, and with a random walk it doesn't produce a tune.
   - `motif.every` defaults to **1** (Themes.java:302-323), so in most themes and all presets the idea restarts
     every bar. When it's longer than a bar it overlaps the next statement.
   - There are no cadences, no long notes at phrase ends, and no rests.
4. **Modal defaults with no leading tone.** Fantasy: A aeolian, D dorian ×3, D aeolian, E phrygian (Presets.java:77-99).
   Sci-fi: all lydian (Presets.java:122-143). Genres: dorian/aeolian/mixolydian almost everywhere, only chiptune/yo/pentatonic
   ionian (Presets.java:161-326). HarmonicRhythm's "v" is diatonic (minor), and no code raises the 7th.
   Measured: no dominant-seventh or major-V sonority appears in any render.
5. **Open fifths and parallel fifths.** Brass plays degrees {0, 4} (root+fifth) in parallel (SL:275, 284). With a drone
   and a static root, this sounds like organum.
6. **Archaic timbres.** The fantasy palette is harp 46, flute 73, fiddle 110, cello 43, choir 52, slow strings 49,
   taiko 116 (Presets.java:79-99), so the harp arpeggio plus flute lead sounds like a recorder-and-lute consort.
7. **Same tempo everywhere in a preset.** Every fantasy state is 100 BPM (Presets.java:79-96). The `pack()` genres use
   one tempo/key/layer set for all six states (Presets.java:333-342), so states only differ by intensity.
8. **Missing leads.** `lofi` has no `lead` layer (Presets.java:243). `scifi-space` peaceful/sneaking/danger/trading have
   no lead (Presets.java:124-139). `arp` is a fixed 0-2-4-2 grid on the tonic (SL:322-334).
9. **The critic can't see harmony or form.** `melody score` gives the old combat lead (3 distinct bar patterns of 14,
   all eighth notes over a D minor drone) **0.735–0.763**, about the same as the folk fixture (0.744). It also
   **rejects whole 8-bar phrases with `inRange`** (a hook-sized range rule) and rejects 16-note slices with
   `cadenceStable`. It's tuned for 1-bar hooks, not for lines or bars against a progression.

## 4. Measurements (seed 1, `athenacl render … --formats mid,events,txt`, analyzer `research/tools/melody-diag/analyze_events.py`)
Full outputs: `research/tools/melody-diag/analysis-before.txt`, `analysis-after.txt`, `scores.txt`.

| Render | Bass roots per bar | Sonorities per beat (top) | Lead rhythm values | Lead intervals ≥7 st | Distinct lead bar patterns | Lead overlaps |
|---|---|---|---|---|---|---|
| fantasy-dungeon (hooks on) | A ×14 | Am 39, Am7 6 | 1/8, 1/4, 1/2 | 8 / 35 | 12 / 14 | 0 |
| fantasy-party | D ×14 | clusters of D-E-F-A-B (lead walk over the tonic) | all 1/8 | 24 / 223 | 3 / 14 | 104 |
| fantasy-combat | D ×14 | Dm7 28, Dm 11 | all 1/8 | 0 / 111 | 3 / 14 | 0 |
| fantasy preset (audition) | A ×16 | Am 20, "C-E-A-B" 20 | all 1/4 | 12 / 127 | 3 / 16 | 60 |
| house preset | A ×16 | identical lead to the fantasy preset (same seed path) | all 1/4 | 12 / 127 | 3 / 16 | 60 |
| lofi preset | F ×16 | Fm(add6) 32 | (no lead) | – | – | – |
| scifi-space preset | C ×16 | C 32, C-E-F#-G 32 | (no lead) | – | – | – |

Also of note: the `house` preset's lead is note-for-note the fantasy preset's lead. The motif seed path depends only on
seed and scale size, so presets with the same tempo and seven-note scale share melodies.

## 5. Local prototype (not pushed)
On the branch `local/melodic-prototype`, `StockLayers.java` only (diff: `research/tools/melody-diag/prototype-stocklayers.diff`).
`-Dathenacl.prototype=false` restores the old output.
- **Register-correct `scaleMidi`:** degrees climb from the tonic, with no fold at C.
- **8-bar functional progressions:** minor-ish modes play i–VI–iv–V | i–VI–V–i; major modes I–vi–IV–V | I–IV–V–I. V gets
  a raised third (leading tone) and a 7th on cadences. Diminished non-dominant triads are swapped for iv.
- **Pad** voices those chords in close position. **Bass** plays the chord root and fifth plus an approach note at higher
  intensity. **Harp** arpeggiates the current chord in eighths. **Brass** plays full triads.
- **Phrase-based lead** (replaces motif/scale-walk):
  - 4-bar phrases a a′ b cadence, inside an 8-bar period (half cadence on V, authentic on i).
  - Bars 1–2 share a rhythm cell, drawn from a small library mixing 1/8, 1/4, 3/8, 1/2. Calm states get longer
    values; combat gets eighths.
  - Strong beats take the nearest chord tone of the *current* chord, weak beats step through the scale, with one
    signature leap.
  - The cadence note is long and lands on a chord tone (on the tonic/3rd at bar 8).
  - The second phrase reuses the first one's opening (period form). Deterministic per seed.
- **Results:**
  - Bass roots went from one note to `A F D E A F E A` (dungeon) and `D A# G A D A# A D` (combat).
  - Sonorities: Am / F / Dm / **E7** (dungeon), Dm / B♭maj7 / **A7** (combat).
  - Lead leaps ≥7 st: 8/35 → 1/41 (dungeon), 24/223 → 1/52 (party).
  - Lead overlaps: 104 → 0.
  - `melody score` on bars 1–8 of the dungeon lead: rejected (`cadenceStable`) → **0.895** (chordTone 0.75, closure 1.0).
  - Party/combat bars 1–8 are now rejected by the critic's hook-sized `inRange` rule, because they span about a 10th+.
    That's a critic calibration issue (§3.9), not a melody problem.
  - Existing `:athenacl-adaptive:test`, `:athenacl-theory:test` and `:athenacl-motif:test` still pass. Render hashes and
    layer-check goldens change, by design.
- **Not fixed by the prototype:**
  - The lydian sci-fi substitute chord is still diminished in places.
  - `arp`, `bell` and `chords` still use the old patterns.
  - Hooks aren't integrated into the new lead.
  - Presets aren't changed (same modes, timbres and tempos).
  - The athenaCL core still isn't used.
- **Audio** (GeneralUser GS via Gervill, first ~31 s, normalized to −18 LUFS; **not listened to**), all in
  `research/audio/melody-diag/`:
  - `fantasy-dungeon-before.mp3` / `fantasy-dungeon-after.mp3`
  - `fantasy-combat-before.mp3` / `fantasy-combat-after.mp3`
  - `fantasy-party-before.mp3` / `fantasy-party-after.mp3`
  - `lofi-before.mp3` / `lofi-after.mp3`: only bass/pad/harp change; there is still no lead
  - `scifi-space-before.mp3` / `scifi-space-after.mp3`

## 6. Composition model: genre × state, one module per musical dimension (user decision, Oct 12)
Every musical dimension becomes a separate, named, overridable parameter block. A playing state is **resolved** from
three layers, merged in this order (later wins, maps merge deeply, lists replace):

`genre defaults` (fantasy, scifi-space, lofi, …) → `state theme` (sneaking, combat, … as generic modifiers, optionally
genre-specific) → `per-game override` (the game's theme YAML, globally or per state).

`StockLayers` then only *renders* resolved parameters. It contains **no hard-coded modes, drones, voicings, tempos or
instruments**: no `"dorian".equals(...)`, no `combat(state)` branches, no fixed degree lists.

| Module (key) | Parameters (examples) |
|---|---|
| `scale` | `tonic`, `mode`, `leadingTone: true/false` |
| `harmony` | `style` (functional, modal, pedal, loop), `progression` or `vocabulary`, `harmonicRhythm`, `sevenths` |
| `cadence` | `strength` (none, weak, half, authentic), `every` (bars) |
| `drone` | `enabled`, `degree`, `octave` |
| `voicing` | `style` (close, open, open5, spread), `voiceLead`, `maxLeap` |
| `lead` | `enabled`, `phrase` (period, sentence, AABA, free), `rhythmCells`, `register`, `leap`, `density` |
| `hooks` | `enabled`, `candidates`, `contours`, `every` (phrases) |
| `instruments` | role → instrument name (kit names, §6.12 of 05) |
| `tempo` | `bpm`, or `relative: 0.85` (× genre tempo) |
| `density` | per-layer note density/subdivision, `layers` on/off |
| `dynamics` | velocity base, intensity curve |

```yaml
# presets/genres/fantasy.yaml (genre defaults)
genre: fantasy
scale: {tonic: A, mode: aeolian, leadingTone: true}
harmony: {style: functional, progression: [i, VI, iv, V, i, VI, V, i], harmonicRhythm: 1, sevenths: dominant}
cadence: {strength: authentic, every: 8}
drone: {enabled: false}
voicing: {style: close, voiceLead: true, maxLeap: 4}
lead: {enabled: true, phrase: period, rhythmCells: [lsS, sslL, dotted], register: [A4, E6]}
hooks: {enabled: true, candidates: 64}
tempo: {bpm: 96}
instruments: {lead: flute, pad: strings, bass: cello, arp: harp, brass: horn, perc: taiko}
---
# presets/states/sneaking.yaml (state theme: generic modifiers, any genre)
state: sneaking
scale: {mode: dorian, leadingTone: false}
harmony: {style: pedal, harmonicRhythm: 2}
cadence: {strength: weak}
drone: {enabled: true, degree: 0, octave: 2}
lead: {phrase: sentence, rhythmCells: [long-rest, pizz-pair], density: 0.4}
hooks: {every: 2}
tempo: {relative: 0.8}
density: {layers: {drums: off, arp: sparse}}
dynamics: {base: 48}
---
# presets/states/fantasy/combat.yaml (optional genre-specific state theme, applied after the generic one)
harmony: {progression: [i, VI, VII, V], harmonicRhythm: 1}
instruments: {lead: trumpet, perc: taiko}
---
# themes/my-dungeon.yaml (per-game override)
genre: fantasy
states:
  sneaking: {drone: {enabled: false}, instruments: {lead: clarinet}}
  combat:   {tempo: {bpm: 140}}
```
Resolution is pure and deterministic: `ResolvedState resolve(genre, state, gameOverrides)`. The resolver records the
**provenance of every value** (which layer set it). Unknown keys are validation errors with a path and a fix hint.

## 7. Fix list: ready-to-paste prompt for the coding agent

```text
Context: jvm-athenacl renders sound non-melodic and "medieval". Diagnosis is in docs/research/09-melody-diagnosis.md
(copy it in from the research folder). Root causes: StockLayers plays the tonic chord/root every bar; scaleMidi folds
melodies at the note C; the lead is a quarter/eighth-note random walk restarted every bar; all presets are modal with
no leading tone; brass/bell are open fifths; hooks are only enabled in fantasy-dungeon.yaml; athenacl-core is not
used by athenacl-adaptive. A working prototype diff is at research/tools/melody-diag/prototype-stocklayers.diff;
use it as a reference, not a paste.

Do these as a new layer "L7c: Melodic engine" (one commit per step; each step keeps `gradlew test` green, updates
goldens deliberately with a note in docs/progress.md, and adds CLI exit checks to checks/L7c.check):

0. Composition model (do first): implement genre × state resolution as in section 6 of 09-melody-diagnosis.md.
   Separate, overridable modules: scale, harmony, cadence, drone, voicing, lead, hooks, instruments, tempo, density,
   dynamics. Merge order: genre defaults -> generic state theme -> genre-specific state theme -> per-game theme
   (global, then per state). Deep-merge maps, replace lists, record provenance per value. Move all current preset
   data out of Presets.java into presets/genres/*.yaml and presets/states/*.yaml (classpath resources). Then remove
   every hard-coded musical choice from StockLayers (mode checks, combat(state) branches, fixed degree lists, fixed
   programs): layers only read ResolvedState. Add `athenacl theme resolve` (key=value + --json, with --explain
   showing which layer set each value) and JSON schemas for the genre/state/theme files.
1. Register bug: replace StockLayers.scaleMidi (and HookGenerator.midi / SL.lead's floorMod) with a register-correct
   mapping: midi = 12*(octave+1) + tonicPc + steps[deg mod n] + 12*floorDiv(deg, n). Keep degrees un-wrapped through
   motif transforms. Test: in A aeolian, degrees 0..7 at octave 4 give 69,71,72,74,76,77,79,81.
2. Progressions: move harmony into athenacl-theory as `Progression` (8-bar default, per state/preset/theme YAML
   `harmony: {progression: [i, VI, iv, V, i, VI, V, i], harmonicRhythm: 1, cadences: {4: half, 8: authentic}}`).
   Minor-ish modes raise the third (and add the 7th) on V by default (`harmony.leadingTone: true`), turned off per
   preset where a modal colour is wanted. Avoid diminished triads except vii°/ii° in context. Every pitched layer
   (pad, bass, harp, arp, brass, bell, drone, chords, stinger, wind-down) reads the bar's chord; nothing may assume
   the tonic. Test: no render of any shipped theme/preset has one bass pitch for > 4 consecutive bars (unless the
   state sets `harmony.pedal: true`).
3. Voicing: pad/brass/chords use close voicings with voice leading (reuse the L2 `theory progression --voice-lead`
   code: max leap 4 semitones, no parallel 5ths/8ves) instead of fixed degree lists. Remove the root+fifth-only brass
   and bell (make "open fifth" an explicit `voicing: open5` option).
4. Lead = phrase engine: replace scaleLead and the every-bar motif restart with a phrase generator:
   4-bar phrases inside 8-bar periods (forms: period, sentence, AABA over 16), rhythm cells from a YAML library per
   preset (mix of 1/8, 1/4, dotted, 1/2, ties across beats, rests), strong-beat chord tones against the CURRENT chord,
   passing/neighbour tones on weak beats, one signature leap recovered by step, cadence notes long and stable (half
   cadence on 2/5/7, authentic on 1/3), antecedent/consequent sharing the opening. The L7 motif/hook is the phrase's
   "a" material (transposed to fit the chord via chord-tone targeting), not a whole-bar restart; L7 transforms are used
   for development across periods. Lead notes must never overlap (monophonic). motif.every becomes "phrases between
   hook statements".
5. Hooks everywhere: enable `hooks:` by default for every preset and theme (presets carry defaults; themes override).
   Raise default candidates to 64. Make HookGenerator.shape stochastic within each contour archetype (random interior
   degrees, leap size 3-6 degrees, variable cell) instead of fixed templates; snap strong beats to the actual chord.
6. Critic calibration: add a `phrase` mode to MelodyCritic (range up to a 12th, cadence checked at phrase ends only,
   chordTone measured against the real progression passed in) and a `hook` mode (current rules). Add features for
   rhythmic variety (distinct durations, share of non-quarter notes) and bar-pattern repetition vs variety. Add a
   corpus test: phrase-mode scores on the folk fixture > the old StockLayers lead > random walk.
7. Presets: fantasy gets mixed tonal colours (peaceful/trading in major or minor with leading tone, sneaking/danger
   modal or pedal), distinct tempos per state (e.g. peaceful 84, sneaking 76, trading 96, partying 120, danger 96,
   combat 140), and a less archaic default palette (strings/horn/piano/flute/clarinet; harp and fiddle as colour,
   not as every state's base). Genre `pack()` presets get per-state tempo/density/layer differences and a lead layer
   (lofi: rhodes/vibes lead; sci-fi: lead.saw/square lead in all states, soft in calm ones). Keep lydian/dorian where
   the genre wants it, but with real progressions (e.g. lydian I–II–vi–V, dorian i–IV–VII–i).
8. Use athenaCL: add an `athenacl-core` dependency to athenacl-adaptive behind a `po:` layer source, so a theme layer
   can say `source: {po: "basketGen, (…)", texture: LineGroove}` for rhythm/pitch generators (Markov, grammar, sieve).
   Start with one stock use: a Markov rhythm PO for the lead's rhythm cells and a sieve for arp patterns. Seed via the
   existing seed paths (deterministic).
All later steps read their settings from the resolved modules (e.g. step 2 reads `harmony`/`cadence`/`scale`,
step 3 `voicing`/`drone`, step 4 `lead`, step 5 `hooks`, step 7 edits only YAML data). No step may add a new
hard-coded musical branch in Java.

9. Analysis: extend `analyze` with `harmony.distinctRoots`, `harmony.dominantCadences`, `lead.rhythmValues`,
   `lead.leapsOver7`, `lead.overlaps`, `lead.distinctBarPatterns`, and add a layerCheck that fails if any shipped
   preset renders with distinctRoots < 3 per 8 bars or lead.overlaps > 0.

Verification is by CLI and MIDI/event-log analysis only (no audio renders or MP3s needed). Exit checks (examples):
  athenacl theme resolve --genre fantasy --state sneaking
    → "scale.mode=dorian scale.leadingTone=false harmony.style=pedal drone.enabled=true tempo.bpm=77
       lead.phrase=sentence hooks.enabled=true instruments.lead=flute …" (one key=value per line)
  athenacl theme resolve --genre fantasy --state sneaking --explain
    → each line ends with "from=genre:fantasy|state:sneaking|state:fantasy/sneaking|theme:<file>"
  athenacl theme resolve themes/my-dungeon.yaml --state sneaking → "drone.enabled=false from=theme:…" and
    "instruments.lead=clarinet"
  athenacl theme resolve --genre lofi --state combat --json | jq .lead.enabled → true (lead present in every genre)
  athenacl theme validate samples/themes/bad-key.yaml → exit 3; "error code=THEME_UNKNOWN_KEY path=/states/sneaking/harmny fix=did you mean harmony"
  rg -n "dorian|aeolian|combat\(|\"sneaking\"" athenacl-adaptive/src/main/java/**/StockLayers.java → no matches
  athenacl render --genre fantasy --state sneaking --bars 16 --seed 1 --out build/l7c-s && athenacl analyze build/l7c-s
    → "drone.present=true harmony.style=pedal" ; same with --genre fantasy --state combat → "drone.present=false
       harmony.dominantCadences>=1"
  athenacl debug determinism themes/my-dungeon.yaml --runs 2 → identical hashes
  athenacl render themes/fantasy-dungeon.yaml --seed 1 --out build/l7c && athenacl analyze build/l7c
    → harmony.distinctRoots>=4 harmony.dominantCadences>=1 lead.overlaps=0 lead.rhythmValues>=3
  for every preset: athenacl preset audition <id> --bars 16 --seed 1 --analyze → same thresholds
  athenacl melody score build/l7c/lead.mid --mode phrase --progression build/l7c/harmony.json → score>=0.75
  athenacl debug determinism themes/fantasy-dungeon.yaml --runs 2 → identical hashes
```

## 8. Notes and open questions
- Effort estimate (very rough): item 0 (composition model, YAML migration, `theme resolve`) about 1–1.5 weeks; items 1–4 at about 1–1.5 weeks, 5–7 about 1 week, 8 about 1 week, 9 about 2–3 days.
  Items 1–3 alone should remove most of the "medieval" impression; item 4 makes it melodic.
- Ask the user: does "medieval" bother them in *every* state, or is a modal/drone colour still wanted for
  sneaking/danger in the dungeon? Should the fantasy palette lose harp/fiddle as base instruments?
- Testing: verify every step with `theme resolve`, `render --formats mid,events` + `analyze`, and
  `research/tools/melody-diag/analyze_events.py`-style metrics (bass roots per bar, sonority types, lead intervals,
  rhythm values, overlaps, drone presence). No MP3 renders needed. The MP3s in §5 stay as one-off reference
  material only.
