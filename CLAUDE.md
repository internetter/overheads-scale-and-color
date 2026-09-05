# CLAUDE.md — Overhead Scale & Color (RuneLite plugin)

## What this is

A RuneLite plugin that shrinks the overhead prayer icon above your own player so it stops covering
the character model, and draws a colored ring around it so it stays readable once small. Purely
cosmetic. No automation, no input simulation, no network calls. Local player only.

Formerly "Overhead Prayer Scaler". Renamed during Phase 0, before anything shipped.

## Non-negotiable constraints

1. **Know what has actually been checked.** `DESIGN.md` has two appendices: the verified API
   surface (confirmed with `javap` against the pinned jars) and the still-unverified list. Do not
   promote something from the second list to a factual claim without a human looking at a screen.
2. **No reflection into client internals.** Plugin Hub rejects it. Public `runelite-api` and
   `runelite-client` surfaces only.
3. **Verify every API signature against the resolved jars before using it.** `build.gradle` uses
   `latest.release`, as the Plugin Hub requires — so the target moves. Find what it actually
   resolved to (`./gradlew dependencies --configuration compileClasspath | grep runelite:client`)
   and check that jar with `javap`. Everything here was verified against **1.12.38**; re-check when
   it moves. Treat any method named in prose as a hypothesis. If something does not exist, say so
   and stop — do not substitute a call that merely compiles. *This is not hypothetical: the
   original design was built on `Client#setLocalPlayerHidden2D`, which does not exist.*
4. **Clean up in `shutDown()`.** Unregister the render callback, remove the overlay, drop caches.
   The current design toggles no client state, so there is no flag to restore — if that ever
   changes, restoring it becomes mandatory.
5. **No per-frame allocation in the render path.** Scaled `BufferedImage`s are built once per
   (icon, scale, interpolation). Ring colors and strokes are resolved once and rebuilt only on
   config change. `render()` looks up and draws.
6. **Local player only.** Do not touch other players or NPCs. See `DESIGN.md` "Scope" — this is a
   Plugin Hub review boundary, not just a scope preference.

## Architecture in one paragraph

The client draws overhead icons natively and they cannot be resized in place. So: suppress the
client's 2D draw pass for the local player by registering a `RenderCallback` with
`RenderCallbackManager` and returning `false` from `addEntity` when `drawingUI` is true, then
redraw the icon ourselves at the configured scale in an `ABOVE_SCENE` overlay, positioned with
`Perspective.getCanvasImageLocation` against `getLogicalHeight()` plus a height offset, anchored
bottom-centre. Icon sprites come from `SpriteID.HEADICONS_PRAYER` (440) indexed by
`HeadIcon.ordinal()`. A colored ring is stroked around the icon at a fixed pixel thickness. Full
reasoning, rejected alternatives, and the corrections to the original plan are in `DESIGN.md`.

## Config surface

Two core options plus a ring section. Do not add more without a reason that survives `DESIGN.md`
"Scope".

| Key | Type | Default | Notes |
|---|---|---|---|
| `scale` | int 10–100 | 50 | percent of native size |
| `heightOffset` | int | 40 | tuned on screen, not derived |
| `hideOnlyWhilePraying` | bool | true | DESIGN.md approach A2; restores 2D elements when no overhead is active |
| `smoothScaling` | bool | true | bilinear vs nearest-neighbour |
| `ringEnabled` | bool | true | |
| `ringThickness` | int 1–6 | 2 | screen pixels, constant across scales |
| `ringGap` | int 0–10 | 2 | |
| `ringOutline` | bool | true | dark edge; without it green vanishes on grass |
| `palette` | enum | Standard | includes CVD-safe palettes — see below |
| `distinctStyles` | bool | false | solid / dashed / dotted per type |
| `meleeColor`, `rangedColor`, `magicColor` | Color | — | used only when palette is Custom |

The master on/off is the plugin toggle itself. Do not add a redundant "enabled" boolean.

Suppression, bottom-centre anchoring, and drawing the replacement are **not** configurable. They
were toggles during the spike so the alternatives could be compared on screen; every "off" state
is either useless (no replacement drawn) or actively broken (vanilla and scaled icons stacked on
each other). Deleted before submission rather than shipped as traps.

**Config keys are an API.** Renaming one after release silently wipes users' settings. The group
was renamed to `overheadscalecolor`, and `zPadding` to `heightOffset`, before anything shipped —
the last moment either was free.

## Accessibility

The ring's default red/green/blue mapping is intuitive but puts red and green in opposition — the
pair lost in the most common color vision deficiency (~8% of men). Since the ring exists to make a
small icon more legible, that mapping alone would fail the users who most need it. Hence the
alternate palettes and the color-independent line-style option. If you touch the ring, do not
regress this. Reasoning and the full palette table are in `DESIGN.md` Step 5.

## Layout

```
src/main/java/com/internetter/overheadscalecolor/
  OverheadScaleColorPlugin.java    // lifecycle, render callback veto
  OverheadScaleColorConfig.java    // @ConfigGroup interface
  OverheadScaleColorOverlay.java   // render(), anchor math, ring drawing, icon cache
  RingPalette.java                 // color presets incl. CVD-safe palettes
runelite-plugin.properties         // hub manifest; build=standard
```

## Plugin Hub constraints

These are hard rejection criteria, from [Jagex's third-party client guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1)
and RuneLite's [rejected features list](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features).
Only the ones this plugin could plausibly trip are listed; read the sources before adding anything
substantial.

- Java 11. **No reflection**, no JNI/JNA, no `Unsafe`, no external processes, no dynamic
  classloading or code generation, no Java serialization.
- **No injected input** — mouse, keyboard, or chat text.
- **No prayer switching indicators, attack prediction, or combat prayer recommendations.** This is
  why the ring must stay on your own already-active overhead and never appear on another player or
  an NPC: the same ring elsewhere is a prayer indicator.
- **No player group summaries** including other players' prayer usage. Local player only.
- **No unhiding hidden interface components**, no menu entries that send actions to the server.
- No exposing player information over HTTP; this plugin makes no network calls at all.
- Config group names must be specific. Never rename a key or group without a migration.
- Don't ship a `META-INF/services/net.runelite.client.plugins.Plugin` file, build artifacts, or
  ripped assets. Keep a permissive license.
- Optimise any bundled PNG — Java holds images at `width × height × 4` bytes in memory.

## Coding conventions

- Java 11. RuneLite house style: Lombok `@Slf4j`, `@Inject` field injection, tabs, Allman braces.
- Use `net.runelite.api.gameval` constants rather than magic numbers.
- Log at `debug` in anything per-frame or per-tick, and dedupe it — the overlay uses a bitmask so a
  missing sprite is logged once per icon rather than once per frame.
- Null-check `client.getLocalPlayer()` everywhere. It is null on the login screen and during
  loading, and `render()` runs then.

## Build and test loop

```bash
./gradlew build     # compiles
./gradlew run       # launches the dev client with the plugin side-loaded
```

The plugin appears in the dev client's normal plugin list, **not** the Plugin Hub panel — the Hub
lists published plugins only.

### Dev client login on this machine

`./gradlew run` sets `-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT` on Windows. Norton (and other
AV products) intercept TLS with a locally-generated root CA that lives in the Windows certificate
store but not in the JDK's `cacerts`; without this the client fails login with
`PKIX path building failed`. Dev-run only, never shipped.

Jagex account login needs `.runelite/credentials.properties`, written by launching once from the
Jagex Launcher with `--insecure-write-credentials` in the launcher's client arguments. **That file
allows login bypassing your password — never commit it, never paste it, delete it when done.**
Likewise never paste `~/.runelite/logs/client.log` publicly; it contains display name and account
email.

## Verification discipline

**State plainly which parts were verified in game and which only compiled.** "Compiles clean" is
not "works". Do not write in-game confirmation into the README until it has actually happened. The
project has already had one confidently-wrong API assumption reach a design document; the cost of
that is what this section exists to prevent.

## Manual test matrix

| Case | Expect |
|---|---|
| No prayer active | Nothing drawn, no flicker |
| Each protection prayer, one at a time | Correct icon, correct ring color |
| Smite / Retribution / Redemption | Correct icon, **no** ring |
| Combined overheads (range+mage etc.) | Correct icon, **no** ring |
| Scale 100, offset 0 | Same anchor point as vanilla |
| Scale 10 | Tiny but visible, ring still legible |
| Change scale / palette with prayer active | Updates without restart |
| Each CVD palette | Three types distinguishable |
| Distinct styles on | Solid / dashed / dotted, outline follows the dashes |
| Ring over grass, water, sand, cave floor | Outline keeps it readable everywhere |
| Toggle plugin off | Vanilla icon returns immediately, healthbar/hitsplats return |
| Walking, running, teleporting | Icon tracks the player, no lag or drift |
| Camera zoom in/out, rotate | Scales and tracks correctly |
| Fixed / resizable / stretched mode | Correct in all three |
| GPU plugin on and off | Correct in both |
| Entity Hider enabled simultaneously | No flicker; vetoes compose (see `DESIGN.md` Conflicts) |
| Login screen, world hop, logout | No NPE in the console |

## Things not to do

- Do not add "hide other players' overheads" or any PvP-facing feature. A ring on another player's
  overhead reads as a prayer indicator and is on the hub's restricted list.
- Do not bundle ripped game assets while sprites load from the cache at runtime.
- Do not claim the plugin is on the Plugin Hub, or that a submission was reviewed, unless a real
  PR URL exists.
- Do not invent RuneLite API methods. If the API cannot do something, the correct output is "the
  API cannot do this," not a plausible-looking call.
