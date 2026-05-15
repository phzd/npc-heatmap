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
		Map<WorldPoint, Integer> tileCounts = plugin.getTileCounts();
		if (tileCounts.isEmpty())
		{
			return null;
		}

		int highestTileCount = tileCounts.values().stream().mapToInt(Integer::intValue).max().orElse(1);
		int opacity = config.tileOpacity();

		for (Map.Entry<WorldPoint, Integer> entry : tileCounts.entrySet())
		{
			WorldPoint worldPoint = entry.getKey();
			int tileCount = entry.getValue();

			if (worldPoint.getPlane() != client.getPlane())
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
		}

		return null;
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