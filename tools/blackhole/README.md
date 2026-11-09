# Black hole with Clifford algebra

A CPU Schwarzschild ray tracer built on `vsr.E3` and `vsr.P3`. It renders a
lensed accretion disk, photon ring, Doppler asymmetry, gravitational redshift,
and a procedural cosmic background to PNG without any graphics dependency.

This is a physically motivated visualization, not a GRMHD or Kerr simulation.
The disk is a geometrically thin, optically thick emitter; it has no vertical
structure, polarization, radiative transfer, or time-dependent MHD.

## Physics

Units are `G = c = 1`, Schwarzschild radius `rs = 1`, and mass `M = rs / 2`.
For a null geodesic, the conserved spatial angular-momentum bivector and the
exact central-force equation are

$$
L = x \wedge v,\qquad h^2 = -L L,\qquad
\frac{d^2 x}{d\lambda^2} = -\frac{3}{2}\frac{h^2 x}{r^5}.
$$

`BlackHole.angularMomentumSq` evaluates `L` and `h²` as E3 geometric products.
The path is integrated with classical RK4. The accretion disk is the PGA3 plane
`pi = e2`, and each equator crossing is computed as the PGA meet
`pi ∧ (P0 ∨ P1)`; `--validate` also checks this intersection analytically.

Disk emission uses the Shakura–Sunyaev profile

$$
p(r) = \left(\frac{r_{\rm in}}{r}\right)^{3/4}
\left(1-\sqrt{\frac{r_{\rm in}}{r}}\right)^{1/4}
$$

to shape both the temperature and the emissivity. For display, the temperature
is mapped to a 1200–10200 K ramp so the outer disk remains visible; that mapping
is an artistic choice, not a full radiative-transfer model. Colour comes from a
Planckian-locus approximation. The emitter velocity is
`β = sqrt(M/r) φ̂`; for a photon direction `rayDir` from camera to disk, the
observer direction is `-rayDir`, so

$$
D = \frac{1}{\gamma(1-\beta\cdot\hat n)},\qquad
T_{\rm obs} = D\sqrt{1-r_s/r}\,T.
$$

The `I ∝ D^4` boost is applied after colour lookup. This is the same convention
used by the renderer; it intentionally avoids the sign error that reverses the
bright and dim sides of the disk.

## Cosmic environment and light distortion

The background is a procedural celestial sphere: a tilted galactic band with
dust, multi-octave emission nebulae, three star layers, and a small spiral
galaxy near the line of sight. It is sampled using the **final** ray direction,
after the geodesic has been integrated, so the band, nebulae, stars, and galaxy
are all gravitationally lensed by the hole.

Near the critical curve the sky is given a mild magnification boost and a thin
blue critical-curve glow. This is a rendering aid for making the lensed arcs
legible, not a separate physical emission process. The `--validate` checks cover
the geodesic and PGA geometry; they do not validate the artistic background
palette.

## Run

```bash
./tools/blackhole/run.sh --out /tmp/black_hole.png
./tools/blackhole/run.sh --width 960 --height 960 --samples 2 --out /tmp/black_hole_2x.png
./tools/blackhole/run.sh --validate
```

`run.sh` compiles the pure `versor-java` sources into a temporary directory and
runs the standalone class. `JAVA_HOME` must point at JDK 25; the script defaults
to `~/.jdks/jbrsdk-25.0.3`.

| Option | Default | Meaning |
|---|---:|---|
| `--width`, `--height` | 640 | output size |
| `--samples` | 1 | per-pixel samples (1–16) |
| `--steps` | 700 | maximum geodesic steps per ray |
| `--fov` | 50 | vertical field of view in degrees |
| `--out` | `tools/blackhole/black_hole.png` | PNG path |
| `--validate` | off | run checks, then render |

The default render uses 700 steps and is intended for a still image. Increase
`--steps` for paths that skim the photon sphere; increase `--samples` for
antialiasing.

## Validation

`--validate` checks:

- `h² = 100` for `x = 10 e3`, `v = e1`;
- a PGA plane/line meet at a known point;
- weak-field deflection for impact parameter 50 against `2 rs / b`;
- capture at `b = 2` and escape at `b = 3` (critical value `3√3/2 ≈ 2.598`);
- conservation of `h²` over a flyby.

Example output:

```text
  [ok] weak-field deflection within 5% (got=0.041222 expected=0.040000 rel=3.056%)
  [ok] b=2.0 captured
  [ok] b=3.0 escapes
  [ok] h^2 conserved over a flyby (relative error=1.193e-13)
```
