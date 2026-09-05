package com.internetter.overheadscalecolor;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

/**
 * Config keys are an API: renaming one after release silently resets every user's saved settings.
 * Add to this interface sparingly -- see the "Scope" section of DESIGN.md.
 */
@ConfigGroup(OverheadScaleColorConfig.GROUP)
public interface OverheadScaleColorConfig extends Config
{
	String GROUP = "overheadscalecolor";

	@ConfigSection(
		name = "Ring",
		description = "Colored ring drawn around the overhead icon.",
		position = 20
	)
	String RING_SECTION = "ring";

	@ConfigItem(
		position = 1,
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
		position = 2,
		keyName = "heightOffset",
		name = "Height offset",
		description = "Raises the icon above your head. The player's logical height alone sits it too low, so the default is 40."
	)
	@Range(min = -200, max = 200)
	default int heightOffset()
	{
		return 40;
	}

	@ConfigItem(
		position = 3,
		keyName = "hideOnlyWhilePraying",
		name = "Only hide while praying",
		description =
			"The client draws your icon, healthbar, hitsplats, overhead chat and name in a single pass that can only be suppressed as a whole, "
				+ "so replacing the icon also hides those. With this ON that cost applies only while an overhead prayer is active, and everything "
				+ "is normal the rest of the time. Turn it OFF only if you find the switch-over distracting."
	)
	default boolean hideOnlyWhilePraying()
	{
		return true;
	}

	@ConfigItem(
		position = 4,
		keyName = "smoothScaling",
		name = "Smooth scaling",
		description = "Bilinear downscaling. Turn off for nearest-neighbour, which keeps hard pixel edges."
	)
	default boolean smoothScaling()
	{
		return true;
	}

	// ---- Ring ----
	//
	// Only the three protection prayers and their Deflect equivalents get a ring. Combined
	// overheads (RANGE_MAGE, RANGE_MELEE, MAGE_MELEE, RANGE_MAGE_MELEE) have no single correct
	// color, and Smite / Retribution / Redemption / Wrath / Soul Split are not protection prayers
	// at all. Those render ringless rather than guessing.

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
		description = "Ring thickness in screen pixels. Constant across scales, so it does not thin out as the icon shrinks.",
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
		description = "Outline the ring in dark grey. Recommended -- without it green is hard to see on grass and blue is hard to see on water.",
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
