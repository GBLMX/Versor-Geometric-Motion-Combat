# Contributing

Thanks for helping improve Versor Geometric Motion Combat.

## Before you start

- Open an issue for behavior changes, compatibility work, or new commands. This
  keeps the scope reviewable and avoids duplicate work.
- Small documentation fixes and obvious test corrections can go straight to a PR.
- Security issues must follow [SECURITY.md](SECURITY.md), not a public issue.

## Development setup

Follow [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md). In short:

```bash
git clone --recursive https://github.com/GBLMX/Versor-Geometric-Motion-Combat.git
cd Versor-Geometric-Motion-Combat
./gradlew gaTest combatTest weTest build
```

JDK 25 is required. The math core is a submodule; if it is missing, run
`git submodule update --init --recursive`.

## What a good PR contains

- One focused behavior change or one documentation area.
- A regression test for behavior changes where practical. The project uses
  standalone `main`-class suites (`GaTest`, `CombatChecks`, `WeChecks`) rather
  than JUnit.
- Updated `docs/COMMANDS.md` / `docs/CONFIGURATION.md` when syntax or config keys
  change.
- A `CHANGELOG.md` entry for user-visible changes.
- No generated files: `run/`, `build/`, `logs/`, `out-*`, `lib/versor-java/out*`.

## Verification

Run before pushing:

```bash
./gradlew gaTest combatTest weTest build
```

For math changes, also run the narrowest relevant benchmark and report the
workload, JVM, and before/after numbers. Do not change a dispatch threshold based
on a single noisy run.

## Code style

- Match the surrounding code; tabs are used in mod sources, four spaces in
  `versor-java`.
- Keep server decisions in the common source set; client code may only present
  state.
- Do not trust client-supplied arcs, positions, or hit results.
- Keep geometry bounded. New payloads need explicit size limits.
- Prefer existing patterns (PGA planes/lines, `ArcSweep`, `CombatRules`) over
  parallel geometry code.

## Compatibility

State the tested Minecraft/Fabric versions and any mod interactions in the PR.
Do not silently change the `//` prefix behavior or `blocks_attacks` handling; both
are compatibility-sensitive.

## Submodule changes

If `lib/versor-java` changes:

1. Commit and push the submodule change first.
2. Update the parent pointer.
3. Include the submodule commit hash in the PR description.

## License

By submitting a contribution you confirm that you have the right to submit it and
that it may be distributed as part of this repository under the
[Apache License 2.0](LICENSE).
