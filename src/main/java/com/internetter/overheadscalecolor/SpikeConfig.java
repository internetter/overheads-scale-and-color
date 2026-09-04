package com.internetter.overheadscalecolor;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

/**
 * Plugin configuration. A few items here (debug markers, the interpolation toggle) exist for
 * on-screen comparison rather than for users, and should be reviewed before release.
 *
 * Group name was renamed from "overheadprayerscalerspike" while nothing had shipped. Renaming a
 * config group silently resets every user's saved settings, so this was the last free moment to
 * do it. See the Plugin Hub constraints in CLAUDE.md.
 */
@ConfigGroup(SpikeConfig.GROUP)
public interface SpikeConfig extends Config
{
	String GROUP = "overheadscalecolor";

	@ConfigSection(
		name = "Ring",
		description = "Colored ring drawn around the overhead icon.",
		position = 20
	)
	String RING_SECTION = "ring";

	// ---- Suppression: stop the client drawing its own overhead icon ----

	@ConfigItem(
		position = 1,
		keyName = "suppress2D",
		name = "Hide the vanilla icon",
		description = "Stop the client drawing its own overhead icon, so only the scaled one shows. Turning this off leaves both drawn on top of each other."
	)
	default boolean suppress2D()
	{
		return true;
	}

	@ConfigItem(
		position = 2,
		keyName = "suppressOnlyWhenIconActive",
		name = "Only while praying",
		description =
			"The client draws your icon, healthbar, hitsplats, overhead chat and name in one pass, and it can only be suppressed as a whole -- "
				+ "so hiding the vanilla icon also hides those. With this ON that cost applies only while an overhead prayer is active; "
				+ "the rest of the time everything is normal. Turning it OFF hides them permanently and gains nothing. Leave it on."
	)
	default boolean suppressOnlyWhenIconActive()
	{
		return true;
	}

	// ---- Placement and size ----

	@ConfigItem(
		position = 3,
		keyName = "drawOverlay",
		name = "Draw replacement icon",
		description = "Draw our own head icon in an ABOVE_SCENE overlay."
	)
	default boolean drawOverlay()
	{
		return true;
	}

	@ConfigItem(
		position = 4,
		keyName = "scale",
		name = "Scale %",
		description = "Rendered size as a percent of native size. 100 is vanilla size; the floor is 10 because the source sprite is only ~30px tall and anything smaller stops rendering at all."
	)
	@Range(min = 10, max = 100)
	default int scale()
	{
		return 50;
	}

	@ConfigItem(
		position = 5,
		keyName = "anchorBottom",
		name = "Anchor bottom-center",
		description = "On: keep the icon's bottom edge fixed as it shrinks. Off: keep its center fixed (raw getCanvasImageLocation)."
	)
	default boolean anchorBottom()
	{
		return true;
	}

	@ConfigItem(
		position = 6,
		keyName = "zPadding",
		name = "Height offset",
		description = "Added to the player's logical height to tune vertical placement. Default 40 sits the icon near its vanilla position rather than down on the player's head."
	)
	@Range(min = -200, max = 200)
	default int zPadding()
	{
		return 40;
	}

	// ---- Downscale quality and debugging ----

	@ConfigItem(
		position = 7,
		keyName = "smoothScaling",
		name = "Bilinear downscale",
		description = "On: bilinear. Off: nearest-neighbour. Compare both on screen before choosing a default."
	)
	default boolean smoothScaling()
	{
		return true;
	}

	@ConfigItem(
		position = 8,
		keyName = "debugMarkers",
		name = "Debug markers",
		description = "Draw the icon bounding box and a crosshair at the raw projected point."
	)
	default boolean debugMarkers()
	{
		return false;
	}

	// ---- Coloured ring ----
	//
	// Only the three protection prayers and their Deflect equivalents get a ring. Combined
	// overheads (RANGE_MAGE, RANGE_MELEE, MAGE_MELEE, RANGE_MAGE_MELEE) have no single correct
	// colour, and Smite / Retribution / Redemption / Wrath / Soul Split are not protection
	// prayers at all. Those render ringless rather than guessing.

	@ConfigItem(
		position = 21,
		keyName = "ringEnabled",
		name = "Show ring",
		description = "Draw a colored ring around the icon so it stays readable at small scales.",
		section = RING_SECTION
	)
	default boolean ringEnabled()
	{
		return true;
	}

	@ConfigItem(
		position = 22,
		keyName = "ringThickness",
		name = "Thickness",
		description = "Ring thickness in pixels. Fixed in screen pixels, so it does not thin out as the icon shrinks.",
		section = RING_SECTION
	)
	@Range(min = 1, max = 6)
	default int ringThickness()
	{
		return 2;
	}

	@ConfigItem(
		position = 23,
		keyName = "ringGap",
		name = "Gap",
		description = "Pixels between the icon edge and the ring.",
		section = RING_SECTION
	)
	@Range(min = 0, max = 10)
	default int ringGap()
	{
		return 2;
	}

	@ConfigItem(
		position = 24,
		keyName = "ringOutline",
		name = "Dark outline",
		description = "Outline the ring in dark grey. Strongly recommended -- without it green vanishes on grass and blue vanishes on water.",
		section = RING_SECTION
	)
	default boolean ringOutline()
	{
		return true;
	}

	@ConfigItem(
		position = 25,
		keyName = "palette",
		name = "Color palette",
		description = "Ring colors. The alternatives are chosen for the common forms of color vision deficiency; pick the one that matches your vision, or Custom to set all three yourself.",
		section = RING_SECTION
	)
	default RingPalette palette()
	{
		return RingPalette.STANDARD;
	}

	@ConfigItem(
		position = 26,
		keyName = "distinctStyles",
		name = "Distinct ring styles",
		description = "Also vary the ring's line pattern by prayer type: melee solid, ranged dashed, magic dotted. A cue that does not depend on color at all -- recommended with the Monochrome palette, and useful to anyone.",
		section = RING_SECTION
	)
	default boolean distinctStyles()
	{
		return false;
	}

	// The three pickers below apply only when the palette above is set to Custom. RuneLite has no
	// clean way to hide config items conditionally, so they stay visible and say so instead.

	@Alpha
	@ConfigItem(
		position = 27,
		keyName = "meleeColor",
		name = "Custom: Melee",
		description = "Ring color for Protect from Melee (and Deflect Melee). Used only when the palette is set to Custom.",
		section = RING_SECTION
	)
	default Color meleeColor()
	{
		return new Color(0xFF4136);
	}

	@Alpha
	@ConfigItem(
		position = 28,
		keyName = "rangedColor",
		name = "Custom: Ranged",
		description = "Ring color for Protect from Missiles (and Deflect Missiles). Used only when the palette is set to Custom.",
		section = RING_SECTION
	)
	default Color rangedColor()
	{
		return new Color(0x2ECC40);
	}

	@Alpha
	@ConfigItem(
		position = 29,
		keyName = "magicColor",
		name = "Custom: Magic",
		description = "Ring color for Protect from Magic (and Deflect Magic). Used only when the palette is set to Custom.",
		section = RING_SECTION
	)
	default Color magicColor()
	{
		return new Color(0x2E9BFF);
	}
}
