# Combat

The combat model is server-authoritative and geometry-first. The server bakes one
blade arc, tests it against entity boxes, resolves parries, and sends the arc to
clients for presentation.

## Swing arc

`ArcSweep.bake` builds a horizontal circular arc at eye height:

- 48 samples (`ArcSweep.SAMPLES`);
- ±65° half-angle (`HALF_ANGLE = 65°`);
- blade thickness `0.55` blocks (`BLADE_RADIUS`);
- reach clamped to the entity interaction range and the configured
  `[min_reach, max_reach]`.

The reach vector is

```text
(-r sin(yaw), 0, r cos(yaw))
```

with Minecraft yaw conventions: `0` faces `+Z`, `+90°` faces `-X`. The start
vector is rotated by `-65°` about `+Y`; a PGA screw motion then sweeps the full
130° over `t ∈ [0, 1]`.

The same baked points are sent in `SwingPayload`. The client does not recompute
the hit arc.

## Hit test

For each arc segment `s(t) = a + t(b-a)`, the server computes the squared distance
to the target's AABB:

```text
d²(t) = || s(t) - clamp(s(t), box) ||²
```

This is convex in `t`; `ArcSweep.segmentDistanceSq` minimizes it with 32 ternary
iterations. A hit occurs when the minimum is at most `BLADE_RADIUS²`. Testing the
continuous segment prevents a fast target from slipping between the 48 samples.

## Tick batching and double-hit prevention

`CombatManager` queues attacks during the tick and flushes them at
`END_SERVER_TICK`. This matters for mods such as Better Combat that call
`Player.attack` once per target in one swing:

1. The first call captures the arc, reach, eye position, and attack-strength
   scale.
2. Every direct target in the same tick is added to an exclusion set.
3. At flush time, one arc adjudication excludes all direct targets.

Therefore a target cannot receive both a vanilla direct hit and an extra swept
hit from the same swing.

## Damage and knockback

Arc damage:

```text
damage = arc_damage * (0.2 + 0.8 * s²)
```

where `s` is `getAttackStrengthScale(0.5f)`. The result is applied through the
server damage source. Horizontal knockback is normalized from attacker to target
and scaled by `knockback`; vertical knockback is fixed at `0.15`.

A successful direct hit is handled by vanilla. The arc only applies to targets
not already hit directly in that tick.

## Parry

A parry is checked before queuing a swing:

- `parry` must be enabled;
- the defender must be blocking;
- the used item must carry a `blocks_attacks` component;
- the damage type must not be in `bypassed_by`;
- the defender's look vector must point toward the attacker (dot > `0.45`);
- the baked arc must cross the guard plane within `parry_reach`.

The guard plane is built from the defender's eye and look vector. Each arc
segment is converted to a PGA line and intersected with the plane
(`plane ∧ line`). A crossing is accepted only when it lies in the segment and
inside the shield radius. The whole attack is cancelled and the attacker is
pushed back.

## Dodge roll

`RollManager`:

- horizontal look-directed impulse: `0.6` blocks/tick;
- preserves non-negative vertical velocity;
- invulnerability: 10 ticks;
- cooldown: 20 ticks;
- broadcasts `RollPayload` and triggers a field burst.

The cooldown state is cleared on disconnect.

## Compatibility notes

- **Better Combat / multi-target attack handlers**: the deferred, excluded-target
  design avoids double damage.
- **Sword Blocking Mechanics / `blocks_attacks` items**: parry reads the component,
  not a specific shield item, so modded blocking items can participate.
- **WorldEdit-style mods**: the `//` prefix conflict is intentional; use one mod
  that owns `//` at a time.
- **Client-only installs**: visuals may be missing and combat decisions remain on
  the server.

## Tuning

All values live in `data/versormotion/combat.json` and are reloaded with
`/reload`. See [Configuration](CONFIGURATION.md). Arc sample count, blade radius,
and the ±65° sweep are code constants; changing them changes both server tests
and client replay and requires a rebuild.

## Related code

```text
CombatManager.java                 tick queue, damage, knockback, payload
combat/ArcSweep.java               arc baking and segment-AABB distance
combat/GuardPlane.java             guard-plane intersection
combat/CombatRules.java            datapack tuning and clamps
combat/CombatRulesReloadListener.java
RollManager.java                   roll state
```

For the underlying math, see [Math and physics](MATH_PHYSICS.md).
