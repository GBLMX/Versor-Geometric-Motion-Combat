# Changelog

All notable user-visible changes are recorded here. The format follows the spirit
of Keep a Changelog; versions use `MAJOR.MINOR.PATCH`.

## [0.1.0] - 2026-10-21

### Added

- Server-authoritative swept attacks with a 48-point PGA blade arc and continuous
  segment–AABB collision.
- Guard-plane parries using the `blocks_attacks` component and `bypassed_by`
  rules.
- Dodge roll with impulse, invulnerability, cooldown, and synced rotor animation.
- Divergence-free vortex particles and an analytic Rankine-vortex tornado.
- WorldEdit-style bounded editing: selection, set/replace, spheres, cylinders,
  copy/cut/paste, undo/redo.
- Display entity transform rotation with independent left/right rotation sides.
- Camera roll tilt and damage-scaled shake.
- Client visual configuration via Cloth Config / Mod Menu.
- `versor-java` geometric algebra core with dense, sparse, batch, and matrix
  paths; 454 standalone checks.
- Math/physics, architecture, kernel scheduling, command, configuration, and
  development documentation.
- Fixed uv/ManimGL environment under `tools/manim/` with a versor-java-driven
  rotor scene and the migrated STA/Cl(1,3) scene.
- GitHub build workflow and issue/PR templates.

### Compatibility

- Minecraft 26.3
- Fabric Loader 0.19.5
- Fabric API 0.163.0+26.3
- Java 25

### License

- Relicensed under the Apache License 2.0. See `LICENSE` and `NOTICE`.
