package com.internetter.overheadscalecolor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.FontMetrics;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.HealthBarConfig;
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
	static final int MELEE = 0;
	static final int RANGED = 1;
	static final int MAGIC = 2;
	static final int NO_RING = -1;

	/** Offsets skull entries away from HeadIcon ordinals in the shared sprite caches. */
	private static final int SKULL_CACHE_NS = 1000;

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

	/**
	 * The client's own health bar definition, captured from PostHealthBarConfig. Suppressing the 2D
	 * pass takes the real bar with it, so in Shrink mode we redraw one using these exact sprites.
	 * Volatile because the event arrives off the client thread.
	 */
	private volatile HealthBarConfig healthBarConfig;
	private BufferedImage healthBarFront;
	private BufferedImage healthBarBack;
	private HealthBarConfig healthBarCachedFrom;

	void setHealthBarConfig(HealthBarConfig cfg)
	{
		this.healthBarConfig = cfg;
	}

	/**
	 * Hitsplats recorded while suppression is hiding the client's own. Bounded at four, which is what
	 * the client itself shows at once. Written from the event thread, read on the client thread, so
	 * the deque is synchronised on itself at both ends.
	 */
	private final Deque<RestoredHitsplat> hitsplats = new ArrayDeque<>();

	void recordHitsplat(RestoredHitsplat splat)
	{
		synchronized (hitsplats)
		{
			while (hitsplats.size() >= 4)
			{
				hitsplats.pollFirst();
			}
			hitsplats.addLast(splat);
		}
	}

	@Inject
	OverheadScaleColorOverlay(Client client, OverheadScaleColorConfig config, SpriteManager spriteManager)
	{
		this.client = client;
		this.config = config;
		this.spriteManager = spriteManager;
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPosition(OverlayPosition.DYNAMIC);
	}

	/**
	 * Invalidation is requested from the AWT event thread (config changes and plugin lifecycle both
	 * arrive there) but the caches are read and written by {@link #render(Graphics2D)} on the client
	 * thread. Clearing them directly would be concurrent mutation of a plain HashMap, so these only
	 * raise a flag and the client thread does the actual clearing at the top of the next frame.
	 */
	private volatile boolean invalidatePending;
	private volatile boolean fullInvalidatePending;

	/** Config changed: drop derived state, keep the native sprites. */
	void invalidate()
	{
		invalidatePending = true;
	}

	/**
	 * Plugin stopping: drop everything. The plugin instance and this overlay survive a
	 * disable/enable cycle in RuneLite, so the sprites are dropped too rather than left to go stale.
	 */
	void invalidateAll()
	{
		fullInvalidatePending = true;
	}

	/** Must only be called on the client thread. */
	private void applyPendingInvalidation()
	{
		if (fullInvalidatePending)
		{
			fullInvalidatePending = false;
			invalidatePending = false;
			nativeCache.clear();
			scaledCache.clear();
			ringStateValid = false;
			missingLogged = 0;
		}
		else if (invalidatePending)
		{
			invalidatePending = false;
			scaledCache.clear();
			ringStateValid = false;
		}
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		applyPendingInvalidation();

		final Player local = client.getLocalPlayer();
		if (local == null)
		{
			return null;
		}

		final LocalPoint lp = local.getLocalLocation();
		if (lp == null)
		{
			return null;
		}

		final HeadIcon icon = local.getOverheadIcon();
		final boolean shrink = config.mode() == OverheadMode.SHRINK;

		// Whether the client's 2D pass is currently being vetoed. Everything it would have drawn --
		// health bar, hitsplats, skull -- has to be put back by us for exactly this period, and that
		// is not the same condition as "an overhead icon is showing".
		final boolean suppressing = shrink && (!config.hideOnlyWhilePraying() || icon != null);

		if (icon != null)
		{
			drawIcon(graphics, local, lp, icon, shrink);
		}

		if (suppressing)
		{
			if (config.restoreHealthBar())
			{
				drawHealthBar(graphics, local, lp);
			}

			if (config.restoreHitsplats())
			{
				drawHitsplats(graphics, local, lp);
			}

			if (config.restoreSkull())
			{
				drawSkull(graphics, local, lp);
			}
		}

		return null;
	}

	private void drawIcon(Graphics2D graphics, Player local, LocalPoint lp, HeadIcon icon, boolean shrink)
	{
		final BufferedImage nativeImg = nativeSprite(icon);
		if (nativeImg == null)
		{
			return;
		}

		// In ring-only mode the client is still drawing its own icon at native size, so the ring is
		// sized against the native sprite and no replacement is drawn over the top.
		final BufferedImage img = shrink
			? scaled(icon.ordinal(), nativeImg, config.scale(), config.smoothScaling())
			: nativeImg;

		// Ring-only draws around the CLIENT's icon, which sits at a different height than a
		// replacement we draw ourselves, so it needs its own offset.
		final int zOffset = local.getLogicalHeight()
			+ (shrink ? config.heightOffset() : config.ringOnlyOffset());

		final Point p = Perspective.getCanvasImageLocation(client, lp, img, zOffset);
		if (p == null)
		{
			return;
		}

		final int x = p.getX();
		final int y = p.getY() + (nativeImg.getHeight() - img.getHeight()) / 2;

		if (config.ringEnabled())
		{
			drawRing(graphics, icon, x, y, img.getWidth(), img.getHeight());
		}

		if (shrink)
		{
			graphics.drawImage(img, x, y, null);
		}
	}

	/**
	 * Puts back the PK skull that suppression removed. Scaled alongside the prayer icon so the two
	 * stay visually consistent.
	 */
	private void drawSkull(Graphics2D graphics, Player local, LocalPoint lp)
	{
		final int skull = local.getSkullIcon();
		if (skull < 0)
		{
			return;
		}

		final BufferedImage nativeImg = skullSprite(skull);
		if (nativeImg == null)
		{
			return;
		}

		final BufferedImage img = scaled(SKULL_CACHE_NS + skull, nativeImg, config.scale(), config.smoothScaling());

		final Point p = Perspective.getCanvasImageLocation(client, lp, img,
			local.getLogicalHeight() + config.skullOffset());
		if (p == null)
		{
			return;
		}

		graphics.drawImage(img, p.getX(), p.getY() + (nativeImg.getHeight() - img.getHeight()) / 2, null);
	}

	private BufferedImage skullSprite(int index)
	{
		final int key = SKULL_CACHE_NS + index;
		final BufferedImage cached = nativeCache.get(key);
		if (cached != null)
		{
			return cached;
		}

		final BufferedImage img = spriteManager.getSprite(SpriteID.HEADICONS_PK, index);
		if (img != null)
		{
			nativeCache.put(key, img);
		}
		return img;
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
	static int ringSlotFor(HeadIcon icon)
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
				return NO_RING;
		}
	}

	/**
	 * Redraws the health bar the 2D suppression removed, using the client's own sprites so it is
	 * indistinguishable from vanilla.
	 *
	 * getHealthRatio() returns -1 when the client would not be showing a bar at all, so the timing
	 * comes from the client rather than being guessed -- the bar appears and disappears exactly when
	 * it normally would.
	 */
	private void drawHealthBar(Graphics2D graphics, Player local, LocalPoint lp)
	{
		final int ratio = local.getHealthRatio();
		final int scale = local.getHealthScale();
		if (ratio < 0 || scale <= 0)
		{
			return;
		}

		final HealthBarConfig cfg = healthBarConfig;
		if (cfg == null)
		{
			return;
		}

		if (cfg != healthBarCachedFrom)
		{
			healthBarCachedFrom = cfg;
			healthBarFront = spriteToImage(cfg.getHealthBarFrontSprite());
			healthBarBack = spriteToImage(cfg.getHealthBarBackSprite());
		}

		if (healthBarFront == null || healthBarBack == null)
		{
			return;
		}

		final int w = healthBarFront.getWidth();
		final int h = healthBarFront.getHeight();

		final Point p = Perspective.getCanvasImageLocation(client, lp, healthBarFront,
			local.getLogicalHeight() + config.healthBarOffset());
		if (p == null)
		{
			return;
		}

		final int filled = Math.min(w, (int) Math.round((double) w * ratio / scale));

		graphics.drawImage(healthBarBack, p.getX(), p.getY(), null);
		if (filled > 0)
		{
			graphics.drawImage(healthBarFront, p.getX(), p.getY(), p.getX() + filled, p.getY() + h,
				0, 0, filled, h, null);
		}
	}

	/**
	 * Redraws the hitsplats suppression removed. Expiry uses the client's own
	 * getDisappearsOnGameCycle, so they last exactly as long as the real ones would.
	 */
	private void drawHitsplats(Graphics2D graphics, Player local, LocalPoint lp)
	{
		final int cycle = client.getGameCycle();

		final RestoredHitsplat[] live;
		synchronized (hitsplats)
		{
			final int linger = config.hitsplatLingerMs() / 20;
			hitsplats.removeIf(h -> h.expired(cycle - linger));
			if (hitsplats.isEmpty())
			{
				return;
			}
			live = hitsplats.toArray(new RestoredHitsplat[0]);
		}

		final Point anchor = Perspective.localToCanvas(client, lp,
			client.getTopLevelWorldView().getPlane(),
			local.getLogicalHeight() + config.hitsplatOffset());
		if (anchor == null)
		{
			return;
		}

		final Object priorAA = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		final Font priorFont = graphics.getFont();
		graphics.setFont(priorFont.deriveFont(Font.BOLD, 12f));
		final FontMetrics fm = graphics.getFontMetrics();

		// Spread multiple splats horizontally, centred on the player, like the client does.
		final int w = config.hitsplatSize();
		final int h = (int) Math.round(w * 0.9);
		final int spacing = w - w / 5;
		int cx = anchor.getX() - ((live.length - 1) * spacing) / 2;

		final Stroke priorStroke = graphics.getStroke();
		graphics.setStroke(new BasicStroke(1f));

		for (RestoredHitsplat splat : live)
		{
			final int cy = anchor.getY();
			final Shape star = fourPointStar(cx, cy, w / 2.0, h / 2.0);

			// Shade top-to-bottom rather than filling flat, so the splat has the same sense of
			// volume the client's own graphics have.
			graphics.setPaint(new GradientPaint(
				cx, cy - h / 2f, splat.highlight(),
				cx, cy + h / 2f, splat.shadow()));
			graphics.fill(star);

			// setColor replaces the gradient paint for the outline.
			graphics.setColor(new Color(0, 0, 0, 180));
			graphics.draw(star);

			final String text = Integer.toString(splat.amount);
			final int tx = cx - fm.stringWidth(text) / 2;
			final int ty = cy + fm.getAscent() / 2 - 1;

			graphics.setColor(Color.BLACK);
			graphics.drawString(text, tx + 1, ty + 1);
			graphics.setColor(Color.WHITE);
			graphics.drawString(text, tx, ty);

			cx += spacing;
		}

		graphics.setStroke(priorStroke);

		graphics.setFont(priorFont);
		if (priorAA != null)
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, priorAA);
		}
	}

	/**
	 * A four-point star with concave sides, matching the plugin's own icon. The waist factor sets how
	 * deeply the sides pinch: lower is spikier, higher is rounder. 0.26 keeps the centre wide enough
	 * for a three-digit number to sit legibly inside.
	 */
	private static Shape fourPointStar(double cx, double cy, double rx, double ry)
	{
		final double waist = 0.26;
		final double wx = rx * waist;
		final double wy = ry * waist;

		final Path2D.Double p = new Path2D.Double();
		p.moveTo(cx, cy - ry);
		p.quadTo(cx + wx, cy - wy, cx + rx, cy);
		p.quadTo(cx + wx, cy + wy, cx, cy + ry);
		p.quadTo(cx - wx, cy + wy, cx - rx, cy);
		p.quadTo(cx - wx, cy - wy, cx, cy - ry);
		p.closePath();
		return p;
	}

	private static BufferedImage spriteToImage(net.runelite.api.SpritePixels sprite)
	{
		try
		{
			return sprite == null ? null : sprite.toBufferedImage();
		}
		catch (RuntimeException e)
		{
			return null;
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

	private BufferedImage scaled(int id, BufferedImage src, int scalePercent, boolean smooth)
	{
		final long key = ((long) id << 32) | ((long) scalePercent << 1) | (smooth ? 1 : 0);
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
