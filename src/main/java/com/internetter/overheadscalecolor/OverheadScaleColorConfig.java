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
		position = 0,
		keyName = "mode",
		name = "Mode",
		description =
			"Ring only: replaces the Protect from Melee / Missiles / Magic icon with just the coloured ring. Other overheads "
				+ "(Smite, Retribution, Redemption) are left exactly as normal. "
				+ "Shrink icon: replaces every overhead with a smaller copy, ringed for the protection prayers. While the client's "
				+ "own icon is hidden, so are your health bar, hitsplats, skull and overhead chat -- the plugin draws those back."
	)
	default OverheadMode mode()
	{
		return OverheadMode.RING_ONLY;
	}

	@ConfigItem(
		position = 1,
		keyName = "scale",
		name = "Scale %",
		description = "Size of the indicator as a percent of the normal icon -- the ring in Ring only mode, the icon in Shrink mode. Above 100 the shrink-mode icon is enlarged and will look soft, since the source sprite is only 24px."
	)
	@Range(min = 10, max = 250)
	default int scale()
	{
		return 50;
	}

	@ConfigItem(
		position = 2,
		keyName = "heightOffset",
		name = "Height offset",
		description = "Height of the ring, and the icon in Shrink mode, above your head."
	)
	@Range(min = -200, max = 200)
	default int heightOffset()
	{
		return 70;
	}

	@ConfigItem(
		position = 3,
		keyName = "hideOnlyWhilePraying",
		name = "Only hide while praying",
		description =
			"Limits the hiding to the moments an overhead prayer is actually active, so everything is normal "
				+ "the rest of the time. Turn it OFF only if you find the switch-over distracting. In Ring only mode, "
				+ "Smite / Retribution / Redemption are always left normal either way."
	)
	default boolean hideOnlyWhilePraying()
	{
		return true;
	}

	@ConfigItem(
		position = 4,
		keyName = "smoothScaling",
		name = "Smooth scaling",
		description = "Shrink mode. Bilinear downscaling; turn off for nearest-neighbour, which keeps hard pixel edges."
	)
	default boolean smoothScaling()
	{
		return true;
	}

	@ConfigItem(
		position = 5,
		keyName = "restoreHealthBar",
		name = "Restore health bar",
		description =
			"Hiding the client's icon also hides its health bar, so this draws one back using the client's "
				+ "own bar sprites, appearing and disappearing exactly when the real one would."
	)
	default boolean restoreHealthBar()
	{
		return true;
	}

	@ConfigItem(
		position = 6,
		keyName = "healthBarOffset",
		name = "Health bar height",
		description = "Vertical placement of the restored health bar. Adjust until it sits where the normal one did."
	)
	@Range(min = -200, max = 200)
	default int healthBarOffset()
	{
		return 30;
	}

	@ConfigItem(
		position = 7,
		keyName = "restoreHitsplats",
		name = "Restore hitsplats",
		description =
			"Redraws the hitsplats hiding the icon removed. The client does not expose its hitsplat "
				+ "graphics, so these are drawn rather than reproduced -- close, but not pixel-identical to normal ones."
	)
	default boolean restoreHitsplats()
	{
		return true;
	}

	@ConfigItem(
		position = 8,
		keyName = "hitsplatOffset",
		name = "Hitsplat height",
		description = "Vertical placement of the restored hitsplats."
	)
	@Range(min = -200, max = 200)
	default int hitsplatOffset()
	{
		return -75;
	}

	@ConfigItem(
		position = 10,
		keyName = "hitsplatSize",
		name = "Hitsplat size",
		description = "Width of the restored hitsplat stars in pixels."
	)
	@Range(min = 14, max = 44)
	default int hitsplatSize()
	{
		return 26;
	}

	@ConfigItem(
		position = 11,
		keyName = "hitsplatLingerMs",
		name = "Hitsplat linger (ms)",
		description = "Extra time restored hitsplats stay on screen beyond the moment the client would have dropped them."
	)
	@Range(min = 0, max = 2000)
	default int hitsplatLingerMs()
	{
		return 600;
	}

	@ConfigItem(
		position = 12,
		keyName = "restoreSkull",
		name = "Restore PK skull",
		description =
			"Hiding the client's icon also hides your PK skull, so this draws it back from the client's own "
				+ "skull sprite, scaled to match the prayer icon."
	)
	default boolean restoreSkull()
	{
		return true;
	}

	@ConfigItem(
		position = 13,
		keyName = "skullOffset",
		name = "Skull height",
		description = "Height of the restored PK skull. Normally sits below the prayer icon."
	)
	@Range(min = -200, max = 200)
	default int skullOffset()
	{
		return 40;
	}

	@ConfigItem(
		position = 14,
		keyName = "restoreOverheadChat",
		name = "Restore overhead chat",
		description =
			"Hiding the client's icon also hides your overhead chat text, so this draws it back in the game's own font. "
				+ "Chat effects such as wave and flashing colours are not reproduced and render as plain text."
	)
	default boolean restoreOverheadChat()
	{
		return true;
	}

	@ConfigItem(
		position = 15,
		keyName = "chatOffset",
		name = "Overhead chat height",
		description = "Height of the restored overhead chat text. Normally sits above the prayer icon."
	)
	@Range(min = -200, max = 300)
	default int chatOffset()
	{
		return 110;
	}

	@Alpha
	@ConfigItem(
		position = 16,
		keyName = "chatColor",
		name = "Overhead chat color",
		description = "Colour of the restored overhead chat text."
	)
	default Color chatColor()
	{
		return Color.WHITE;
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
		description = "Space between the ring and the icon, in pixels. In Ring only mode it adds to the size set by Scale %.",
		section = RING_SECTION
	)
	@Range(min = 0, max = 24)
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
