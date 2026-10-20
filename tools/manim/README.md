# versor-java + ManimGL

Fixed, reproducible environment for the Manim scenes, plus `versor_ga`: a small
Clifford algebra library written for animation authoring.

## Files

| File | Purpose |
|---|---|
| `pyproject.toml` | Python 3.14, ManimGL pinned to a git commit, `av`, and the local `versor_ga` package. |
| `uv.lock` | Fully resolved dependency graph. |
| `versor_ga/` | E3/PGA3/CGA3 kernel, geometric wrappers, KDE palette, flat JSON bridge, and Manim animations. |
| `ga_demo.py` | Rotor, motor, plane, line, point, and sphere demo. |
| `versor_scene.py` | Scene driven by `data/versor-rotor.json` from `versor-java`. |
| `bridge_data/` | Wolfram exporter and checked-in `data/demo.json` following the flat record contract. |
| `bridge_scene.py` | ManimGL renderer for the flat JSON records. |
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
from versor_ga import CGA, E3, KDE, PGA
from versor_ga.animation import RotorAnimation, MotorAnimation

class Demo(ThreeDScene):
    def construct(self):
        self.camera.background_color = KDE.background

        # E3 rotor
        arrow = Line3D(ORIGIN, np.array([1.0, 0.0, 0.0]), color=KDE.vector)
        self.play(RotorAnimation(arrow, E3.rotor((0, 0, 1), PI / 2)))

        # PGA point/line/plane + motor
        p = PGA.point(1, 0, 0)
        q = PGA.point(2, 2, 1)
        line = PGA.line(p, q).to_mobject(color=KDE.line)
        plane = PGA.plane((0, 0, 1), -2).to_mobject(color=KDE.plane)
        self.play(ShowCreation(line), ShowCreation(plane))
        self.play(MotorAnimation(line, PGA.motor(
            PGA.rotor((0, 0, 1), PI / 2), (1, 0, 0),
        )))

        # CGA sphere
        self.play(ShowCreation(CGA.sphere(0, 0, 0, 1).to_mobject(color=KDE.sphere)))
```

Geometry wrappers keep the data a scene needs: `Point`, `Plane`, and
`CGASphere` expose `.mv` for algebra; `Line` keeps its endpoints for `Line3D`.
All of them provide `.to_mobject()`.

| Preset | Construction | Mobject |
|---|---|---|
| `E3` | `E3.vector`, `E3.rotor` | rotor applied by `RotorAnimation` |
| `PGA` | `PGA.point`, `PGA.line`, `PGA.plane`, `PGA.rotor`, `PGA.motor` | `Sphere`, `Line3D`, `ParametricSurface` |
| `CGA` | `CGA.point`, `CGA.sphere`, `CGA.plane` | `Sphere`, `ParametricSurface` |

`RotorAnimation` rotates about `about_point`; `MotorAnimation` applies `T * R`
in the PGA convention. `nlerp` interpolates rotors when you need a custom path.

## Palette

`from versor_ga import KDE` exposes the palette sampled from the active KDE
`CachyOSNordLightly` scheme and the fish/zsh syntax colors. Preset mobjects use
it by default, and `KDE` is a frozen dataclass so a scene can override roles
without touching the library.

| Role | Hex | Source |
|---|---|---|
| `background` | `#161925` | KDE `[Colors:View] BackgroundNormal`, kitty background |
| `text` | `#47ADDA` | fish `fish_color_normal`, KDE View foreground |
| `command` | `#66C2F2` | fish `fish_color_command`, zsh `command` |
| `keyword` | `#FF6A00` | fish/zsh keyword, KDE `ForegroundNeutral` |
| `string` | `#DFFC00` | fish/zsh quoted argument (lime) |
| `number` | `#00C1E4` | fish `fish_color_valid_path`, zsh numeric constant |
| `operator` | `#7CB7FF` | fish/zsh operators and redirections |
| `comment` | `#666A73` | fish/zsh comments, KDE inactive text |
| `error` | `#FF5370` | fish `fish_color_error` |
| `selection` / `ink` | `#47ADD6` / `#0A0B0C` | KDE selection background / foreground |
| `vector` / `point` / `line` / `plane` / `sphere` | `#FF6A00` / `#DFFC00` / `#00A8FF` / `#B30DBF` / `#00C1E4` | scene roles |

The full ANSI 0-15 set and KDE accents are in
[`versor_ga/palette.py`](versor_ga/palette.py).

## Flat JSON bridge

Wolfram is the math engine; ManimGL is the renderer. The bridge contract is a
JSON **list of flat records**, not one deeply nested object:

```json
[
  {
    "id": "sin_curve",
    "kind": "sampled_polyline",
    "style": {"color": "#FF6A00", "stroke_width": 5, "smooth": true},
    "points": [[-5.0, -0.9589], [-4.98, -0.9412]]
  }
]
```

Supported `kind` values:

| Kind | Payload | Manim result |
|---|---|---|
| `sampled_polyline` | `points: [[x, y], ...]` or `[[x, y, z], ...]` | `VMobject`, smooth by default |
| `parametric` | `points: [[t, x, y], ...]` or `[[t, x, y, z], ...]` | `VMobject`; `t` is dropped |
| `scalar_series` | `values: [v0, v1, ...]` | No mobject; `series_value(record, t)` for a `ValueTracker` |

`versor_ga.bridge` validates ids, kinds, dimensions, and numeric payloads.
`build_mobject` / `build_mobjects` convert with `axes.c2p`, so the axes ranges
must cover the Wolfram sampling domain. `style` only controls color, stroke
width, and `smooth`; the palette default is `KDE.text`.

Generate the checked-in demo from `tools/manim` (the script writes a relative
path because Wolfram's CJK path handling is unreliable on this machine):

```bash
cd tools/manim
wolframscript -file bridge_data/generate_data.wls
uv run --frozen --no-config manimgl bridge_scene.py JsonBridgeScene -l -w --video_dir videos
```

The Wolfram side exports `Normal[records]` to `bridge_data/data/demo.json`.
`bridge_scene.py` reads only that file; it never invokes Wolfram during render.

## Tests and demo

```bash
cd tools/manim
uv run --frozen --no-config python -m unittest discover -s tests -v
uv run --frozen --no-config manimgl ga_demo.py VersorGADemo -l -w --video_dir videos
```

`tests/` covers basis products, E3/PGA/CGA round trips, Manim mobject creation,
the rotor/motor animations, and the flat JSON bridge. `ga_demo.py` renders
`videos/VersorGADemo.mp4`; `bridge_scene.py` renders the checked-in flat data.

## Integration test

```bash
cd tools/manim
./test.sh
```

The script:

1. runs `lib/versor-java/build.sh` (454 algebra checks);
2. compiles `bridge/VersorManimDump.java` against `lib/versor-java/out`;
3. writes `data/versor-rotor.json` (61 E3 rotor samples);
4. runs the `versor_ga` and bridge unit tests;
5. renders `VersorRotor` at low quality into `videos/`;
6. renders `JsonBridgeScene` from the checked-in flat data.

Expected outputs: `videos/VersorRotor.mp4` and `videos/JsonBridgeScene.mp4`.

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
