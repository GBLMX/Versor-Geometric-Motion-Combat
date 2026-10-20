# Documentation

This directory is the reference documentation for Versor Geometric Motion
Combat. The top-level [README](../README.md) is the project landing page.

## Start here

| Document | Read it for |
|---|---|
| [Architecture](ARCHITECTURE.md) | Modules, server authority, network flow, render integration. |
| [Commands](COMMANDS.md) | `/versor`, `/vm`, `/we`, and `//` command reference. |
| [Combat](COMBAT.md) | Swept attacks, guard-plane parries, rolls, damage, compatibility. |
| [Configuration](CONFIGURATION.md) | `combat.json` keys, ranges, reload behavior. |
| [Development](DEVELOPMENT.md) | Build, test, submodule, CI, debugging, benchmarks. |
| [Roadmap](ROADMAP.md) | Current scope, next steps, explicit non-goals. |

## Mathematics and physics

| Document | Contents |
|---|---|
| [Math and physics](MATH_PHYSICS.md) | Clifford algebra, E3/PGA3/CGA3, twist kinematics, field calculus, combat and fluid models. |
| [Kernel scheduling](../lib/versor-java/docs/KERNEL_SCHEDULING.md) | Dense/sparse/batch/matrix kernels, dispatch thresholds, measured optimizations. |
| [Performance report](../lib/versor-java/PERFORMANCE.md) | Benchmark methodology and results for the math core. |
| [Manim environment](../tools/manim/README.md) | Fixed uv/ManimGL setup, versor-java rotor scene, migrated STA scene. |

## Project policy

| Document | Contents |
|---|---|
| [Contributing](../CONTRIBUTING.md) | Issue/PR workflow and verification expectations. |
| [Security](../SECURITY.md) | Private vulnerability reporting. |
| [Changelog](../CHANGELOG.md) | Release history. |
| [License](../LICENSE) | Apache License 2.0 and NOTICE. |

## Terminology

- **PGA3**: 3D projective geometric algebra, used for points, planes, lines, and
  rigid motors.
- **CGA3**: 3D conformal geometric algebra, used for spheres, circles, and
  point/sphere incidence.
- **Baked arc**: the 48 points the server tests and sends to clients. Clients
  replay exactly these points instead of recomputing hit geometry.
- **Server-authoritative**: damage, parry, roll, and editing decisions are made
  on the server; client state is presentation only.
- **`versor-java`**: the pure-Java geometric algebra core in
  [`lib/versor-java`](../lib/versor-java).
