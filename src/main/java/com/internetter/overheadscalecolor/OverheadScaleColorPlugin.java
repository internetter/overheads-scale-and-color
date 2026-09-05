package com.internetter.overheadscalecolor;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
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
	}

	@Override
	protected void shutDown()
	{
		renderCallbackManager.unregister(drawCallback);
		overlayManager.remove(overlay);
		overlay.invalidateAll();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (OverheadScaleColorConfig.GROUP.equals(event.getGroup()))
		{
			overlay.invalidate();
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
}
