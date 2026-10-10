# 02 — What the soundfonts contain, and what they render like

*Oct 9, 2026. Follows [01-soundfont-microsoft-gs.md](01-soundfont-microsoft-gs.md). Everything
below was produced on the research box with OpenJDK 21.0.12's built-in Gervill synth, the same
engine a Java port would use. **Nobody listened to these renders.** The "listening notes" are
measurements (loudness, peaks, spectrum, stereo width, decay), not judgments of taste.*

## Files

| File | What |
|---|---|
| `audio/demo.mid` | The demo score (type-1 SMF, 480 PPQ, 120 bpm, ~30 s) |
| `audio/demo-generaluser-gs.mp3` | Render with **GeneralUser GS 2.0.3** |
| `audio/demo-fluidr3-gm.mp3` | Render with **FluidR3_GM** (athenaCL's current built-in font) |
| `audio/demo-java-emergency.mp3` | Render with Java's **built-in default**, the generated "Emergency GM sound set" |
| `audio/instruments-<bank>.csv` | Full instrument dump per soundbank: `java_bank, bank_msb, bank_lsb, program, percussion, name` |
| `audio/render-metrics.json` | All the numbers in §3 |
| `tools/Demo.java`, `tools/analyze.py`, `tools/inst.py` | Score generator + renderer + dumper, metrics script, bank summary script |

MP3s: 44.1 kHz stereo, 192 kbps CBR (LAME via ffmpeg), 31.0 s, about 745 KB each.
Rendering: `AudioSynthesizer.openStream` (offline, faster than real time). Gervill's default
reverb/chorus stay on, there's no normalization, and all three use the same MIDI.
Regenerate with
`java --add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED Demo <label> [path.sf2]`.

### Demo score (timeline)

| Time | Part | GM program | Phrase |
|---|---|---|---|
| 0–4 s | Piano | 0 Acoustic Grand | C-major arpeggio, then a held 5-note chord |
| 4–8 s | Strings | 48 String Ensemble 1 | Two sustained 4-voice chords (C, F) |
| 8–12 s | Brass | 61 Brass Section | Fanfare in octaves |
| 12–16 s | Flute | 73 Flute | 8-note melody, high register |
| 16–20 s | Guitar | 24 Nylon Guitar | Four strummed chords (E, A, G, E) |
| 20–24 s | Bass + drums | 33 Fingered Bass + ch. 10 kit | Root-note groove with hi-hat/kick/snare |
| 24–28 s | Drums alone | ch. 10, Standard kit | Two bars, tom fill at the end |
| 28–31 s | Crash | ch. 10 | Crash cymbal + kick, then decay |

Every channel sends bank select 0/0, program change, CC7 = 100, CC10 = 64. That's the same kind of
messages athenaCL's `midiTools` emits (athenaCL itself sends no bank select).

---

## 1. Soundbank contents (from `Soundbank.getInstruments()` in Gervill)

Java reports the bank as a 14-bit number, `MSB*128 + LSB`. The CSVs split it into MSB and LSB. In
Gervill, SF2 bank 128 (the drum bank) appears as `percussion=true`.

| | GeneralUser GS 2.0.3 | FluidR3_GM | Java emergency bank |
|---|---|---|---|
| File size | 32,319,396 B (SF2) | 148,398,306 B (SF2) | Generated in memory at startup. Cached copy `~/.gervill/soundbank-emg.sf2` = 1,878,120 B |
| License | GeneralUser GS License v2.0 (permissive) | MIT | Part of OpenJDK (GPL-2.0 + Classpath Exception) |
| Instruments total | **287** | **189** | **129** |
| Melodic | 274 | 158 | 128 |
| Drum kits | **13** | **31** | **1** |
| GM bank-0 coverage (programs 0–127) | **128/128** | **128/128** | **128/128** |
| Melodic outside bank 0 | 146 | 30 | 0 |
| Banks used (MSB) | 1–9, 11, 12, 13, 16, 24, 25, 26, 120 | 8, 9, 16 | 0 only |

### GS variation banks

- **FluidR3_GM**: 30 variations in the **Roland GS layout**: MSB 8 (28 presets, e.g. Detuned EP 1/2,
  Coupled Harpsichord, Church Bell, Ukulele, 12-String Guitar, Funk Guitar), MSB 9 (1), MSB 16 (1).
- **GeneralUser GS**: the most extensive set. GS variations sit at the usual MSB 8/9/16/24–26
  (e.g. Tin Whistle variants at 24–26). It also has **its own extras** at MSB 1–7 ("Mono" and
  alternate strings/brass, e.g. MSB 1 = Synth Bass 101, Tremolo Strings Mono, Trumpet 2, Square
  Wave, Synth Mallet), MSB 11–13 (31 presets at MSB 11: "noVel" organs, Wah Guitar (CC21), Solar
  Wind, ...), and MSB 120 holds its 13 drum kits as melodic presets (for GM2/XG-style kit addressing).
- **Java emergency**: none. GM only.
- **Reference, Microsoft `gm.dls`** (from the preset list of an SF2 conversion on
  [Musical Artifacts #713](https://musical-artifacts.com/artifacts/713); not dumped here because the
  file can't legally be on the box): 128 GM + **98 variation presets** across 9 sub-banks + **9 kits**.
  That converter numbers its banks 1–9, not the Roland MSB numbers. How the Microsoft synth maps
  CC0 values onto them was **not verified**.

### Drum kits

| GS kit slot (program) | GeneralUser GS | FluidR3_GM | Java emergency | gm.dls (ref.) |
|---|---|---|---|---|
| 0 Standard | ✔ (+ Standard 2, 3 at 1–2) | ✔ (+ Standard 1–7 at 1–7) | ✔ only kit | ✔ |
| 8 Room | ✔ | ✔ (+ Room 1–7) | – | ✔ |
| 16 Power | ✔ | ✔ (+ Power 1–3) | – | ✔ |
| 24 Electronic | ✔ | ✔ | – | ✔ |
| 25 TR-808 | ✔ ("808/909") | ✔ | – | ✔ |
| 26 Dance | ✔ | – | – | – |
| 32 Jazz | ✔ | ✔ (+ Jazz 1–4) | – | ✔ |
| 40 Brush | ✔ | ✔ (+ Brush 1–2) | – | ✔ |
| 48 Orchestra | ✔ | ✔ | – | ✔ |
| 56 SFX | ✔ | – | – | ✔ |
| 127 CM-64/MT-32 | ✔ | – | – | – |

On kit coverage, GeneralUser GS is the only one of the three that covers every GS kit slot
gm.dls has (including SFX). FluidR3 has more kits overall, but they're numbered sub-variants, and it
has no SFX kit.

---

## 2. Measured render characteristics

Integrated loudness and true peak from ffmpeg `ebur128`. Per-section RMS uses the mono sum.
Sections overlap slightly because of release and reverb tails.

### Whole render

| | GeneralUser GS | FluidR3_GM | Java emergency |
|---|---|---|---|
| Integrated loudness | **−21.9 LUFS** | **−19.3 LUFS** | **−20.1 LUFS** |
| Loudness range (LRA) | 6.0 LU | 7.0 LU | 7.0 LU |
| True peak | −6.5 dBFS | −5.9 dBFS | **−2.0 dBFS** |
| Clipped samples | 0 | 0 | 0 |
| Final crash: time to fall below −60 dBFS | 1.7 s | **2.6 s** | **1.2 s** |
| Render time (load + 31 s render) | ~1.75 s | ~1.45 s | ~0.35 s |

### Per section: RMS level (dBFS)

| Section | GeneralUser GS | FluidR3_GM | Java emergency |
|---|---|---|---|
| Piano | −25.6 | **−30.9** | −27.5 |
| Strings | −24.6 | −23.0 | −24.2 |
| Brass | −27.4 | −24.6 | −27.2 |
| Flute | −22.2 | −22.7 | −25.0 |
| Nylon guitar | −25.2 | −22.7 | −25.3 |
| Bass + drums | −24.4 | −21.1 | **−18.3** |
| Drums alone | −27.2 | −27.0 | **−21.1** |
| Crash + tail | −34.2 | −30.3 | −30.7 |
| **Spread (max − min of first 7)** | **5.2 dB** | **9.8 dB** | **8.7 dB** |

### Per section: spectral centroid (Hz). Higher means more high-frequency energy.

| Section | GeneralUser GS | FluidR3_GM | Java emergency |
|---|---|---|---|
| Piano | 2052 | 2345 | **680** |
| Strings | 1700 | 1698 | 1458 |
| Brass | 3538 | 3513 | 2637 |
| Flute | 1613 | 1929 | 1994 |
| Nylon guitar | 1546 | 1832 | 2062 |
| Bass + drums | 2524 | 3599 | 3100 |
| Drums alone | 2913 | 4624 | 4257 |

### Per section: stereo width (side level relative to mid, dB). Closer to 0 means wider.

| Section | GeneralUser GS | FluidR3_GM | Java emergency |
|---|---|---|---|
| Piano | −14.2 | **0.0** | −19.6 |
| Strings | −9.1 | **+0.2** | −15.5 |
| Brass | −9.2 | −8.6 | −17.2 |
| Flute | −16.8 | −2.7 | −21.1 |
| Guitar | −15.7 | −14.9 | −15.9 |

All MIDI pans were centered, so any width comes from stereo samples or zone panning in the
soundfont plus Gervill's reverb and chorus.

## 3. Notes on the renders (measurements only, not listening)

- **GeneralUser GS** has the **most even balance**: sections stay within 5.2 dB of each other.
  It is also the quietest overall (−21.9 LUFS) with the most headroom (−6.5 dBFS peak). Its
  instruments are moderately wide (strings and brass about −9 dB side/mid) and its piano is
  mostly centered.
- **FluidR3_GM** is the **loudest** overall (−19.3 LUFS), but its **piano is ~9.8 dB below its
  loudest section**, so in a mixed athenaCL texture the piano would likely sit low unless its
  velocity or volume is raised. Piano, strings and flute come out **as wide as the mix allows**
  (side ≈ mid, i.e. true stereo samples). Its drums carry the most high-frequency energy
  (centroid 4.6 kHz), and its crash has the longest decay (2.6 s to −60 dBFS).
- **Java emergency bank** puts nearly all its energy in the **drums** (bass+drums −18.3 dBFS,
  drums −21.1 dBFS, true peak −2.0 dBFS) while melodic parts sit ~6–9 dB lower. Its **piano is
  very dark** (centroid 680 Hz vs ~2.0–2.3 kHz for the sample-based fonts), consistent with simple
  generated waveforms rather than recorded samples. It is the **narrowest** image (−15 to −21 dB
  side/mid) and has the shortest crash decay (1.2 s). It is fine as a "never silent" fallback, but
  its levels between instruments differ quite a bit from the sample-based fonts.
- **For a GS/Microsoft-like default:** by contents (GS variation layout, every gm.dls kit slot
  including SFX, 32 MB, permissive license), GeneralUser GS is the closest match of the three.
  Whether it *sounds* closer to the Microsoft GS Wavetable Synth is a listening question and is
  **not answered here**.
- **Caveats:** one short synthetic phrase per instrument, a single velocity range (70–110),
  Gervill's own interpretation of SF2 (other synths such as FluidSynth or rustysynth may differ),
  and MP3 encoding on top. RMS and centroid include bleed from the previous section's tails.
  The numbers say nothing about realism or timbre quality.

## Next steps

- Have the user listen and pick the default font. Optionally re-render a real athenaCL-generated
  MIDI file instead of this synthetic demo.
- On a Windows machine, render the same `demo.mid` with `C:\Windows\System32\drivers\gm.dls`
  through the same `Demo` class (`java ... Demo gm-dls C:\Windows\System32\drivers\gm.dls`) for a
  direct comparison. Keep that render private and don't redistribute it (see 01 §2).
