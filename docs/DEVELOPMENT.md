# Development

## Prerequisites

- JDK 25 (`JAVA_HOME` must point at it).
- Git, with submodule support.
- Minecraft 26.3 / Fabric Loader 0.19.5 toolchain (Gradle/Loom resolves it).
- Optional for visual testing: a local display; the dev client disables XVFB.

## Checkout

```bash
git clone --recursive https://github.com/GBLMX/Versor-Geometric-Motion-Combat.git
cd Versor-Geometric-Motion-Combat
```

If the math core is missing:

```bash
git submodule update --init --recursive
```

Update it deliberately:

```bash
git -C lib/versor-java fetch
git -C lib/versor-java checkout origin/main
git add lib/versor-java
```

Commit the submodule pointer when changing it; do not mix unrelated submodule
updates into a feature PR.

## Build

```bash
./gradlew build
```

Output:

```text
build/libs/versor-geometric-motion-combat-0.1.0.jar
```

## Test

```bash
./gradlew gaTest        # standalone geometric algebra checks
./gradlew combatTest    # arc, guard, field, tornado, rules
./gradlew weTest        # selection, shapes, clipboard, undo
./gradlew build         # compile + package
```

All three checks are plain `main` classes, not JUnit tests. They exit non-zero on
the first failure. `failOnNoDiscoveredTests = false` keeps Gradle's `test` task
from failing when no JUnit tests are present.

## Run

```bash
./gradlew runClient
./gradlew runServer
```

The dev client sets `useXvfb = false`; `runServer` forwards stdin so commands can
be exercised from a script. Do not use the client entrypoint in headless CI.

## Math core

The submodule can be built independently:

```bash
cd lib/versor-java
./build.sh
```

Benchmarks:

```bash
./bench.sh stress        # dense/matrix/high-dimensional checks
./bench.sh sparse        # COO product paths
./bench.sh batchscale    # batch allocation, reuse, thread scaling
./bench.sh jmh           # JMH harness
```

Read [`PERFORMANCE.md`](../lib/versor-java/PERFORMANCE.md) before changing a
kernel threshold. The report records failed optimizations; do not redo them
without a new measurement.

## Manim visualization

The ManimGL environment is pinned by `tools/manim/pyproject.toml` and
`tools/manim/uv.lock` (Python 3.14, ManimGL git commit, `av`). It no longer
depends on `/tmp`.

`versor_ga` is a small E3/PGA/CGA library for animation authoring:
multivectors with `.mv`, geometric wrappers with `.to_mobject()`, and
`RotorAnimation` / `MotorAnimation`. `versor_ga.KDE` is the palette sampled from
the active KDE CachyOS Nord scheme and fish/zsh syntax colors; preset mobjects
use it by default.

The flat Wolfram -> JSON bridge is `versor_ga/bridge.py`: records carry
`id`, `kind`, `style`, and either `points` or `values`. `bridge_data/generate_data.wls`
exports `bridge_data/data/demo.json`; `bridge_scene.py` consumes the 2D curves
and `bridge_showcase.py` renders bars, a table, a 3D surface, a space curve, and
scatter points without invoking Wolfram during rendering. Rebuild the demo data
from `tools/manim`:

```bash
wolframscript -file bridge_data/generate_data.wls
```

```bash
cd tools/manim
uv sync --frozen --no-config
uv run --frozen --no-config python -m unittest discover -s tests -v
uv run --frozen --no-config manimgl ga_demo.py VersorGADemo -l -w --video_dir videos
./test.sh
```

`test.sh` runs the 454 algebra checks, exports `data/versor-rotor.json` using
`vsr.E3`, regenerates and validates the flat Wolfram bridge data when
`wolframscript` is available, runs the `versor_ga` and bridge tests, and renders
`videos/VersorRotor.mp4` plus `videos/JsonBridgeScene.mp4`. The STA scene is
under `tools/manim/sta/` and requires Wolfram Engine 15.0. See
[`tools/manim/README.md`](../tools/manim/README.md).

## Cross-library benchmark

```bash
cd tools/bench-ga
./run.sh
```

The harness compares `versor-java` with `clifford` and `kingdon` on Cl(3,0) and
Cl(4,1) geometric products, rotor sandwiches, and a 1000-element batch. It
reports ns per element into `results.csv`. See
[`tools/bench-ga/README.md`](../tools/bench-ga/README.md) for methodology and
caveats.

## Code map

| Path | Responsibility |
|---|---|
| `src/main/java/.../CombatManager.java` | Swing queue, damage, knockback, payload. |
| `src/main/java/.../RollManager.java` | Roll impulse, invulnerability, cooldown. |
| `src/main/java/.../FluidDrag.java` | Quadratic water drag and gravity. |
| `src/main/java/.../combat/` | `ArcSweep`, `GuardPlane`, `CombatRules`. |
| `src/main/java/.../field/` | Stream and tornado baking, field broadcasts. |
| `src/main/java/.../wedit/` | Selection, shapes, clipboard, history, commands. |
| `src/main/java/.../net/` | S2C payload records and registration. |
| `src/client/java/.../` | Render, animation, camera, config, particles. |
| `lib/versor-java/src/main/java/vsr/` | Pure GA core. |
| `lib/versor-java/src/test/java/vsr/GaTest.java` | Core checks. |

## Rendering notes

The project targets the 26.3 `com.mojang.renderpearl.*` API. The render path uses
extraction/drawing and `StagedVertexBuffer`. Do not call `upload()` inside an
existing render pass; the current event is `LevelRenderEvents.END_MAIN`.
`AFTER_TRANSLUCENT_TERRAIN` is inside the main pass and produces
`"Close the existing render pass before performing additional commands"`.

## CI

`.github/workflows/build.yml` checks out submodules, installs JDK 25, runs the
Gradle build plus the three standalone suites, and uploads the mod jar. CI does
not run the client.

## Pull request checklist

1. Keep the change scoped to one behavior or document.
2. Add or adjust the narrowest test that would catch a regression.
3. Run `./gradlew gaTest combatTest weTest build`.
4. Update `docs/COMMANDS.md` / `docs/CONFIGURATION.md` when user-facing syntax or
   keys change.
5. Update `CHANGELOG.md` for user-visible changes.
6. Do not commit `run/`, `build/`, `logs/`, `out-*`, or generated benchmark files.
