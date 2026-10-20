# Security Policy

## Supported versions

Security fixes target the latest `main` branch and the current Minecraft 26.3 /
Fabric line. Older snapshots and unsupported Minecraft versions are not
maintained.

## Reporting a vulnerability

Do not open a public issue for a vulnerability.

Use GitHub's private vulnerability reporting:

1. Open the repository's **Security** tab.
2. Choose **Report a vulnerability**.
3. Include the affected version, steps to reproduce, impact, and any relevant
   logs or a minimal proof of concept.

If private reporting is unavailable, contact the repository owner through their
GitHub profile and ask for a private channel before sharing details.

## Scope

Examples of in-scope reports:

- client-trusted combat state that lets a modified client deal extra damage or
  bypass a parry/roll check;
- unbounded block edits, particle payloads, or allocation from a malicious
  client/datapack;
- server crashes or remote code execution caused by command or payload input;
- permission bypasses in `/we` or command execution.

General gameplay balance, missing features, and compatibility issues are ordinary
issues, not security reports.

## Disclosure

Please allow time for a fix and a coordinated release before public disclosure.
Credit can be included in the changelog if desired.
