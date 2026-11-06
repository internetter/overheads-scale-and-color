# CLAUDE.md — Overhead Scale & Color (RuneLite plugin)

## What this is

A RuneLite plugin that replaces the overhead prayer icon above your own player with a colored ring,
or a smaller copy of the icon. Purely cosmetic. No automation, no input simulation, no network
calls. Local player only.

## Non-negotiable constraints

1. **Know what has actually been checked.** "Compiles" and "works" are different claims. Do not
   write an in-game behaviour into the docs without a human having looked at a screen.
2. **No reflection into client internals.** Plugin Hub rejects it. Public `runelite-api` and
   `runelite-client` surfaces only.
3. **Verify every API signature against the resolved jars before using it.** `build.gradle` uses
   `latest.release`, as the Plugin Hub requires — so the target moves. Find what it actually
   resolved to (`./gradlew dependencies --configuration compileClasspath | grep runelite:client`)
   and check that jar with `javap`. Everything here was verified against **1.13.6** (`javap`, plus a
   clean `-Xlint:all` compile); re-check when it moves. Treat any method named in prose as a hypothesis. If something does not exist, say so
   and stop — do not substitute a call that merely compiles. *This is not hypothetical: the
   original design was built on `Client#setLocalPlayerHidden2D`, which does not exist.*
4. **Clean up in `shutDown()`.** Unregister the render callback, remove the overlay, drop caches.
   The current design toggles no client state, so there is no flag to restore — if that ever
   changes, restoring it becomes mandatory.
5. **No per-frame allocation in the render path.** Images, ring colors and strokes are memoised and
   rebuilt only on config change. The one exception is a `GradientPaint` per visible hitsplat,
   bounded at four and only while splats are on screen. (Splat text is built once per splat in
   `RestoredHitsplat`, not per frame.)
6. **Local player only.** Do not touch other players or NPCs. See `DESIGN.md` "Scope" — this is a
   Plugin Hub review boundary, not just a scope preference.

## Architecture in one paragraph

The client offers no way to resize or hide an overhead icon on its own, so the plugin vetoes the
local player's 2D draw pass (`RenderCallback` registered with `RenderCallbackManager`, returning
`false` from `addEntity` when `drawingUI` is true) and draws its own indicator in an overlay. That
veto is all-or-nothing: it takes the health bar, hitsplats, PK skull and overhead chat with the
icon, so the plugin draws all of those back. The overlay is on `UNDER_WIDGETS`, not `ABOVE_SCENE` —
see `DESIGN.md`, this one is not obvious. Full reasoning and the client constraints are in
`DESIGN.md`.

## Config surface

Two core options plus a ring section. Do not add more without a reason that survives `DESIGN.md`
"Scope".

| Key | Type | Default | Notes |
|---|---|---|---|
| `mode` | enum | Ring only | Ring only, or Shrink icon |
| `scale` | int 10–250 | 50 | ring size, or icon size in Shrink mode |
| `heightOffset` | int | 70 | tuned on screen, not derived |
| `hideOnlyWhilePraying` | bool | true | no suppression when no overhead is active; when to suppress is decided only in `OverheadScaleColorOverlay.suppresses` |
| `smoothScaling` | bool | true | Shrink mode only |
| `restoreHealthBar` / `restoreHitsplats` / `restoreSkull` / `restoreOverheadChat` | bool | true | each with its own offset (`healthBarOffset`, `hitsplatOffset`, `skullOffset`, `chatOffset`) |
| `hitsplatSize`, `hitsplatLingerMs` | int | 26, 600 | restored hitsplats only |
| `chatColor` | Color | white | restored overhead chat only |
| `ringEnabled`, `ringThickness`, `ringGap`, `ringOutline` | | | |
| `palette` | enum | Standard | includes CVD-safe palettes |
| `distinctStyles` | bool | false | solid / dashed / dotted per type |
| `meleeColor`, `rangedColor`, `magicColor` | Color | — | used only when palette is Custom |

The master on/off is the plugin toggle itself. Do not add a redundant "enabled" boolean.

**Config keys are an API.** Renaming one after release silently wipes users' settings. The group
was renamed to `overheadscalecolor`, and `zPadding` to `heightOffset`, before anything shipped —
the last moment either was free.

## Accessibility

The ring's default red/green/blue mapping is intuitive but puts red and green in opposition — the
pair lost in the most common color vision deficiency (~8% of men). Since the ring exists to make a
small icon more legible, that mapping alone would fail the users who most need it. Hence the
alternate palettes and the color-independent line-style option. If you touch the ring, do not
regress this. Reasoning and the full palette table are in `DESIGN.md`.

## Layout

```
src/main/java/com/internetter/overheadscalecolor/
  OverheadScaleColorPlugin.java    // lifecycle, render callback veto
  OverheadScaleColorConfig.java    // @ConfigGroup interface
  OverheadScaleColorOverlay.java   // render(), anchor math, ring, and the restored 2D elements
  OverheadMode.java                // Ring only / Shrink icon
  RingPalette.java                 // color presets incl. CVD-safe palettes
  RestoredHitsplat.java            // one recorded hitsplat and its color
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
| No prayer active | Nothing drawn, everything vanilla |
| Each protection prayer | Correct ring color; correct icon in Shrink mode |
| Smite / Retribution / Redemption, Ring only | Vanilla icon, untouched; real health bar / hitsplats |
| Smite / Retribution / Redemption, Shrink | Shrunk icon, **no** ring |
| Taking damage while praying | Health bar, hitsplats and their timing all present |
| Skulled while praying | Skull still visible |
| Talking while praying | Overhead chat still visible |
| Each CVD palette / distinct styles | Three types distinguishable |
| Camera zoom, rotate; fixed / resizable / stretched | Tracks correctly |
| GPU plugin on and off | Correct in both |
| Entity Hider enabled simultaneously | No flicker; vetoes compose |
| Login screen, world hop, logout | No NPE in the console |

## Things not to do

- Do not add "hide other players' overheads" or any PvP-facing feature. A ring on another player's
  overhead reads as a prayer indicator and is on the hub's restricted list.
- Do not bundle ripped game assets while sprites load from the cache at runtime.
- Do not claim the plugin is on the Plugin Hub, or that a submission was reviewed, unless a real
  PR URL exists.
- Do not invent RuneLite API methods. If the API cannot do something, the correct output is "the
  API cannot do this," not a plausible-looking call.
