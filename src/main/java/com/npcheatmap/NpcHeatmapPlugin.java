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
	private static final String CONFIG_GROUP = "npcheatmap";
	private static final String HEATMAP_KEY = "heatmapData";

	@Inject
	private Client client;

	@Inject
	private NpcHeatmapConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private NpcHeatmapOverlay overlay;

	@Inject
	private ConfigManager configManager;

	private final Map<WorldPoint, Integer> tileCounts = new ConcurrentHashMap<>();
	private Set<String> trackedNpcNames = new HashSet<>();

	@Override
	protected void startUp()
	{
		loadHeatmap();
		rebuildTrackedNpcNames();
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		saveHeatmap();
		overlayManager.remove(overlay);
	}

	@Provides
	NpcHeatmapConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NpcHeatmapConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CONFIG_GROUP.equals(event.getGroup()))
		{
			return;
		}

		if ("npcNames".equals(event.getKey()))
		{
			rebuildTrackedNpcNames();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (trackedNpcNames.isEmpty())
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

			if (!trackedNpcNames.contains(npc.getName().toLowerCase()))
			{
				continue;
			}

			WorldPoint trueTile = WorldPoint.fromLocalInstance(client, npc.getLocalLocation());
			if (trueTile == null)
			{
				trueTile = npc.getWorldLocation();
			}

			if (trueTile != null)
			{
				tileCounts.merge(trueTile, 1, Integer::sum);
			}
		}
	}

	public Map<WorldPoint, Integer> getTileCounts()
	{
		return Collections.unmodifiableMap(tileCounts);
	}

	private void saveHeatmap()
	{
		if (tileCounts.isEmpty())
		{
			configManager.unsetConfiguration(CONFIG_GROUP, HEATMAP_KEY);
			return;
		}

		String serialised = tileCounts.entrySet().stream()
			.map(entry ->
			{
				WorldPoint worldPoint = entry.getKey();
				return worldPoint.getX() + "," + worldPoint.getY() + "," + worldPoint.getPlane() + "," + entry.getValue();
			})
			.collect(Collectors.joining("|"));

		configManager.setConfiguration(CONFIG_GROUP, HEATMAP_KEY, serialised);
	}

	private void loadHeatmap()
	{
		String raw = configManager.getConfiguration(CONFIG_GROUP, HEATMAP_KEY);
		if (raw == null || raw.isBlank())
		{
			return;
		}

		for (String entry : raw.split("\\|"))
		{
			String[] parts = entry.split(",");
			if (parts.length != 4)
			{
				continue;
			}

			try
			{
				int worldX = Integer.parseInt(parts[0]);
				int worldY = Integer.parseInt(parts[1]);
				int plane = Integer.parseInt(parts[2]);
				int tickCount = Integer.parseInt(parts[3]);
				tileCounts.merge(new WorldPoint(worldX, worldY, plane), tickCount, Integer::sum);
			}
			catch (NumberFormatException ignored)
			{
			}
		}
	}

	private void rebuildTrackedNpcNames()
	{
		String raw = config.npcNames();
		if (raw == null || raw.isBlank())
		{
			trackedNpcNames = new HashSet<>();
			return;
		}

		trackedNpcNames = Arrays.stream(raw.split(","))
			.map(String::trim)
			.filter(name -> !name.isEmpty())
			.map(String::toLowerCase)
			.collect(Collectors.toSet());
	}
}