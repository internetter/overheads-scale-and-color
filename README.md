# Overhead Scale & Color

A RuneLite plugin that replaces your own overhead prayer icon with a colored ring, or with a smaller
version of the icon, so it stops covering your character.

Cosmetic only. Local player only — it does not affect other players or NPCs.

## What it does

The game client draws overhead icons at a fixed size and offers no way to resize them, so the plugin
hides its icon and draws its own indicator instead.

Hiding the client's icon also hides your health bar, hitsplats, PK skull and overhead chat — the
client draws them all in one pass. The plugin draws them back, so nothing is lost.

## Options

| Option | Default | What it does |
|---|---|---|
| Mode | Ring only | Ring only, or a shrunken copy of the icon. |
| Scale % | 50 | Size of the ring, or of the icon in Shrink mode. |
| Height offset | 70 | How high above your character it sits. |
| Only hide while praying | on | Leaves everything untouched when no overhead prayer is active. |
| Smooth scaling | on | Shrink mode. Off gives hard pixel edges. |
| Restore health bar / hitsplats / PK skull / overhead chat | on | Draws back what hiding the icon removes. Each has its own height. |
| Hitsplat size / linger | 26 px / 600 ms | Size of restored hitsplats, and extra time they stay on screen. |
| Overhead chat color | white | Color of restored overhead chat. |
| **Ring** | | |
| Show ring | on | |
| Thickness / Gap | 2 / 2 | Ring line weight, and space between ring and icon. |
| Dark outline | on | Keeps the ring visible against grass, water and sand. |
| Color palette | Standard | See below. |
| Distinct ring styles | off | Melee solid, ranged dashed, magic dotted. |

Rings are drawn for Protect from Melee, Missiles and Magic, and their Deflect equivalents. In
**Ring only** mode those are the only overheads the plugin touches: Smite, Retribution, Redemption
and the rest show exactly as they normally would. In **Shrink icon** mode every overhead is shrunk,
and the non-protection ones are drawn without a ring.

## Colors

| Palette | Melee | Ranged | Magic |
|---|---|---|---|
| Standard | red | green | blue |
| Red-green safe | orange | white | blue |
| Blue-yellow safe | vermillion | green | magenta |
| Monochrome | white | light grey | dark grey |
| Custom | your choice | your choice | your choice |

The alternate palettes are for the common forms of color vision deficiency. **Distinct ring styles**
tells the three apart by line pattern instead of color, and works with any palette.

## Building

Requires JDK 11.

```bash
./gradlew build
```

## License

BSD 2-Clause.
