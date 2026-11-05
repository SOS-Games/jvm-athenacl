# Changelog

## Unreleased

- The default instrument kit is `gm-full` (MuseScore_General). `gm-lite` (GeneralUser GS) remains the fallback. A render whose MuseScore bank is missing or fails its checksum exits 0, prints `warning=KIT_SOURCE_MISSING`, and uses GeneralUser when that bank is present.
- `--kit`, `ATHENACL_KIT`, and `kit.default` (`-Dkit.default`, `~/.athenaclrc`, or `local.properties`) choose the kit. A theme that lists `instruments.kits` keeps that list. See `docs/soundbanks.md`.
- The six game themes and the genre presets pin `gm-full`. GeneralUser stays the per-instrument fallback.
