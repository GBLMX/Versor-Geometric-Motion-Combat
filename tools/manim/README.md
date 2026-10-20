# versor-java + ManimGL

Fixed, reproducible environment for the Manim scenes. It no longer depends on
`/tmp/manim-venv` or a manually built virtualenv.

## Files

| File | Purpose |
|---|---|
| `pyproject.toml` | Python 3.14, ManimGL pinned to a git commit, plus `av` (missing from ManimGL's metadata). |
| `uv.lock` | Fully resolved dependency graph. |
| `versor_scene.py` | Manim scene driven by `data/versor-rotor.json` from `versor-java`. |
| `bridge/VersorManimDump.java` | Generates the rotor samples using `vsr.E3`. |
| `sta/` | The migrated STA/Cl(1,3) scene and Wolfram exporter. |
| `test.sh` | Builds `versor-java`, regenerates the data, and renders the scene. |

## Setup

```bash
cd tools/manim
uv sync --frozen --no-config
uv run --frozen --no-config manimgl --version
```

`--no-config` makes uv use the official PyPI URLs recorded in `uv.lock`
instead of a machine-local mirror, so the lock is portable.

ManimGL is installed from commit `fafa083a4fb274bba9cabde0b6e2f50ba6da0622`
(the source previously used at `/tmp/manim`). `av` is explicit because ManimGL's
`install_requires` omits it while `manimlib` imports it.

The venv is `tools/manim/.venv`; it is ignored by git.

## Run the integration test

```bash
cd tools/manim
./test.sh
```

The script:

1. runs `lib/versor-java/build.sh` (454 algebra checks);
2. compiles `bridge/VersorManimDump.java` against `lib/versor-java/out`;
3. writes `data/versor-rotor.json` (61 E3 rotor samples);
4. renders `VersorRotor` at low quality into `videos/`.

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
