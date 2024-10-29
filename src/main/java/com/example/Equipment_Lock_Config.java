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
import net.runelite.client.config.ConfigSection;

@ConfigGroup("EquipmentLock")
public interface Equipment_Lock_Config extends Config
{
	@ConfigSection(
    		name = "Set Group ID",
    		description = "Declare your group's ID",
   		position = 0,
    		closedByDefault = false
	)
	String groupSection = "Set Group ID";
	
	@ConfigItem(
			keyName = "groupId",
			name = "Group ID",
			description = "The ID of your Group",
			section = groupSection,
			position = 1
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

	@ConfigSection(
    		name = "Emote Clue Item Exclusions",
    		description = "Settings for clue scroll emote item exclusions",
   		position = 2,
    		closedByDefault = true
	)

	String clueSection = "Clue Emote Item Exclusions";
	
	@ConfigItem(
			keyName = "excludeBeginnerClues",
			name = "Exclude Beginner Clues",
			description = "Exclude items required for Beginner Clues",
			section = clueSection,
			position = 1
	)
	default boolean excludeBeginnerClues()
	{
		return false;
	}
	
	@ConfigItem(
			keyName = "excludeEasyClues",
			name = "Exclude Easy Clues",
			description = "Exclude items required for Easy Clues",
			section = clueSection,
			position = 2
	)
	default boolean excludeEasyClues()
	{
		return false;
	}

	@ConfigItem(
			keyName = "excludeMediumClues",
			name = "Exclude Medium Clues",
			description = "Exclude items required for Medium Clues",
			section = clueSection,
			position = 3
	)
	default boolean excludeMediumClues()
	{
		return false;
	}

	@ConfigItem(
			keyName = "excludeHardClues",
			name = "Exclude Hard Clues",
			description = "Exclude items required for Hard Clues",
			section = clueSection,
			position = 4
	)
	default boolean excludeHardClues()
	{
		return false;
	}

	@ConfigItem(
			keyName = "excludeEliteClues",
			name = "Exclude Elite Clues",
			description = "Exclude items required for Elite Clues",
			section = clueSection,
			position = 5
	)
	default boolean excludeEliteClues()
	{
		return false;
	}

	@ConfigItem(
			keyName = "excludeMasterClues",
			name = "Exclude Master Clues",
			description = "Exclude items required for Master Clues",
			section = clueSection,
			position = 6
	)
	default boolean excludeMasterClues()
	{
		return false;
	}
}
