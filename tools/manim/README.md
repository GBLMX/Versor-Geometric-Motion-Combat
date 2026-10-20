# versor-java + ManimGL

Fixed, reproducible environment for the Manim scenes, plus `versor_ga`: a small
Clifford algebra library written for animation authoring.

## Files

| File | Purpose |
|---|---|
| `pyproject.toml` | Python 3.14, ManimGL pinned to a git commit, `av`, and the local `versor_ga` package. |
| `uv.lock` | Fully resolved dependency graph. |
| `versor_ga/` | E3/PGA3/CGA3 kernel, geometric wrappers, and Manim animations. |
| `ga_demo.py` | Rotor, motor, plane, line, point, and sphere demo. |
| `versor_scene.py` | Scene driven by `data/versor-rotor.json` from `versor-java`. |
| `bridge/VersorManimDump.java` | Generates the rotor samples using `vsr.E3`. |
| `sta/` | The migrated STA/Cl(1,3) scene and Wolfram exporter. |
| `tests/` | `versor_ga` algebra and Manim adapter tests. |
| `test.sh` | Builds `versor-java`, runs tests, regenerates data, and renders. |

## Setup

```bash
cd tools/manim
uv sync --frozen --no-config
uv run --frozen --no-config python -c "import versor_ga"
```

`--no-config` makes uv use the official PyPI URLs recorded in `uv.lock`
instead of a machine-local mirror, so the lock is portable.

ManimGL is installed from commit `fafa083a4fb274bba9cabde0b6e2f50ba6da0622`
(the source previously used at `/tmp/manim`). `av` is explicit because ManimGL's
`install_requires` omits it while `manimlib` imports it.

The venv is `tools/manim/.venv`; it is ignored by git.

## `versor_ga`

The library is deliberately narrow: E3, PGA3, and CGA3; dense coefficients;
no symbolic engine, no general high-dimensional solver. It provides the parts a
Manim scene actually needs:

```python
import numpy as np
from manimlib import *
from versor_ga import E3, PGA, CGA
from versor_ga.animation import RotorAnimation, MotorAnimation

class Demo(ThreeDScene):
    def construct(self):
        # E3 rotor
        arrow = Line3D(ORIGIN, np.array([1.0, 0.0, 0.0]))
        self.play(RotorAnimation(arrow, E3.rotor((0, 0, 1), PI / 2)))

        # PGA point/line/plane + motor
        p = PGA.point(1, 0, 0)
        q = PGA.point(2, 2, 1)
        line = PGA.line(p, q).to_mobject(color=BLUE)
        plane = PGA.plane((0, 0, 1), -2).to_mobject(color=PURPLE)
        self.play(ShowCreation(line), ShowCreation(plane))
        self.play(MotorAnimation(line, PGA.motor(
            PGA.rotor((0, 0, 1), PI / 2), (1, 0, 0),
        )))

        # CGA sphere
        self.play(ShowCreation(CGA.sphere(0, 0, 0, 1).to_mobject(color=TEAL)))
```

Geometry wrappers expose `.mv` for algebra and `.to_mobject()` for Manim:

| Preset | Construction | Mobject |
|---|---|---|
| `E3` | `E3.vector`, `E3.rotor` | rotor applied by `RotorAnimation` |
| `PGA` | `PGA.point`, `PGA.line`, `PGA.plane`, `PGA.rotor`, `PGA.motor` | `Sphere`, `Line3D`, `ParametricSurface` |
| `CGA` | `CGA.point`, `CGA.sphere`, `CGA.plane` | `Sphere`, `ParametricSurface` |

`RotorAnimation` rotates about `about_point`; `MotorAnimation` applies `T * R`
in the PGA convention. `nlerp` interpolates rotors when you need a custom path.

## Tests and demo

```bash
cd tools/manim
uv run --frozen --no-config python -m unittest discover -s tests -v
uv run --frozen --no-config manimgl ga_demo.py VersorGADemo -l -w --video_dir videos
```

`tests/` covers basis products, E3/PGA/CGA round trips, Manim mobject creation,
and the rotor/motor animations. `ga_demo.py` renders `videos/VersorGADemo.mp4`.

## Integration test

```bash
cd tools/manim
./test.sh
```

The script:

1. runs `lib/versor-java/build.sh` (454 algebra checks);
2. compiles `bridge/VersorManimDump.java` against `lib/versor-java/out`;
3. writes `data/versor-rotor.json` (61 E3 rotor samples);
4. runs the `versor_ga` unit tests;
5. renders `VersorRotor` at low quality into `videos/`.

Expected output: `videos/VersorRotor.mp4`.

## STA scene

The Wolfram-side exporter is unchanged:

```bash
wolframscript -file sta/sta.wl
uv run manimgl sta/sta_scene.py STASpacetime -w --video_dir videos
uv run manimgl sta/sta_scene.py STAGraphics -w --video_dir videos
```

`sta/sta.wl` requires Wolfram Engine 15.0 for `CliffordAlgebra`. This scene is
independent of `versor-java`; it was migrated from `/tmp/sta-manim` so the data
and source no longer live in a tmpfs.
