# Configuration

There are two independent configuration surfaces:

| Surface | File | Owner |
|---|---|---|
| Gameplay tuning | `data/versormotion/combat.json` (datapack) | Server |
| Client presentation | `config/versormotion-client.json` | Client |

## Server: `combat.json`

The file ships with the mod at:

```text
src/main/resources/data/versormotion/combat.json
```

A datapack can override the same path. The server reads it on data reload
(`/reload`) through `CombatRulesReloadListener`.

### Defaults and ranges

| Key | Default | Accepted range | Effect |
|---|---:|---:|---|
| `arc_damage` | 2.0 | 0–100 | Base damage for an arc hit. |
| `knockback` | 0.3 | 0–5 | Horizontal knockback multiplier. |
| `swing_cooldown_ticks` | 4 | 0–200 | Minimum ticks between adjudicated swings. |
| `swing_ticks` | 6 | 1–40 | Client trail lifetime. |
| `min_reach` | 2.0 | 1.0–6.0 | Lower reach clamp. |
| `max_reach` | 6.0 | 2.0–8.0 | Upper reach clamp; forced to at least `min_reach`. |
| `parry` | true | boolean | Enable guard-plane parries. |
| `parry_reach` | 1.15 | 0.5–4.0 | Shield coverage radius. |
| `field_seeds` | 10 | 1–64 | Vortex seed count. |
| `field_steps` | 6 | 1–32 | Integration steps per seed. |
| `field_ticks` | 10 | 1–60 | Field effect lifetime. |

Missing, non-numeric, or non-boolean values fall back to defaults. Finite numbers
are clamped. Invalid JSON keeps the previous rules and logs an error.

### Example

```json
{
  "arc_damage": 2.0,
  "knockback": 0.3,
  "swing_cooldown_ticks": 4,
  "swing_ticks": 6,
  "min_reach": 2.0,
  "max_reach": 6.0,
  "parry": true,
  "parry_reach": 1.15,
  "field_seeds": 10,
  "field_steps": 6,
  "field_ticks": 10
}
```

### Reload

```mcfunction
reload
```

`/versor rules` prints the active values. The payload geometry (48 arc samples,
blade radius, ±65°) is code-defined; changing it requires rebuilding both sides.

## Client: `versormotion-client.json`

Stored in the client `config/` directory. Cloth Config provides the UI; Mod Menu
adds an entry when installed.

| Key | Default | Range | Effect |
|---|---:|---:|---|
| `swingParticles` | true | bool | Show swing spark particles. |
| `arcMesh` | true | bool | Render the baked arc mesh. |
| `cameraShakePercent` | 100 | 0–200 | Camera shake scale; 0 disables. |

Reload or restart the client after editing the JSON manually. The values are
clamped on load and save.
