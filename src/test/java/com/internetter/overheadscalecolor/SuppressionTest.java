package com.internetter.overheadscalecolor;

import static com.internetter.overheadscalecolor.OverheadScaleColorOverlay.NO_RING;
import static com.internetter.overheadscalecolor.OverheadScaleColorOverlay.ringSlotFor;
import static com.internetter.overheadscalecolor.OverheadScaleColorOverlay.suppresses;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import net.runelite.api.HeadIcon;
import org.junit.Test;

/**
 * Pins when the client's own overhead is hidden.
 *
 * The failure this guards against is an overhead disappearing with nothing in its place: Ring only
 * mode has no replacement for Smite, Retribution, Redemption and the like, so it must leave them to
 * the client rather than hide them.
 */
public class SuppressionTest
{
	@Test
	public void ringOnlyHidesExactlyTheRingedOverheads()
	{
		for (HeadIcon icon : HeadIcon.values())
		{
			final boolean ringed = ringSlotFor(icon) != NO_RING;
			assertEquals("Ring only, " + icon, ringed, suppresses(icon, OverheadMode.RING_ONLY, true));
			assertEquals("Ring only, " + icon, ringed, suppresses(icon, OverheadMode.RING_ONLY, false));
		}
	}

	@Test
	public void ringOnlyLeavesNonProtectionOverheadsVisible()
	{
		assertFalse(suppresses(HeadIcon.SMITE, OverheadMode.RING_ONLY, true));
		assertFalse(suppresses(HeadIcon.RETRIBUTION, OverheadMode.RING_ONLY, true));
		assertFalse(suppresses(HeadIcon.REDEMPTION, OverheadMode.RING_ONLY, true));
	}

	@Test
	public void shrinkReplacesEveryOverhead()
	{
		for (HeadIcon icon : HeadIcon.values())
		{
			assertTrue("Shrink, " + icon, suppresses(icon, OverheadMode.SHRINK, true));
		}
	}

	@Test
	public void noOverheadHidesOnlyWhenAskedTo()
	{
		for (OverheadMode mode : OverheadMode.values())
		{
			assertFalse(mode.name(), suppresses(null, mode, true));
			assertTrue(mode.name(), suppresses(null, mode, false));
		}
	}
}
