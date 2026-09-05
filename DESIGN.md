# DESIGN.md — Overhead Scale & Color

> **Status.** Written before Phase 0, then revised once the spike made contact with the client.
> Sections marked **[revised]** replaced an assumption that turned out to be wrong. The original
> claims are kept in strikethrough or called out explicitly, because knowing *why* the first guess
> failed is worth more than a clean document.
>
> Verification vocabulary used throughout: **verified statically** = checked with `javap` against
> the resolved 1.12.38 jars. **verified in game** = someone looked at the screen and said so.
> **assumed** = neither. These are not interchangeable.

## The problem

Overhead prayer icons are drawn by the game client as part of its 2D entity pass, at a fixed size
derived from the sprite and the camera. There is no scale parameter exposed anywhere in
`runelite-api`, and no config in vanilla OSRS. RuneLite has had open feature requests for
resizable overhead prayers for years with no core implementation, which is itself evidence that
there is no clean hook.

So the plugin cannot resize the icon. It has to **prevent the client from drawing it and draw its
own replacement.**

## Chosen approach: suppress + redraw

### Step 1 — suppress **[revised]**

**The original plan was wrong.** This document previously proposed:

```java
client.setLocalPlayerHidden2D(true);   // does not exist
client.setPlayersHidden2D(true);       // does not exist
```

Neither method exists. `net.runelite.api.Client` in 1.12.38 has **no** methods matching `hide`,
`hidden`, or `hiding` at all. *(verified statically, and the published javadoc agrees.)*

The real mechanism is a **draw-veto callback**. Decompiling the bundled `EntityHiderPlugin` shows
what it actually does:

```java
protected void startUp()  { hooks.registerRenderableDrawListener(drawListener); }
protected void shutDown() { hooks.unregisterRenderableDrawListener(drawListener); }
```

and inside its `shouldDraw(Renderable, boolean drawingUI)`, for the local player:

```java
return drawingUI ? !hideLocalPlayer2D : !hideLocalPlayer;
```

So `drawingUI == true` is the client's 2D pass for that entity, and returning `false` suppresses
it. Same coarse local-player-2D suppression the original design wanted, reached by a different
door.

**Use the non-deprecated API.** `Hooks.RenderableDrawListener` is deprecated in 1.12.38 — Entity
Hider simply has not migrated. The current pair is:

```java
net.runelite.client.callback.RenderCallbackManager#register(RenderCallback)   // @Singleton
net.runelite.client.callback.RenderCallback#addEntity(Renderable, boolean)    // false = veto
```

`Hooks.registerRenderableDrawListener` is now a one-line delegate to `RenderCallbackManager.register`,
so both paths land in the same list. *(verified statically.)*

No flag is being toggled, so there is nothing to restore on shutdown beyond unregistering the
callback. That simplifies the lifecycle relative to the original design.

**The cost, and why there is no surgical alternative.** The veto is coarse: it suppresses the local
player's entire 2D pass, not just the prayer icon. The obvious escape — blank the icon and let the
client draw everything else normally — is not available. `Player` exposes `getOverheadIcon()` and
no setter; `OverheadIcon` appears nowhere else in `runelite-api` or `runelite-client`. (`Player`
*does* have `setSkullIcon(int)`, so the omission looks deliberate rather than accidental.)
*(verified statically.)* The API cannot do it.

So the whole 2D pass goes, taking the healthbar, hitsplats, overhead chat and username with it.
RuneLite's own Entity Hider config describes this only as "the local player's 2D elements" without
enumerating, so the precise list is inference and is on the unverified list below.

Options considered:

- **A1.** Veto always. Accept and document the cost.
- **A2.** Veto only while a head icon is actually active. **Chosen, and the default.**
- **A3.** Redraw the healthbar and hitsplats ourselves. Real scope creep; a separate plugin.

**A2 dominates A1** and the choice does not depend on measuring the collateral first. While a
prayer is active the two are identical; the rest of the time A2 is completely normal and A1 is
still hiding things for no benefit. A1 is strictly worse, so A2 ships on by default. A1 survives
only as the `hideOnlyWhilePraying` toggle, kept as an escape hatch in case the switch-over between
states turns out to be visually noisy in a way static reasoning cannot predict.

What measurement would still change: if the collateral turns out to be trivial (say hitsplats
survive), the toggle stops mattering. If it is severe, A3 becomes worth reconsidering as a separate
plugin. Neither changes the default.

### Step 2 — redraw

An `Overlay` with `OverlayLayer.ABOVE_SCENE` and `OverlayPosition.DYNAMIC` — above the 3D scene,
below interfaces.

```
render(Graphics2D g):
  player = client.getLocalPlayer()            // null on login screen; render() runs then
  icon   = player.getOverheadIcon()           // HeadIcon; null when no prayer
  if (icon == null) return null
  img    = iconCache.get(icon, scale)
  point  = Perspective.getCanvasImageLocation(client, player.getLocalLocation(), img, zOffset)
  if (point == null) return null              // off-screen
  drawRing(g, icon, point, img)               // see Step 5
  g.drawImage(img, point.getX(), y, null)
```

#### The anchor math **[revised]**

This document originally warned that top-left anchoring would make the icon "creep downward as it
shrinks." That was the wrong failure mode, because the anchor is not top-left.
`Perspective.getCanvasImageLocation` centres the image on the projected point in **both** axes:

```java
Point p = localToCanvas(client, localPoint, plane, zOffset);
return new Point(p.getX() - image.getWidth() / 2,
                 p.getY() - image.getHeight() / 2);
```

*(verified statically by decompiling `Perspective`.)*

Under centre anchoring a shrinking icon converges on its own centre, so its **bottom edge rises**
toward the head — the opposite of the predicted bug. Bottom-centre anchoring is therefore:

```java
y = getCanvasImageLocation(...).getY() + (nativeHeight - scaledHeight) / 2;
```

which is the identity at scale 100. Centre anchoring was a toggle during the spike so the two could
be compared on screen; bottom-centre won and the toggle was removed rather than shipped.

#### Height offset

`zOffset = player.getLogicalHeight() + heightOffset`. The raw logical height alone places the icon
too low — close to sitting on the player's head. `heightOffset` defaults to **40**, which was
arrived at by tuning on screen, not derived. A principled constant would be better; this is good
enough for v1 and is user-adjustable.

### Step 3 — where the sprite images come from

**Route 1 (chosen): load from the cache at runtime.** `net.runelite.api.gameval.SpriteID` contains
`HEADICONS_PRAYER = 440`. Load with `SpriteManager#getSprite(440, index)`.

The index is **`HeadIcon.ordinal()`**. The enum has exactly 15 values, matching that archive's file
count. *(The constant and the enum are verified statically; the ordinal-to-index correspondence is
inferred, and is only confirmed to the extent that icons render and look correct in game.)*

No bundled assets, always matches live game art. Route 2 — shipping PNGs in `src/main/resources` —
remains the fallback if the mapping ever drifts, but is not currently needed.

### Step 4 — scaling quality

- Cache one `BufferedImage` per `(HeadIcon, scalePercent, interpolation)`. Invalidate on config
  change. Nothing is built in the render path.
- `TYPE_INT_ARGB`, bilinear interpolation by default. Nearest-neighbour stays a user toggle — it is
  a genuine preference at these sizes, not a debugging aid.
- **Floor the scale at 10%.** The source sprite is roughly 30px tall, so 1% is a third of a pixel:
  it does not render, it disappears. A slider that bottoms out at invisible is a hide toggle
  wearing a costume.

### Step 5 — the colored ring **[added]**

A ring drawn around the icon, colored by prayer type, so the icon stays readable once shrunk. This
was not in the original scope; it was requested after the spike showed the shrink working.

**Stroked at render time, not baked into the cached image.** Baking it in would scale the ring down
with the icon, making it thinnest at exactly the sizes where it is most needed. Thickness is
therefore in screen pixels and constant across scales. The cost is a few `drawOval` calls per
frame; the strokes and colors themselves are cached and rebuilt only on config change.

**Coverage.** Only the three protection prayers and their Deflect equivalents are ringed:

| HeadIcon | Ring |
|---|---|
| `MELEE`, `DEFLECT_MELEE` | melee color |
| `RANGED`, `DEFLECT_RANGE` | ranged color |
| `MAGIC`, `DEFLECT_MAGE` | magic color |
| `RANGE_MAGE`, `RANGE_MELEE`, `MAGE_MELEE`, `RANGE_MAGE_MELEE` | none — no single correct color |
| `SMITE`, `RETRIBUTION`, `REDEMPTION`, `WRATH`, `SOUL_SPLIT` | none — not protection prayers |

Rendering the ambiguous ones ringless is deliberate. Inventing a color for a combined overhead
would be worse than showing none.

**A dark outline on both edges, on by default.** This matters more than hue choice: unoutlined
green disappears on grass and blue disappears on water.

#### Color vision deficiency

The intuitive mapping — red melee, green ranged, blue magic — puts red and green in opposition,
which is precisely the pair lost in the most common deficiency (red-green, roughly 8% of men).
Since the ring exists to make a small icon *more* legible, shipping only that mapping would fail
the users who most need the feature.

Nudging the shades does not fix this. A dichromat's color space is effectively two-dimensional —
one surviving hue axis plus luminance — so three categories cannot be separated by hue alone. Each
palette therefore separates on **both** the surviving axis and luminance, and drops the confusable
color entirely rather than tuning it:

| Palette | Melee | Ranged | Magic | For |
|---|---|---|---|---|
| Standard | `#FF4136` | `#2ECC40` | `#2E9BFF` | default |
| Red-green friendly | `#E69F00` orange | `#FFFFFF` white | `#0072B2` blue | deuteran / protan |
| Blue-yellow friendly | `#D55E00` vermillion | `#009E73` green | `#CC79A7` magenta | tritan |
| Monochrome | `#FFFFFF` | `#A0A0A0` | `#4A4A4A` | achromatopsia, max contrast |
| Custom | user | user | user | anything else |

Colors are drawn from the Okabe-Ito qualitative palette where possible — the long-standing
reference set for CVD-safe categorical encoding.

**Distinct ring styles** is a separate toggle that varies the line pattern by type — melee solid,
ranged dashed, magic dotted. It is a cue that does not depend on color at all, which is the only
thing that works under full achromatopsia, and it is useful to everyone. The outline follows the
same dash pattern, otherwise a solid dark ring underneath would swamp the shape cue.

## Rejected alternatives

| Approach | Why not |
|---|---|
| `client.setLocalPlayerHidden2D` | Does not exist. See Step 1. |
| `SpriteManager#addSpriteOverrides` / `client.getSpriteOverrides()` with a pre-shrunk sprite | Expected not to reach overhead icons; prior art for overhead skull icons showed the map populated and the call firing with no effect on rendering. Moot in any case now that suppress+redraw works. `SpriteOverride` also requires a bundled `getFileName()` per sprite id, which cannot address sub-indices of an archive. |
| Baking the ring into the cached image | Ring would shrink with the icon, thinning out exactly when needed. See Step 5. |
| Reflection into the client's head-icon sprite array | Plugin Hub rejects reflection outright, and it breaks on every client update. |
| Drawing an opaque patch over the vanilla icon | The 3D scene is behind it. Impossible to do cleanly. |
| A mixin | Mixins live in the injected client core, not in hub plugins. Only viable if you fork RuneLite. |
| Anchoring to a static screen position | The icon must track the player through movement, camera rotation, and zoom. |

## Scope

**In scope for v1:**

- Local player only.
- Scale (10–100%), bottom-centre anchoring, height offset.
- Colored ring: palettes including CVD-safe options, custom colors, thickness, gap, outline,
  optional distinct line styles.

**Out of scope:**

- **Other players' and NPCs' overheads.** Vetoing other players' 2D pass nukes names, healthbars
  and hitsplats for everyone on screen — a huge behavioural change for a cosmetic plugin, and a
  PvP-relevant one that invites reviewer scrutiny. If it is ever added it must be separate,
  default-off, and clearly labelled.
- Per-prayer scale values, custom icon art, repositioning, opacity, animation.
- Skull icons, hitsplats, healthbars, overhead chat.

## Conflicts **[revised]**

**The conflict this document originally predicted does not exist.** The original text said Entity
Hider and this plugin would both reapply flags every frame, that last writer wins, and to expect
flicker or one plugin silently losing.

That was reasoned from the shared-mutable-flag design, which is not how it works.
`RenderCallbackManager.addEntity` iterates its callbacks and returns `false` on the **first** one
that returns `false`:

```
if (callbacks.isEmpty()) return true;
for (i = 0; i < callbacks.size(); i++)
    if (!callbacks.get(i).addEntity(renderable, drawingUI)) return false;   // short-circuit
return true;
```

*(verified statically.)* That is a logical AND across every registered plugin, in a single
`CopyOnWriteArrayList` that both the deprecated and current registration paths feed. There is no
shared mutable state, so there is no last-writer-wins race and no per-frame flicker war. If either
plugin vetoes, the draw is suppressed; neither can un-veto the other.

Practical consequence: running Entity Hider's "hide local player 2D" alongside this plugin is
harmless and idempotent. **Still worth one in-game confirmation** before the README states it as
fact.

## Appendix — verified API surface

Checked with `javap` against the resolved `net.runelite:client:1.12.38` and
`runelite-api:1.12.38` jars. Re-check this table when the pinned version moves.

| Call | Status |
|---|---|
| `Player#getOverheadIcon()` → `HeadIcon` | exists (on `Player`, not `Actor`) |
| `Player#setOverheadIcon(...)` | **does not exist** — `OverheadIcon` appears only as a getter, nowhere else in api or client. There is no way to blank the icon and let the client draw the rest. |
| `Player#setSkullIcon(int)` | exists — note the asymmetry; the skull icon *is* writable |
| `Actor#getLogicalHeight()` → `int` | exists |
| `NPC#getOverheadSpriteIds()` → `short[]`, `getOverheadArchiveIds()` → `int[]` | exist |
| `SpriteManager#getSprite(int, int)` → `BufferedImage` | exists |
| `SpriteID.HEADICONS_PRAYER` | `= 440` (also `HEADICONS_PK = 439`, `HEADICONS_HINT = 441`) |
| `Perspective#getCanvasImageLocation(Client, LocalPoint, BufferedImage, int)` | exists; centres in both axes |
| `RenderCallbackManager#register/unregister(RenderCallback)` | exist; `@Singleton`, backed by `CopyOnWriteArrayList` |
| `RenderCallback#addEntity(Renderable, boolean)` | exists; `false` vetoes the draw |
| `Hooks.RenderableDrawListener` + register/unregister | exist but **deprecated**; delegate to `RenderCallbackManager` |
| `Client#getSpriteOverrides()`, `#createSpritePixels(int[], int, int)` | exist |
| `OverlayLayer.ABOVE_SCENE`, `OverlayPosition.DYNAMIC` | exist |
| `@ConfigSection`, `@Alpha`, `@Range` | exist |
| `Client#setLocalPlayerHidden2D(boolean)` | **does not exist** — no hide/hidden methods on `Client` at all |

### `HeadIcon` — all 15 values, in ordinal order

Ordinal doubles as the sprite index into archive 440.

| # | Value | # | Value | # | Value |
|---|---|---|---|---|---|
| 0 | `MELEE` | 5 | `REDEMPTION` | 10 | `WRATH` |
| 1 | `RANGED` | 6 | `RANGE_MAGE` | 11 | `SOUL_SPLIT` |
| 2 | `MAGIC` | 7 | `RANGE_MELEE` | 12 | `DEFLECT_MELEE` |
| 3 | `RETRIBUTION` | 8 | `MAGE_MELEE` | 13 | `DEFLECT_RANGE` |
| 4 | `SMITE` | 9 | `RANGE_MAGE_MELEE` | 14 | `DEFLECT_MAGE` |

It is not "the six prayers" — 6–9 are combined overheads and 10–14 are Ancient/Arceuus and
Leagues-era icons.

## Appendix — still unverified

Carried over from the Phase 0 investigation. Nothing here is known to be broken; it is simply not
yet confirmed on screen. The README states the same split for users.

- **What else the 2D veto hides.** Expected: healthbar, hitsplats, overhead chat, username.
  RuneLite's own Entity Hider config says only "the local player's 2D elements" without
  enumerating, so the precise list is inference. Mitigated by defaulting to A2 rather than
  measured away.
- **Scale 100 + offset 0 matching vanilla placement exactly.** Needs an A/B screenshot.
- Camera zoom and rotation; fixed / resizable / stretched modes; GPU plugin on and off; teleport;
  world hop and logout/login.
- **That every `HeadIcon` maps to the correct sprite.** Icons render and look right, but the
  ordinal-to-index correspondence has not been checked per prayer. Smite, Retribution and
  Redemption are the ones a mistake would hide in.
- Entity Hider running simultaneously. Expected to compose cleanly (see Conflicts); unconfirmed.

## Plugin Hub notes

- `runelite-plugin.properties` with `build=standard` makes the PR eligible for automated review.
- Description leads with "cosmetic / visual only, no automation."
- The ring is local-player only. A ring on *other* players' overheads would read as a PvP prayer
  indicator and land squarely in the restricted list — do not add it.
- Nothing here touches menus, input, or the network. No reflection.
- Hub reviewers push back on infinitely-scaling config keys and on incorrect Swing thread use.
  Neither applies while the plugin stays this size.
