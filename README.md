# Overhead Scale & Color

A RuneLite plugin that shrinks the overhead prayer icon above **your own** player so it stops
covering your character, and draws a colored ring around it so it stays readable once small.

Cosmetic and visual only. No automation, no input simulation, no network calls, no reflection.
Local player only — it does not touch other players or NPCs.

> **Status: in development. Not submitted to the Plugin Hub.** Parts of this have been confirmed on
> screen and parts have not; the "What has actually been verified" section below says which. There
> is no Hub listing and no submission PR.

## What it does

The game client draws overhead icons at a fixed size and offers no way to resize them. So this
plugin suppresses the client's 2D draw pass for your own player and redraws the icon itself, at
whatever scale you pick.

**That suppression is coarse**, and it is the plugin's main trade-off: it hides your player's other
2D elements too. Exactly which ones is not yet documented here, because it has not been measured
carefully enough to state — see below.

## Options

| Option | Default | What it does |
|---|---|---|
| Scale % | 50 | Size as a percent of native. Floor is 10 — the source sprite is ~30px, below that it stops rendering. |
| Height offset | 40 | Vertical placement. Raise it if the icon sits on your head. |
| Anchor bottom-center | on | Keeps the icon's bottom edge fixed as it shrinks. |
| Bilinear downscale | on | Off gives nearest-neighbour. |
| **Ring** | | |
| Show ring | on | |
| Thickness | 2 | Screen pixels — stays constant as the icon shrinks. |
| Gap | 2 | Space between icon and ring. |
| Dark outline | on | Keeps the ring visible over grass, water, and sand. |
| Color palette | Standard | See accessibility, below. |
| Distinct ring styles | off | Solid / dashed / dotted per prayer type. |
| Custom colors | — | Used only when the palette is set to Custom. |

Rings are drawn for the three protection prayers and their Deflect equivalents. Combined overheads
and the non-protection prayers (Smite, Retribution, Redemption, Wrath, Soul Split) are deliberately
left ringless rather than assigned an invented color.

## Accessibility

The obvious mapping — red melee, green ranged, blue magic — puts red and green in opposition, which
is exactly the pair lost in the most common color vision deficiency (roughly 8% of men). Since the
ring exists to make a small icon easier to see, shipping only that mapping would fail the people
who most need it.

Shifting the shades does not fix it: with dichromacy the color space is effectively two-dimensional,
so three categories cannot be told apart by hue alone. Each alternate palette separates on both the
surviving hue axis and luminance, and drops the confusable color rather than tuning it.

| Palette | Melee | Ranged | Magic |
|---|---|---|---|
| Standard | red `#FF4136` | green `#2ECC40` | blue `#2E9BFF` |
| Red-green friendly (deuteran / protan) | orange `#E69F00` | white `#FFFFFF` | blue `#0072B2` |
| Blue-yellow friendly (tritan) | vermillion `#D55E00` | green `#009E73` | magenta `#CC79A7` |
| Monochrome / high contrast | white | light grey | dark grey |
| Custom | your choice | your choice | your choice |

Colors come from the Okabe-Ito qualitative palette where possible.

**Distinct ring styles** varies the line pattern by prayer type instead of relying on color at all —
the only cue that works under full achromatopsia, and useful to anyone. Recommended alongside the
Monochrome palette.

## What has actually been verified

Being specific about this because "it compiles" and "it works" are different claims.

**Confirmed on screen:**

- The icon is suppressed and the replacement is drawn in its place.
- Sprites load from the game cache and show the correct art.
- The icon tracks the player and scales.

**Verified against the RuneLite 1.12.38 jars, but not on screen:**

- Draw vetoes from multiple plugins compose as a logical AND, so running this alongside Entity
  Hider should not flicker or conflict.

**Not yet verified at all:**

- The full list of what else the 2D suppression hides (healthbar, hitsplats, overhead chat,
  username).
- That scale 100 with offset 0 matches vanilla placement exactly.
- Camera zoom and rotation, fixed/resizable/stretched modes, GPU plugin on and off.
- Behaviour across world hop and logout/login.
- That every `HeadIcon` maps to the right sprite. Smite, Retribution and Redemption are the easy
  ones to get wrong.

## Building

Requires JDK 11.

```bash
./gradlew build     # compile
./gradlew run       # launch a dev client with the plugin side-loaded
```

The plugin appears in the dev client's normal plugin list, not the Plugin Hub panel — the Hub lists
published plugins only.

On Windows the `run` task sets `-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT`. Antivirus products
that intercept TLS (Norton among them) install a root CA into the Windows certificate store that
the JDK's own `cacerts` does not have, which otherwise makes the dev client fail login with
`PKIX path building failed`. This affects the dev launcher only and is not part of the plugin.

Logging into a Jagex account from a dev client needs `.runelite/credentials.properties`, produced by
launching once from the Jagex Launcher with `--insecure-write-credentials` in its client arguments.
**That file permits login without your password. Do not commit it or share it, and delete it when
you are done developing.**

## Documentation

- `DESIGN.md` — how it works, what was tried and rejected, which original assumptions turned out to
  be wrong, plus appendices listing the verified API surface and what remains unverified.
- `CLAUDE.md` — working notes, conventions, and Plugin Hub constraints for contributors.

## License

BSD 2-Clause.
