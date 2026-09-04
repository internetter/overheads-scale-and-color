package com.internetter.overheadscalecolor;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.SpritePixels;
import net.runelite.api.events.CommandExecuted;
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
 * Plugin lifecycle: registers the draw veto that suppresses the client's native overhead icon,
 * and owns the overlay that redraws it scaled. See DESIGN.md for why it works this way.
 *
 * Suppression mechanism note: DESIGN.md originally proposed {@code client.setLocalPlayerHidden2D(boolean)}.
 * That method does not exist on {@code Client} in runelite-api 1.12.38 -- there are no hide/hidden
 * methods on Client at all. The real mechanism, as used by the bundled Entity Hider, is a draw
 * veto callback. Entity Hider still uses the deprecated {@code Hooks.RenderableDrawListener};
 * this uses the current {@link RenderCallback} / {@link RenderCallbackManager} pair, which
 * {@code Hooks} itself now merely delegates to. Verified by decompiling client-1.12.38.jar.
 */
@Slf4j
@PluginDescriptor(
	name = "Overhead Scale & Color",
	description = "Cosmetic / visual only, no automation. Shrinks your own overhead prayer icon and rings it in a colour by prayer type.",
	tags = {"overhead", "prayer", "icon", "scale", "colour", "color"},
	enabledByDefault = false
)
public class SpikePlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private RenderCallbackManager renderCallbackManager;

	@Inject
	private SpikeConfig config;

	@Inject
	private SpikeOverlay overlay;

	private final RenderCallback drawListener = new RenderCallback()
	{
		@Override
		public boolean addEntity(Renderable renderable, boolean drawingUI)
		{
			return shouldDraw(renderable, drawingUI);
		}
	};

	/** Last icon seen, so the per-frame path can log transitions without spamming. */
	private HeadIcon lastIcon;

	@Provides
	SpikeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SpikeConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		renderCallbackManager.register(drawListener);
		log.info("Overhead Scale & Color started");
	}

	@Override
	protected void shutDown()
	{
		renderCallbackManager.unregister(drawListener);
		overlayManager.remove(overlay);
		overlay.invalidateAll();
		lastIcon = null;
		log.info("Overhead Scale & Color stopped");
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (SpikeConfig.GROUP.equals(event.getGroup()))
		{
			overlay.invalidate();
		}
	}

	/**
	 * Returning false vetoes the draw.
	 *
	 * {@code drawingUI} true means the client is in its 2D pass for this entity (name, healthbar,
	 * hitsplats, overhead prayer, overhead chat). Entity Hider branches on exactly this flag to
	 * separate its "hide 2D" options from its "hide model" options.
	 *
	 * Multiple callbacks compose as a logical AND: {@link RenderCallbackManager#addEntity} returns
	 * false as soon as any registered callback does, and the deprecated Hooks listener path
	 * registers into that same list. So this does NOT race with Entity Hider -- there is no
	 * last-writer-wins conflict of the kind DESIGN.md anticipated. Needs confirming on screen.
	 */
	private boolean shouldDraw(Renderable renderable, boolean drawingUI)
	{
		if (!drawingUI || !config.suppress2D())
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

		if (config.suppressOnlyWhenIconActive() && local.getOverheadIcon() == null)
		{
			return true;
		}

		return false;
	}

	// ------------------------------------------------------------------
	// Dev-mode probes. Run the client with --developer-mode and type these
	// in the chatbox. All output goes to the console at INFO (spike only --
	// the real plugin logs at debug per CLAUDE.md).
	// ------------------------------------------------------------------

	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		switch (event.getCommand().toLowerCase())
		{
			case "spikeicon":
				dumpCurrentIcon();
				break;
			case "spikedump":
				dumpPrayerArchive();
				break;
			case "spikenpc":
				dumpNpcOverheads();
				break;
			case "spikeoverride":
				trySpriteOverride();
				break;
			default:
				break;
		}
	}

	/** Reports the local player's current HeadIcon and the sprite its ordinal resolves to. */
	private void dumpCurrentIcon()
	{
		final Player local = client.getLocalPlayer();
		if (local == null)
		{
			log.info("[spike] local player is null");
			return;
		}

		final HeadIcon icon = local.getOverheadIcon();
		if (icon == null)
		{
			log.info("[spike] no overhead icon active");
			return;
		}

		final BufferedImage img = spriteManager.getSprite(SpriteID.HEADICONS_PRAYER, icon.ordinal());
		log.info("[spike] HeadIcon={} ordinal={} sprite={}", icon, icon.ordinal(),
			img == null ? "NULL" : img.getWidth() + "x" + img.getHeight());
		log.info("[spike] logicalHeight={}", local.getLogicalHeight());
	}

	/**
	 * Enumerates archive {@link SpriteID#HEADICONS_PRAYER} and reports which indices
	 * resolve and at what size. If the count and order line up with HeadIcon, the ordinal mapping
	 * hypothesis survives -- though only a screenshot proves the icons are the right way round.
	 */
	private void dumpPrayerArchive()
	{
		log.info("[spike] --- archive {} (HEADICONS_PRAYER) ---", SpriteID.HEADICONS_PRAYER);
		final HeadIcon[] icons = HeadIcon.values();
		for (int i = 0; i < 32; i++)
		{
			final BufferedImage img = spriteManager.getSprite(SpriteID.HEADICONS_PRAYER, i);
			if (img == null)
			{
				log.info("[spike] index {}: null", i);
				continue;
			}
			final String guess = i < icons.length ? icons[i].name() : "(beyond HeadIcon enum)";
			log.info("[spike] index {}: {}x{}  -> would be {}", i, img.getWidth(), img.getHeight(), guess);
		}
		log.info("[spike] HeadIcon enum has {} values: {}", icons.length, Arrays.toString(icons));
	}

	/** Reads overhead sprite/archive ids off nearby NPCs -- an alternative discovery path. */
	private void dumpNpcOverheads()
	{
		log.info("[spike] --- nearby NPCs with overhead sprites ---");
		int found = 0;
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			final short[] sprites = npc.getOverheadSpriteIds();
			final int[] archives = npc.getOverheadArchiveIds();
			if (sprites == null && archives == null)
			{
				continue;
			}
			found++;
			log.info("[spike] npc={} id={} archives={} sprites={}",
				npc.getName(), npc.getId(), Arrays.toString(archives), Arrays.toString(sprites));
		}
		log.info("[spike] {} NPC(s) with overhead sprite data", found);
	}

	/**
	 * Do raw sprite overrides reach overhead icons? DESIGN.md expects no. Unused by the plugin.
	 * Paints archive 440 solid red and leaves it that way until the client reloads sprites.
	 */
	private void trySpriteOverride()
	{
		clientThread.invoke(() ->
		{
			final int w = 32;
			final int h = 32;
			final int[] pixels = new int[w * h];
			Arrays.fill(pixels, 0xFFFF0000);

			final SpritePixels red = client.createSpritePixels(pixels, w, h);
			client.getSpriteOverrides().put(SpriteID.HEADICONS_PRAYER, red);

			log.info("[spike] put red 32x32 override at sprite id {}; overrides map size now {}",
				SpriteID.HEADICONS_PRAYER, client.getSpriteOverrides().size());
			log.info("[spike] turn OFF 'Suppress local player 2D' and look at the vanilla icon.");
		});
	}
}
