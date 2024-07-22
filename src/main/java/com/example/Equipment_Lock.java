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

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.events.ConfigChanged;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@Slf4j
@PluginDescriptor(
		name = "Equipment Lock"
)

public class Equipment_Lock extends Plugin {
	@Inject
	private Client client;

	@Inject
	private Gson gson;

	@Inject
	private Equipment_Lock_Config config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private AWSLambdaClient awsLambdaClient;

	private static final String TABLE_NAME = "Item_Assignments";
	private static final String PARTITION_KEY = "group_item"; // Composite key for group and item
	private static final String ATTRIBUTE_OWNER = "owner";

	// Local cache for the item ownership information
	private Map<String, String> localCache = new HashMap<>();

	// Whitelist of items required for quests
	private static final Set<String> QUEST_ITEMS_WHITELIST = new HashSet<>(Arrays.asList(
			"Spiked boots", "Desert disguise", "Dramen staff", "Ghostspeak amulet", "Glarial's amulet",
			"Ham shirt", "Ham robe", "Ham gloves", "Ham boots", "Ham cloak", "Ham logo", "Ice gloves",
			"M'speak amulet", "Zombie monkey greegree", "Ninja monkey greegree", "Kruk monkey greegree",
			"Bearded gorilla greegree", "Gorilla greegree", "Monkey talisman", "Karamjan monkey greegree",
			"Catspeak amulet", "Gas mask", "Mourner top", "Mourner trousers", "Mourner boots",
			"Mourner gloves", "Mourner cloak", "Ogre bow", "SilverLight", "Excalibur", "Ring of visibility",
			"Ivandis flail", "Blisterwood flail", "Bronze med helm", "Iron chainbody", "Plague jacket",
			"Plague trousers", "Climbing boots", "Gold helmet", "Fixed device", "Ice arrows",
			"Lunar helm", "Lunar amulet", "Lunar ring", "Lunar Torso", "Lunar legs", "Lunar legs",
			"Lunar boots", "Lunar cape", "Lunar staff", "Clockwork suit", "Silly jester hat",
			"Silly jester top", "Silly jester tights", "Silly jester boots", "Hard hat", "Builder's shirt",
			"Builder's trousers", "Builder's boots", "Black full helm", "Black platebody", "Black platelegs",
			"Elite black full helm", "Elite black platebody", "Elite black platelegs", "Ardougne knight helm",
			"Ardougne knight platebody", "Ardougne knight platelegs", "Dark squall hood", "Dark squall robe top",
			"Dark squall robe bottom", "Mirror shield", "V's shield", "Slave shirt", "Slave robe", "Slave boots",
			"Desert shirt", "Desert robes", "Desert boots", "Khazard armour", "Khazard helmet", "Anti-dragon shield",
			"Reinforced goggles", "Mith grapple", "Ogre arrow", "10th squad sigil", "White goblin mail",
			"Yellow goblin mail", "Black goblin mail", "Blue goblin mail", "Orange goblin mail", "Purple goblin mail",
			"Goblin mail", "Ring of charos(a)", "Ring of charos", "Leather boots", "Leather gloves", "Priest gown",
			"Magic secateurs", "Vyre noble shoes", "Vyre noble legs", "Vyre noble top", "Zamorak staff", "Guthix staff",
			"Saradomin staff", "Dawnbringer", "Diving apparatus", "Fishbowl helmet", "Anger sword", "Anger spear",
			"Anger mace", "Anger battleaxe"
			// Add more items here as needed
	));

	@Override
	protected void startUp() throws Exception {
		log.info("Equipment Lock started!");
		cacheData(config.groupId());
	}

	@Override
	protected void shutDown() throws Exception {
		log.info("Equipment Lock stopped!");
		localCache.clear();
	}

	public String cleanItemName(String itemName) {
		return itemName.replaceAll("<[^>]*>", "");
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event) {
		if (event.getMenuOption().equals("Wear") || event.getMenuOption().equals("Wield")) {
			String itemName = cleanItemName(event.getMenuTarget());
			String playerName = client.getLocalPlayer().getName();
			String groupId = config.groupId();
			if (itemName != null && playerName != null && !groupId.isEmpty()) {
				handleItemEquip(itemName, playerName, groupId, event);
			}
		}
	}

	private void handleItemEquip(String itemName, String playerName, String groupId, MenuOptionClicked event) {
		String accountHash = Long.toString(client.getAccountHash());

		log.info("Equipped item name: " + itemName);
		log.info("Account hash: " + accountHash);
		log.info("Group ID: " + groupId);

		if (config.excludeQuestItems() && QUEST_ITEMS_WHITELIST.contains(itemName)) {
			log.info("Item is whitelisted and quest item exclusion is enabled, bypassing server check.");
			return;
		}

		String cacheKey = groupId + "_" + itemName;
		String owner = localCache.get(cacheKey);

		if (owner != null) {
			// Item found in cache, check ownership
			log.info("Item found in cache with owner: " + owner);
			if (!accountHash.equals(owner)) {
				// Player is not the owner, prevent equipping
				log.info("Player is not the owner, preventing equip.");
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[Equipment Lock]: Another member of your group has already claimed the right to this item, so you cannot equip it.", null);
				event.consume();
			} else {
				log.info("Player is the owner, allowing equipping.");
			}
		} else {
			// Item not found in cache, fetch from server
			log.info("Item not found in cache, fetching from server.");
			Map<String, Object> payload = new HashMap<>();
			payload.put("action", "getItem");
			payload.put("group_item", cacheKey);

			try {
				String response = awsLambdaClient.callLambda(payload); // Synchronous call
				log.info("Response from getItem: " + response);

				// Parse the response (assuming the response contains the owner information)
				Map<String, Object> responseMap = gson.fromJson(response, new TypeToken<Map<String, Object>>() {}.getType());
				Map<String, Object> body = responseMap.containsKey("body") ? gson.fromJson(responseMap.get("body").toString(), new TypeToken<Map<String, Object>>() {}.getType()) : null;
				Map<String, String> item = body != null ? (Map<String, String>) body.get("Item") : null;

				log.info("Parsed item: " + item);
				if (item != null && item.containsKey(ATTRIBUTE_OWNER)) {
					// Item exists, update local cache
					String serverOwner = item.get(ATTRIBUTE_OWNER);
					log.info("Item exists with owner: " + serverOwner);
					localCache.put(cacheKey, serverOwner);

					if (!accountHash.equals(serverOwner)) {
						// Player is not the owner, prevent equipping
						log.info("Player is not the owner, preventing equip.");
						client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Someone else in your group has claimed the right to this item, so you cannot equip it.", null);
						event.consume();
					} else {
						log.info("Player is the owner, allowing equipping.");
					}
				} else {
					// Item does not exist, allow equipping immediately and create a new entry
					log.info("Item does not exist, allowing equip and creating new entry.");

					// Allow equipping immediately
					log.info("Player is allowed to equip the item.");

					// Create new entry in DynamoDB asynchronously
					CompletableFuture.runAsync(() -> {
						try {
							Map<String, Object> putPayload = new HashMap<>();
							putPayload.put("action", "putItem");
							Map<String, String> newItem = new HashMap<>();
							newItem.put(PARTITION_KEY, cacheKey);
							newItem.put(ATTRIBUTE_OWNER, accountHash);
							putPayload.put("item", newItem);

							awsLambdaClient.callLambdaAsync(putPayload).thenAccept(putResponse -> {
								log.info("Item added to DynamoDB: " + putResponse);
								localCache.put(cacheKey, accountHash); // Update the cache
							});
						} catch (Exception e) {
							log.error("Error adding item to DynamoDB", e);
						}
					});
				}
			} catch (Exception e) {
				log.error("Error handling item equip", e);
			}
		}
	}



	@Provides
	Equipment_Lock_Config provideConfig(ConfigManager configManager) {
		return configManager.getConfig(Equipment_Lock_Config.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (event.getGroup().equals("EquipmentLock") && event.getKey().equals("groupId")) {
			String newGroupId = config.groupId();
			log.info("Group ID changed: " + newGroupId);
			cacheData(newGroupId);
		}
	}

	private void cacheData(String groupId) {
		// Fetch and cache data asynchronously
		CompletableFuture.runAsync(() -> {
			try {
				log.info("Starting cache data process for groupId: " + groupId);

				Map<String, Object> payload = new HashMap<>();
				payload.put("action", "getAllItemsForGroup");
				payload.put("groupPrefix", groupId + "_"); // Adding underscore to group prefix

				log.info("Payload being sent: " + payload);

				String response = awsLambdaClient.callLambda(payload);
				log.info("Cache response: " + response);

				Map<String, Object> responseMap = gson.fromJson(response, new TypeToken<Map<String, Object>>() {}.getType());
				log.info("Parsed response map: " + responseMap);

				Map<String, Object> body = responseMap.containsKey("body") ? gson.fromJson(responseMap.get("body").toString(), new TypeToken<Map<String, Object>>() {}.getType()) : null;
				log.info("Parsed body: " + body);

				List<Map<String, String>> items = body != null ? (List<Map<String, String>>) body.get("Items") : null;
				log.info("Parsed items: " + items);

				if (items != null) {
					for (Map<String, String> item : items) {
						String key = item.get("group_item");
						String owner = item.get(ATTRIBUTE_OWNER);
						localCache.put(key, owner);
						log.info("Cached item - Key: " + key + ", Owner: " + owner);
					}
				}

				log.info("Cache updated with items: " + localCache);
			} catch (Exception e) {
				log.error("Error caching data", e);
			}
		});
	}
}
