# Changelog

## Unreleased

- Genre tempo is drawn from each genre's bpm range (same seed, same bpm). State files scale that draw with `relative` instead of pinning a bpm. A theme omits `bpm` so the opening state follows the draw; `pin: {tempo.bpm:}` still wins. `themes/fantasy-combat.yaml` keeps its 144 pin.
- `gm-plus` is an opt-in kit. It extends `gm-full` and replaces `piano` and `nylon` (also `guitar.nylon`) with the CC0 FreePats upright piano and Spanish classical guitar. `piano.grand` is the optional YDP Grand (CC BY 3.0). A missing FreePats file falls through to MuseScore, then GeneralUser. The default kit stays `gm-full`. See `docs/soundbanks.md` and `THIRD_PARTY.md`.
- The default instrument kit is `gm-full` (MuseScore_General). `gm-lite` (GeneralUser GS) remains the fallback. A render whose MuseScore bank is missing or fails its checksum exits 0, prints `warning=KIT_SOURCE_MISSING`, and uses GeneralUser when that bank is present.
- `--kit`, `ATHENACL_KIT`, and `kit.default` (`-Dkit.default`, `~/.athenaclrc`, or `local.properties`) choose the kit. A theme that lists `instruments.kits` keeps that list. See `docs/soundbanks.md`.
- The six game themes and the genre presets pin `gm-full`. GeneralUser stays the per-instrument fallback.
