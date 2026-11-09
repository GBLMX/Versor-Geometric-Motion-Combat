"""Animated Schwarzschild black hole, authored with ManimGL and versor_ga.

The palette follows the bionickernel.com works pages: near-black navy,
cyan linework, warm orange emission and a restrained grid.  The disk
particles are rotated with an E3 rotor around the disk normal; the companion
Java tool (`tools/blackhole/BlackHole.java`) is the ray-traced still renderer.

Render:
    uv run --frozen --no-config manimgl blackhole_scene.py BlackHoleScene \
        -l -w --video_dir videos
"""

from __future__ import annotations

import numpy as np
from manimlib import *

from versor_ga import E3


class Works:
    """Palette sampled from https://bionickernel.com/works/."""

    bg = "#161925"
    grid = "#242a3a"
    text = "#e8edf7"
    dim = "#81a1c1"
    cyan = "#66c2f2"
    blue = "#47add6"
    orange = "#ff8a4c"
    peach = "#ffd2b8"
    shadow = "#0a0b0c"


# Scene-space geometry.  The shadow radius is one unit; the photon ring and
# disk radii are deliberately exaggerated for legibility.
DISK_TILT = 0.30
SHADOW_R = 1.0
PHOTON_R = 1.20
DISK_INNER = 1.58
DISK_OUTER = 4.05
N_PARTICLES = 110
CENTER = np.array([-0.55, -0.12, 0.0])


def disk_point(radius: float, angle: float) -> np.ndarray:
    """Project a point on the equatorial disk to Manim's XY plane."""
    rotor = E3.rotor((0.0, 1.0, 0.0), angle)
    p = E3.rotate(np.array([radius, 0.0, 0.0]), rotor)
    return CENTER + np.array([p[0], p[2] * DISK_TILT, 0.0])


def ring_color(t: float):
    """Cyan-hot inner rings through peach to the cool orange outer disk."""
    t = float(np.clip(t, 0.0, 1.0))
    if t < 0.55:
        return interpolate_color(Works.cyan, Works.peach, t / 0.55)
    return interpolate_color(Works.peach, Works.orange, (t - 0.55) / 0.45)


class BlackHoleScene(Scene):
    def construct(self):
        self.camera.background_color = Works.bg
        rng = np.random.default_rng(7)

        # ------------------------------------------------------------------
        # Grid and star field.  The grid matches the thin cyan-tinted rules on
        # the reference works pages; stars sit above it.
        # ------------------------------------------------------------------
        grid = VGroup()
        for x in np.arange(-7.2, 7.2 + 0.35, 0.35):
            grid.add(Line([x, -4.1, 0.0], [x, 4.1, 0.0], stroke_color=Works.grid,
                          stroke_width=0.8, stroke_opacity=0.55))
        for y in np.arange(-4.1, 4.1 + 0.35, 0.35):
            grid.add(Line([-7.2, y, 0.0], [7.2, y, 0.0], stroke_color=Works.grid,
                          stroke_width=0.8, stroke_opacity=0.55))

        stars = VGroup()
        for _ in range(230):
            x = rng.uniform(-7.0, 7.0)
            y = rng.uniform(-4.0, 4.0)
            if np.hypot(x - CENTER[0], y - CENTER[1]) < 2.6:
                continue
            radius = rng.choice([0.006, 0.009, 0.013, 0.018])
            color = interpolate_color(Works.text, Works.cyan, rng.random())
            stars.add(
                Dot(radius=float(radius), color=color, fill_opacity=rng.uniform(0.35, 0.95))
                .move_to([x, y, 0.0])
            )

        # Low-opacity gas glow, flattened into an ellipse.
        gas = (
            Annulus(
                inner_radius=DISK_INNER,
                outer_radius=DISK_OUTER,
                fill_color=Works.orange,
                fill_opacity=0.10,
                stroke_width=0,
            )
            .stretch(DISK_TILT, 1)
            .move_to(CENTER)
        )
        gas_outer = (
            Annulus(
                inner_radius=DISK_OUTER * 0.86,
                outer_radius=DISK_OUTER * 1.22,
                fill_color=Works.blue,
                fill_opacity=0.05,
                stroke_width=0,
            )
            .stretch(DISK_TILT, 1)
            .move_to(CENTER)
        )

        # Concentric emission rings.
        rings = VGroup()
        for i in range(14):
            u = i / 13.0
            radius = DISK_INNER + (DISK_OUTER - DISK_INNER) * u
            width = 1.6 + 2.6 * (1.0 - u)
            rings.add(
                ParametricCurve(
                    lambda t, r=radius: CENTER + np.array([r * np.cos(t), DISK_TILT * r * np.sin(t), 0.0]),
                    t_range=(0.0, TAU, 0.02),
                    stroke_color=ring_color(u),
                    stroke_width=width,
                    stroke_opacity=0.72 - 0.35 * u,
                )
            )

        # ------------------------------------------------------------------
        # Shadow, photon ring and lensed secondary arcs.
        # ------------------------------------------------------------------
        shadow = Circle(
            radius=SHADOW_R,
            fill_color=Works.shadow,
            fill_opacity=1.0,
            stroke_width=0,
        ).move_to(CENTER)
        shadow_glow = Circle(
            radius=SHADOW_R * 1.03,
            fill_color=Works.shadow,
            fill_opacity=0.75,
            stroke_width=0,
        ).move_to(CENTER)

        photon_ring = VGroup()
        for radius, width, opacity in (
            (PHOTON_R, 4.0, 0.95),
            (PHOTON_R * 1.025, 7.0, 0.16),
            (PHOTON_R * 0.985, 2.0, 0.55),
        ):
            photon_ring.add(
                Circle(
                    radius=radius,
                    stroke_color=Works.cyan,
                    stroke_width=width,
                    stroke_opacity=opacity,
                    fill_opacity=0.0,
                ).move_to(CENTER)
            )

        def lens_arc(radius: float, start: float, angle: float, color, width: float, opacity: float):
            return ParametricCurve(
                lambda t: CENTER
                + np.array([radius * np.cos(t), 0.88 * radius * np.sin(t), 0.0]),
                t_range=(start, start + angle, 0.01),
                stroke_color=color,
                stroke_width=width,
                stroke_opacity=opacity,
            )

        upper_arc = lens_arc(PHOTON_R * 1.28, 0.18 * PI, 0.64 * PI, Works.orange, 4.0, 0.9)
        lower_arc = lens_arc(PHOTON_R * 1.12, 1.16 * PI, 0.62 * PI, Works.blue, 3.0, 0.55)

        # ------------------------------------------------------------------
        # Particles.  The angular speed follows a Keplerian r^(-3/2) profile;
        # the actual 3D rotation is an E3 rotor around the disk normal.
        # ------------------------------------------------------------------
        radii = rng.uniform(DISK_INNER * 1.06, DISK_OUTER * 0.96, N_PARTICLES)
        angles = rng.uniform(0.0, TAU, N_PARTICLES)
        jitter = rng.normal(0.0, 0.018, N_PARTICLES)
        particle_data = list(zip(radii, angles, jitter))
        clock = ValueTracker(0.0)

        def particle_layer():
            now = clock.get_value()
            group = VGroup()
            for radius, angle0, jit in particle_data:
                # Differential rotation: inner particles complete more orbits.
                omega = 0.42 * (DISK_INNER / radius) ** 1.5
                phase = angle0 + omega * now
                p = disk_point(radius + jit, phase)
                u = (radius - DISK_INNER) / (DISK_OUTER - DISK_INNER)
                base = interpolate_color(Works.peach, Works.orange, float(np.clip(u, 0.0, 1.0)))
                # Relativistic beaming: the approaching side is brighter.
                beam = 0.5 + 0.5 * np.cos(phase)
                color = interpolate_color(base, Works.text, 0.45 * float(beam))
                # A small halo plus a bright core; VMobject dots render reliably
                # in both the interactive window and the ffmpeg writer.
                group.add(
                    Dot(radius=0.075 - 0.020 * u, color=color, fill_opacity=0.10, stroke_width=0).move_to(p),
                    Dot(radius=0.028 - 0.008 * u, color=color, fill_opacity=0.95, stroke_width=0).move_to(p),
                )
            return group

        particles = always_redraw(particle_layer)

        # ------------------------------------------------------------------
        # Labels and equations.
        # ------------------------------------------------------------------
        title = Text("Schwarzschild black hole", font_size=34, color=Works.text)
        title.to_corner(UL, buff=0.35)
        subtitle = Text(
            "accretion disk  |  null geodesics  |  E3 rotor",
            font_size=20,
            color=Works.dim,
        ).next_to(title, DOWN, buff=0.08, aligned_edge=LEFT)

        formula = Tex(r"r_s=\frac{2GM}{c^2}", font_size=34, color=Works.text)
        formula.to_corner(UR, buff=0.45)

        scales = VGroup(
            Tex(r"r_{\rm ph}=1.5\,r_s", font_size=27, color=Works.cyan),
            Tex(r"r_{\rm ISCO}=3\,r_s", font_size=27, color=Works.orange),
            Tex(r"b_c=3\sqrt{3}\,M\approx2.598\,r_s", font_size=27, color=Works.blue),
        ).arrange(DOWN, aligned_edge=LEFT, buff=0.14)
        scales.next_to(formula, DOWN, buff=0.30, aligned_edge=RIGHT)

        ring_label = Text("photon ring", font_size=22, color=Works.cyan)
        ring_label.next_to(CENTER + RIGHT * 1.9 + UP * 1.0, RIGHT, buff=0.22)
        ring_arrow = Arrow(
            ring_label.get_left(),
            CENTER + np.array([1.18, 0.42, 0.0]),
            buff=0.08,
            stroke_width=2.0,
            max_tip_length_to_length_ratio=0.18,
            color=Works.cyan,
        )

        disk_label = Text("accretion disk", font_size=22, color=Works.orange)
        disk_label.next_to(CENTER + RIGHT * 3.15 + DOWN * 0.62, RIGHT, buff=0.18)
        disk_arrow = Arrow(
            disk_label.get_left(),
            CENTER + np.array([2.5, -0.48, 0.0]),
            buff=0.08,
            stroke_width=2.0,
            max_tip_length_to_length_ratio=0.18,
            color=Works.orange,
        )

        # ------------------------------------------------------------------
        # Animation.
        # ------------------------------------------------------------------
        self.play(FadeIn(grid, run_time=1.0), FadeIn(stars, run_time=1.2),
                  FadeIn(title), FadeIn(subtitle))
        self.play(FadeIn(gas_outer), FadeIn(gas), run_time=0.9)
        self.play(ShowCreation(rings, lag_ratio=0.03), run_time=1.8)
        # Particles are added before the shadow so the shadow occludes the far
        # side of the disk; the disk's inner edge remains outside the horizon.
        self.add(particles)
        self.play(FadeIn(shadow_glow), FadeIn(shadow), ShowCreation(photon_ring), run_time=1.1)
        self.play(
            clock.animate.set_value(5.8),
            run_time=5.8,
            rate_func=linear,
        )
        self.play(ShowCreation(upper_arc), ShowCreation(lower_arc), FadeIn(formula), FadeIn(scales), run_time=1.2)
        self.play(
            FadeIn(ring_label),
            ShowCreation(ring_arrow),
            FadeIn(disk_label),
            ShowCreation(disk_arrow),
            run_time=1.2,
        )
        self.wait(1.0)
