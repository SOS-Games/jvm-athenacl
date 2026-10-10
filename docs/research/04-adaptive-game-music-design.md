# 04 — athenaCL-inspired adaptive music generator for games (Java): design research

*Research note, Oct 9, 2026. Builds on [01](01-soundfont-microsoft-gs.md) (soundbanks/Gervill),
[02](02-soundfont-instruments.md) (soundfont comparison) and [03](03-java-port-plan.md) (port analysis).
No project was created. A small **proof of concept** was built and run. Its source is
`tools/adaptive-poc/AdaptivePoc.java`, the render is `audio/adaptive-poc-peaceful-sneaking-combat.mp3`, and
the results are in §7. Labels: **[verified]** = I ran or read it; **[sourced]** = cited; **[proposal]** =
design suggestion; **[estimate]** = judgment.*

## TL;DR

- **Goal shift:** from "port athenaCL faithfully" to "a real-time, configurable generator that a game
  steers with states plus an intensity value". athenaCL's *ideas* fit well: Textures as
  layer generators, ParameterObjects as time-varying control streams, Paths/set classes as harmony,
  Clones as variations. Its *execution model* doesn't: it renders a whole fixed-length score
  offline (`baseTexture.score()` → `_scoreMain()` loops over the full time range).
- **Approach (proposal):** a bar-by-bar generator with a lookahead scheduler, state changes quantized to
  the next bar or beat, layering driven by intensity, stingers, per-theme soundbanks, and seeded
  determinism per (seed, state, bar).
- **Port plan changes:** port the generator *concepts* first (ParameterObjects as pull-based streams, pitch
  and scale tools, a few textures rewritten as incremental generators). Drop byte-exact conformance
  with pregui as a gate. Keep it only as an optional test for ported POs.
- **PoC:** it works. One 230-line Java file switches peaceful → sneaking → combat with
  bar-quantized transitions, an intensity-driven layer, and a stinger. It rendered 49 s through Gervill
  with GeneralUser GS in about 3 s, and two runs were byte-identical. Honest limits are in §7.

---

## 1. Prior art and techniques

### Core techniques

| Technique | What it is | Where it shows up |
|---|---|---|
| **Vertical layering / re-orchestration** | One piece is split into synchronized stems or layers. Intensity fades layers in and out, or swaps instruments. | Elias "Levels" ("lower Level values would indicate lower levels of intensity with less Tracks playing", [Elias tutorial](https://www.gamedeveloper.com/audio/adaptive-music-with-elias-tutorial-series-part-1)). FMOD layering via an `Intensity` parameter with per-track volume automation ([course notes, DeHaan MUSC 233](https://www.danielrdehaan.com/garden/courses/sp26-musc-233/weekly-units/week-13/)). *Red Dead Redemption*: stems all recorded in **A minor at 130 BPM** so they can be combined freely ([Destructoid](https://www.destructoid.com/go-behind-the-scenes-of-red-dead-redemptions-soundtrack/), [Game Developer](https://www.gamedeveloper.com/audio/myths-mavericks-and-music-of-i-red-dead-redemption-i-)). |
| **Horizontal resequencing** | Pick the next segment from a pool based on state, at musically valid exit points. | Wwise **Music Switch Container** + transition matrix ([Wwise docs](https://www.audiokinetic.com/en/public-library/2024.1.4_8780/?id=transitions_tab_music_objects)). FMOD transition regions ([FMOD docs](https://www.fmod.com/docs/2.04/studio/authoring-events.html)). iMUSE jump hooks (below). |
| **Quantized transitions** | A change requested mid-bar takes effect at the next grid point: beat, bar, cue. | Wwise "Exit source at: Immediate / Next Grid / Next Bar / Next Beat / Next Cue". FMOD: "Quantizing a transition region … means that it will only cause the playback position to jump on the specific notes or bars". Elias "Agility: Bar / Beat / Anytime / Custom" ([Elias Stinger](https://manual.elias.audio/v4x-elias-studio-elias-assets/stinger), [Arrangement](https://manual.elias.audio/v4x-elias-studio-elias-assets/arrangement)). |
| **Transition segments / bridges** | A short composed bridge between source and destination (fills, risers, wind-downs). | Wwise "transition segment … a musical bridge" ([Wwise 201](https://www.audiokinetic.com/en/courses/wwise201/?id=using_transition_segments_configuring_transition_segment)). |
| **Stingers** | One-shot musical accents synced to the beat or bar over the running music (discoveries, hits, deaths). They can also mask transitions. | Wwise stingers. Elias "Stingers are one-shot sounds that play in a musical context … Help with segue transitions or play motifs". |
| **Sync to same position** | The destination starts at the same bar/beat offset as the source (parallel arrangements). | Wwise "Sync to: Same Time as Playing Segment" ([Audiokinetic Q&A](https://www.audiokinetic.com/qa/10715/transition-between-two-states-in-a-very-specific-way)). |
| **Intensity parameter** | One continuous control (0–100) drives layering, region choice and probabilities. | FMOD user parameters set from code (`setParameterByName("Intensity", v)`) ([FMOD parameters](https://www.fmod.com/docs/2.04/studio/parameters.html)). |
| **Scheduling lookahead** | The engine needs advance notice before a quantization point. | FMOD staff: "the exact amount of advance notice required is between one and two updates … 40 ms should be sufficient" ([FMOD forum](https://qa.fmod.com/t/transition-not-working-if-value-change-happens-too-close-to-the-transition-point/20768)). |

### Systems

- **iMUSE (LucasArts, 1991):** MIDI-based. Composers embed *markers* and *hooks* (SysEx) in the
  sequences. Game scripts queue commands that fire when playback reaches a marker, so the music can
  jump to endings or branches at musically sensible points
  ([Mixnmojo history](https://web.archive.org/web/20150402100150/http:/mixnmojo.com/features/sitefeatures/LucasArts-Secret-History-Monkey-Island-2-LeChucks-Revenge/9);
  [US 5,315,057](https://patents.google.com/patent/US5315057A/en), filed 1991, **expired 2011**).
  It is the closest ancestor of this project, because it was also MIDI driven through a synth.
- **FMOD Studio / Wwise:** the industry standard for *authored* adaptive music: segments, stems,
  transition matrices, stingers, parameters. They arrange **pre-composed audio**; they don't compose.
- **Elias 4:** adaptive-music-first middleware: arrangements with states, agility, stingers, variations.
  Its docs say MIDI tracks "will be supported in a future update"
  ([Arrangement](https://manual.elias.audio/v4x-elias-studio-elias-assets/arrangement)).
- **Hexany Audio:** a Los Angeles **studio** (not a tool) that composes and implements adaptive music in
  Wwise/FMOD/Unreal/Unity ([hexanyaudio.com](https://hexanyaudio.com/services/)). Useful as a
  reference for the craft, not for technology.
- **Minecraft:** a counterexample. It is context pools (survival, creative, biome-specific) with
  random track choice and a **10–20 minute gap**, not adaptive transitions
  ([Minecraft Wiki: Music](https://minecraft.wiki/w/Music)). Sparse, ambient and non-intrusive is a valid design target for "peaceful".
- **Procedural / generative:** *Spore* (Brian Eno, generative recombination) and *No Man's Sky* (PULSE
  recombining human-made fragments by context) ([Game Developer deep dive](https://www.gamedeveloper.com/audio/deep-dive-generative-music-in-video-games)).
  Research: **MetaCompose** (Scirea) generates chord, melody and accompaniment abstractions and improvises
  in real time to express valence/arousal. Its studies found **arousal changes reliably perceived,
  valence less so** ([thesis](https://en.itu.dk/-/media/EN/Research/PhD-Programme/PhD-defences/2017/PhD-Thesis-Final-Version-Marco-Scirea-pdf.pdf),
  [paper](https://marcoscirea.com/papers/metacompose-journal.pdf)). Plans & Morelli, *Experience-Driven
  Procedural Music Generation for Games*, IEEE TCIAIG 4(3), 2012 ([researchr](https://researchr.org/publication/PlansM12)).

**Takeaway [proposal]:** model the library on the middleware *vocabulary* (states, intensity,
quantized transitions, stingers, layers). The authored segments become **generated** bars, because
athenaCL-style generation is the differentiator. MetaCompose's finding suggests leaning on
arousal-type cues (tempo, density, dynamics, register, percussion) for state contrast, with mode and
harmony as secondary.

---

## 2. Mapping game states to musical parameters [proposal]

Axes: **arousal** (calm → excited) is driven mostly by tempo, density, dynamics, percussion and register
spread. **Valence/tension** (safe → threatening) is driven mostly by mode, harmony, dissonance and articulation.
Every state also has an **intensity 0..1** that interpolates *within* the state.

| Param | **Peaceful** | **Sneaking** | **Combat** | **Danger** (threat nearby, not yet fighting) | **Trading** | **Partying** |
|---|---|---|---|---|---|---|
| Tempo (BPM) | 70–90 | 90–105, half-time feel | 130–160 | 60–80, or pulse-less | 95–110 | 115–130 |
| Meter / feel | 4/4 or 3/4, straight, rubato-ish | 4/4, sparse, syncopated | 4/4, driving 8ths/16ths | free/ostinato pulse | 4/4 swing-light | 6/8 or 4/4 dance, swung |
| Mode / scale | Major pentatonic, Lydian | Dorian, minor | Harmonic minor, Phrygian | Locrian, octatonic, whole-tone, chromatic clusters | Mixolydian, major | Major, Mixolydian |
| Harmony | I–IV–vi–V, slow (1 chord / bar or 2) | i–i–VII–i, pedal tones | i–VI–iv–V, faster rhythm | static pedal + tritones, unresolved | I–vi–ii–V, light | I–IV–V, circle-of-fifths |
| athenaCL harmony source | Path of consonant set classes (e.g. 3-11, 4-26, 5-35) | Paths with 3-7, 4-23 | 3-11 + 3-3, 4-19 | 3-5, 3-12, 4-9, 8-28 (octatonic) | 3-11, 4-27 | 3-11, 4-26 |
| Melodic density (notes/bar at i=0 → i=1) | 2 → 6 | 1 → 4 (staccato) | 6 → 14 | 0 → 3 (motif fragments) | 4 → 8 | 6 → 12 |
| Register | mid-high melody, wide spacing | low-mid, narrow | full range, low brass | very low + very high, nothing in the middle | mid | mid-high |
| Dynamics (vel) | 50–80 | 35–65 | 85–120 | 30–90, swells | 60–80 | 75–100 |
| Articulation | legato | staccato, pizzicato | marcato, accents | tremolo, long swells | light, plucked | punchy |
| Percussion | none → soft shaker | pedal hat, side stick | full kit + timpani/taiko | heartbeat kick, rolls | hand percussion | full groove |
| Layers added with intensity | pad → bass → melody → arpeggio | pad → bass pulse → melody → light perc | strings + bass → brass → perc → ostinato → choir | drone → pulse → stabs | rhythm guitar → melody → bass | groove → bass → lead → horns |
| Entry transition | fade 1–2 bars | next bar, drop layers | next **beat** or bar + **stinger** | next bar, riser | next bar | next bar + fill |
| Exit transition | – | – | **wind-down** 1–2 bars then target | resolve to peaceful or escalate to combat | fade | fill then target |

Intensity rules **[proposal]:**
- **Continuous** properties (tempo ±10%, velocity, density, filter/CC74 brightness, note-length factor)
  interpolate per bar with smoothing. The PoC uses `intensity += (target − intensity) × 0.5` per bar.
- **Discrete** properties (layer on/off, percussion pattern tier, fills) use thresholds with
  **hysteresis**, e.g. enter at 0.6, leave at 0.5, so the music doesn't flicker.
- Tempo changes only at bar lines. Large BPM jumps (sneaking 100 → combat 144) are best covered by a
  stinger or a one-bar fill.

---

## 3. What to reuse from athenaCL (pregui), and what's missing

### Reuse

| athenaCL concept | Where (pregui) | Role in the adaptive engine |
|---|---|---|
| **ParameterObjects**: callable, time-varying value streams `__call__(t, refDict)` | `libPmtr/basePmtr.py:293` `class Parameter`, `:463` `__call__`. Generators in `libPmtr/valueSingle.py`: `Constant` :225, `BasketGen` :257, `SieveList` :516, `IterateGroup` :2265, `MarkovValue` :2846, `GrammarTerminus` :3037, `FeedbackModelLibrary` :3088, `OperatorCongruence` :3366. Rhythms in `libPmtr/rhythmSingle.py`: `GaRhythm` :96, `Loop` :224, `PulseTriple` :412, `MarkovPulse` :726 | **The best fit.** They are already *pull-based* and evaluated per event at time `t`, which is exactly what a bar-by-bar generator needs. Bind PO arguments to state and intensity (e.g. `density = ru(0.2+0.6*i, 0.4+0.6*i)`). Markov, grammar and sieve POs give variation without repetition. |
| **Selector** (ordered/random/cyclic choice) | `basePmtr.py:106` `class Selector` | Melody and chord-tone choice, layer variation. |
| **Textures**: event generators per instrument | `libTM/baseTexture.py` (`makeEvent` :1326, `score` :1412), `libTM/LineGroove.py` (`_scoreMain`), `HarmonicAssembly`, `LineCluster`, `MonophonicOrnament`, `DroneSustain` | Each **layer** in a theme state is a texture: melody = LineGroove/MonophonicOrnament, pad = DroneSustain/HarmonicAssembly, arp = LineCluster. They need to be rewritten as incremental generators (below). |
| **Paths / set classes / multisets** | `pitchPath.py:24` `PolyPath`, `multiset.py:555` `Multiset`, `setTables.py` (Forte tables), `sieve.py:797` `Sieve`, `:1557` `SievePitch`, `temperament.py` | Harmony and scale vocabulary per state. Set classes give a principled "tension" knob (consonant ↔ dissonant sets). Sieves generate unusual scales for sci-fi. |
| **Clones** | `clone.py:34` `class Clone`, `libPmtr/cloneFilter.py` (time/pitch filters) | Variations of a layer (echo, transposed doubling, retrograde fragments). At higher intensity, add a clone instead of a new layer. Stingers can be clones of the current motif. |
| **Generators** | `markov.py`, `grammar.py` (L-systems), `automata.py`, `feedback.py`, `genetic.py`, `omde/rand.py` (distributions) | Melodic contour, rhythm cells, long-term variety. |
| **General MIDI instrument names** | `libOrc/generalMidi.py` (`gmProgramNames`) | Theme configs can use readable instrument names. |

### Missing for real time

| Gap | Why (pregui evidence) | Needed |
|---|---|---|
| **Fixed-length, whole-score rendering** | `baseTexture.score()` → `_scoreMain()` iterates `for pathPos in self.getPathPos()` and `while tCurrent < tEndSet` across the texture's `getTimeRange()`. Path positions get time slices from `clockPoints()`/`_clockUpdate()` (`baseTexture.py:867–930`) proportional to a *known total duration*. | **Open-ended generation**: `generate(barContext) → events for [t0, t1)` with state carried between calls (last pitch, PO internal state, path position by bar count, not by % of duration). |
| **Absolute seconds, no bar grid** | Events carry `time`/`dur` in seconds plus `bpm` per event (`makeEvent`). Rhythm POs produce pulses relative to bpm. | A **musical clock**: bars/beats/ticks ↔ microseconds, with tempo changes at bar lines. Quantization queries such as "next bar after t + lookahead". |
| **No scheduler** | Output is a file (`eventList.py` `EngineMidiFile`, `midiTools.MidiScore.write`). | A **lookahead scheduler** that sends timestamped `ShortMessage`s to the synth `Receiver` 1–2 bars or 100–300 ms ahead, and can cancel scheduled but unplayed events when a change must be fast. |
| **No transitions** | – | Transition rules (quantization, fades by CC7/CC11 ramps, all-notes-off on removed layers, bridge/stinger, tempo handling). |
| **Global RNG** | module-level `random` (03 §3) | **Seeded per (seed, state, bar, layer)** streams. Results are reproducible and independent of how long a state lasted before. The PoC does this with `new Random(hash(seed, state, bar))`. |
| **Event mode / orchestra coupling** | `libOrc/*` maps instruments; Csound-centric parameters | A **theme config** that maps layers to GM program, bank, channel and soundbank. |
| **Interactive control** | Commands are text (`command.py`), offline | A thread-safe Java API (`setState`, `setIntensity`, `trigger(stinger)`). |

---

## 4. Architecture for the Java library [proposal]

### 4.1 Components

```
 game thread ──setState/setIntensity/trigger──►  ControlQueue (lock-free)
                                                     │
                     ┌───────────────────────────────▼──────────────────────────────┐
                     │ MusicDirector (single scheduler thread)                       │
                     │  StateMachine ── TransitionRules ── IntensitySmoother         │
                     │  MusicalClock (bars/beats ↔ µs)                               │
                     │  for each upcoming bar within lookahead:                      │
                     │     BarContext(state, intensity, chord, bar#, seed)           │
                     │     Layer generators (Textures + POs) ─► List<NoteEvent>      │
                     │     Clone/variation, stingers, transition events              │
                     └───────────────────────────────┬──────────────────────────────┘
                                                     │ timestamped events
                       ┌─────────────────────────────┼──────────────────────────┐
                       ▼                             ▼                          ▼
              GervillOutput                  ExternalMidiOutput          EventListenerOutput
     (Synthesizer + per-theme SF2,     (any javax.sound MidiDevice:   (callbacks / ring buffer for
      realtime or offline render)       hardware, loopMIDI, DAW)      LWJGL-OpenAL, libGDX, jME)
```

### 4.2 Theme config (YAML; JSON is equivalent)

```yaml
# themes/fantasy.yaml
theme: fantasy
soundbank: { path: soundfonts/GeneralUser-GS.sf2, fallback: emergency }
seed: 2026
defaults: { meter: 4/4, quantize: bar, lookaheadMs: 150 }
instruments:            # GM program numbers (0-based); bank optional (GS variation = CC0 MSB)
  strings: { program: 48 }
  slowStrings: { program: 49 }
  harp: { program: 46 }
  flute: { program: 73 }
  clarinet: { program: 71 }
  pizz: { program: 45 }
  brass: { program: 61 }
  horn: { program: 60 }
  timpani: { program: 47 }
  choir: { program: 52 }
  kit: { percussion: true, program: 48 }       # GS Orchestra kit
states:
  peaceful:
    tempo: [78, 90]                              # bpm at intensity 0 .. 1
    scale: { root: C4, mode: majorPentatonic }
    harmony: { path: [3-11, 4-26, 3-11, 5-35], chordsPerBar: 1, progression: markov(I,IV,vi,V) }
    layers:
      - { name: pad,    instrument: slowStrings, texture: droneSustain, minIntensity: 0.0 }
      - { name: bass,   instrument: pizz,        texture: lineGroove, register: [36, 48], minIntensity: 0.2 }
      - { name: melody, instrument: flute,       texture: monophonicOrnament, register: [67, 86],
          rhythm: "mp,(l,((2,1,1),(1,1,1),(4,3,1))),(c,2)",   # athenaCL PO syntax still accepted
          density: [0.3, 0.7], minIntensity: 0.0 }
      - { name: arp,    instrument: harp,        texture: lineCluster, minIntensity: 0.5 }
  sneaking:
    tempo: [92, 104]
    scale: { root: D4, mode: dorian }
    layers:
      - { name: pad,    instrument: strings,  texture: droneSustain, velocity: [35, 50] }
      - { name: bass,   instrument: pizz,     texture: lineGroove, articulation: staccato }
      - { name: melody, instrument: clarinet, texture: lineGroove, density: [0.2, 0.5] }
      - { name: perc,   instrument: kit, pattern: [pedalHat, sideStick], minIntensity: 0.3 }
  combat:
    tempo: [136, 156]
    scale: { root: D4, mode: harmonicMinor }
    layers:
      - { name: strings,  instrument: strings, texture: lineGroove, rhythm: sixteenthOstinato }
      - { name: brass,    instrument: brass,   texture: harmonicAssembly, minIntensity: 0.3 }
      - { name: perc,     instrument: kit,     pattern: battleGroove }
      - { name: timpani,  instrument: timpani, minIntensity: 0.5 }
      - { name: choir,    instrument: choir,   minIntensity: 0.8 }
  danger:   { inherits: sneaking, scale: { mode: octatonic }, tempo: [64, 76] }
  trading:  { tempo: [100, 108], scale: { root: G4, mode: mixolydian } }
  partying: { tempo: [118, 128], meter: 6/8, scale: { root: F4, mode: major } }
stingers:
  combatStart: { instrument: brass, notes: "D3,A3,D4", velocity: 120, plus: [crashCymbal], quantize: beat }
  victory:     { instrument: horn,  motif: [0, 4, 7, 12], quantize: bar }
transitions:
  - { from: "*", to: combat, quantize: beat, stinger: combatStart, fadeOutBars: 0 }
  - { from: combat, to: "*", quantize: bar, bridge: windDown, bridgeBars: 2 }
  - { from: "*", to: "*", quantize: bar, crossfadeBars: 1 }
```

```yaml
# themes/scifi.yaml (same states, different sound world)
theme: scifi
soundbank: { path: soundfonts/scifi-pack.sf2, fallback: soundfonts/GeneralUser-GS.sf2 }
instruments:
  pad: { program: 89 }        # Pad 2 (warm)
  sweep: { program: 95 }      # Pad 8 (sweep)
  bass: { program: 38 }       # Synth Bass 1
  lead: { program: 81 }       # Lead 2 (sawtooth)
  bell: { program: 98 }       # FX 3 (crystal)
  arp: { program: 80 }        # Lead 1 (square)
  kit: { percussion: true, program: 25 }   # TR-808 kit
states:
  peaceful: { tempo: [84, 92], scale: { root: E4, sieve: "3@0|4@1" },    # athenaCL sieve as scale
              layers: [ {instrument: pad, texture: droneSustain}, {instrument: bell, texture: lineCluster, minIntensity: 0.4} ] }
  sneaking: { tempo: [96, 104], scale: { root: E4, mode: phrygian },
              layers: [ {instrument: bass, texture: lineGroove, articulation: staccato}, {instrument: kit, pattern: [closedHat16ths], minIntensity: 0.3} ] }
  combat:   { tempo: [140, 150], scale: { root: E4, mode: phrygianDominant },
              layers: [ {instrument: bass, rhythm: sixteenthOstinato}, {instrument: lead}, {instrument: arp, minIntensity: 0.4}, {instrument: kit, pattern: breakbeat} ] }
```

Config notes: a theme selects **one soundbank** (01 §4 resolver chain). Instruments are GM programs
plus an optional GS bank variation (02 §1), so one game can swap the whole sound world by
switching theme files. The athenaCL PO argument syntax stays available as an expert escape hatch (03 §3 parser).

### 4.3 State machine and transition rules

- States come from the config. The game calls `setState(name, intensity)` at any time. Only the
  **latest pending request** is kept: if several arrive within one bar, the last one wins.
- Rule resolution like Wwise's matrix: the most specific rule `(from, to)` beats `(*, to)` beats
  `(from, *)` beats `(*, *)`.
- Rule fields: `quantize` (immediate | beat | bar | phrase(n)), `stinger`, `bridge` (a
  generated fill or wind-down of n bars), `crossfadeBars` (CC11 ramps), `tempoChange` (at boundary | ramp
  over bridge), `minDwellBars` (anti-flapping), `cooldown` for stingers.
- **Fast path:** for `quantize: beat` or `immediate`, the scheduler **retracts** events it already queued
  beyond the new boundary. That needs a cancellable queue in the output, because events already handed to
  Gervill's receiver can't be cancelled. So schedule only ~100–200 ms ahead and keep the rest of the bar
  in the engine's own queue.

### 4.4 Real-time scheduler on Gervill

- Open `MidiSystem.getSynthesizer()`. Load the theme's `Soundbank` with `loadAllInstruments`
  (01 §3). Use `synth.getReceiver()` and send `ShortMessage`s **with timestamps in µs**
  relative to `synth.getMicrosecondPosition()`. Gervill schedules timestamped messages internally.
  The PoC uses exactly this receiver path in offline mode (`AudioSynthesizer.openStream`). The
  real-time device path was **not run** here, because the box has no audio device.
- **Lookahead:** generate a bar when it is within *one bar* of the play head, but release events
  to the receiver only for the next ~150 ms (a 10–20 ms tick on a dedicated thread). Gervill's own
  output latency is set by its line buffer (`"latency"` info property on `open(line, info)`, default
  around 120 ms in Gervill sources **[unverified exact default]**). Total control-to-sound latency ≈
  quantization wait (up to 1 bar, ~1.7–2.9 s at these tempos) + lookahead + device buffer.
- **Theme switch:** load the new soundbank on a background thread. At a bar boundary,
  `unloadAllInstruments(old)` → `loadAllInstruments(new)`, then re-send program changes. Large SF2s take about 1 s
  to load (02 §2), so preload them.
- **Offline mode** (tests, trailers, CI audio snapshots): the same director drives
  `AudioSynthesizer.openStream`, pulling audio in 10 ms blocks. This is the PoC's driver, needing
  `--add-exports` (01 §3.3).

### 4.5 Output abstraction

```java
public interface MusicOutput extends AutoCloseable {
    void send(MidiEvent e, long timeMicros);      // already-timed short messages
    void cancelAfter(long timeMicros);            // retract not-yet-released events (fast transitions)
    long nowMicros();                             // the output's clock (synth or audio device)
}
// Implementations:
//  GervillOutput        – internal SoftSynthesizer + theme soundbank (realtime or offline WAV)
//  MidiDeviceOutput     – any javax.sound.midi.MidiDevice (hardware synth, virtual port into a DAW)
//  EventSinkOutput      – hands NoteOn/NoteOff/CC to the game (callback or SPSC ring buffer) so it can
//                         drive its own sampler (LWJGL OpenAL, libGDX AudioDevice, jMonkeyEngine)
```

For engines that own the audio thread (libGDX `AudioDevice.writeSamples`, LWJGL OpenAL streaming,
jME), the cleanest route is to **pull PCM** from Gervill's `openStream` on the engine's audio thread
and queue it as a streaming source. That keeps one audio clock and avoids `javax.sound.sampled` device
issues on some platforms. `--add-exports` is required for that path (01 §3.3). **[proposal; not
tested in an engine]**

### 4.6 Java API sketch

```java
AdaptiveMusic music = AdaptiveMusic.builder()
        .theme(Theme.load(Path.of("themes/fantasy.yaml")))
        .output(Outputs.gervill())                  // or Outputs.midiDevice("loopMIDI"), Outputs.events(listener)
        .seed(2026)
        .lookahead(Duration.ofMillis(150))
        .build();

music.start("peaceful", 0.2f);
music.setIntensity(0.6f);                           // smooth, per-bar
music.setState("sneaking", 0.4f);                   // quantized per transition rules
music.trigger("combatStart");                       // stinger, quantized to the next beat
music.setState("combat", 0.8f);
music.switchTheme(Theme.load(Path.of("themes/scifi.yaml")));   // soundbank preloaded, swapped at a bar line
music.onBar(bar -> hud.debug(bar.state() + " " + bar.index())); // callbacks for sync (e.g. footsteps on beat)
music.close();

// Offline render, for previews and regression tests
try (OfflineRender r = AdaptiveMusic.renderer(theme, seed)) {
    r.at(0.0).setState("peaceful", 0.3f).at(16.4).setState("sneaking", 0.4f).at(29.1).setState("combat", 0.6f);
    r.renderWav(Path.of("preview.wav"), Duration.ofSeconds(46));
}

// Extension point: athenaCL concepts as SPI
public interface LayerGenerator {                  // "Texture", incremental
    List<NoteEvent> generateBar(BarContext ctx, LayerState state, RandomSource rng);
}
public interface ValueStream {                      // "ParameterObject", pull-based
    double next(double tSeconds, BarContext ctx);
}
```

### 4.7 Threading and latency

- **Game thread:** only enqueues commands (lock-free MPSC queue). It never blocks and never touches the synth.
- **Director thread:** one thread owns all musical state. A tick every 10–20 ms
  (`ScheduledExecutorService` or a `LockSupport.parkNanos` loop) generates bars ahead and releases events
  within the lookahead. Generation must stay well under the bar length; the PoC rendered 49 s in about 3 s
  *including* synthesis.
- **Audio thread:** Gervill's own (real time) or the engine's (pull mode). No allocation-heavy work there.
- **Soundbank loading:** a background executor. Swap at a bar boundary.
- **Latency budget [estimate]:** command → audible change = quantization wait (beat ≈ 0.4 s, bar ≈
  1.7–2.9 s at 84–144 BPM) + lookahead (100–200 ms) + output buffer (~50–150 ms). For urgent events (player
  hit), use `quantize: beat` or `immediate` stingers with event retraction.
- **GC:** reuse `ShortMessage` objects or pool events. Gervill itself allocates little per note
  **[unverified]**. Profile with `-Xlog:gc` during combat.

---

## 5. Revised phased plan (vs 03)

The goal changed from "faithful offline port" to "real-time adaptive generator inspired by athenaCL". So:

| 03 plan | Revised |
|---|---|
| Phase 1 = exact port of LineGroove + ~10 POs + CPython RNG + MIDI file writer, byte-exact vs pregui | **Phase A (2–3 weeks [estimate]): adaptive core.** Musical clock, director/scheduler, state machine with quantized transitions, intensity smoothing, theme YAML loader, Gervill output (realtime + offline), stingers. Generators: simple built-ins like the PoC's, behind the `LayerGenerator`/`ValueStream` SPI. Deliverable: the PoC as a library + a demo CLI that plays states from the keyboard. |
| Byte-exact conformance as the gate | **Byte-exact is no longer a gate.** Real-time output depends on when the game calls the API. Use instead: (a) **deterministic offline renders** for a scripted timeline (event JSON or MIDI hash; the PoC is already byte-reproducible); (b) **musical invariants** tested per bar (notes in scale, layer counts vs intensity, transitions only on boundaries, no stuck notes, loudness within target per state). Keep pregui golden tests **only for individual ported POs** you want to behave like athenaCL (03 §9), and only if that fidelity matters to you. |
| CPython-compatible MT RNG first | **Not needed.** Use a fast, splittable, seedable RNG (`java.util.random.RandomGenerator` `L64X128MixRandom` or SplittableRandom), seeded per (theme seed, state, bar, layer). Add CPython-MT only if a PO must match athenaCL exactly. |
| Port 136 POs, 14 textures | **Phase B: port the useful subset as streams.** About 25 POs (`constant`, `basketGen`, `randomUniform`/`Gauss`/`Beta`, `markovValue`, `grammarTerminus`, `sieveList`, `iterateGroup`, `lineSegment`/`breakPointLinear`, `wave*`, `operator*`, `pulseTriple`, `loop`, `markovPulse`, `gaRhythm`, `feedbackModelLibrary`, `accumulator`, `cyclicGen`) plus **5 textures rewritten incrementally**: LineGroove, LineCluster, HarmonicAssembly, DroneSustain, MonophonicOrnament. Plus pitch/set-class/sieve tools with `setTables` generated as data. |
| XML project files, command interpreter | **Phase C:** the athenaCL PO-string parser for expert config, Clones as layer variations, scripted bridges and fills, more stingers. Command interpreter and XML projects become **optional / later** (useful only for importing old athenaCL projects). |
| – | **Phase D:** engine integrations (libGDX/LWJGL/jME samples with pull-mode PCM), external MIDI output, profiling, theme authoring tool (live reload of YAML with an offline preview render). |

GPL still applies if any athenaCL code is translated (03 §10). If the library only borrows **ideas**
(stream-based parameters, set-class harmony) and you write fresh code without translating the
Python, it could carry a permissive license. That is a decision to make **before** Phase B.
**[legal judgment; not legal advice]**

---

## 6. Risks and open questions

### Risks
1. **Musical quality of generated material.** Rule-based generation tends to sound aimless or
   repetitive over long play sessions. MetaCompose found valence hard to convey. Mitigations: authored
   motifs per theme (generate variations of a *given* motif via clones/ornaments, rather than free melody),
   phrase structure (4/8-bar phrases with cadences), and listening tests early.
2. **Gervill sound quality and CPU.** It is a basic SF2 player: no per-channel effects chains beyond
   reverb and chorus, and loudness differs widely between programs and fonts (02 §2). The PoC needed
   hand-tuned CC7/velocity to keep states within ~7 dB. Needed: per-theme loudness calibration (render each
   state offline, measure LUFS, store gains).
3. **Latency vs musicality.** Bar quantization at slow tempos means 2–3 s of delay. Combat entry needs beat
   quantization plus retraction.
4. **Platform audio.** `javax.sound.sampled` behavior differs per OS. Engines with their own audio
   (libGDX/LWJGL) are better served by pull-mode PCM, which needs `--add-exports` (internal JDK API) and
   may break with future JDKs.
5. **Soundfont licensing per theme.** Only ship fonts with clear licenses (01 §3.4). Never gm.dls.
6. **Scope creep.** Middleware-grade features (sync-to-same-position, bridges, layering curves, editors)
   are a lot of work. Start with states + intensity + bar quantization + stingers.
7. **GPL** if athenaCL code is translated (§5).

### Questions for the user
1. **Target engines and Java versions?** (libGDX, LWJGL, jMonkeyEngine, custom; desktop only or Android too?
   Android has no `javax.sound.midi` synth, so it would need a different synth.)
2. **Generated vs authored:** fully generated music, or **composer-authored motifs and progressions**
   that the engine varies? This changes the generator design a lot.
3. **Licensing goal:** is GPL acceptable for games that embed this, or should the library be
   permissive (clean-room, ideas only)?
4. **Latency tolerance:** is waiting for the next bar OK for most states? Which states need beat or
   immediate response?
5. **States:** fixed six, or game-defined? Can two be active at once (e.g. *trading* during *danger*)? Are
   there one-shot events (victory, death, discovery) that should be stingers?
6. **Sound quality bar:** is GM/SF2 quality (GeneralUser GS) acceptable, or do themes need custom SF2s, or
   rendering through external synths or DAWs (external MIDI out)?
7. **Determinism:** should the same seed and same game timeline give identical music (replays, multiplayer
   sync), or is freshness each session preferred?
8. **Tooling:** who writes themes: developers (YAML) or composers (needs a preview tool)?

---

## 7. Proof of concept [verified]

**Files:** `tools/adaptive-poc/AdaptivePoc.java` (one file, ~230 lines, JDK 21 single-file launch),
`tools/adaptive-poc/run-output.txt` (bar log), `audio/adaptive-poc-peaceful-sneaking-combat.mp3`
(49.0 s, 192 kbps, GeneralUser GS 2.0.3).

**Run:** `java --add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED AdaptivePoc.java GeneralUser-GS.sf2 out.wav`

**What it does:**
- A "fantasy" theme with three states (peaceful: C major pentatonic, 84 BPM, slow strings, acoustic
  bass, flute, harp arpeggio at intensity ≥ 0.4; sneaking: D Dorian, 100 BPM, warm pad, pizzicato,
  clarinet, light kit; combat: D harmonic minor, 144 BPM, strings, contrabass 8ths, brass, kit,
  timpani ostinato at intensity ≥ 0.5).
- Generates **one bar at a time** within a 100 ms lookahead, in 10 ms audio blocks.
- Applies a state request at the **next bar line**: program changes, CC7 per state, all-notes-off on
  channels the new state doesn't use, and a **stinger** (crash + orchestra hit) on combat entry.
- Intensity is smoothed per bar and gates layers and density.
- Melodies are a random walk snapped to scale, with chord tones on downbeats, seeded per
  (seed, state, bar).

**Game script and what happened** (from `run-output.txt`):

| Game request (t) | Applied at | Notes |
|---|---|---|
| peaceful @0.3 (0.00 s) | bar 0, 0.00 s | start |
| peaceful @0.7 (9.00 s) | intensity ramps 0.30 → 0.50 (bar 4) → 0.60 (bar 5) | harp layer enters when intensity ≥ 0.4 |
| sneaking @0.4 (16.40 s) | **bar 6, 17.14 s** | waited 0.74 s for the bar line |
| combat @0.6 (29.10 s) | **bar 12, 31.54 s** | the request came 40 ms *before* bar 11 (29.14 s), but bar 11 was already generated inside the 100 ms lookahead, so the change waited a **full extra bar (2.44 s)**. This is the lookahead/quantization trade-off FMOD documents, and the reason for event retraction or "beat" quantization for combat. |
| combat @1.0 (36.00 s) | ramps 0.58 → 0.99 over bars 14–19 | tom fills appear at intensity > 0.7 |

**Measurements** (mono-sum RMS / sample peak, spectral centroid):

| Section | Time (s) | RMS dBFS | Peak dBFS | Centroid Hz |
|---|---|---|---|---|
| peaceful i=0.3 | 0.00–11.43 | −26.9 | −12.4 | 1512 |
| peaceful → 0.6 | 11.43–17.14 | −21.4 | −6.0 | 1081 |
| sneaking | 17.14–31.54 | −28.7 | −9.9 | 997 |
| combat entry | 31.54–36.54 | −24.8 | −7.1 | 3483 |
| combat → 1.0 | 36.54–46.50 | −22.3 | −3.2 | 3022 |

Whole file: −21.9 LUFS integrated, −3.2 dBFS peak, 0 clipped samples. Render time ~3 s for 49 s of
audio (load + generate + synthesize). **Two runs produced byte-identical WAVs** (sha256 prefix `fd29011194344694`).

**Honest assessment:**
- **Works:** bar-quantized switching, tempo change at the bar line, layer gating by intensity, the
  stinger, deterministic output, a theme expressed as data, and the timestamped-Receiver path through Gervill.
- **Loudness needed manual tuning.** The first render had combat **~20 dB louder** than sneaking
  (−22 vs −44 dBFS RMS) with a −0.2 dBFS peak. Per-state CC7 (combat 84) and higher calm-state velocities
  brought the states within ~7 dB. A real library needs automatic per-state calibration.
- **Not tested:** the real-time device output (no audio device on the box), beat-quantized transitions
  and retraction, crossfades (hard switches only, plus all-notes-off), theme or soundbank switching,
  and engine integration.
- **No listening evaluation.** Nobody listened to the MP3, so musical quality (the PoC's melodies are
  simple random walks) is unjudged. The numbers only show the states differ in level, brightness and
  activity as intended (centroid ~1.0–1.5 kHz calm vs ~3.0–3.5 kHz combat; onset rate rose from about
  1.1/s peaceful to 2.7/s sneaking to 3.5/s combat in a first-pass measurement before the loudness fix).
