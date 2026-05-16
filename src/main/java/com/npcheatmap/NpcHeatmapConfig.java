package com.npcheatmap;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("npcheatmap")
public interface NpcHeatmapConfig extends Config
{
	@ConfigItem(
		keyName = "npcNames",
		name = "NPC Names",
		description = "Comma-separated list of NPC names to track (case-insensitive)",
		position = 0
	)
	default String npcNames()
	{
		return "";
	}

	@Range(min = 0, max = 255)
	@ConfigItem(
		keyName = "tileOpacity",
		name = "Tile Opacity",
		description = "Opacity of the heatmap tile fill (0 = transparent, 255 = fully opaque)",
		position = 1
	)
	default int tileOpacity()
	{
		return 180;
	}

	@ConfigItem(
		keyName = "ignoreAttackingNpcs",
		name = "Ignore Attacking NPCs",
		description = "Do not track tile positions of NPCs that are currently attacking the player",
		position = 2
	)
	default boolean ignoreAttackingNpcs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "ignoreDyingNpcs",
		name = "Ignore Dying NPCs",
		description = "Do not track tile positions of NPCs that are currently dying",
		position = 3
	)
	default boolean ignoreDyingNpcs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackSouthWestTileOnly",
		name = "Track south-west tile only",
		description = "When enabled, only the south-west tile of an NPC is tracked. When disabled, every tile the NPC occupies is tracked.",
		position = 4
	)
	default boolean trackSouthWestTileOnly()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTileCount",
		name = "Show tile count",
		description = "Display the tick count above each heatmap tile",
		position = 5
	)
	default boolean showTileCount()
	{
		return false;
	}
}