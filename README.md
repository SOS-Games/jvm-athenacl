# jvm-athenacl

A Java rewrite of [athenaCL](https://github.com/ales-tsurko/athenaCL) as an adaptive game-music engine for libGDX desktop. Package `io.github.sosgames.athenacl`. License [GPL-3.0-or-later](LICENSE).

The tree is early. Layers L0 through L5 and L7 are in: a headless CLI, note-list render to MIDI and WAV, a piano roll, the GeneralUser GS soundbank, an EBU R128 loudness meter, scale, chord, and progression commands, rhythm, an offline adaptive director, the fantasy preset with a loudness contour, and motifs that a lead can remember and bring back. In-game playback comes later. The layer plan is [docs/research/05-project-plan.md](docs/research/05-project-plan.md). What is done is [docs/progress.md](docs/progress.md).

## Requirements

JDK 21. The Gradle wrapper is in the repo.

## Run

From the repo root:

```bat
gradlew.bat :athenacl-cli:agentCli --args="help"
```

On macOS and Linux the entry is `./gradlew`.

```bat
gradlew.bat :athenacl-cli:agentCli --args="render samples/notes/c-major-scale.yaml --out build/l0 --formats mid,wav,events,txt"
gradlew.bat :athenacl-cli:agentCli --args="play samples/notes/c-major-scale.yaml"
gradlew.bat :athenacl-cli:agentCli --args="describe-music build/l0/events.jsonl --roll"
gradlew.bat :athenacl-cli:agentCli --args="soundbank install generaluser"
gradlew.bat :athenacl-cli:agentCli --args="analyze build/l0"
gradlew.bat :athenacl-cli:agentCli --args="describe scale D-dorian"
```

Text output is `key=value`. `--json` prints one result document. Logs go to stderr. `soundbank install generaluser` downloads GeneralUser GS into the cache (`~/.cache/athenacl-j/soundbanks`, or `--cache`). The `.sf2` is not in git.

```bat
gradlew.bat test
gradlew.bat layerCheck
```

After `gradlew.bat :athenacl-cli:installDist`, the launcher is `athenacl-cli\build\install\athenacl\bin\athenacl.bat`.

Coding-agent notes: [AGENTS.md](AGENTS.md).
