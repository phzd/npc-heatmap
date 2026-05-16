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
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.util.HashMap;
import java.util.Map;

public class NpcHeatmapOverlay extends Overlay
{
	private final Client client;
	private final NpcHeatmapPlugin plugin;
	private final NpcHeatmapConfig config;

	@Inject
	NpcHeatmapOverlay(Client client, NpcHeatmapPlugin plugin, NpcHeatmapConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Map<String, Map<WorldPoint, Integer>> tileCountsByNpcName = plugin.getTileCountsByNpcName();
		if (tileCountsByNpcName.isEmpty())
		{
			return null;
		}

		int opacity = config.tileOpacity();

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

				Polygon tilePoly = Perspective.getCanvasTilePoly(client, localPoint);
				if (tilePoly == null)
				{
					continue;
				}

				float heatRatio = (float) tileCount / highestTileCount;
				Color tileColor = heatRatioToColor(heatRatio, opacity);

				graphics.setColor(tileColor);
				graphics.fillPolygon(tilePoly);

				if (config.showTileCount())
				{
					drawTileCount(graphics, tilePoly, tileCount);
				}
			}
		}

		return null;
	}

	private void drawTileCount(Graphics2D graphics, Polygon tilePoly, int tileCount)
	{
		String countText = String.valueOf(tileCount);
		FontMetrics fontMetrics = graphics.getFontMetrics();
		int textWidth = fontMetrics.stringWidth(countText);
		int textHeight = fontMetrics.getAscent();

		int centerX = (int) tilePoly.getBounds().getCenterX() - textWidth / 2;
		int centerY = (int) tilePoly.getBounds().getCenterY() + textHeight / 2;

		graphics.setColor(Color.BLACK);
		graphics.drawString(countText, centerX + 1, centerY + 1);

		graphics.setColor(Color.WHITE);
		graphics.drawString(countText, centerX, centerY);
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