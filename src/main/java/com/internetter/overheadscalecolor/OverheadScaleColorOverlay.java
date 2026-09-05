package com.internetter.overheadscalecolor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.HeadIcon;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws the scaled overhead icon and its colored ring.
 *
 * Icons come from sprite archive {@link SpriteID#HEADICONS_PRAYER} (440), indexed by
 * {@link HeadIcon#ordinal()}. The enum has exactly 15 values, matching that archive's file count,
 * and icons render correctly in game -- but the correspondence is inferred rather than documented,
 * and has not been checked prayer by prayer.
 *
 * Nothing in {@link #render(Graphics2D)} allocates: scaled images, ring colors and strokes are all
 * memoised and rebuilt only on config change.
 */
@Slf4j
public class OverheadScaleColorOverlay extends Overlay
{
	/** Ring outline. Softened black -- pure black reads as a hard edge at 1px. */
	private static final Color RING_OUTLINE = new Color(0, 0, 0, 180);

	/** Ring slots. Also index the color and stroke arrays. */
	private static final int MELEE = 0;
	private static final int RANGED = 1;
	private static final int MAGIC = 2;

	private final Client client;
	private final OverheadScaleColorConfig config;
	private final SpriteManager spriteManager;

	/** Scaled images, memoised per (icon ordinal, scale, interpolation). */
	private final Map<Long, BufferedImage> scaledCache = new HashMap<>();

	/** Native (unscaled) sprites, memoised per icon ordinal. Never holds null -- see below. */
	private final Map<Integer, BufferedImage> nativeCache = new HashMap<>();

	private final Color[] ringColors = new Color[3];
	private final Stroke[] ringStrokes = new Stroke[3];
	private final Stroke[] outlineStrokes = new Stroke[3];
	private boolean ringStateValid;

	/** Bitmask of icon ordinals already logged as missing, so the log is not written per frame. */
	private int missingLogged;

	@Inject
	OverheadScaleColorOverlay(Client client, OverheadScaleColorConfig config, SpriteManager spriteManager)
	{
		this.client = client;
		this.config = config;
		this.spriteManager = spriteManager;
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPosition(OverlayPosition.DYNAMIC);
	}

	/** Config changed: drop derived state, keep the native sprites. */
	void invalidate()
	{
		scaledCache.clear();
		ringStateValid = false;
	}

	/** Plugin shutting down: drop everything. */
	void invalidateAll()
	{
		scaledCache.clear();
		nativeCache.clear();
		ringStateValid = false;
		missingLogged = 0;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final Player local = client.getLocalPlayer();
		if (local == null)
		{
			return null;
		}

		final HeadIcon icon = local.getOverheadIcon();
		if (icon == null)
		{
			return null;
		}

		final BufferedImage nativeImg = nativeSprite(icon);
		if (nativeImg == null)
		{
			return null;
		}

		final BufferedImage img = scaled(icon, nativeImg, config.scale(), config.smoothScaling());
		final LocalPoint lp = local.getLocalLocation();
		if (lp == null)
		{
			return null;
		}

		final int zOffset = local.getLogicalHeight() + config.heightOffset();

		// getCanvasImageLocation centres the image on the projected point in BOTH axes (verified
		// by decompiling Perspective). A naive scale-down therefore shrinks toward the centre and
		// the bottom edge rises; shifting down by half the height difference pins the bottom edge,
		// which keeps the icon anchored where vanilla put it at every scale.
		final Point p = Perspective.getCanvasImageLocation(client, lp, img, zOffset);
		if (p == null)
		{
			return null;
		}

		final int x = p.getX();
		final int y = p.getY() + (nativeImg.getHeight() - img.getHeight()) / 2;

		if (config.ringEnabled())
		{
			drawRing(graphics, icon, x, y, img.getWidth(), img.getHeight());
		}

		graphics.drawImage(img, x, y, null);
		return null;
	}

	/**
	 * The ring is stroked at render time rather than baked into the cached image deliberately. Its
	 * whole purpose is legibility at small scales, and baking it in would shrink it along with the
	 * icon -- thinnest exactly when it matters most. Thickness is in screen pixels instead.
	 */
	private void drawRing(Graphics2D graphics, HeadIcon icon, int x, int y, int w, int h)
	{
		final int slot = ringSlotFor(icon);
		if (slot < 0)
		{
			return;
		}

		if (!ringStateValid)
		{
			refreshRingState();
		}

		final int radius = Math.max(w, h) / 2 + config.ringGap();
		final int diameter = radius * 2;
		final int left = x + w / 2 - radius;
		final int top = y + h / 2 - radius;

		final Object priorAA = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		final Stroke priorStroke = graphics.getStroke();

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		if (config.ringOutline())
		{
			graphics.setStroke(outlineStrokes[slot]);
			graphics.setColor(RING_OUTLINE);
			graphics.drawOval(left, top, diameter, diameter);
		}

		graphics.setStroke(ringStrokes[slot]);
		graphics.setColor(ringColors[slot]);
		graphics.drawOval(left, top, diameter, diameter);

		graphics.setStroke(priorStroke);
		if (priorAA != null)
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, priorAA);
		}
	}

	/**
	 * Resolve palette colors and build strokes once per config change. Reading a Color back from
	 * ConfigManager parses a string and allocates, so doing this per frame would allocate on every
	 * render.
	 */
	private void refreshRingState()
	{
		final RingPalette palette = config.palette();
		if (palette == RingPalette.CUSTOM)
		{
			ringColors[MELEE] = config.meleeColor();
			ringColors[RANGED] = config.rangedColor();
			ringColors[MAGIC] = config.magicColor();
		}
		else
		{
			ringColors[MELEE] = palette.melee();
			ringColors[RANGED] = palette.ranged();
			ringColors[MAGIC] = palette.magic();
		}

		final float thickness = config.ringThickness();
		final float outline = thickness + 2f;

		if (config.distinctStyles())
		{
			// A cue that survives total color loss: melee solid, ranged dashed, magic dotted.
			// Dash lengths scale with thickness so the pattern stays legible as it thickens.
			final float[] dashed = {thickness * 2.5f, thickness * 2f};
			final float[] dotted = {0.1f, thickness * 2.6f};

			ringStrokes[MELEE] = new BasicStroke(thickness);
			ringStrokes[RANGED] = new BasicStroke(thickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, dashed, 0f);
			ringStrokes[MAGIC] = new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, dotted, 0f);

			// The outline follows the same dash pattern; a solid dark ring underneath would swamp
			// the shape cue this option exists to provide.
			outlineStrokes[MELEE] = new BasicStroke(outline);
			outlineStrokes[RANGED] = new BasicStroke(outline, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, dashed, 0f);
			outlineStrokes[MAGIC] = new BasicStroke(outline, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, dotted, 0f);
		}
		else
		{
			final Stroke ring = new BasicStroke(thickness);
			final Stroke out = new BasicStroke(outline);
			for (int i = 0; i < 3; i++)
			{
				ringStrokes[i] = ring;
				outlineStrokes[i] = out;
			}
		}

		ringStateValid = true;
	}

	/**
	 * Which ring slot an icon uses, or -1 for icons that render ringless.
	 *
	 * Deflect variants share their protection-prayer equivalent's slot. Combined overheads and the
	 * non-protection prayers (Smite, Retribution, Redemption, Wrath, Soul Split) get no ring rather
	 * than an invented color.
	 */
	private static int ringSlotFor(HeadIcon icon)
	{
		switch (icon)
		{
			case MELEE:
			case DEFLECT_MELEE:
				return MELEE;
			case RANGED:
			case DEFLECT_RANGE:
				return RANGED;
			case MAGIC:
			case DEFLECT_MAGE:
				return MAGIC;
			default:
				return -1;
		}
	}

	private BufferedImage nativeSprite(HeadIcon icon)
	{
		final int index = icon.ordinal();
		final BufferedImage cached = nativeCache.get(index);
		if (cached != null)
		{
			return cached;
		}

		final BufferedImage img = spriteManager.getSprite(SpriteID.HEADICONS_PRAYER, index);
		if (img == null)
		{
			// Deliberately NOT cached. getSprite returns null while the client's sprite cache is
			// still warming up, and caching that would permanently blank this icon for the rest of
			// the session. Retrying costs a map lookup per frame until it resolves.
			if ((missingLogged & (1 << index)) == 0)
			{
				missingLogged |= 1 << index;
				log.debug("no sprite yet for HeadIcon {} (archive {} index {})", icon, SpriteID.HEADICONS_PRAYER, index);
			}
			return null;
		}

		nativeCache.put(index, img);
		return img;
	}

	private BufferedImage scaled(HeadIcon icon, BufferedImage src, int scalePercent, boolean smooth)
	{
		final long key = ((long) icon.ordinal() << 32) | ((long) scalePercent << 1) | (smooth ? 1 : 0);
		final BufferedImage cached = scaledCache.get(key);
		if (cached != null)
		{
			return cached;
		}

		if (scalePercent == 100)
		{
			scaledCache.put(key, src);
			return src;
		}

		final int w = Math.max(1, src.getWidth() * scalePercent / 100);
		final int h = Math.max(1, src.getHeight() * scalePercent / 100);

		final BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = dst.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			smooth ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();

		scaledCache.put(key, dst);
		return dst;
	}
}
