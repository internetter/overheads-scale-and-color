package com.internetter.overheadscalecolor;

import java.awt.Color;
import net.runelite.api.HitsplatID;

/**
 * One hitsplat the 2D suppression removed, held until the client says it would have expired.
 *
 * Unlike the health bar, the client does not hand us its hitsplat sprites -- there is no equivalent
 * of PostHealthBarConfig, and gameval exposes only a single hitmark id. These are therefore drawn
 * rather than reproduced, so they read correctly at a glance but are not pixel-identical to vanilla.
 */
final class RestoredHitsplat
{
	private static final Color DAMAGE = new Color(0xC81E1E);
	private static final Color BLOCK = new Color(0x1E5FC8);
	private static final Color POISON = new Color(0x1E8C1E);
	private static final Color VENOM = new Color(0x0B4D0B);
	private static final Color HEAL = new Color(0x9B30C8);
	private static final Color MAX = new Color(0xE03A3A);
	private static final Color OTHER = new Color(0x4A4A4A);

	final int amount;
	final int type;
	final int expiresOnCycle;

	RestoredHitsplat(int amount, int type, int expiresOnCycle)
	{
		this.amount = amount;
		this.type = type;
		this.expiresOnCycle = expiresOnCycle;
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
				return BLOCK;
			case HitsplatID.POISON:
				return POISON;
			case HitsplatID.VENOM:
				return VENOM;
			case HitsplatID.HEAL:
				return HEAL;
			case HitsplatID.DAMAGE_MAX_ME:
			case HitsplatID.DAMAGE_MAX_ME_CYAN:
			case HitsplatID.DAMAGE_MAX_ME_ORANGE:
			case HitsplatID.DAMAGE_MAX_ME_YELLOW:
				return MAX;
			case HitsplatID.DAMAGE_ME:
			case HitsplatID.DAMAGE_OTHER:
			case HitsplatID.DAMAGE_ME_CYAN:
			case HitsplatID.DAMAGE_OTHER_CYAN:
			case HitsplatID.DAMAGE_ME_ORANGE:
			case HitsplatID.DAMAGE_OTHER_ORANGE:
			case HitsplatID.DAMAGE_ME_YELLOW:
			case HitsplatID.DAMAGE_OTHER_YELLOW:
			case HitsplatID.DAMAGE_ME_WHITE:
			case HitsplatID.DAMAGE_OTHER_WHITE:
				return DAMAGE;
			default:
				return amount == 0 ? BLOCK : OTHER;
		}
	}
}
