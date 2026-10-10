# 06 — jvm-mw: agent CLI and debug-tool patterns

*Studied Oct 9, 2026 from a clone of [SOS-Games/jvm-mw](https://github.com/SOS-Games/jvm-mw) at commit
`9485d74` (2026-09-22). Paths are relative to that repo. Purpose: give athenaCL-J (doc 05) the same
conventions the user already uses.*

## What jvm-mw is
An unofficial GPL-3.0 Java port of OpenMW 0.51.0 (a Morrowind engine), using libGDX for window/input/Scene2D
with its own OpenGL renderer (`README.md`, `AGENTS.md`). Package `io.github.jvmmw`. About 21k lines of Java.

## Build and libraries
| Item | jvm-mw |
|---|---|
| Gradle | 9.3.1 wrapper, **Groovy DSL** (`build.gradle`, `settings.gradle`), `foojay-resolver-convention` 1.0.0 |
| Modules | `core` (`java-library`: engine + debug tools) and `lwjgl3` (desktop launcher, `application` plugin, fat jar with `Main-Class` and `Enable-Native-Access` manifest attributes) |
| Java | **21** (`java.sourceCompatibility = 21`, `options.release = 21`, setup says JDK 21) |
| Versions | `gradle.properties`: `gdxVersion=1.14.0`, `projectVersion=0.1.0`; daemon off, `-Xmx1G` |
| Libraries | libGDX `gdx`, `gdx-bullet`, LWJGL3 backend; `recast4j` 1.5.7 (recast/detour), `sqlite-jdbc`, `lz4-java`. **No CLI, JSON, logging or test libraries.** |
| Tests | No `src/test`. Logic self-checks (`AiPackage.checkFinishFront()`, `checkWanderHours()`, …) run at the start of every debug-CLI call and throw `IllegalStateException` on failure. |
| Logging | `Gdx.app.log(tag, msg)` in the viewer. Perf lines are prefixed `perf` (`PerfTrace`). |

## Debug CLI (headless)
- **One class**, `core/src/main/java/io/github/jvmmw/debug/DebugCli.java` (585 lines). It's a hand-rolled
  `switch` on `args[0]`, with no CLI library and no subcommand flags. It's run through a Gradle `JavaExec` task
  (`core/build.gradle`):
  `gradlew.bat :core:debugCli --args="cell Addamasartus"` (group `jvm-mw`, `workingDir` = repo root so
  `local.properties` resolves).
- **Commands are short nouns** taking positional args: `help`, `nif <path>`, `cell <name>`, `interiors [filter]`, `spawn`,
  `npc`, `crea`, `actor`, `levc`, `kf`, `exterior <gx> <gy>`, `pgrd`, `navdb`, `navpath`. Lookups accept an id or a
  case-insensitive substring of id or name.
- **Output is plain text on stdout as grep-stable `key=value` lines**, one record per line, prefixed by a record
  kind: `cell=… refs=… fogDensity=… pgrd=N e=M`, `door <id> tes=(x,y,z) dest=… dodt=…`, closing summary
  counters (`doors=3 kit=40 …`). Long lists are capped with `... truncated, N more`. Headless commands that
  render images write PNGs under `build/` (`navdb` → `build/navdb-*.png`, `*-after.png`).
- **`help`** (or no args / `-h`) prints copy-pasteable example invocations, then one or two lines per command.
- **Exit codes:** unknown command → message on **stderr**, help on stdout, **exit 2**. Usage errors and "not found"
  throw `IllegalArgumentException("Usage: …")` / `IllegalStateException("No NPC_ matching …")`. These are uncaught,
  so the JVM exits **1** with a stack trace. Success is 0. There are no other codes.
- **No JSON, no server, no batch mode, no MCP.**

## In-viewer debug tools
- **F3 / HUD "Dump"** writes `build/debug-snapshot.txt` (overwritten) and copies it to the clipboard. The snapshot is
  `key=value` lines: camera/fog/grid, perf (`fps= frameMs= ms.update= draws= …`), physics, nav and the debug knobs.
  It's the hand-off format from a live session to an agent (`docs/phase30-perf-debug.md`).
- **F4** toggles a compact top-right overlay (fps, frame ms, draws, fattest section). Its documented rule: treat numbers
  as settled at `n=60` samples. **F5–F7** toggle debug geometry (pathgrid, navmesh carpet, colliders).
- **`FrameProfiler`**: named CPU sections with a 60-frame rolling window. **`PerfTrace`**: nested named spans, keeps slow
  spans over 50 ms, logs `perf …` for anything over 100 ms, and sums load phases.
- **`DebugVars`**: tuning knobs as `public static final` fields, overridable without a rebuild by
  `jvmmw.debug.NAME` in gitignored `local.properties`, `-Djvmmw.debug.NAME`, or env `JVMMW_DEBUG_NAME`.
  They're printed in every dump. AGENTS.md tells agents not to revert defaults the user changed.
- Config follows the same triple everywhere: `local.properties` key / `-D` property / `JVMMW_*` env var (e.g.
  `jvmmw.data`, `jvmmw.navmesh`).

## Docs for agents
- **`AGENTS.md`** (~15 KB): project rules (pins, what not to commit, commit-per-working-phase, "other-LLM claims"
  verification template), a **"Debug CLI (headless)" section with example invocations and a `Command | Use when`
  table**, the in-viewer key bindings and dump keys, domain gotchas, and a class-comment style rule.
- **Phase specs** `docs/phaseN-*.md` (57 phases): Goal / Why this slice / In scope / Out of scope / Test cell, including
  the exact `key=value` lines a phase must emit. `docs/next-topics.md` holds small slices and `docs/big-topics.md`
  large holes. **`docs/NAME_MAP.md`** maps Java names to the original C++ (kept out of class comments).
- `docs/setup.md`: machine requirements, data paths, run commands.

## Takeaways for athenaCL-J
Adopt: text `key=value` output as the default, nouns-as-commands with positional args, `help` with
copy-pasteable examples, a Gradle `JavaExec` debug task run from the repo root, exit 2 for unknown commands
and 1 for unexpected errors, F3 snapshot file + F4 overlay in libGDX, `PerfTrace`-style slow-span logging, the
`DebugVars` knob triple, AGENTS.md with a Use-when table, phase specs, and a NAME_MAP (Python → Java).
Differ (needed by doc 05): JUnit + golden conformance tests (athenaCL parity needs them), optional `--json`
for schema-validated output, more exit codes for validation/check failures, a CLI library for ~110 ported
athenaCL commands, and Java 17 minimum vs jvm-mw's 21 (open question).
