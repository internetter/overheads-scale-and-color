package com.internetter.overheadscalecolor;

/**
 * How the plugin treats the client's own overhead icon.
 *
 * The distinction exists because of a hard client limitation. Drawing a resized icon requires the
 * client to stop drawing its own, and the only mechanism for that suppresses the player's entire 2D
 * pass -- icon, healthbar, hitsplats, overhead chat and name together. There is no way to suppress
 * the icon alone: {@code Player} has no overhead-icon setter, {@code Client} has no hide flags, and
 * sprite overrides do not reach overhead icons (tested in game, they do not).
 *
 * The ring, by contrast, is drawn by an overlay on top of the scene and needs nothing suppressed.
 * So colouring costs nothing and resizing costs the 2D pass, and the two are separated here rather
 * than bundled.
 */
public enum OverheadMode
{
	/**
	 * Hide the client's icon and show only the coloured ring. The ring itself is the indicator of
	 * which protection prayer is active, so nothing needs to line up with anything.
	 */
	RING_ONLY("Ring only"),

	/** Hide the client's icon and draw a resized copy of it, with the ring around it. */
	SHRINK("Shrink icon");

	private final String label;

	OverheadMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
