# DESIGN.md — Overhead Scale & Color

How the plugin works, and the client constraints that shape it.

## The constraint everything follows from

The client draws overhead icons at a fixed size and offers no way to change or hide one:

- `Player` has `getOverheadIcon()` and no setter.
- `Client` has no hide flags. (`setLocalPlayerHidden2D`, which older material references, does not
  exist.)
- Sprite overrides do not reach overhead icons — tested in game, not assumed.

The only available mechanism is a draw veto: register a `RenderCallback` with
`RenderCallbackManager` and return `false` from `addEntity` when `drawingUI` is true for the local
player. That is what the bundled Entity Hider uses.

**The veto is all-or-nothing.** It suppresses the player's entire 2D pass — icon, health bar,
hitsplats, PK skull and overhead chat together. There is no finer control, so anything the plugin
hides, it has to draw back.

## What it draws

Two modes, both of which hide the client's icon:

- **Ring only** — a colored ring stands in for the icon.
- **Shrink icon** — a resized copy of the icon, inside the ring.

Positioned with `Perspective.getCanvasImageLocation` against `getLogicalHeight()` plus a height
offset. Icon sprites come from `SpriteID.HEADICONS_PRAYER` (440), indexed by `HeadIcon.ordinal()`.

`getCanvasImageLocation` centres the image on the projected point in **both** axes, so a shrinking
icon converges on its own centre and its bottom edge rises. Bottom-centre anchoring adds
`(nativeHeight - scaledHeight) / 2`.

### Layer

`OverlayLayer.UNDER_WIDGETS`, not `ABOVE_SCENE`. Despite the names, `ABOVE_SCENE` renders from the
client's `drawScene()` hook, which runs *before* overheads — anything drawn there is painted over
moments later. `UNDER_WIDGETS` renders from `drawAboveOverheads()`: after the scene and overheads,
still beneath interfaces.

## What it restores

| Element | Source | Fidelity |
|---|---|---|
| Health bar | `PostHealthBarConfig` sprites | exact, with a plain fallback if the event has not fired |
| PK skull | `SpriteID.HEADICONS_PK` | exact |
| Overhead chat | `getOverheadText()`, game font via `FontManager` | exact typeface; chat effects render as plain text |
| Hitsplats | `HitsplatApplied` | drawn, not reproduced — see below |

Timing comes from the client in every case: `getHealthRatio()` returns -1 when no bar would show,
and hitsplats expire on `getDisappearsOnGameCycle()`.

**Hitsplats are the one approximation.** There is no `PostHealthBarConfig` equivalent for them, and
the `SpriteID.Hitmark` group is indexed by something other than the hitsplat type, so the client's
own graphics cannot be resolved. They are drawn as four-point stars coloured per type, covering all
33 types in `HitsplatID`. Unknown types fall back to the damage/zero convention rather than an
invented colour.

The health bar restore has a chicken-and-egg: `PostHealthBarConfig` fires when the client is about
to draw a bar, which is the thing being vetoed. If suppression starts first the event never arrives,
so a plainly drawn bar is used until it does.

## Accessibility

The intuitive red/green/blue mapping puts red and green in opposition — the pair lost in the most
common colour vision deficiency. Adjusting shades cannot fix it: a dichromat's colour space is
effectively two-dimensional, so three categories cannot be separated by hue alone. Each alternate
palette drops the confusable colour and separates on both the surviving hue axis and luminance,
using Okabe-Ito colours where possible. **Distinct ring styles** varies the line pattern instead of
colour, which is the only cue that works under achromatopsia.

Rings are drawn for the three protection prayers and their Deflect equivalents. Combined overheads
and the non-protection prayers render ringless rather than being assigned an invented colour.

## Scope

**In:** local player only; ring and icon size, height, palette, line style; restoring what
suppression removes.

**Out:** other players' and NPCs' overheads. Vetoing their 2D pass would nuke names, health bars and
hitsplats for everyone on screen, and a ring on another player's overhead reads as a prayer
indicator — squarely on the Plugin Hub's restricted list.

## Conflicts

Draw vetoes compose as a logical AND: `RenderCallbackManager.addEntity` returns false as soon as any
registered callback does, and both the current and deprecated registration paths feed the same list.
So running alongside Entity Hider is harmless and idempotent — no last-writer-wins race, no flicker.

## Notes for the Plugin Hub

- `build=standard`, `runeLiteVersion = 'latest.release'`.
- No reflection, no network calls, no input injection, no menu changes.
- Verified against 1.12.39. API details here should be re-checked with `javap` when that moves.
