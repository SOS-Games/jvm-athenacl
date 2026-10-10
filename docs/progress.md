# Progress

Short log of layers that are in the tree. The layer plan is `docs/research/05-project-plan.md`. Commands and check-file rules live in `AGENTS.md`.

## L0a — CLI skeleton

`gradlew layerCheck -Player=L0a` passes (11 checks).

`athenacl` runs headless from `gradlew :athenacl-cli:agentCli`. Commands: `help`, `version`, `schema`. Text is `key=value` (help is a page of examples). `--json` prints one Result document. Exit codes are 0–7. Logs stay on stderr. `--dry-run` plans a write and does not touch the disk. A real write uses a temp file and a rename; identical bytes leave the file time alone.

## L0b — Events, render, describe

`gradlew layerCheck -Player=L0b` passes (6 checks).

A note list such as `samples/notes/c-major-scale.yaml` renders to `score.mid`, `score.wav`, `events.jsonl`, and `score.txt`. The hash is the event log. A second render of the same notes is `changed=false` with the same hash. `describe-music --roll` prints a bar summary and a piano roll. `debug determinism` compares that hash across runs. `debug inspect --where pitch=E4` prints the one matching note. A missing note list exits 4.

## L1 — Soundbanks and loudness

`gradlew layerCheck` passes (L0a, L0b, and L1, 25 checks).

`soundbank install generaluser` downloads GeneralUser GS (32,319,396 bytes, pinned SHA-256) into a cache. A dry run writes nothing. Installing again reports `status=present changed=false`. `describe instruments` reports 287 instruments and 13 drum kits. `analyze` prints integrated loudness and true peak for `score.wav`. A missing soundbank file still exits 0, with `soundbank=emergency warning=SOUNDBANK_FALLBACK`.
