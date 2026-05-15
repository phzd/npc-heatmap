package com.npcheatmap;

import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@PluginDescriptor(
	name = "NPC Heatmap",
	description = "Tracks NPC positions over time and displays a heatmap of their most frequent tiles",
	tags = {"npc", "heatmap", "tile", "tracker"}
)
public class NpcHeatmapPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private NpcHeatmapConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private NpcHeatmapOverlay overlay;

	/**
	 * WorldPoint -> tick count accumulated across all tracked NPCs.
	 * ConcurrentHashMap so the overlay can iterate safely on the render thread
	 * while the game thread writes on ticks.
	 */
	private final Map<WorldPoint, Integer> heatmap = new ConcurrentHashMap<>();

	/**
	 * Lower-cased NPC names derived from config, rebuilt on config change.
	 */
	private Set<String> trackedNames = new HashSet<>();

	@Override
	protected void startUp()
	{
		rebuildTrackedNames();
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		heatmap.clear();
	}

	@Provides
	NpcHeatmapConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NpcHeatmapConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"npcheatmap".equals(event.getGroup()))
		{
			return;
		}

		if ("npcNames".equals(event.getKey()))
		{
			// Clear existing data when the tracked NPC list changes so stale
			// tiles from previously tracked NPCs do not persist.
			heatmap.clear();
			rebuildTrackedNames();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (trackedNames.isEmpty())
		{
			return;
		}

		List<NPC> npcs = client.getNpcs();
		if (npcs == null)
		{
			return;
		}

		for (NPC npc : npcs)
		{
			if (npc == null || npc.getName() == null)
			{
				continue;
			}

			if (!trackedNames.contains(npc.getName().toLowerCase()))
			{
				continue;
			}

			WorldPoint tile = WorldPoint.fromLocalInstance(client, npc.getLocalLocation());
			if (tile == null)
			{
				tile = npc.getWorldLocation();
			}

			if (tile != null)
			{
				heatmap.merge(tile, 1, Integer::sum);
			}
		}
	}

	/**
	 * Returns an unmodifiable view of the heatmap for the overlay to read.
	 */
	public Map<WorldPoint, Integer> getHeatmap()
	{
		return Collections.unmodifiableMap(heatmap);
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private void rebuildTrackedNames()
	{
		String raw = config.npcNames();
		if (raw == null || raw.isBlank())
		{
			trackedNames = new HashSet<>();
			return;
		}

		trackedNames = Arrays.stream(raw.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.map(String::toLowerCase)
			.collect(Collectors.toSet());
	}
}