# Soundbanks and kits

The engine plays General MIDI through SoundFont 2 banks. The bank is not stored in git. `soundbank install` downloads it into the cache (`~/.cache/athenacl-j/soundbanks/`, or `--cache`).

## Kits

| Kit | Bank | Size | License | Role |
| --- | --- | --- | --- | --- |
| `gm-full` | MuseScore_General v0.2 SF2 | 215,614,036 bytes | MIT | Default. `soundbank install musescore-general` |
| `gm-lite` | GeneralUser GS 2.0.3 | 32,319,396 bytes | GeneralUser GS License v2.0 (bundling and commercial use allowed) | Small fallback. `soundbank install generaluser` |

`instruments/gm-full.yaml` extends `gm-lite`. Every MuseScore instrument lists a `gm-lite:` fallback. A render whose MuseScore file is missing, or whose checksum does not match, exits 0 and prints one warning:

```text
warning=KIT_SOURCE_MISSING kit=gm-full source=msg fallback=gm-lite hint="athenacl soundbank install musescore-general"
```

When GeneralUser is installed, playback uses it. When neither bank is installed, the event log is still written and a WAV uses the JDK emergency bank. The musical hash does not include the soundfont.

## Choosing a kit

Precedence:

1. `--kit`
2. `ATHENACL_KIT`
3. `kit.default` in `-Dkit.default`, then `~/.athenaclrc`, then `local.properties`
4. Built-in `gm-full`

`ATHENACL_RC` (or `-Dathenacl.rc`) points at a different rc file. The rc file is `key=value`, same as `local.properties`.

A theme that lists `instruments.kits` keeps that list. `themes/fantasy-dungeon.yaml` pins `gm-full`. `theme resolve` prints `kit=` and `source=` (`flag`, `env`, `rc`, `pin`, or `builtin`). A pin beats `ATHENACL_KIT` and `kit.default`. `--kit` beats the pin.

Fantasy and sci-fi presets that do not pin a kit use this default. Other note-list renders stay on the soundbank path unless you pass `--kit`.

## Install

```text
athenacl soundbank install musescore-general
athenacl soundbank install generaluser
```

The download URLs and SHA-256 pins live in `SoundbankCatalog`. GeneralUser is fetched from a pinned commit, not from schristiancollins.com. MuseScore_General is fetched from the OSUOSL mirror. Each kit names the license file under `licenses/`. `credits` copies the license files a theme actually uses.

## What is not allowed

SF3 is rejected (`SOUNDBANK_SF3_UNSUPPORTED`) before Gervill opens it. Gervill's decoder adds noise on compressed samples. `gm.dls` and conversions of the Roland bank are not used.

Timbres of Heaven, Arachno, Musyng Kite, Crisis GM, and SGM are not shipped. Their licenses forbid redistribution or commercial use. FluidR3_GM is superseded by MuseScore_General. A specialty overlay (FreePats piano and guitar) is not part of this tree.

## Adding an SF2 kit

1. Add the bank to `SoundbankCatalog` (id, file name, size, sha256, url, license) and a license file under `licenses/`.
2. Add `instruments/<id>.yaml` with `sources` (spdx, attribution, redistribute, commercial, file) and `instruments`. Point `fallback` at `gm-full:` or `gm-lite:`.
3. `soundbank install <id>`, then `kit scan <id>` and `kit validate instruments/<id>.yaml`.

`kit validate` checks the checksum, the license, and that two instruments did not receive the same slot. A missing license is exit 3 (`KIT_LICENSE_DENIED`).
