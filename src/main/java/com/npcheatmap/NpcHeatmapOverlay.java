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
		Map<WorldPoint, Integer> heatmap = plugin.getHeatmap();
		if (heatmap.isEmpty())
		{
			return null;
		}

		int maxCount = heatmap.values().stream().mapToInt(Integer::intValue).max().orElse(1);
		int opacity = config.tileOpacity();

		for (Map.Entry<WorldPoint, Integer> entry : heatmap.entrySet())
		{
			WorldPoint worldPoint = entry.getKey();
			int count = entry.getValue();

			if (worldPoint.getPlane() != client.getPlane())
			{
				continue;
			}

			LocalPoint localPoint = LocalPoint.fromWorld(client, worldPoint);
			if (localPoint == null)
			{
				continue;
			}

			Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);
			if (poly == null)
			{
				continue;
			}

			float ratio = (float) count / maxCount;
			Color fillColor = heatColor(ratio, opacity);

			graphics.setColor(fillColor);
			graphics.fillPolygon(poly);
		}

		return null;
	}

	/**
	 * Maps a 0.0-1.0 ratio to a colour gradient:
	 *   0.0 = green  (0,   255, 0)
	 *   0.5 = yellow (255, 255, 0)
	 *   1.0 = red    (255, 0,   0)
	 */
	private Color heatColor(float ratio, int alpha)
	{
		int r;
		int g;

		if (ratio <= 0.5f)
		{
			// green -> yellow
			r = Math.round(ratio * 2f * 255f);
			g = 255;
		}
		else
		{
			// yellow -> red
			r = 255;
			g = Math.round((1f - (ratio - 0.5f) * 2f) * 255f);
		}

		r = Math.max(0, Math.min(255, r));
		g = Math.max(0, Math.min(255, g));

		return new Color(r, g, 0, alpha);
	}
}