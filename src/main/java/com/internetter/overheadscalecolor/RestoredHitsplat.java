package com.internetter.overheadscalecolor;

import java.awt.Color;
import net.runelite.api.HitsplatID;

/**
 * One hitsplat the 2D suppression removed, held until the client says it would have expired.
 *
 * Unlike the health bar, the client does not hand us its hitsplat graphics -- there is no equivalent
 * of PostHealthBarConfig, and the Hitmark sprite group is indexed by something other than the
 * hitsplat type. These are therefore drawn rather than reproduced. The colours below follow what
 * each type actually looks like in game, so a poison hit reads as poison and a max hit reads as a
 * max hit, but they are this plugin's rendition rather than the client's own pixels.
 */
final class RestoredHitsplat
{
	// Base colours, keyed to how each splat actually appears in game.
	private static final Color RED = new Color(0xB01818);        // ordinary damage
	private static final Color RED_MAX = new Color(0xE02020);    // max hit, brighter than ordinary
	private static final Color BLUE = new Color(0x2050B0);       // block / zero damage
	private static final Color GREEN = new Color(0x1E8C1E);      // poison
	private static final Color GREEN_DARK = new Color(0x0A4A0A); // venom
	private static final Color ORANGE_DISEASE = new Color(0xB86A18);
	private static final Color PURPLE = new Color(0x8A2BC0);     // heal
	private static final Color CYAN = new Color(0x18A0B0);
	private static final Color ORANGE = new Color(0xD07818);
	private static final Color YELLOW = new Color(0xC8B018);
	private static final Color WHITE = new Color(0xD8D8D8);
	private static final Color POISE = new Color(0x707890);
	private static final Color PRAYER = new Color(0x3858C0);
	private static final Color BLEED = new Color(0x8C1030);
	private static final Color SANITY_LOSS = new Color(0x6038A0);
	private static final Color SANITY_GAIN = new Color(0x38A0A0);
	private static final Color DOOM = new Color(0x582060);
	private static final Color BURN = new Color(0xC04810);
	private static final Color CORRUPTION = new Color(0x0E7A5A);

	final int amount;
	final int type;
	final int expiresOnCycle;

	/** Derived once here rather than per frame, since they depend only on the type. */
	final Color highlight;
	final Color shadow;

	RestoredHitsplat(int amount, int type, int expiresOnCycle)
	{
		this.amount = amount;
		this.type = type;
		this.expiresOnCycle = expiresOnCycle;

		final Color base = color();
		this.highlight = shift(base, 0.42f);
		this.shadow = shift(base, -0.30f);
	}

	boolean expired(int gameCycle)
	{
		return gameCycle >= expiresOnCycle;
	}

	Color color()
	{
		switch (type)
		{
			case HitsplatID.BLOCK_ME:
			case HitsplatID.BLOCK_OTHER:
				return BLUE;

			case HitsplatID.DAMAGE_ME:
			case HitsplatID.DAMAGE_OTHER:
				return RED;

			case HitsplatID.DAMAGE_MAX_ME:
				return RED_MAX;

			case HitsplatID.DAMAGE_ME_CYAN:
			case HitsplatID.DAMAGE_OTHER_CYAN:
			case HitsplatID.DAMAGE_MAX_ME_CYAN:
			case HitsplatID.CYAN_UP:
			case HitsplatID.CYAN_DOWN:
				return CYAN;

			case HitsplatID.DAMAGE_ME_ORANGE:
			case HitsplatID.DAMAGE_OTHER_ORANGE:
			case HitsplatID.DAMAGE_MAX_ME_ORANGE:
				return ORANGE;

			case HitsplatID.DAMAGE_ME_YELLOW:
			case HitsplatID.DAMAGE_OTHER_YELLOW:
			case HitsplatID.DAMAGE_MAX_ME_YELLOW:
				return YELLOW;

			case HitsplatID.DAMAGE_ME_WHITE:
			case HitsplatID.DAMAGE_OTHER_WHITE:
			case HitsplatID.DAMAGE_MAX_ME_WHITE:
				return WHITE;

			case HitsplatID.DAMAGE_ME_POISE:
			case HitsplatID.DAMAGE_OTHER_POISE:
			case HitsplatID.DAMAGE_MAX_ME_POISE:
				return POISE;

			case HitsplatID.POISON:
				return GREEN;
			case HitsplatID.VENOM:
				return GREEN_DARK;
			case HitsplatID.DISEASE:
			case HitsplatID.DISEASE_BLOCKED:
				return ORANGE_DISEASE;
			case HitsplatID.HEAL:
				return PURPLE;
			case HitsplatID.PRAYER_DRAIN:
				return PRAYER;
			case HitsplatID.BLEED:
				return BLEED;
			case HitsplatID.SANITY_DRAIN:
				return SANITY_LOSS;
			case HitsplatID.SANITY_RESTORE:
				return SANITY_GAIN;
			case HitsplatID.DOOM:
				return DOOM;
			case HitsplatID.BURN:
				return BURN;
			case HitsplatID.CORRUPTION:
				return CORRUPTION;

			default:
				// Unknown or newly added type: fall back on the damage/zero convention rather than
				// inventing a colour, so a future hitsplat still reads sensibly.
				return amount == 0 ? BLUE : RED;
		}
	}

	private static Color shift(Color c, float amount)
	{
		if (amount >= 0)
		{
			return new Color(
				(int) (c.getRed() + (255 - c.getRed()) * amount),
				(int) (c.getGreen() + (255 - c.getGreen()) * amount),
				(int) (c.getBlue() + (255 - c.getBlue()) * amount));
		}

		final float k = 1f + amount;
		return new Color((int) (c.getRed() * k), (int) (c.getGreen() * k), (int) (c.getBlue() * k));
	}
}
