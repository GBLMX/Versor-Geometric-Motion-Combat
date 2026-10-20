"""STA (spacetime algebra, Cl(1,3)) graphics demo.

All Clifford-algebra quantities (rotors, reversion, vector/bivector
transforms, the Wigner rotation) are computed by Wolfram Language in
``sta.wl`` using the built-in ``CliffordAlgebra``; this scene only
interpolates the exported numbers and draws them with manimgl.
"""

import json
import os

import numpy as np

from manimlib import *

from versor_ga import KDE

HERE = os.path.dirname(os.path.abspath(__file__))
with open(os.path.join(HERE, "data", "sta.json")) as fh:
    DATA = json.load(fh)

PHI = np.array(DATA["boost"]["phi"])
CONTRACTION = np.array(DATA["boost"]["contraction"])
E1 = np.array(DATA["boost"]["vectors"]["e1"])
TH_PHI = np.array(DATA["thomas"]["phi"])
TH_ANGLE = np.array(DATA["thomas"]["angle"])
TH_R = np.array(DATA["thomas"]["R"])
# the Wigner rotation is about z; keep the signed angle so the visual
# rotation matches the Wolfram-computed matrix exactly
SIGNED_ANGLE = np.arctan2(TH_R[:, 1, 0], TH_R[:, 0, 0])

# unit cube vertices / edges (index pairs)
CUBE_V = np.array([[i, j, k] for i in (-1, 1) for j in (-1, 1) for k in (-1, 1)], float)
CUBE_E = [
    (i, j) for i in range(8) for j in range(i + 1, 8)
    if np.sum(np.abs(CUBE_V[i] - CUBE_V[j])) == 2
]


def interpolate(p, xs, ys):
    return float(np.interp(p, xs, ys))


def make_cube(verts, color=KDE.line, width=0.035):
    return Group(*[Line3D(verts[i], verts[j], width=width, color=color) for i, j in CUBE_E])


class STASpacetime(Scene):
    """Rotor sandwich R v R~ acting on a spacetime vector + contraction."""

    def construct(self):
        self.camera.background_color = KDE.background
        title = Tex(r"\text{Spacetime Algebra}\ \ \mathrm{Cl}(1,3)", font_size=52)
        title.to_edge(UP, buff=0.35)
        relations = Tex(
            r"\gamma_0^2=1,\qquad \gamma_1^2=\gamma_2^2=\gamma_3^2=-1",
            font_size=32,
        )
        relations.next_to(title, DOWN, buff=0.22)

        self.play(FadeIn(title), FadeIn(relations))

        rotor = VGroup(
            Tex(r"R=e^{-\frac{\phi}{2}\gamma_0\gamma_1}", font_size=40),
            Tex(r"=\cosh\tfrac{\phi}{2}-\sinh\tfrac{\phi}{2}\,\gamma_0\gamma_1", font_size=36),
            Tex(r"v'=R\,v\,\tilde R", font_size=40),
        ).arrange(DOWN, aligned_edge=LEFT, buff=0.32)
        rotor.move_to(LEFT * 4.9 + UP * 0.9)

        self.play(LaggedStart(*[FadeIn(m, shift=RIGHT * 0.25) for m in rotor], lag_ratio=0.3))

        # --- spacetime diagram (t horizontal, x vertical) ---------------
        axes = Axes(
            x_range=(-2.7, 2.7, 1),
            y_range=(-2.7, 2.7, 1),
            width=5.6,
            height=5.6,
            axis_config=dict(stroke_color=KDE.axes, stroke_width=2),
        )
        axes.move_to(RIGHT * 3.5 + DOWN * 0.15)
        t_label = Tex("t", font_size=34).next_to(axes.get_x_axis().get_end(), RIGHT, buff=0.15)
        x_label = Tex("x", font_size=34).next_to(axes.get_y_axis().get_end(), UP, buff=0.15)

        cone1 = DashedLine(axes.c2p(-2.6, -2.6), axes.c2p(2.6, 2.6), color=KDE.axes, stroke_width=2)
        cone2 = DashedLine(axes.c2p(-2.6, 2.6), axes.c2p(2.6, -2.6), color=KDE.axes, stroke_width=2)

        phi_max = 1.6
        hyperbola = ParametricCurve(
            lambda p: axes.c2p(np.sinh(p), np.cosh(p)),
            t_range=(0, phi_max, 0.01),
            color=KDE.sphere,
            stroke_width=4,
        )

        self.play(
            ShowCreation(axes), FadeIn(t_label), FadeIn(x_label),
            ShowCreation(cone1), ShowCreation(cone2),
            run_time=1.6,
        )
        self.play(ShowCreation(hyperbola), run_time=1.2)

        phi_tracker = ValueTracker(0.0)

        def unit_arrow():
            p = phi_tracker.get_value()
            t = interpolate(p, PHI, E1[:, 0])
            x = interpolate(p, PHI, E1[:, 1])
            return Arrow(
                axes.c2p(0, 0), axes.c2p(t, x),
                buff=0, thickness=6,
                fill_color=KDE.vector, fill_opacity=1.0,
                tip_width_ratio=5,
            )

        arrow = always_redraw(unit_arrow)
        self.add(arrow)

        phi_readout = DecimalNumber(0, num_decimal_places=2, font_size=32)
        phi_readout.add_updater(lambda m: m.set_value(phi_tracker.get_value()))
        phi_readout.next_to(axes, DOWN, buff=0.28)
        phi_caption = Tex(r"\phi =", font_size=32).next_to(phi_readout, LEFT, buff=0.08)

        cosh_readout = DecimalNumber(1, num_decimal_places=2, font_size=32)
        cosh_readout.add_updater(lambda m: m.set_value(np.cosh(phi_tracker.get_value())))
        cosh_caption = Tex(r"\cosh\phi =", font_size=32)
        cosh_group = VGroup(cosh_caption, cosh_readout).arrange(RIGHT, buff=0.08)
        cosh_group.next_to(phi_caption, DOWN, buff=0.2, aligned_edge=LEFT)

        self.play(FadeIn(VGroup(phi_caption, phi_readout, cosh_group)))

        # --- Lorentz contraction of a rod --------------------------------
        rod_y = -2.75
        rod_x0 = -6.2
        rod_len = 2.4

        def rod():
            factor = interpolate(phi_tracker.get_value(), PHI, CONTRACTION)
            end = rod_x0 + rod_len * 2 * factor
            return Line(
                np.array([rod_x0, rod_y, 0]), np.array([end, rod_y, 0]),
                stroke_width=9, color=KDE.attribute,
            )

        rod_mob = always_redraw(rod)
        rod_brace = Brace(Line(
            np.array([rod_x0, rod_y - 0.35, 0]),
            np.array([rod_x0 + rod_len * 2, rod_y - 0.35, 0]),
        ), DOWN, color=KDE.axes)
        rod_caption = Tex(r"L_0", font_size=30).next_to(rod_brace, DOWN, buff=0.08)
        gamma_caption = Tex(r"L=L_0/\cosh\phi", font_size=30)

        self.play(FadeIn(gamma_caption), ShowCreation(rod_mob))
        gamma_caption.next_to(rod_mob, UP, buff=0.22)

        self.play(phi_tracker.animate.set_value(phi_max), run_time=7, rate_func=smooth)
        self.play(FadeIn(rod_brace), FadeIn(rod_caption), run_time=0.8)
        self.wait(1.2)

        # final identity panel
        note = VGroup(
            Tex(r"\text{reversion } \tilde R:\ \gamma_0\gamma_1\mapsto-\gamma_0\gamma_1", font_size=28),
            Tex(r"R\tilde R=1", font_size=28),
        ).arrange(DOWN, aligned_edge=LEFT, buff=0.12)
        note.next_to(rotor, DOWN, buff=0.45, aligned_edge=LEFT)
        if note.get_width() > 6.7:
            note.set_width(6.7)
            note.next_to(rotor, DOWN, buff=0.45, aligned_edge=LEFT)
        if note.get_left()[0] < -6.8:
            note.shift(RIGHT * (-6.8 - note.get_left()[0]))
        self.play(FadeIn(note))
        self.wait(1.5)


class STAGraphics(ThreeDScene):
    """Boost contraction and Wigner rotation of a wireframe cube."""

    def construct(self):
        self.camera.background_color = KDE.background
        self.frame.reorient(68, -48)
        self.frame.set_height(9.0)

        axes = ThreeDAxes(
            x_range=(-3, 3, 1), y_range=(-3, 3, 1), z_range=(-3, 3, 1),
            width=6, height=6, depth=6,
            axis_config=dict(stroke_color=KDE.axes, stroke_width=2),
        )
        axes.add_axis_labels(
            r"\gamma_1", r"\gamma_2", r"\gamma_3", font_size=30, buff=0.15
        )
        axes_labels = axes.axis_labels
        header = Tex(
            r"R\,x\,\tilde R:\ \ \text{rotors act on geometry}",
            font_size=38,
        ).fix_in_frame().to_corner(UL, buff=0.35)

        self.add(axes, axes_labels)
        self.play(FadeIn(header), run_time=0.8)

        # --- act 1: boost contraction -----------------------------------
        boost_phi = ValueTracker(0.0)

        def boosted_cube():
            factor = interpolate(boost_phi.get_value(), PHI, CONTRACTION)
            return make_cube(CUBE_V * np.array([factor, 1.0, 1.0]))

        cube = always_redraw(boosted_cube)
        self.add(cube)

        gamma_caption = Tex(r"\gamma=\cosh\phi=", font_size=30).fix_in_frame()
        gamma_value = DecimalNumber(1, num_decimal_places=2, font_size=30).fix_in_frame()
        gamma_value.add_updater(
            lambda m: m.set_value(np.cosh(boost_phi.get_value()))
        )
        gamma_group = VGroup(gamma_caption, gamma_value).arrange(RIGHT, buff=0.08)
        gamma_group.fix_in_frame().to_corner(UR, buff=0.35)
        self.add(gamma_group)

        self.play(boost_phi.animate.set_value(2.4), run_time=6, rate_func=smooth)
        self.wait(0.6)

        self.play(FadeOut(cube), FadeOut(gamma_group))

        # --- act 2: Wigner rotation -------------------------------------
        theta_phi = ValueTracker(0.0)

        def rotated_cube():
            p = theta_phi.get_value()
            a = interpolate(p, TH_PHI, SIGNED_ANGLE)
            c, s = np.cos(a), np.sin(a)
            r = np.array([[c, -s, 0.0], [s, c, 0.0], [0.0, 0.0, 1.0]])
            return make_cube(CUBE_V @ r.T, color=KDE.string)

        cube2 = always_redraw(rotated_cube)
        self.add(cube2)

        angle_caption = Tex(r"\theta_W=", font_size=30).fix_in_frame()
        angle_value = DecimalNumber(
            0, num_decimal_places=1, font_size=30, unit=r"^\circ"
        ).fix_in_frame()
        angle_value.add_updater(
            lambda m: m.set_value(
                interpolate(theta_phi.get_value(), TH_PHI, TH_ANGLE) * 180 / np.pi
            )
        )
        angle_group = VGroup(angle_caption, angle_value)
        angle_group.arrange(RIGHT, buff=0.06).fix_in_frame().to_corner(UR, buff=0.35)

        note = Tex(
            r"R_2R_1 = \text{boost}\cdot\text{rotation}",
            font_size=32,
        ).fix_in_frame().to_corner(DL, buff=0.35)

        self.add(angle_group, note)
        self.play(
            theta_phi.animate.set_value(3.0),
            self.frame.animate.reorient(68, 6),
            run_time=9,
            rate_func=smooth,
        )
        self.wait(1.5)
