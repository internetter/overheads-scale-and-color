package com.internetter.overheadscalecolor;

import com.google.inject.Provides;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.SpritePixels;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.GameState;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Suppresses the client's native overhead icon for the local player and redraws it scaled, with an
 * optional colored ring. See DESIGN.md for the reasoning and the rejected alternatives.
 *
 * The client offers no way to resize an overhead icon, and no way to blank one either --
 * {@code Player} exposes {@code getOverheadIcon()} with no setter. Nor is there a hide flag:
 * {@code Client} has no hide/hidden methods at all in 1.12.38, contrary to a good deal of older
 * material referencing {@code setLocalPlayerHidden2D}. The only available mechanism is a draw veto,
 * which is what the bundled Entity Hider uses (still via the now-deprecated
 * {@code Hooks.RenderableDrawListener}; this uses the current {@link RenderCallback} /
 * {@link RenderCallbackManager} pair that {@code Hooks} delegates to).
 */
@Slf4j
@PluginDescriptor(
	name = "Overhead Scale & Color",
	description = "Cosmetic only. Shrinks your own overhead prayer icon and rings it in a color by prayer type.",
	tags = {"overhead", "prayer", "icon", "scale", "color", "colour", "accessibility"}
)
public class OverheadScaleColorPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private RenderCallbackManager renderCallbackManager;

	@Inject
	private OverheadScaleColorConfig config;

	@Inject
	private OverheadScaleColorOverlay overlay;

	// TEST ONLY (branch spike/sprite-override)
	@Inject
	private ClientThread clientThread;

	@Inject
	private SpriteManager spriteManager;

	private final RenderCallback drawCallback = new RenderCallback()
	{
		@Override
		public boolean addEntity(Renderable renderable, boolean drawingUI)
		{
			return shouldDraw(renderable, drawingUI);
		}
	};

	@Provides
	OverheadScaleColorConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OverheadScaleColorConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		renderCallbackManager.register(drawCallback);

		if (config.overrideTest())
		{
			installOverride();
		}
	}

	@Override
	protected void shutDown()
	{
		renderCallbackManager.unregister(drawCallback);
		overlayManager.remove(overlay);
		overlay.invalidateAll();
		clearOverride();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (OverheadScaleColorConfig.GROUP.equals(event.getGroup()))
		{
			overlay.invalidate();

			if (config.overrideTest())
			{
				installOverride();
			}
			else
			{
				clearOverride();
			}
		}
	}

	/**
	 * Returning false vetoes the draw.
	 *
	 * {@code drawingUI} true means the client is in its 2D pass for this entity -- name, healthbar,
	 * hitsplats, overhead prayer, overhead chat. The pass is all-or-nothing, which is where this
	 * plugin's one real cost comes from.
	 *
	 * Multiple callbacks compose as a logical AND: {@link RenderCallbackManager#addEntity} returns
	 * false as soon as any registered callback does, and the deprecated Hooks listener path
	 * registers into that same list. So this does not race with Entity Hider; if either vetoes, the
	 * draw is suppressed, and neither can un-veto the other.
	 */
	private boolean shouldDraw(Renderable renderable, boolean drawingUI)
	{
		// TEST ONLY: with the probe on we must NOT suppress, or there is no vanilla
		// icon on screen to observe the override against.
		if (config.overrideTest())
		{
			return true;
		}

		if (!drawingUI)
		{
			return true;
		}

		if (!(renderable instanceof Player))
		{
			return true;
		}

		final Player local = client.getLocalPlayer();
		if (local == null || renderable != local)
		{
			return true;
		}

		// Restore everything the moment no overhead is active, so the cost is paid only while
		// there is actually an icon to replace.
		if (config.hideOnlyWhilePraying() && local.getOverheadIcon() == null)
		{
			return true;
		}

		return false;
	}


	// ==================================================================
	// THROWAWAY TEST CODE -- branch spike/sprite-override only.
	//
	// Question: does an entry in Client#getSpriteOverrides() reach the
	// overhead prayer icons? If yes, the plugin can register pre-scaled
	// icons and let the client draw them, which removes the need to
	// suppress the 2D pass -- and with it the healthbar/hitsplat cost.
	//
	// Known risk: the client may read the head-icon archive once into a
	// cached array, in which case a later override is ignored. The probe
	// therefore reinstalls on login as well as on startup and on config
	// change, to give it the best chance of being picked up.
	//
	// Delete this entire block before merging to main.
	// ==================================================================

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (config.overrideTest() && event.getGameState() == GameState.LOGGED_IN)
		{
			installOverride();
		}
	}

	private void installOverride()
	{
		clientThread.invoke(() ->
		{
			final int[] pixels;
			final int w;
			final int h;

			if (config.overrideSolidRed())
			{
				w = 32;
				h = 32;
				pixels = new int[w * h];
				// SpritePixels are RGB with 0 meaning transparent, so 0xFF0000 is opaque red.
				Arrays.fill(pixels, 0xFF0000);
			}
			else
			{
				final BufferedImage src = spriteManager.getSprite(SpriteID.HEADICONS_PRAYER, 0);
				if (src == null)
				{
					log.info("[ovtest] source sprite unavailable; cannot build a scaled override yet");
					return;
				}

				w = Math.max(1, src.getWidth() / 2);
				h = Math.max(1, src.getHeight() / 2);

				final BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
				final Graphics2D g = dst.createGraphics();
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g.drawImage(src, 0, 0, w, h, null);
				g.dispose();

				pixels = new int[w * h];
				for (int i = 0; i < pixels.length; i++)
				{
					final int argb = dst.getRGB(i % w, i / w);
					// 0 is transparent in SpritePixels, so drop low-alpha pixels entirely.
					pixels[i] = ((argb >>> 24) < 128) ? 0 : (argb & 0xFFFFFF);
				}
			}

			final SpritePixels sp = client.createSpritePixels(pixels, w, h);
			client.getSpriteOverrides().put(SpriteID.HEADICONS_PRAYER, sp);
			client.getWidgetSpriteOverrides().put(SpriteID.HEADICONS_PRAYER, sp);

			log.info("[ovtest] installed {}x{} override at sprite id {} ({}); sprite map={} widget map={}",
				w, h, SpriteID.HEADICONS_PRAYER,
				config.overrideSolidRed() ? "solid red" : "half-size real icon",
				client.getSpriteOverrides().size(), client.getWidgetSpriteOverrides().size());
			log.info("[ovtest] suppression is DISABLED while this probe is on -- turn on a protection prayer and look at your overhead icon");
		});
	}

	private void clearOverride()
	{
		clientThread.invoke(() ->
		{
			final boolean had = client.getSpriteOverrides().remove(SpriteID.HEADICONS_PRAYER) != null;
			client.getWidgetSpriteOverrides().remove(SpriteID.HEADICONS_PRAYER);
			if (had)
			{
				log.info("[ovtest] override removed");
			}
		});
	}
}
