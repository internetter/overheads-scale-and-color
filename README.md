# Overhead Scale & Color

A RuneLite plugin that shrinks the overhead prayer icon above your own player so it stops covering
your character, and draws a colored ring around it so it stays readable once small.

Cosmetic only. Local player only — it does not affect other players or NPCs.

## What it does

The game client draws overhead icons at a fixed size and offers no way to resize them, so the
plugin hides the client's own icon and draws its own in place of it at whatever size you choose.

Your overhead icon, healthbar, hitsplats, overhead chat and name are drawn together and can only be
hidden as a group. While an overhead prayer is active, those are hidden along with the icon. When
no overhead prayer is active, everything is back to normal.

## Options

| Option | Default | What it does |
|---|---|---|
| Scale % | 50 | Size as a percent of the normal icon. |
| Height offset | 40 | How high above your character the icon sits. |
| Only hide while praying | on | Keeps your healthbar and hitsplats visible whenever no overhead prayer is active. |
| Smooth scaling | on | Off gives hard pixel edges instead. |
| Show ring | on | |
| Thickness | 2 | Ring thickness in pixels. Stays the same at any scale. |
| Gap | 2 | Space between the icon and the ring. |
| Dark outline | on | Keeps the ring visible against grass, water and sand. |
| Color palette | Standard | See below. |
| Distinct ring styles | off | Melee solid, ranged dashed, magic dotted. |
| Custom colors | — | Used only when the palette is set to Custom. |

## Rings

Rings are drawn for Protect from Melee, Missiles and Magic, and their Deflect equivalents. Smite,
Retribution, Redemption, Wrath, Soul Split and the combined overheads are drawn without one.

| Palette | Melee | Ranged | Magic |
|---|---|---|---|
| Standard | red | green | blue |
| Red-green friendly | orange | white | blue |
| Blue-yellow friendly | vermillion | green | magenta |
| Monochrome | white | light grey | dark grey |
| Custom | your choice | your choice | your choice |

The alternate palettes are for the common forms of color vision deficiency. **Distinct ring styles**
tells the three apart by line pattern instead of color, and can be used with any palette.

## Building

```bash
./gradlew build
```

## License

BSD 2-Clause.
