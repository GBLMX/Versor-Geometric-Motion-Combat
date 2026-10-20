# Commands

All commands are server-side and use Brigadier. Coordinates accept relative
values (`~`). The root aliases are:

- `/versor`
- `/vm`

The WorldEdit-style tree is `/we`; `//command` is translated to `/we command`.

## `/versor`

### Query and effect commands

These work from a player, command block, function, or server console.

| Command | Output / effect |
|---|---|
| `/versor field at <pos>` | Prints `F(x)`, divergence, and curl of the built-in swirl field. |
| `/versor field burst <pos>` | Bakes a divergence-free vortex burst and returns the particle sample count. |
| `/versor field tornado <pos>` | Broadcasts a 352-point tornado and returns the point count. |
| `/versor motion rotation <axis> <omega> <time> <point>` | Prints position, velocity, and acceleration at `time`. |
| `/versor sweep rotation <axis> <omega> <t0> <t1> <samples> <point>` | Prints the swept AABB and path length; returns `samples`. |
| `/versor display rotate <targets> <axis> <angle> [local]` | Applies an E3 rotor to target Display entities. |
| `/versor rules` | Prints the active combat tuning. |

Examples:

```mcfunction
versor field at 1 2 3
versor field burst 0 64 0
versor field tornado 0 64 0
versor motion rotation 0 0 1 1.0 0.5 1 0 0
versor sweep rotation 0 1 0 3.14 0 1 4 1 0 0
versor display rotate @e[type=display] 0 1 0 45
versor display rotate @e[type=display] 0 1 0 45 local
```

`display rotate` uses degrees. Without `local` it writes `left_rotation` (world
rotation at the entity origin); with `local` it writes `right_rotation`
(entity-local rotation). Translation, scale, and the other rotation side are
preserved.

### Player-action commands

These require a player executor.

| Command | Effect |
|---|---|
| `/versor attack` | Swings the main hand, runs server arc adjudication, and returns the number of extra targets hit. |
| `/versor roll` | Starts a dodge roll, or reports that it is on cooldown. |

### Permission notes

- `/versor` itself has no explicit permission gate; the actor commands still
  require a player.
- `/we` requires the gamemaster permission level
  (`Permissions.COMMANDS_GAMEMASTER`).

## `/we` — WorldEdit-style editing

The editing core is deliberately bounded. It is not a drop-in WorldEdit
replacement.

### Selection

| Command | Meaning |
|---|---|
| `/we wand` | Gives the selection wand (left click = pos1, right click = pos2). |
| `/we pos1` | Set corner 1 to the player's block position. |
| `/we pos2` | Set corner 2 to the player's block position. |
| `/we size` | Print selection dimensions and volume; reports over-limit volumes without failing. |

`//pos1` and `//pos2` use the player's feet block, not a ray-traced target. The
wand is the targeted selection tool.

### Inspection and edits

| Command | Meaning |
|---|---|
| `/we count <filter>` | Count matching blocks, e.g. `#minecraft:logs`. |
| `/we set <block>` | Fill the selection. |
| `/we replace <to>` | Replace non-air blocks. |
| `/we replace <from> <to>` | Replace blocks matching a predicate/tag. |
| `/we sphere <block> <r>` | Solid sphere centered on the selection center. |
| `/we hsphere <block> <r>` | Hollow sphere. |
| `/we cyl <block> <r> [height]` | Solid cylinder; height centered on the selection center. |
| `/we hcyl <block> <r> [height]` | Hollow cylinder. |
| `/we copy` | Copy the selection. |
| `/we cut` | Copy and clear the selection. |
| `/we paste` | Paste relative to the player's position. |
| `/we undo` | Undo the player's last edit. |
| `/we redo` | Redo the player's last undone edit. |

Block states support bracket syntax:

```text
//set minecraft:oak_stairs[facing=north,half=top]
//replace #minecraft:logs minecraft:stripped_oak_log
```

### Limits

| Limit | Value |
|---|---:|
| Blocks per command | 32,768 |
| Sphere/cylinder radius | 32 |
| Cylinder height | 64 |
| Undo history per player | 5 entries |

Exceeding a limit fails the command rather than partially applying an edit.

### `//` translation

`CommandsMixin` intercepts a command string beginning with `/` and executes
`we <rest>`. Vanilla strips one leading slash before dispatch, so the server sees
`/set` for what the player typed as `//set`.

This claims the `//` prefix. Do not install WorldEdit or another mod that uses
the same prefix if you rely on both command sets.

## Output examples

```text
F(1.000, 2.000, 3.000) = (-2.000, 1.000, 0.000); div = 0.000; curl = (-0.000, -0.000, 2.000)
motion t=0.500: pos = (0.878, 0.479, 0.000); vel = (-0.479, 0.878, 0.000); acc = (-0.878, -0.479, 0.000)
sweep 4 samples: aabb = [(0.540, 0.000, 0.000), (1.000, 0.841, 0.000)]; length = 0.995
```
