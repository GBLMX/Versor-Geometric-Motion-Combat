"""Palette sampled from the active KDE CachyOS Nord scheme and shells.

Source of truth on this machine:

* ``~/.config/kdeglobals`` (``CachyOSNordLightly``)
* ``~/.config/kitty/themes/kde-cachyos.conf`` (ANSI 0-17)
* ``~/.config/fish/conf.d/kde-cachyos-theme.fish`` (syntax roles)
* ``~/.zshrc`` (``zsh-syntax-highlighting`` roles)

The names below mirror those roles rather than KDE's widget names, so a scene
can say ``KDE.keyword`` or ``KDE.string`` and stay consistent with the shell.
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Palette:
    # Surfaces and text (KDE)
    background: str = "#161925"  # [Colors:View] BackgroundNormal 22,25,37
    surface: str = "#1E2233"  # [Colors:Window] BackgroundNormal 30,34,51
    text: str = "#47ADDA"  # [Colors:View] ForegroundNormal 71,173,218
    command: str = "#66C2F2"  # [Colors:Window] ForegroundNormal 102,194,242
    inactive: str = "#666A73"  # [Colors:Window] ForegroundInactive 102,106,115
    focus: str = "#6DBCD1"  # [Colors:Window] DecorationFocus 109,188,209
    selection: str = "#47ADD6"  # [Colors:Selection] BackgroundNormal 71,173,214
    ink: str = "#0A0B0C"  # [Colors:Selection] ForegroundNormal 10,11,12

    # Syntax / semantic roles (fish + zsh)
    keyword: str = "#FF6A00"  # ForegroundNeutral
    param: str = "#D3DAE3"  # Selection ForegroundInactive
    string: str = "#DFFC00"  # View ForegroundNegative (lime)
    number: str = "#00C1E4"  # View ForegroundActive
    type: str = "#6DBCD1"  # DecorationFocus
    operator: str = "#7CB7FF"  # View ForegroundVisited
    attribute: str = "#18A5DE"  # Window ForegroundPositive
    comment: str = "#666A73"  # Window ForegroundInactive
    error: str = "#FF5370"  # derived red (fish error/cancel/status)
    link: str = "#00A8FF"  # Window ForegroundLink
    visited: str = "#B30DBF"  # Window ForegroundVisited

    # ANSI 0-15, useful when a scene needs the raw terminal palette
    red: str = "#FF5370"  # color1 (derived)
    lime: str = "#DFFC00"  # color2
    orange: str = "#FF6A00"  # color3
    blue: str = "#00A8FF"  # color4
    magenta: str = "#B30DBF"  # color5
    cyan: str = "#00C1E4"  # color6
    white: str = "#D3DAE3"  # color7
    bright_black: str = "#666A73"  # color8
    bright_red: str = "#FF7A93"  # color9
    bright_lime: str = "#E9FF5C"  # color10
    bright_orange: str = "#FFA040"  # color11
    bright_blue: str = "#4595FF"  # color12
    bright_magenta: str = "#E24BE8"  # color13
    bright_cyan: str = "#66C2F2"  # color14
    bright_white: str = "#FCFCFC"  # color15

    # Scene roles
    vector: str = "#FF6A00"
    point: str = "#DFFC00"
    line: str = "#00A8FF"
    plane: str = "#B30DBF"
    sphere: str = "#00C1E4"
    axes: str = "#666A73"


KDE = Palette()

__all__ = ["KDE", "Palette"]
