package com.npcheatmap;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.Map;

public class NpcHeatmapMinimapOverlay extends Overlay
{
	private final Client client;
	private final NpcHeatmapPlugin plugin;
	private final NpcHeatmapConfig config;

	@Inject
	NpcHeatmapMinimapOverlay(Client client, NpcHeatmapPlugin plugin, NpcHeatmapConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(PRIORITY_LOW);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Map<String, Map<WorldPoint, Integer>> tileCountsByNpcName = plugin.getTileCountsByNpcName();
		if (tileCountsByNpcName.isEmpty())
		{
			return null;
		}

		for (Map.Entry<String, Map<WorldPoint, Integer>> npcEntry : tileCountsByNpcName.entrySet())
		{
			Map<WorldPoint, Integer> tileCounts = npcEntry.getValue();
			if (tileCounts.isEmpty())
			{
				continue;
			}

			int highestTileCount = tileCounts.values().stream().mapToInt(Integer::intValue).max().orElse(1);

			for (Map.Entry<WorldPoint, Integer> tileEntry : tileCounts.entrySet())
			{
				WorldPoint worldPoint = tileEntry.getKey();
				int tileCount = tileEntry.getValue();

				if (worldPoint.getPlane() != client.getPlane())
				{
					continue;
				}

				if (!isHighestCountForTile(worldPoint, tileCount, npcEntry.getKey(), tileCountsByNpcName))
				{
					continue;
				}

				LocalPoint localPoint = LocalPoint.fromWorld(client, worldPoint);
				if (localPoint == null)
				{
					continue;
				}

				int localX = localPoint.getX() & -Perspective.LOCAL_TILE_SIZE;
				int localY = localPoint.getY() & -Perspective.LOCAL_TILE_SIZE;

				net.runelite.api.Point mp1 = Perspective.localToMinimap(client, new LocalPoint(localX, localY));
				net.runelite.api.Point mp2 = Perspective.localToMinimap(client, new LocalPoint(localX, localY + Perspective.LOCAL_TILE_SIZE));
				net.runelite.api.Point mp3 = Perspective.localToMinimap(client, new LocalPoint(localX + Perspective.LOCAL_TILE_SIZE, localY + Perspective.LOCAL_TILE_SIZE));
				net.runelite.api.Point mp4 = Perspective.localToMinimap(client, new LocalPoint(localX + Perspective.LOCAL_TILE_SIZE, localY));

				if (mp1 == null || mp2 == null || mp3 == null || mp4 == null)
				{
					continue;
				}

				Polygon minimapTilePoly = new Polygon();
				minimapTilePoly.addPoint(mp1.getX(), mp1.getY());
				minimapTilePoly.addPoint(mp2.getX(), mp2.getY());
				minimapTilePoly.addPoint(mp3.getX(), mp3.getY());
				minimapTilePoly.addPoint(mp4.getX(), mp4.getY());

				float heatRatio = (float) tileCount / highestTileCount;
				Color tileColor = heatRatioToColor(heatRatio, config.tileOpacity());

				graphics.setColor(tileColor);
				graphics.fillPolygon(minimapTilePoly);
			}
		}

		return null;
	}

	private boolean isHighestCountForTile(
		WorldPoint worldPoint,
		int countForCurrentNpc,
		String currentNpcName,
		Map<String, Map<WorldPoint, Integer>> tileCountsByNpcName
	) {
		for (Map.Entry<String, Map<WorldPoint, Integer>> npcEntry : tileCountsByNpcName.entrySet())
		{
			if (npcEntry.getKey().equals(currentNpcName))
			{
				continue;
			}

			Integer otherNpcCount = npcEntry.getValue().get(worldPoint);
			if (otherNpcCount != null && otherNpcCount > countForCurrentNpc)
			{
				return false;
			}
		}

		return true;
	}

	private Color heatRatioToColor(float heatRatio, int alpha)
	{
		int red;
		int green;

		if (heatRatio <= 0.5f)
		{
			red = Math.round(heatRatio * 2f * 255f);
			green = 255;
		}
		else
		{
			red = 255;
			green = Math.round((1f - (heatRatio - 0.5f) * 2f) * 255f);
		}

		red = Math.max(0, Math.min(255, red));
		green = Math.max(0, Math.min(255, green));

		return new Color(red, green, 0, alpha);
	}
}