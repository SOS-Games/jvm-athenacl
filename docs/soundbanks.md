# Soundbanks and kits

The engine plays General MIDI through SoundFont 2 banks. The bank is not stored in git. `soundbank install` downloads it into the cache (`~/.cache/athenacl-j/soundbanks/`, or `--cache`). `gradlew fetchSoundbanks` installs every catalog bank. It is not part of `layerCheck`.

## Kits

| Kit | Bank | Size | License | Role |
| --- | --- | --- | --- | --- |
| `gm-full` | MuseScore_General v0.2 SF2 | 215,614,036 bytes | MIT | Default. `soundbank install musescore-general` |
| `gm-lite` | GeneralUser GS 2.0.3 | 32,319,396 bytes | GeneralUser GS License v2.0 (bundling and commercial use allowed) | Small fallback. `soundbank install generaluser` |
| `gm-plus` | FreePats on top of `gm-full` | see below | CC0-1.0, and CC-BY-3.0 for the optional grand | Opt-in. A theme pins `instruments.kits: [gm-plus]` |

`instruments/gm-full.yaml` extends `gm-lite`. Every MuseScore instrument lists a `gm-lite:` fallback. A render whose MuseScore file is missing, or whose checksum does not match, exits 0 and prints one warning:

```text
warning=KIT_SOURCE_MISSING kit=gm-full source=msg fallback=gm-lite hint="athenacl soundbank install musescore-general"
```

`instruments/gm-plus.yaml` extends `gm-full`. It replaces `piano` with FreePats Upright Piano KW and `nylon` (also named `guitar.nylon`) with the FreePats Spanish classical guitar. Every other name stays on MuseScore, then GeneralUser. Each replacement lists `gm-full:` and then `gm-lite:`. `piano.grand` is the YDP Grand. That source is `optional: true`: a missing file falls through to MuseScore piano and does not add a warning. When the file is installed and a theme resolves `piano.grand`, `credits` prints the CC BY attribution.

When GeneralUser is installed, playback uses it. When no bank is installed, the event log is still written and a WAV uses the JDK emergency bank. The musical hash does not include the soundfont. A render with `--kit gm-plus` and an empty cache still exits 0.

## FreePats sizes

The download is an archive. The cache keeps the extracted SF2. Both hashes are pinned.

| Id | Instrument | Archive | SF2 | License |
| --- | --- | --- | --- | --- |
| `freepats-upright-kw` | `piano` | 28,832,466 bytes, `.7z` | 57,377,848 bytes | CC0-1.0 |
| `freepats-spanish-guitar` | `nylon`, `guitar.nylon` | 9,990,750 bytes, `.7z` | 19,822,546 bytes | CC0-1.0 |
| `freepats-ydp-grand` | `piano.grand` (optional) | 36,737,684 bytes, `.tar.bz2` | 118,398,836 bytes | CC-BY-3.0 |

Extracted `gm-plus` audio is about 196 MB on top of MuseScore (about 206 MB) and GeneralUser (about 31 MB). The YDP grand is most of that. Skip it by not running `soundbank install freepats-ydp-grand`. The two CC0 banks are the piano and the classical guitar.

CC BY credit, also in `licenses/FreePats-YDP-Grand-CC-BY-3.0.txt` and `THIRD_PARTY.md`: Zenph Studios YDP samples, SF2 by Roberto for FreePats.

## Choosing a kit

Precedence:

1. `--kit`
2. `ATHENACL_KIT`
3. `kit.default` in `-Dkit.default`, then `~/.athenaclrc`, then `local.properties`
4. Built-in `gm-full`

`ATHENACL_RC` (or `-Dathenacl.rc`) points at a different rc file. The rc file is `key=value`, same as `local.properties`.

A theme that lists `instruments.kits` keeps that list. `themes/fantasy-dungeon.yaml` pins `gm-full`. To use the overlay, pin `gm-plus` the same way. `theme resolve` prints `kit=` and `source=` (`flag`, `env`, `rc`, `pin`, or `builtin`). A pin beats `ATHENACL_KIT` and `kit.default`. `--kit` beats the pin.

Fantasy and sci-fi presets that do not pin a kit use the built-in default, `gm-full`. Other note-list renders stay on the soundbank path unless you pass `--kit`.

## Install

```text
athenacl soundbank install musescore-general
athenacl soundbank install generaluser
athenacl soundbank install freepats-upright-kw
athenacl soundbank install freepats-spanish-guitar
athenacl soundbank install freepats-ydp-grand
```

`--dry-run` prints `action=download`, the SF2 `size=` and `sha256=`, and for FreePats also `archiveSize=` and `archiveSha256=`. It does not create the cache.

The download URLs and SHA-256 pins live in `SoundbankCatalog`. GeneralUser is fetched from a pinned commit, not from schristiancollins.com. MuseScore_General is fetched from the OSUOSL mirror. FreePats archives come from freepats.zenvoid.org. Install unpacks `.7z`, `.zip`, `.tar.bz2`, `.tar.gz`, and `.tar.xz`, checks the extracted SF2, and rejects SF3 (`SOUNDBANK_SF3_UNSUPPORTED`) before the file is kept.

Each kit names the license file under `licenses/`. Install copies that file next to the bank. `credits` copies the license files a theme's resolved instruments actually use. `gradlew soundbankSmoke` installs the catalog, runs `kit scan` on each FreePats bank, and renders a scale through each. It is not part of `layerCheck`.

## What is not allowed

SF3 is rejected (`SOUNDBANK_SF3_UNSUPPORTED`) before Gervill opens it. Gervill's decoder adds noise on compressed samples. `gm.dls` and conversions of the Roland bank are not used.

Timbres of Heaven, Arachno, Musyng Kite, Crisis GM, and SGM are not shipped. Their licenses forbid redistribution or commercial use. FluidR3_GM is superseded by MuseScore_General. Salamander Grand is CC BY and about 1.27 GB, so it is not in `gm-plus`.

## Adding an SF2 kit

1. Add the bank to `SoundbankCatalog` (id, file name, size, sha256, url, license, license file; archive name, size, and sha256 when the URL is not the SF2) and a license file under `licenses/`.
2. Add `instruments/<id>.yaml` with `sources` (spdx, attribution, redistribute, commercial, file) and `instruments`. Point `fallback` at `gm-full:` or `gm-lite:`. Set `optional: true` on a source whose absence should stay quiet.
3. `soundbank install <id>`, then `kit scan <id>` and `kit validate instruments/<id>.yaml`.

`kit validate` checks the checksum, the license, and that two instruments did not receive the same slot. A missing license is exit 3 (`KIT_LICENSE_DENIED`).
