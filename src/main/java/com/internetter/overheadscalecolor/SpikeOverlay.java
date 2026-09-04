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
 * Draws the scaled overhead icon and its coloured ring.
 *
 * Working hypothesis being tested here: the prayer head icons live in sprite archive
 * {@link SpriteID#HEADICONS_PRAYER} (440), indexed by {@link HeadIcon#ordinal()}. The enum has
 * exactly 15 values, which matches the known file count of that archive. That correspondence is
 * plausible, and icons do render correctly, but it has not been checked per prayer.
 */
@Slf4j
public class SpikeOverlay extends Overlay
{
	private final Client client;
	private final SpikeConfig config;
	private final SpriteManager spriteManager;

	/**
	 * Scaled images, memoised per (icon ordinal, scale, interpolation). Cleared on config change
	 * so nothing is rebuilt per frame -- CLAUDE.md constraint 5.
	 */
	private final Map<Long, BufferedImage> scaledCache = new HashMap<>();

	/** Native (unscaled) sprites, memoised per icon ordinal. */
	private final Map<Integer, BufferedImage> nativeCache = new HashMap<>();

	/** Ring outline colour. Dark grey rather than black -- black reads as a hard edge at 1px. */
	private static final Color RING_OUTLINE = new Color(0, 0, 0, 180);

	/** Prayer types that get a ring. Index into the stroke/colour arrays below. */
	private static final int MELEE = 0;
	private static final int RANGED = 1;
	private static final int MAGIC = 2;

	// Strokes and config-derived colours are rebuilt on config change, never per frame
	// (CLAUDE.md constraint 5: no allocation in the render path).
	private final Color[] ringColors = new Color[3];
	private final Stroke[] ringStrokes = new Stroke[3];
	private final Stroke[] outlineStrokes = new Stroke[3];
	private boolean ringStateValid;

	@Inject
	SpikeOverlay(Client client, SpikeConfig config, SpriteManager spriteManager)
	{
		this.client = client;
		this.config = config;
		this.spriteManager = spriteManager;
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPosition(OverlayPosition.DYNAMIC);
	}

	void invalidate()
	{
		scaledCache.clear();
		ringStateValid = false;
	}

	/**
	 * Pull the ring colours and strokes out of config once, on change. Reading a Color from
	 * ConfigManager parses a string and allocates, so doing it per frame would allocate on every
	 * render for every prayer type.
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
			// A cue that survives total colour loss: melee solid, ranged dashed, magic dotted.
			// Dash lengths scale with thickness so the pattern stays legible as it thickens.
			final float[] dashed = {thickness * 2.5f, thickness * 2f};
			final float[] dotted = {0.1f, thickness * 2.6f};

			ringStrokes[MELEE] = new BasicStroke(thickness);
			ringStrokes[RANGED] = new BasicStroke(thickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, dashed, 0f);
			ringStrokes[MAGIC] = new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, dotted, 0f);

			// The outline follows the same dash pattern, otherwise a solid dark ring would sit
			// under the dashes and swamp the shape cue we just added.
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
	 * Which ring slot an icon uses, or -1 for icons that should render ringless.
	 *
	 * Deflect variants map to the same slot as their protection-prayer equivalents. Combined
	 * overheads and the non-protection prayers (Smite, Retribution, Redemption, Wrath, Soul Split)
	 * deliberately get no ring rather than an invented colour.
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

	void invalidateAll()
	{
		scaledCache.clear();
		nativeCache.clear();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.drawOverlay())
		{
			return null;
		}

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
		if (img == null)
		{
			return null;
		}

		final LocalPoint lp = local.getLocalLocation();
		if (lp == null)
		{
			return null;
		}

		final int zOffset = local.getLogicalHeight() + config.zPadding();

		// getCanvasImageLocation centres the image on the projected point in BOTH axes --
		// verified by decompiling Perspective in runelite-api 1.12.38. So a naive scale-down
		// shrinks toward the centre and the bottom edge rises. Shifting down by half the
		// height difference pins the bottom edge instead.
		final Point p = Perspective.getCanvasImageLocation(client, lp, img, zOffset);
		if (p == null)
		{
			return null;
		}

		int x = p.getX();
		int y = p.getY();

		if (config.anchorBottom())
		{
			y += (nativeImg.getHeight() - img.getHeight()) / 2;
		}

		if (config.ringEnabled())
		{
			drawRing(graphics, icon, x, y, img.getWidth(), img.getHeight());
		}

		graphics.drawImage(img, x, y, null);

		if (config.debugMarkers())
		{
			graphics.setColor(Color.CYAN);
			graphics.drawRect(x, y, img.getWidth(), img.getHeight());

			final Point raw = Perspective.localToCanvas(client, lp, client.getTopLevelWorldView().getPlane(), zOffset);
			if (raw != null)
			{
				graphics.setColor(Color.MAGENTA);
				graphics.drawLine(raw.getX() - 4, raw.getY(), raw.getX() + 4, raw.getY());
				graphics.drawLine(raw.getX(), raw.getY() - 4, raw.getX(), raw.getY() + 4);
			}
		}

		return null;
	}

	/**
	 * Ring is stroked at render time rather than baked into the cached image on purpose. The whole
	 * point of the ring is legibility at small scales; baking it in would shrink it along with the
	 * icon and it would disappear exactly when it is most needed. Thickness is therefore in screen
	 * pixels and constant across scales.
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

		final int cx = x + w / 2;
		final int cy = y + h / 2;
		final int radius = Math.max(w, h) / 2 + config.ringGap();
		final int diameter = radius * 2;
		final int left = cx - radius;
		final int top = cy - radius;

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

	private BufferedImage nativeSprite(HeadIcon icon)
	{
		final int index = icon.ordinal();
		if (nativeCache.containsKey(index))
		{
			return nativeCache.get(index);
		}

		// Null is cached deliberately: if the archive/index hypothesis is wrong we must not
		// re-hit SpriteManager every single frame.
		final BufferedImage img = spriteManager.getSprite(SpriteID.HEADICONS_PRAYER, index);
		nativeCache.put(index, img);
		if (img == null)
		{
			log.debug("no sprite for HeadIcon {} at archive {} index {}", icon, SpriteID.HEADICONS_PRAYER, index);
		}
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
