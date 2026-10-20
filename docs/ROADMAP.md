# Roadmap

This is a scope document, not a promise of dates.

## Current state — `0.1.0`

- Server-authoritative swept attacks with continuous segment–AABB tests.
- Guard-plane parry using `blocks_attacks` components.
- Dodge roll with impulse, invulnerability, cooldown, and synced animation.
- Divergence-free vortex particles and Rankine-vortex tornado.
- Bounded WorldEdit-style editing (`/we` and `//`).
- Display entity transform editing.
- Projective/conformal geometric algebra core with 454 checks.
- Dense, sparse, batch, and matrix kernels; benchmark and JMH harnesses.

## Near term

1. **More compatibility coverage**
   - Test against common combat and shield mods.
   - Document interaction with multi-target handlers beyond Better Combat.
2. **Combat observability**
   - Optional debug command for the last baked arc and rejected candidates.
   - More deterministic tests around tick batching and exclusion.
3. **Editing ergonomics**
   - Better selection feedback and command suggestions.
   - More block-state diagnostics; still bounded, no schematics.
4. **Documentation**
   - English/Chinese parity for command and configuration references.
   - More worked examples for math and physics.

## Later

- More CGA primitives and queries (circle intersections, sphere–sphere).
- Optional Vector API path for heavy PGA3 batch sandwich, behind a runtime flag.
- More direct sparse sandwich expansion with support pruning.
- Pose/screw helpers and collision queries built on the existing motion core.
- Client-side documentation links and diagnostics screen.

## Explicit non-goals

- Replacing WorldEdit or supporting schematics/NBT/region files.
- Client-authoritative hit or roll decisions.
- Supporting Minecraft versions before 26.3 in the same jar.
- Shipping native BLAS/JNI/GPU dependencies in the mod.
- Unbounded block edits or particle payloads.
