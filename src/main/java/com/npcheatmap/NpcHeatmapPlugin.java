package com.npcheatmap;

import com.google.inject.Provides;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
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
	private static final String HEATMAP_KEY_PREFIX = "heatmapData_";

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

	private final Map<String, Map<WorldPoint, Integer>> tileCountsByNpcName = new ConcurrentHashMap<>();
	private Set<String> trackedNpcNames = new HashSet<>();

	@Override
	protected void startUp()
	{
		rebuildTrackedNpcNames();
		loadAllHeatmaps();
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		saveAllHeatmaps();
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
			Set<String> previousNames = trackedNpcNames;
			rebuildTrackedNpcNames();

			Set<String> removedNames = new HashSet<>(previousNames);
			removedNames.removeAll(trackedNpcNames);

			for (String removedName : removedNames)
			{
				tileCountsByNpcName.remove(removedName);
			}
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

			String npcNameLower = npc.getName().toLowerCase();
			if (!trackedNpcNames.contains(npcNameLower))
			{
				continue;
			}

			if (config.ignoreAttackingNpcs() && npc.getInteracting() instanceof Player)
			{
				continue;
			}

			if (config.ignoreDyingNpcs() && npc.getHealthRatio() == 0)
			{
				continue;
			}

			WorldPoint southWestTile = npc.getWorldLocation();
			if (client.isInInstancedRegion())
			{
				LocalPoint southWestLocalPoint = new LocalPoint(
					npc.getLocalLocation().getX() - (npc.getComposition().getSize() - 1) * Perspective.LOCAL_TILE_SIZE / 2,
					npc.getLocalLocation().getY() - (npc.getComposition().getSize() - 1) * Perspective.LOCAL_TILE_SIZE / 2
				);
				southWestTile = WorldPoint.fromLocal(client, southWestLocalPoint);
			}

			if (southWestTile == null)
			{
				continue;
			}

			if (config.trackSouthWestTileOnly())
			{
				tileCountsByNpcName
					.computeIfAbsent(npcNameLower, name -> new ConcurrentHashMap<>())
					.merge(southWestTile, 1, Integer::sum);
			}
			else
			{
				int npcSize = npc.getComposition().getSize();
				Map<WorldPoint, Integer> tileCounts = tileCountsByNpcName
					.computeIfAbsent(npcNameLower, name -> new ConcurrentHashMap<>());

				for (int xOffset = 0; xOffset < npcSize; xOffset++)
				{
					for (int yOffset = 0; yOffset < npcSize; yOffset++)
					{
						WorldPoint occupiedTile = new WorldPoint(
							southWestTile.getX() + xOffset,
							southWestTile.getY() + yOffset,
							southWestTile.getPlane()
						);
						tileCounts.merge(occupiedTile, 1, Integer::sum);
					}
				}
			}
		}
	}

	public Map<String, Map<WorldPoint, Integer>> getTileCountsByNpcName()
	{
		return Collections.unmodifiableMap(tileCountsByNpcName);
	}

	public void removeTile(WorldPoint worldPoint)
	{
		tileCountsByNpcName.values().forEach(tileCounts -> tileCounts.remove(worldPoint));
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		MenuAction menuAction = event.getMenuEntry().getType();
		if (!client.isKeyPressed(KeyCode.KC_SHIFT))
		{
			return;
		}

		if (menuAction != MenuAction.WALK && menuAction != MenuAction.SET_HEADING)
		{
			return;
		}

		int worldViewId = event.getMenuEntry().getWorldViewId();
		WorldView worldView = client.getWorldView(worldViewId);
		if (worldView == null)
		{
			return;
		}

		Tile selectedTile = worldView.getSelectedSceneTile();
		if (selectedTile == null)
		{
			return;
		}

		WorldPoint hoveredTile = WorldPoint.fromLocalInstance(client, selectedTile.getLocalLocation());

		boolean tileIsTracked = tileCountsByNpcName.values().stream()
			.anyMatch(tileCounts -> tileCounts.containsKey(hoveredTile));

		if (!tileIsTracked)
		{
			return;
		}

		client.createMenuEntry(-1)
			.setOption("Remove from heatmap")
			.setTarget("")
			.setType(MenuAction.RUNELITE)
			.onClick(e -> removeTile(hoveredTile));
	}

	private void saveAllHeatmaps()
	{
		for (Map.Entry<String, Map<WorldPoint, Integer>> npcEntry : tileCountsByNpcName.entrySet())
		{
			String npcName = npcEntry.getKey();
			Map<WorldPoint, Integer> tileCounts = npcEntry.getValue();

			if (tileCounts.isEmpty())
			{
				configManager.unsetConfiguration(CONFIG_GROUP, HEATMAP_KEY_PREFIX + npcName);
				continue;
			}

			String serialised = tileCounts.entrySet().stream()
				.map(entry ->
				{
					WorldPoint worldPoint = entry.getKey();
					return worldPoint.getX() + "," + worldPoint.getY() + "," + worldPoint.getPlane() + "," + entry.getValue();
				})
				.collect(Collectors.joining("|"));

			configManager.setConfiguration(CONFIG_GROUP, HEATMAP_KEY_PREFIX + npcName, serialised);
		}
	}

	private void loadAllHeatmaps()
	{
		for (String npcName : trackedNpcNames)
		{
			String raw = configManager.getConfiguration(CONFIG_GROUP, HEATMAP_KEY_PREFIX + npcName);
			if (raw == null || raw.isBlank())
			{
				continue;
			}

			Map<WorldPoint, Integer> tileCounts = tileCountsByNpcName
				.computeIfAbsent(npcName, name -> new ConcurrentHashMap<>());

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