package com.internetter.overheadscalecolor;

import static com.internetter.overheadscalecolor.OverheadScaleColorOverlay.MAGIC;
import static com.internetter.overheadscalecolor.OverheadScaleColorOverlay.MELEE;
import static com.internetter.overheadscalecolor.OverheadScaleColorOverlay.NO_RING;
import static com.internetter.overheadscalecolor.OverheadScaleColorOverlay.RANGED;
import static com.internetter.overheadscalecolor.OverheadScaleColorOverlay.ringSlotFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.HeadIcon;
import org.junit.Test;

/**
 * Pins the HeadIcon -> ring mapping.
 *
 * Worth a test because a mistake here is invisible: a wrongly-ringed overhead still looks like a
 * perfectly plausible plugin, so nothing about it draws attention. The rule is that only the three
 * protection prayers and their Deflect equivalents are ringed, and everything else renders ringless
 * rather than being assigned an invented color.
 */
public class RingSlotTest
{
	@Test
	public void protectionPrayersAndTheirDeflectEquivalentsShareASlot()
	{
		assertEquals(MELEE, ringSlotFor(HeadIcon.MELEE));
		assertEquals(MELEE, ringSlotFor(HeadIcon.DEFLECT_MELEE));

		assertEquals(RANGED, ringSlotFor(HeadIcon.RANGED));
		assertEquals(RANGED, ringSlotFor(HeadIcon.DEFLECT_RANGE));

		assertEquals(MAGIC, ringSlotFor(HeadIcon.MAGIC));
		assertEquals(MAGIC, ringSlotFor(HeadIcon.DEFLECT_MAGE));
	}

	@Test
	public void combinedAndNonProtectionOverheadsGetNoRing()
	{
		final Set<HeadIcon> ringless = EnumSet.of(
			// Combined overheads: no single correct color.
			HeadIcon.RANGE_MAGE,
			HeadIcon.RANGE_MELEE,
			HeadIcon.MAGE_MELEE,
			HeadIcon.RANGE_MAGE_MELEE,
			// Not protection prayers at all.
			HeadIcon.SMITE,
			HeadIcon.RETRIBUTION,
			HeadIcon.REDEMPTION,
			HeadIcon.WRATH,
			HeadIcon.SOUL_SPLIT);

		for (HeadIcon icon : ringless)
		{
			assertEquals("expected no ring for " + icon, NO_RING, ringSlotFor(icon));
		}
	}

	/**
	 * Guards against a new HeadIcon appearing in a future API version and silently falling into the
	 * default branch unnoticed. If this fails, decide deliberately whether the new icon is ringed.
	 */
	@Test
	public void everyHeadIconIsAccountedFor()
	{
		assertEquals("HeadIcon gained or lost values; revisit the ring mapping",
			15, HeadIcon.values().length);

		for (HeadIcon icon : HeadIcon.values())
		{
			final int slot = ringSlotFor(icon);
			assertNotNull(icon.name(), icon);
			if (slot != NO_RING && (slot < MELEE || slot > MAGIC))
			{
				throw new AssertionError("slot out of range for " + icon + ": " + slot);
			}
		}
	}
}
