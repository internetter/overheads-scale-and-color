package com.internetter.overheadscalecolor;

import java.awt.Color;

/**
 * Ring colour presets, including palettes chosen for the common forms of colour vision
 * deficiency (CVD).
 *
 * Why presets rather than "just shift the green a bit": a dichromat's colour space is effectively
 * two-dimensional -- one surviving hue axis plus luminance. Three categories separated only by hue
 * cannot be made reliable by nudging the shades. Each palette below therefore separates the three
 * prayer types on BOTH the surviving hue axis and luminance, and avoids the confusable pair
 * entirely rather than trying to fine-tune it.
 *
 * Colours are drawn from the Okabe-Ito qualitative palette where possible, which is the
 * long-standing reference set for CVD-safe categorical encoding.
 *
 * For anyone whose vision is not served by any of these, {@link #CUSTOM} hands all three colours
 * back to the user, and the "distinct ring styles" option adds a non-colour cue on top.
 */
public enum RingPalette
{
	/**
	 * Standard palette. Intuitive mapping, but red/green is precisely the pair lost in the most
	 * common deficiencies, so it is not a safe default for everyone.
	 */
	STANDARD("Standard", 0xFF4136, 0x2ECC40, 0x2E9BFF),

	/**
	 * Deuteranopia / deuteranomaly and protanopia / protanomaly -- red-green deficiency, by far the
	 * most common form (roughly 8% of men, 0.5% of women). Green is dropped entirely. Separation is
	 * orange (mid luminance, yellow axis) / white (max luminance, no hue) / blue (low luminance,
	 * blue axis), which survives both deutan and protan simulation.
	 */
	RED_GREEN("Red-green friendly (deuteran / protan)", 0xE69F00, 0xFFFFFF, 0x0072B2),

	/**
	 * Tritanopia / tritanomaly -- blue-yellow deficiency. Rare. Here red and green are reliable but
	 * blue/green and yellow/violet converge, so blue is dropped and magic moves to magenta.
	 */
	BLUE_YELLOW("Blue-yellow friendly (tritan)", 0xD55E00, 0x009E73, 0xCC79A7),

	/**
	 * Achromatopsia / monochromacy, and anyone who simply wants maximum contrast. Pure luminance
	 * separation. Enable "distinct ring styles" alongside this -- with no hue at all, the dash
	 * pattern is doing most of the work.
	 */
	MONOCHROME("Monochrome / high contrast", 0xFFFFFF, 0xA0A0A0, 0x4A4A4A),

	/** Use the three colour pickers below instead of a preset. */
	CUSTOM("Custom colors", 0, 0, 0);

	private final String label;
	private final Color melee;
	private final Color ranged;
	private final Color magic;

	RingPalette(String label, int melee, int ranged, int magic)
	{
		this.label = label;
		this.melee = new Color(melee);
		this.ranged = new Color(ranged);
		this.magic = new Color(magic);
	}

	Color melee()
	{
		return melee;
	}

	Color ranged()
	{
		return ranged;
	}

	Color magic()
	{
		return magic;
	}

	/** RuneLite renders enum config items using toString(). */
	@Override
	public String toString()
	{
		return label;
	}
}
