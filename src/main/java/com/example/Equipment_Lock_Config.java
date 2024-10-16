/*
 * Copyright (c) 2024, Mati Zuckerman <Mati.Zuckerman@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package com.example;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("EquipmentLock")
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
	@ConfigItem(
			keyName = "excludeClueItems",
			name = "Exclude Emote Clue Required Items",
			description = "Exclude items required for clue scroll emote steps from being locked",
			position = 2
	)
	default boolean excludeClueItems()
	{
		return false;
	}

	@ConfigItem(
			keyName = "excludeEasyClues",
			name = "Easy Clues",
			description = "Exclude items required for Easy Clues",
			hidden = true,
			unhide = "excludeClueItems",
			position = 3
	)
	default boolean excludeEasyClues()
	{
		return false;
	}

	@ConfigItem(
			keyName = "excludeMediumClues",
			name = "Medium Clues",
			description = "Exclude items required for Medium Clues",
			hidden = true,
			unhide = "excludeClueItems",
			position = 4
	)
	default boolean excludeMediumClues()
	{
		return false;
	}

	@ConfigItem(
			keyName = "excludeHardClues",
			name = "Hard Clues",
			description = "Exclude items required for Hard Clues",
			hidden = true,
			unhide = "excludeClueItems",
			position = 5
	)
	default boolean excludeHardClues()
	{
		return false;
	}

	@ConfigItem(
			keyName = "excludeEliteClues",
			name = "Elite Clues",
			description = "Exclude items required for Elite Clues",
			hidden = true,
			unhide = "excludeClueItems",
			position = 6
	)
	default boolean excludeEliteClues()
	{
		return false;
	}

	@ConfigItem(
			keyName = "excludeMasterClues",
			name = "Master Clues",
			description = "Exclude items required for Master Clues",
			hidden = true,
			unhide = "excludeClueItems",
			position = 7
	)
	default boolean excludeMasterClues()
	{
		return false;
	}
}
