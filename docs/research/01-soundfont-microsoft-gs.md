# 01 — Soundfont research: Microsoft GS Wavetable Synth and a Java athenaCL port

*Research note, Oct 9, 2026. Scope: how athenaCL makes sound today, what the Microsoft GS
Wavetable Synth sound set is, how Java can play or render the same kind of soundbank, and a
proposed design for a Gradle library. No project code was created. The small Java experiments
below were run in a scratch directory on the research box (OpenJDK 21.0.12, Debian).*

Labels used: **[verified]** means read in source code or run by me. **[sourced]** means a cited
web source says so. **[unverified]** means plausible but not confirmed.

---

## TL;DR

1. athenaCL's Python engine (`pysrc/`) writes a **Standard MIDI File** and nothing more. The Rust
   GUI plays that file through the `midi-player` crate (which uses the `rustysynth` SF2 synth) with
   a bundled **FluidR3_GM.sf2** (MIT, 148 MB, downloaded by `make init`). Users can pick any other `.sf2`.
   Nothing in the repo uses the Microsoft GS Wavetable Synth or `gm.dls`. **[verified]**
2. The Microsoft GS Wavetable Synth plays **`gm.dls`**, Roland's GS Sound Set (Sound Canvas
   samples) in DLS format, in `%SystemRoot%\System32\drivers\`. Its own readme says it is licensed
   *"for use with Microsoft operating system products only. All other uses require a separate
   written license from Roland."* You **cannot bundle it** or ship an SF2 converted from it. **[sourced]**
3. Java's built-in synth (Gervill, `SoftSynthesizer`) reads **DLS level 1/2 and SF2**. On Windows it
   **already uses `gm.dls` as its default soundbank** when the file is there. So the
   "Microsoft GS sound" is legal on Windows if you load the user's own copy at runtime. **[verified in OpenJDK source]**
4. Offline WAV rendering needs `com.sun.media.sound.AudioSynthesizer.openStream(...)`. That
   interface is in a package `java.desktop` doesn't export, so the code needs
   `--add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED` (or an `Add-Exports` JAR
   manifest entry for `java -jar`). I tested both on JDK 21. **[verified]**
5. Recommended default for a distributable library: **GeneralUser GS** (32 MB SF2, permissive license, GS-compatible).
   Use the user's `gm.dls` on Windows when they opt in, and Java's emergency soundbank as the last fallback.
   A port of athenaCL is a derivative work under **GPL-2.0-or-later**, so the Java library must
   be GPL too. That carries over to apps that embed and distribute it.

---

## 1. How the current repo produces sound

Repo: <https://github.com/ales-tsurko/athenaCL> (shallow clone of the default branch, Oct 9, 2026).

### 1.1 Built-in soundfont and `make init` [verified]

`Makefile`:

```make
# the built-in sound font: too large for the repository, it is a release's asset
SOUND_FONT := resources/FluidR3_GM.sf2
SOUND_FONT_URL := https://github.com/ales-tsurko/athenaCL/releases/download/fluidr3-gm/FluidR3_GM.sf2
SOUND_FONT_SHA256 := 74594e8f4250680adf590507a306655a299935343583256f3b722c48a1bc1cb0

init: $(SOUND_FONT)
$(SOUND_FONT):
	curl --fail --location --output "$@.part" "$(SOUND_FONT_URL)"
	echo "$(SOUND_FONT_SHA256)  $@.part" | shasum -a 256 -c -
	mv "$@.part" "$@"
```

- Release asset [`fluidr3-gm`](https://github.com/ales-tsurko/athenaCL/releases/tag/fluidr3-gm):
  `FluidR3_GM.sf2` is **148,398,306 bytes**. Its SHA-256 matches the Makefile; I downloaded and checked it.
- License: `resources/FluidR3_GM.license.txt` is the **MIT License**, © 2000–2002, 2008 Frank Wen
  and © 2008 Toby Smithe.
- `Cargo.toml` `[package.metadata.bundle] resources` ships the `.sf2` and its license inside the macOS bundle.
- `build.rs` → `link_resources()` symlinks `resources/` next to the built executable ("where the app
  looks for the soundfont"). `src/app/app/state.rs` has
  `const SOUND_FONT: &str = "resources/FluidR3_GM.sf2"`. `builtin_soundfont()` in
  `src/app/app/playback.rs` resolves that path against `current_exe().parent()`.
- One stale comment: `src/app/player/player.rs` says the font is "half a gigabyte kept in Git
  LFS". In fact it is 148 MB and a release asset. Tests build a tiny one-sample SF2 in memory instead.

### 1.2 Playback engine [verified]

- `Cargo.toml` deps: `midi-player = "0.2.3"`. `Cargo.lock` pulls in `rustysynth 1.3.6`, a pure-Rust
  SF2 synthesizer. Audio I/O uses `cpal` and `rodio`. Audio files (WAV/AIFF) are decoded with `symphonia`.
- `src/app/player/output.rs`: `Player::new(sf, settings)` builds the synth from the SF2 path at the
  device sample rate. MIDI and decoded audio go into one `rodio` mixer on one `cpal` stream.
- Soundfont selection is user-facing. The header **SOUND** button chooses an `.sf2` (the file dialog
  filters only `sf2`), and a browser context menu has **Use as sound**. The choice is saved in
  preferences (`src/app/playback/preferences.rs`, `soundfont: Option<PathBuf>`). If a saved font is
  missing, it falls back to the built-in one (`output.rs`: "A missing remembered font must fall back...").
  The manual covers this in `doc/src/getting-started.md`.
- **DLS is not supported** by this path. `rustysynth` is SF2-only, and the UI filters `.sf2`, so
  `gm.dls` can't be used in the current app.

### 1.3 How `pysrc` produces notes [verified]

- The engine writes SMF files and has no synthesis of its own.
  `pysrc/athenaCL/libATH/eventList.py` → `EngineMidiFile._write()` →
  `midiTools.MidiScore(trackList, name, tempo).write(path)`.
- `midiTools.MidiScore`: **960 PPQN** and up to 16 channels. Channel 10 is skipped when channels are
  auto-assigned (GM drums). Each track gets a GM **program change** (`0xC0`), **pan** (CC10),
  **volume** (CC7), optional **portamento** (CC65/CC5), and note on/off. It sends **no bank select**,
  so only GM bank-0 programs and the ch.10 kit matter. GS variation banks (gm.dls bank 1–9 presets)
  are never addressed.
- `_translateMidiList()` reduces each event to `(tStart, sus, midiVel, midiPs, midiPan)`.
  `libOrc/generalMidi.py` maps instrument names to GM program numbers (`"piano1": 0`, ...).
- The legacy Python prefs (`prefTools.py`) launch an *external* MIDI player (QuickTime on macOS,
  `playmidi` on Linux). On Windows that would have been the OS default player, i.e. the Microsoft GS
  Wavetable Synth. That is probably where the familiar "athenaCL sound" on Windows came from. **[unverified inference]**
- Port note: `src/libath/` reimplements some Python modules in Rust (e.g. `rng.rs`, `chaos.rs`).
  Cargo.toml says the RNG streams are ChaCha12 with SHA-256 seed folding, chosen so seeds stay
  reproducible. A Java port that wants **identical output for a given seed** must reproduce that
  exactly. That is outside sound, but worth a later note.

**Consequence for the port:** the Java side only needs a GM-compatible synth that plays a
`javax.sound.midi.Sequence` (or the same SMF). The soundbank is a swappable resource.

---

## 2. Microsoft GS Wavetable Synth (`gm.dls`)

| Item | Finding | Source |
|---|---|---|
| What it is | Windows' built-in software MIDI synth (DirectMusic/WDM era). It plays the **Roland GS Sound Set** (Sound Canvas samples) licensed to Microsoft in 1996. | [Microsoft Support KB](https://support.microsoft.com/en-us/topic/cannot-hear-midi-playback-using-microsoft-gs-wavetable-sw-synth-22b0c998-dbaa-a08b-e133-1cefa017eed4); [Microsoft press release 1996](https://news.microsoft.com/1996/10/22/microsoft-licenses-sound-canvas-sounds-from-industry-leader-roland-corp/) (as linked from Musical Artifacts #713; not opened by me) |
| File | `gm.dls` in `C:\Windows\System32\drivers\` (Windows 98 used `Gm16.dls`) | [Sound.SE answer](https://sound.stackexchange.com/questions/35521/where-is-the-default-windows-8-midi-soundfont-located); MS KB above; OpenJDK source (§3.2) |
| Format | **DLS** (Downloadable Sounds, RIFF), 16-bit samples, about **3.3 MB**. Usually described as DLS Level 1, ~22.05 kHz samples. | Size/rate: [VGMPF DLS wiki](https://vgmpf.com/Wiki/index.php?title=DLS) and an artifact listing ([3.07 MB SF2 conversion](https://musical-artifacts.com/artifacts/5190)). **Exact DLS level and sample rate not verified by me** (no legal copy on the box). |
| Contents | 128 GM melodic programs + GS variation banks (e.g. bank 1 "Ukulele", "Church Bell"), drum kits Standard/Room/Power/Electronic/TR-808/Jazz/Brush/Orchestra/SFX | Preset list at [Musical Artifacts #713](https://musical-artifacts.com/artifacts/713) |
| License | `GMREADME.TXT` next to the file: *"The GM.DLS file contains the Roland SoundCanvas Sound Set ... Roland GS Sound Set/Microsoft (P) 1996 Roland Corporation U.S. The Roland SoundCanvas Sound Set is licensed under Microsoft's End User License Agreement for use with Microsoft operating system products only. All other uses require a separate written license from Roland."* | Quoted in the [Sound.SE answer](https://sound.stackexchange.com/questions/35521/where-is-the-default-windows-8-midi-soundfont-located) and [Musical Artifacts #713](https://musical-artifacts.com/artifacts/713) |
| Modification | DirectX SDK docs: "The Roland GM/GS Sound Set cannot be modified, due to legal restrictions." | [DirectX 9 SDK, Instruments and Downloading (mirror)](http://doc.51windows.net/Directx9_SDK/htm/instrumentsanddownloading.htm) |
| Redistribution | A Microsoft support engineer (2010) quoted the Microsoft copyright FAQ: Microsoft "does not permit the separate redistribution of audio files". | [Microsoft Q&A](https://learn.microsoft.com/en-us/answers/questions/2457054/legal-question-concerning-microsoft-gs-wavetable-s) |

**Takeaways**

- **Do not bundle `gm.dls`**, and do not bundle any SF2 converted from it. "gm.dls → SF2" files online
  (Musical Artifacts [#713](https://musical-artifacts.com/artifacts/713),
  [#5190](https://musical-artifacts.com/artifacts/5190),
  [#8840](https://musical-artifacts.com/artifacts/8840)) are all labeled **Non-free / licensing gray area**.
- **Reading the user's own copy at runtime on Windows** is what Java itself does by default (§3.2),
  and what many Windows MIDI apps do. That is the realistic legal way to get "the Microsoft GS sound".
  The fine print of "for use with Microsoft operating system products only" is a question for a
  lawyer, not something I verified. **[unverified legal interpretation]**
- **Faithful playback is hard.** gm.dls relies on DLS articulators (vibrato LFO, key-number→decay,
  zero-length drum notes that need the volume-envelope decay, GS bank-select variations). FluidSynth
  got these wrong until a native DLS parser was written
  ([FluidSynth #1373](https://github.com/FluidSynth/fluidsynth/issues/1373)). Gervill parses
  `art1`/`art2` articulator chunks ([DLSSoundbank.java](https://github.com/openjdk/jdk/blob/master/src/java.desktop/share/classes/com/sun/media/sound/DLSSoundbank.java)).
  **Whether Gervill + gm.dls sounds identical to the real Microsoft synth is unverified.** I found
  no specific bug reports about Gervill mis-parsing gm.dls. The Gervill author says it is used by
  default on Windows ([Karl Helgason on SO](https://stackoverflow.com/questions/31079113/where-does-the-java-7-gervill-soundbank-live-on-windows)).
  This needs an A/B test on a Windows machine.

---

## 3. Java options

### 3.1 Gervill (`javax.sound.midi` built-in synth)

- Since JDK 7, `MidiSystem.getSynthesizer()` returns Gervill's `SoftSynthesizer`. It supports
  **DLS Level 2.2 and SF2 2.04** and dropped the old proprietary GM format
  ([Oracle: Java Sound enhancements in JDK 7](https://docs.oracle.com/javase/7/docs/technotes/guides/sound/enhancements_7.html);
  [Gervill README](https://github.com/bluenote10/gervill/blob/master/README_Gervill.txt)).
  The reader is registered as `DLSSoundbankReader`/`SF2SoundbankReader` in `java.desktop`'s
  [module-info](https://github.com/openjdk/jdk/blob/master/src/java.desktop/share/classes/module-info.java). **[verified]**
- Loading a bank: `Soundbank sb = MidiSystem.getSoundbank(file); synth.isSoundbankSupported(sb); synth.loadAllInstruments(sb);`.
- **SF3 (Ogg-compressed SF2) is not supported.** `SF2Soundbank.java` has no Ogg decoding, so use
  `.sf2` files only, not `MuseScore_General.sf3` or GeneralUser `.sf3`. **[source read; not run]**
- License: OpenJDK's `java.desktop` is GPL-2.0 **with Classpath Exception**, so using it puts no
  constraint on your code.

### 3.2 Default soundbank lookup (current OpenJDK `SoftSynthesizer.getDefaultSoundbank()`) [verified in source]

Source: [SoftSynthesizer.java, lines ~640–810](https://github.com/openjdk/jdk/blob/master/src/java.desktop/share/classes/com/sun/media/sound/SoftSynthesizer.java). Order:

1. `${java.home}/lib/audio/*.sf2|*.dls`. The **largest** such file wins.
2. **Linux only:** `default.sf2` in `/usr/share/soundfonts/`, `/usr/local/share/soundfonts/`,
   `/usr/share/sounds/sf2/`, `/usr/local/share/sounds/sf2/`. Added by
   [JDK-8140620](https://bugs.openjdk.org/browse/JDK-8140620) (JDK 9, backported to 8u102/8u111).
3. **Windows only:** `%SystemRoot%\system32\drivers\gm.dls`.
4. `~/.gervill/soundbank-emg.sf2` (a cached emergency bank).
5. Otherwise it **generates the "Emergency GM sound set"** (`EmergencySoundbank.createSoundbank()`,
   synthesized waveforms) and caches it to `~/.gervill/soundbank-emg.sf2`.

On my box (Linux, no system soundfont) the default was `Emergency GM sound set`, 129 instruments,
cached file 1,878,120 bytes. It is fine as a "never silent" fallback, but it sounds noticeably
synthetic ([dave yarwood, "Making MIDI Sound Awesome in Your JVM"](https://blog.djy.io/making-midi-sound-awesome-in-your-jvm/)).
macOS has no system lookup and uses the emergency bank
([Helgason](https://stackoverflow.com/questions/31079113/where-does-the-java-7-gervill-soundbank-live-on-windows)).

### 3.3 Offline rendering to WAV [verified by experiment]

`SoftSynthesizer` implements `com.sun.media.sound.AudioSynthesizer`. Its
`openStream(AudioFormat, Map<String,Object>)` returns an `AudioInputStream` that you pull samples
from as fast as the CPU allows, which makes it a natural "render to WAV" API. MIDI events are sent
to `getReceiver()` with **microsecond timestamps**. Pass `"load default soundbank" -> false` in the
info map to skip loading the default bank when you supply your own.

Experiment (4-note arpeggio, 3 s, 44.1 kHz stereo, JDK 21.0.12):

| Bank | Instruments | `loadAllInstruments` | Wall time (load + render) | Output |
|---|---|---|---|---|
| Emergency (default) | 129 | n/a | 135 ms | non-silent WAV (RMS ~1268) |
| GeneralUser GS 2.0.3 (32.3 MB) | 287 | `true` | ~1.0–1.4 s | non-silent WAV (RMS ~1202) |
| FluidR3_GM (148 MB, the athenaCL asset) | 189 | `true` | ~1.1 s | non-silent WAV |

**Module-access gotcha [verified]:**
- `javac` without flags: `package com.sun.media.sound is not visible (... does not export it)`.
- Run without flags: `IllegalAccessError ... module java.desktop does not export com.sun.media.sound to unnamed module`.
- Works with `--add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED` at **both** compile and
  run time. It also works when launched with `java -jar` if the JAR manifest has
  `Add-Exports: java.desktop/com.sun.media.sound` (JEP 261 manifest attribute, only honored on the
  **main** jar). A library can't set this for its consumers, so document it.
- Fully public alternative: `AudioSystem.getAudioInputStream(midiFile)` works (via
  `SoftMidiAudioFileReader`, 44.1 kHz/16-bit/stereo; I verified it returns a stream), but it
  **always uses the default soundbank**. You can't pass yours
  ([source](https://github.com/openjdk/jdk/blob/master/src/java.desktop/share/classes/com/sun/media/sound/SoftMidiAudioFileReader.java)).
  Not useful when a custom font is required.
- Real-time playback (`synth.open()` + `Sequencer`) needs only public API and no flags.

### 3.4 Free GM/GS soundfonts (alternatives to bundling gm.dls)

| Soundfont | License | Size | GS-like? | Notes / source |
|---|---|---|---|---|
| **GeneralUser GS** v2.0.3 (S. Christian Collins) | Custom permissive "GeneralUser GS License v2.0": free use, *"Please feel free to use it in your software projects, and to modify ... packaging"*. Sample provenance caveat in the license text. | **32,319,396 B** SF2 (~30.7 MB RAM) | **Yes.** Roland GS + GM compatible, 261 presets, 13 drum kits | [README](https://github.com/mrbumpy409/GeneralUser-GS/blob/main/documentation/README.md), [LICENSE](https://github.com/mrbumpy409/GeneralUser-GS/blob/main/documentation/LICENSE.txt). Loaded in Gervill **[verified]** |
| **FluidR3_GM** (Frank Wen) | MIT | 148,398,306 B | GM (189 presets in Gervill) | athenaCL's current default; loaded in Gervill **[verified]** |
| **MuseScore_General** | MIT | SF2 206 MB / SF3 38 MB | GM (+ some GS) | [OSUOSL mirror listing](https://ftp.osuosl.org/pub/musescore/soundfont/MuseScore_General/), [license](https://ftp.osuosl.org/pub/musescore/soundfont/MuseScore_General/MuseScore_General_License.md). SF3 not usable in Gervill |
| **TimGM6mb** | GPL-2.0 | ~5.7 MB | GM | Old MuseScore 1 default ([MuseScore handbook](https://musescore.org/en/handbook/2/soundfonts-and-sfz-files)). Small, but lower quality. **Size from search summary; not re-checked** |
| NitroShoe "lightweight Roland SC-55" | Claims CC BY 4.0 | ~8 MB | SC-55 imitation, closest *sound* | [GitHub](https://github.com/nitro-shoe/sc-55-soundfont). **Caution:** the author says "most of the samples were taken from the Microsoft GS Wavetable Synth", so the CC license is doubtful. **Do not bundle** |
| "Microsoft GS Wavetable Synth" SF2 conversions | Non-free (Roland/Microsoft) | ~3 MB | Exact | [#713](https://musical-artifacts.com/artifacts/713), [#5190](https://musical-artifacts.com/artifacts/5190), [#8840](https://musical-artifacts.com/artifacts/8840). **Do not bundle** |
| SC55_zzdenis | Not stated in source (forum release) | 266 MB | SC-55 recordings | [VOGONS thread](https://www.vogons.org/viewtopic.php?t=88648). Multi-layer; license unclear. **Do not bundle** |

**Pick:** GeneralUser GS is the closest legal "GS-flavored" default. It is GS-compatible, small
enough to ship or download, and permissively licensed. Fidelity to the Microsoft sound is a matter of taste; no free set is a true imitation.

---

## 4. Recommended design for the Gradle library

### 4.1 Shape

```
athenacl-java/                (Gradle multi-project, Kotlin DSL)
├─ athenacl-core/             engine port: no javax.sound dependency, emits events / Sequence
├─ athenacl-midi/             SMF writer + javax.sound playback/rendering (the sound layer)
└─ athenacl-cli/              thin CLI (picocli or similar), application plugin, fat jar
```

Keeping `core` free of `javax.sound` lets host apps use only the generated events or `Sequence`
and do their own audio.

### 4.2 Soundbank API (pluggable path, platform default, fallback)

```java
public sealed interface SoundbankSource {
    record File(java.nio.file.Path path) implements SoundbankSource {}          // explicit .sf2/.dls
    record Classpath(String resource) implements SoundbankSource {}             // bundled by the host app
    record SystemDefault() implements SoundbankSource {}                        // platform lookup below
    record Emergency() implements SoundbankSource {}                            // Gervill generated bank
}

public final class SoundbankResolver {
    /** Tries each source in order. Returns the first loadable, supported Soundbank plus a
     *  description of which one won, so the CLI can print it. */
    public static Resolved resolve(List<SoundbankSource> chain) { ... }
}
```

Default chain (all overridable):

1. Explicit path from the API, CLI flag `--soundbank <file>`, or env var `ATHENACL_SOUNDBANK`.
2. User config file (e.g. `~/.config/athenacl/config` → `soundbank=`), matching the Rust app's
   remembered choice.
3. **Platform default (opt-in "system" mode):**
   - Windows: `%SystemRoot%\System32\drivers\gm.dls`. This is the Microsoft GS sound, loaded from
     the user's own OS and never copied.
   - Linux: the same `default.sf2` directories the JDK checks, plus `/usr/share/sounds/sf2/FluidR3_GM.sf2`
     (Debian `fluid-soundfont-gm` path, **[unverified path]**).
   - macOS: none built in. Optionally `~/Library/Audio/Sounds/Banks/*.sf2` **[unverified convention]**.
4. App-managed download cache (`~/.cache/athenacl/GeneralUser-GS.sf2`), populated by a
   `soundbank install` CLI command with a pinned SHA-256, the same idea as the Rust `make init`.
5. Gervill **emergency bank**, so playback is never silent and never throws because a font is missing.

Rules:
- Validate before use with `MidiSystem.getSoundbank` + `isSoundbankSupported`. Reject `.sf3`
  with a clear message.
- When rendering with a custom bank, open with `info.put("load default soundbank", false)` and then
  `loadAllInstruments(bank)`. This avoids loading the emergency/OS bank first.
- Expose `render(Sequence, Path wav, AudioFormat)` (offline, needs `--add-exports`; document it and
  set it in the CLI jar manifest `Add-Exports`), `play(Sequence)` (real time, public API only), and
  `writeMidi(Sequence, Path)`.
- If `AudioSynthesizer` isn't accessible at runtime, catch `IllegalAccessError` and fall back to
  real-time capture or "MIDI file only" with an actionable error.
- **Don't publish the soundfont inside the library jar.** 32–148 MB jars on Maven Central are a bad
  idea. Ship an optional separate artifact (e.g. `athenacl-soundbank-generaluser`) or download on demand.
  Ship the font's license file next to it.

Gradle notes:

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--add-exports", "java.desktop/com.sun.media.sound=ALL-UNNAMED"))
}
tasks.withType<Test>().configureEach {
    jvmArgs("--add-exports", "java.desktop/com.sun.media.sound=ALL-UNNAMED")
}
application { applicationDefaultJvmArgs = listOf("--add-exports", "java.desktop/com.sun.media.sound=ALL-UNNAMED") }
tasks.jar { manifest { attributes("Add-Exports" to "java.desktop/com.sun.media.sound") } }  // CLI jar
```

Tests: copy the Rust repo's idea of a tiny generated one-sample SF2 so CI doesn't download fonts.
Gervill's emergency bank also works as a zero-download test bank (verified: renders non-silent audio).

### 4.3 GPL-2.0 implications

- athenaCL's Python sources say **"GPL ... either version 2 of the License, or (at your option) any
  later version"** (`pysrc/athenaCL/libATH/help.py`, `omde/functional.py`). The repo `LICENSE` is the
  GPL v2 text, and 79 Python files carry `License: GPL` headers. **[verified]**
- A Java translation of that code is a derivative work, so the **Java library must be licensed
  GPL-2.0-or-later** (or GPL-3.0, which "or later" allows). Keep copyright notices for Christopher
  Ariza and the fork's authors.
- **Embedding consequence:** under the FSF's reading, a Java app that links the library and is
  *distributed* must also be GPL-compatible as a whole. Purely internal or unreleased use is
  unaffected (GPL obligations trigger on distribution). Permissively licensed host projects can
  still use it, but the combined distribution becomes GPL. This is the main thing to decide before
  investing. Options: accept GPL; ask the copyright holders for a relicense or exception; or write a
  clean-room library that consumes only athenaCL's *outputs* (MIDI files). **[standard GPL
  interpretation; not legal advice]**
- **No conflict from Java/soundfonts:** OpenJDK's Gervill is GPL-2.0 + Classpath Exception.
  FluidR3 (MIT) and GeneralUser GS (permissive) are separate data files that can ship alongside GPL code.
  `gm.dls` must only ever be read from the user's Windows install.
- GPL-2.0-only vs. "or later" matters if you mix in Apache-2.0 deps: Apache-2.0 is compatible with
  GPL-3 but **not** GPL-2-only. Because athenaCL is "or later", releasing the port as GPL-3.0-or-later
  avoids that problem.

---

## Open questions / next steps

1. A/B test on Windows: Gervill + `gm.dls` vs. the real Microsoft GS Wavetable Synth (drums,
   vibrato, release tails) on a few athenaCL-generated MIDI files.
2. Listen-test GeneralUser GS vs. FluidR3 on athenaCL output and pick the default font.
3. Decide the license posture (§4.3) before starting the port.
4. Confirm the Linux distro paths and macOS conventions in §4.2.

## Experiment artifacts (box only)

`/workspace/jtest/Render.java` (test program), `/workspace/sf/` (downloaded GeneralUser GS and FluidR3),
`/workspace/athenaCL/` (clone).
