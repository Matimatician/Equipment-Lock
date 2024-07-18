package com.example;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("Equipment Lock")
public interface Equipment_Lock_Config extends Config
{
	@ConfigItem(
			keyName = "groupId",
			name = "Group ID",
			description = "The ID of your Group"
	)
	default String groupId()
	{
		return "";
	}

	@ConfigItem(
			keyName = "excludeQuestItems",
			name = "Exclude Items Required for Quests",
			description = "Exclude items required for quests from being locked",
			position = 1
	)
	default boolean excludeQuestItems()
	{
		return true;
	}
}
