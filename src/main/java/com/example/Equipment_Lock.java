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
import com.google.common.collect.ImmutableSet;
import net.runelite.api.Actor;
import net.runelite.api.coords.WorldPoint;

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

	public WorldPoint getLocation(Client client) {
    		return getLocation(client, client.getLocalPlayer());
	}

	public WorldPoint getLocation(Client client, Actor actor) {
    		if (client.isInInstancedRegion()) {
        		return WorldPoint.fromLocalInstance(client, actor.getLocalLocation());
    		}
    		return actor.getWorldLocation();
	}
	
	public final Set<Integer> LMS_REGIONS = ImmutableSet.of(13658, 13659, 13660, 13914, 13915, 13916, 13918, 13919, 13920, 14174, 14175, 14176, 14430, 14431, 14432);

	public boolean isLastManStanding(Client client) {
        	if (LMS_REGIONS.contains(getLocation(client).getRegionID())) {
           		return true;
		}
		return false;
	}

	// Whitelist of items required for quests
	private static final Set<String> QUEST_ITEMS_WHITELIST = new HashSet<>(Arrays.asList(
			"Spiked boots", "Desert disguise", "Dramen staff", "Ghostspeak amulet", "Glarial's amulet",
			"Ham shirt", "Ham robe", "Ham gloves", "Ham boots", "Ham cloak", "Ham logo", "Ice gloves",
			"M'speak amulet", "Zombie monkey greegree", "Ninja monkey greegree", "Kruk monkey greegree",
			"Bearded gorilla greegree", "Gorilla greegree", "Monkey talisman", "Karamjan monkey greegree",
			"Catspeak amulet", "Gas mask", "Mourner top", "Mourner trousers", "Mourner boots", "Ham hood",
			"Mourner gloves", "Mourner cloak", "Ogre bow", "SilverLight", "Excalibur", "Ring of visibility",
			"Ivandis flail", "Blisterwood flail", "Bronze med helm", "Iron chainbody", "Plague jacket",
			"Plague trousers", "Climbing boots", "Gold helmet", "Fixed device", "Ice arrows", "Medical gown",
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
			"Anger mace", "Anger battleaxe", "Bronze arrow", "Bronze sword", "Shortbow" 
			// Add more items here as needed
	));
	// Whitelist of items required for clue scrolls (different tiers)
	private static final Set<String> BEGINNER_CLUE_ITEMS_WHITELIST = new HashSet<>(Arrays.asList(
    		"Gold ring", "Gold necklace", "Chef's hat", "Red cape", "Bronze axe", "Leather boots"
	));
	private static final Set<String> EASY_CLUE_ITEMS_WHITELIST = new HashSet<>(Arrays.asList(
    		"Studded body", "Bronze platelegs", "Staff", "Coif", "Iron platebody", "Leather gloves", "Iron chainbody", "Leather chaps",
		"Iron platelegs", "Emerald amulet", "Oak longbow", "Blue wizard hat", "Bronze 2h sword", "Ham boots", "Steel plateskirt", 
		"Sapphire necklace", "White apron", "Green boots", "Iron med helm", "Emerald ring", "Blue robe top", "Ham robe", "Tiara",
		"Sapphire amulet", "Bronze chainbody", "Sapphire ring", "Longbow", "Pink skirt", "Pink robe top", "Body tiara", 
		"Steel full helm", "Steel platebody", "Iron plateskirt", "Bronze dagger", "Iron full helm", "Gold ring", "Air tiara", 
		"Staff of water", "Desert shirt", "Leather boots", "Green robe bottoms", "Steel axe", "Leather cowl", "Steel pickaxe", 
		"Steel med helm", "Gold necklace", "Bronze spear", "Green hat", "Cream robe top", "Studded chaps", "Bronze full helm",
		"Black axe", "Ruby ring", "Blue robe top", "Turquoise robe bottoms", "Oak shortbow", "Hardleather body", "Bronze axe", 
		"Mithril pickaxe", "Black platebody", "Iron kiteshield", "Black cape", "Steel mace", "Steel longsword", "Emerald necklace",
		"Blue skirt", "Turquoise robe top", "Green robe top", "Iron warhammer"
	));

	private static final Set<String> MEDIUM_CLUE_ITEMS_WHITELIST = new HashSet<>(Arrays.asList(
    		"Green d'hide chaps", "Ring of dueling", "Mithril med helm", "Adamant platebody", "Adamant full helm", "Adamant platelegs",
		"Green hat", "Snakeskin boots", "Iron pickaxe", "Steel platebody", "Maple shortbow", "Team-1 cape", "Team-2 cape", "Team-3 cape", 
		"Team-4 cape", "Team-5 cape", "Team-6 cape", "Team-7 cape", "Team-8 cape", "Team-9 cape", "Team-10 cape","Team-11 cape", 
		"Team-12 cape", "Team-13 cape", "Team-14 cape", "Team-15 cape","Team-16 cape", "Team-17 cape", "Team-18 cape", "Team-19 cape", 
		"Team-20 cape","Team-21 cape", "Team-22 cape", "Team-23 cape", "Team-24 cape", "Team-25 cape", "Team-26 cape", "Team-27 cape", 
		"Team-28 cape", "Team-29 cape", "Team-30 cape","Team-31 cape", "Team-32 cape", "Team-33 cape", "Team-34 cape", "Team-35 cape",
    		"Team-36 cape", "Team-37 cape", "Team-38 cape", "Team-39 cape", "Team-40 cape","Team-41 cape", "Team-42 cape", "Team-43 cape", 
		"Team-44 cape", "Team-45 cape","Team-46 cape", "Team-47 cape", "Team-48 cape", "Team-49 cape", "Team-50 cape", "Team cape i",
		"Team cape x", "Team cape zero", "Brown apron", "Leather boots", "Leather gloves", "Green d'hide body", "Steel sq shield",
		"Adamant halberd", "Mystic robe bottom", "Diamond ring", "Adamant warhammer", "Mithril boots", "Ring of life", "Blue boots",
		"Hardleather body", "Silver sickle", "Adamant sword", "Sapphire amulet", "Adamant plateskirt", "Adamant sq shield", "Bone dagger",
		"Mithril platebody", "Steel kiteshield", "Ring of forging", "Bruise blue snelm", "Staff of air", "Iron 2h sword", "Bronze full helm",
		"Amulet of power", "Steel longsword", "Blue d'hide body", "Mystic gloves", "Adamant med helm", "Snakeskin chaps", "Mithril plateskirt",
		"Maple longbow", "Iron med helm", "Purple gloves", "Mithril full helm", "Mithril chainbody", "Ruby amulet", "Mithril scimitar", 
		"Blue d'hide vambraces", "Adamant boots", "Adamant dagger", "Steel platelegs", "Blue wizard hat", "Blue wizard robe" 
	));

	private static final Set<String> HARD_CLUE_ITEMS_WHITELIST = new HashSet<>(Arrays.asList(
    		"Rune shield (h1)", "Rune shield (h2)", "Rune shield (h3)", "Rune shield (h4)", "Rune shield (h5)", "Mystic hat", "Bone spear",
		"Rune helm (h1)", "Rune helm (h2)", "Rune helm (h3)", "Rune helm (h4)", "Rune helm (h5)", "Rune platebody", "Elemental shield",
		"Saradomin stole", "Guthix stole", "Zamorak stole", "Armadyl stole", "Bandos stole", "Ancient stole", "Blue d'hide chaps", 
		"Rune warhammer", "Blue d'hide body", "Blue d'hide vambraces", "Mystic robe top", "Ring of life", "Amulet of glory", "Rune boots", 
		"Adamant 2h sword", "Mystic fire staff", "Diamond bracelet", "Rune spear", "Rune platelegs", "Rune full helm", "Fire battlestaff",
		"Mithril platelegs", "Saradomin crozier", "Guthix crozier", "Zamorak crozier", "Armadyl crozier", "Bandos crozier", "Ancient crozier",
		"Rune axe", "Red headband", "Black headband", "Brown headband", "White headband", "Blue headband", "Gold headband", "Pink headband", 
		"Green headband", "Diamond ring", "Amulet of power", "Rune halberd", "Amulet of strength", "Iron platebody", "Adamant sq shield",
		"Rune pickaxe", "Rune longsword", "Rune plateskirt"
	));

	private static final Set<String> ELITE_CLUE_ITEMS_WHITELIST = new HashSet<>(Arrays.asList(
    		"Black d'hide chaps", "Spotted cape", "Rolling pin", "Cape of legends", "Dragon battleaxe", "Amulet of glory(6)", "Climbing boots",
		"Amulet of glory", "Amulet of glory(1)", "Amulet of glory(2)", "Amulet of glory(3)", "Amulet of glory(4)", "Amulet of glory(5)", 
		"Amulet of eternal glory", "Holy book", "Unholy book", "Book of balance", "Book of war", "Book of law", "Book of darkness",
		"Sunfire fanatic helm", "Sunfire fanatic cuirass", "Sunfire fanatic chausses", "Rune crossbow", "Ring of visibility", 
		"Ring of shadows", "Ring of shadows (uncharged)", "Barrows gloves", "Dragon med helm", "Seercull", "Helm of neitiznot",
		"Saradomin mitre", "Guthix mitre", "Zamorak mitre", "Armadyl mitre", "Bandos mitre", "Ancient mitre", "Combat bracelet(1)", 
		"Combat bracelet", "Combat bracelet(2)", "Combat bracelet(3)", "Combat bracelet(4)", "Combat bracelet(5)", "Combat bracelet(6)", 
		"Pirate bandana", "Dragon necklace", "Magic longbow", "Rune boots", "Proselyte hauberk", "Dragonstone ring", "Granite shield", 
		"Splitbark body", "Splitbark legs", "Dragon boots", "Rune longsword", "Castle wars bracelet(3)", "Castle wars bracelet(2)", 
		"Castle wars bracelet(1)", "Dragonstone amulet", "Ring of forging", "Blue d'hide vambraces", "Dragon spear", "Rune plateskirt", 
		"Farmer's strawhat", "Shayzien body (5)", "Pyromancer robe", "Black salamander", "Mystic robe bottom", "Rune kiteshield", 
		"Bob's black shirt", "Bob's blue shirt", "Bob's green shirt", "Bob's purple shirt", "Bob's red shirt", "Red d'hide chaps",
		"Lava battlestaff", "Black d'hide vambraces", "Mind shield"
	));

	private static final Set<String> MASTER_CLUE_ITEMS_WHITELIST = new HashSet<>(Arrays.asList(
    		"Bryophyta's staff (uncharged)", "Bryophyta's staff", "Nature tiara", "Zamorak full helm", "Shadow sword", "Dragon battleaxe",
		"Dragon defender", "Araxyte slayer helmet", "Black slayer helmet", "Green slayer helmet", "Red slayer helmet", "Purple slayer helmet", 
		"Turquoise slayer helmet", "Hydra slayer helmet", "Twisted slayer helmet", "Tztok slayer helmet", "Vampyric slayer helmet", 
		"Tzkal slayer helmet", "Araxyte slayer helmet (i)", "Black slayer helmet (i)", "Green slayer helmet (i)", "Red slayer helmet (i)", 
		"Purple slayer helmet (i)", "Turquoise slayer helmet (i)", "Hydra slayer helmet (i)", "Twisted slayer helmet (i)", "Tztok slayer helmet (i)",
		"Vampyric slayer helmet (i)", "Tzkal slayer helmet (i)", "Slayer helmet", "Slayer helmet (i)", "Crystal bow", "Crystal bow (inactive)", 
		"Black d'hide body", "Black d'hide chaps", "Black d'hide vambraces", "Flared trousers", "Fire cape", "Toktz-xil-ul", "Iban's staff", 
		"Iban's staff (u)", "Mystic robe top (dark)", "Mystic robe bottom (dark)", "Black dragon mask", "Death tiara", "Cape of legends", 
		"Ahrim's hood", "Ahrim's hood 100", "Ahrim's hood 75", "Ahrim's hood 50", "Ahrim's hood 25", "Ring of wealth", "Blue moon helm", 
   		"Ahrim's robetop", "Ahrim's robetop 100", "Ahrim's robetop 75", "Ahrim's robetop 50", "Ahrim's robetop 25", "Blue moon chestplate",
   		"Ahrim's robeskirt", "Ahrim's robeskirt 100", "Ahrim's robeskirt 75", "Ahrim's robeskirt 50", "Ahrim's robeskirt 25", "Bandos cloak", 
    		"Ahrim's staff", "Ahrim's staff 100", "Ahrim's staff 75", "Ahrim's staff 50", "Ahrim's staff 25", "Blue moon tassets", "Bandos godsword",
    		"Dharok's helm", "Dharok's helm 100", "Dharok's helm 75", "Dharok's helm 50", "Dharok's helm 25", "Blue moon spear", "Dragon 2h sword", 
    		"Dharok's platebody", "Dharok's platebody 100", "Dharok's platebody 75", "Dharok's platebody 50", "Dharok's platebody 25", "Bandos boots", 
    		"Dharok's platelegs", "Dharok's platelegs 100", "Dharok's platelegs 75", "Dharok's platelegs 50", "Dharok's platelegs 25",
    		"Dharok's greataxe", "Dharok's greataxe 100", "Dharok's greataxe 75", "Dharok's greataxe 50", "Dharok's greataxe 25", "Guardian boots", 
    		"Guthan's helm", "Guthan's helm 100", "Guthan's helm 75", "Guthan's helm 50", "Guthan's helm 25", "Bandos platebody", "Dragon pickaxe", 
   		"Guthan's platebody", "Guthan's platebody 100", "Guthan's platebody 75", "Guthan's platebody 50", "Guthan's platebody 25",
   		"Guthan's chainskirt", "Guthan's chainskirt 100", "Guthan's chainskirt 75", "Guthan's chainskirt 50", "Guthan's chainskirt 25",
  		"Guthan's warspear", "Guthan's warspear 100", "Guthan's warspear 75", "Guthan's warspear 50", "Guthan's warspear 25", "Pharaoh's sceptre",
   		"Karil's coif", "Karil's coif 100", "Karil's coif 75", "Karil's coif 50", "Karil's coif 25", "Obsidian cape", "Dragon med helm", 
   		"Karil's leathertop", "Karil's leathertop 100", "Karil's leathertop 75", "Karil's leathertop 50", "Karil's leathertop 25",
    		"Karil's leatherskirt", "Karil's leatherskirt 100", "Karil's leatherskirt 75", "Karil's leatherskirt 50", "Karil's leatherskirt 25",
    		"Karil's crossbow", "Karil's crossbow 100", "Karil's crossbow 75", "Karil's crossbow 50", "Karil's crossbow 25", "Rune platebody", 
    		"Torag's helm", "Torag's helm 100", "Torag's helm 75", "Torag's helm 50", "Torag's helm 25", "Echo boots", "Toktz-ket-xil",
   		"Torag's platebody", "Torag's platebody 100", "Torag's platebody 75", "Torag's platebody 50", "Torag's platebody 25",
   		"Torag's platelegs", "Torag's platelegs 100", "Torag's platelegs 75", "Torag's platelegs 50", "Torag's platelegs 25",
   		"Torag's hammers", "Torag's hammers 100", "Torag's hammers 75", "Torag's hammers 50", "Torag's hammers 25", "Arclight", 
   		"Verac's helm", "Verac's helm 100", "Verac's helm 75", "Verac's helm 50", "Verac's helm 25", "Brine sabre", "Amulet of the damned",  
    		"Verac's brassard", "Verac's brassard 100", "Verac's brassard 75", "Verac's brassard 50", "Verac's brassard 25", "Rune boots",
    		"Verac's plateskirt", "Verac's plateskirt 100", "Verac's plateskirt 75", "Verac's plateskirt 50", "Verac's plateskirt 25",
    		"Verac's flail", "Verac's flail 100", "Verac's flail 75", "Verac's flail 50", "Verac's flail 25", "Zamorak godsword", 
		"Amulet of glory", "Amulet of glory(1)", "Amulet of glory(2)", "Amulet of glory(3)", "Amulet of glory(4)", "Amulet of glory(5)", 
		"Amulet of glory(6)", "Amulet of eternal glory", "Abyssal whip", "Abyssal whip (or)", "Frozen abyssal whip", "Volcanic abyssal whip",
		"Dragon pickaxe (or)", "Infernal pickaxe", "Crystal pickaxe (inactive)", "Crystal pickaxe", "Helm of neitiznot", "Dragon axe", 
		"Infernal axe", "Infernal axe (uncharged)", "Infernal pickaxe (uncharged)", "Dragon felling axe", "Crystal felling axe (inactive)",
		"Crystal felling axe", "Crystal axe (inactive)", "Crystal axe", "Dragon plateskirt", "Climbing boots", "Dragon chainbody", 
		"Dragon sq shield", "Splitbark body", "Red boater", "Orange boater", "Green boater", "Blue boater", "Black boater", "Pink boater", 
		"Purple boater", "White boater", "Menaphite purple robe", "Menaphite purple top", "Menaphite purple hat", "Hueycoatl hide coif", "Hueycoatl hide vambraces"
		
		
		
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

		log.debug("Equipped item name: " + itemName);
		log.debug("Account hash: " + accountHash);
		log.debug("Group ID: " + groupId);
		String specificAccount = config.specificAccount();

		if (!specificAccount.isEmpty() && !playerName.equals(specificAccount)) { 
			log.debug("Player is not on desired account, bypassing server check.");
			return;
		}
		
		if (isLastManStanding(client)) { 
			log.debug("Player is in Last Man Standing, bypassing server check.");
			return;
		}

		if (config.excludeQuestItems() && QUEST_ITEMS_WHITELIST.contains(itemName)) {
			log.debug("Item is whitelisted and quest item exclusion is enabled, bypassing server check.");
			return;
		}
    		if (config.excludeBeginnerClues() && BEGINNER_CLUE_ITEMS_WHITELIST.contains(itemName)) {
        		log.debug("Item is whitelisted for easy clue scrolls, bypassing server check.");
        		return;
   		 }
		if (config.excludeEasyClues() && EASY_CLUE_ITEMS_WHITELIST.contains(itemName)) {
        		log.debug("Item is whitelisted for easy clue scrolls, bypassing server check.");
        		return;
   		 }
		 if (config.excludeMediumClues() && MEDIUM_CLUE_ITEMS_WHITELIST.contains(itemName)) {
       			 log.debug("Item is whitelisted for medium clue scrolls, bypassing server check.");
       			 return;
    		}
   		 if (config.excludeHardClues() && HARD_CLUE_ITEMS_WHITELIST.contains(itemName)) {
       			 log.debug("Item is whitelisted for hard clue scrolls, bypassing server check.");
       			 return;
    		}
   		 if (config.excludeEliteClues() && ELITE_CLUE_ITEMS_WHITELIST.contains(itemName)) {
    			    log.debug("Item is whitelisted for elite clue scrolls, bypassing server check.");
     			    return;
   		 }
  		  if (config.excludeMasterClues() && MASTER_CLUE_ITEMS_WHITELIST.contains(itemName)) {
       			 log.debug("Item is whitelisted for master clue scrolls, bypassing server check.");
        		 return;
    		}
		

		String cacheKey = groupId + "_" + itemName;
		String owner = localCache.get(cacheKey);

		if (owner != null) {
			// Item found in cache, check ownership
			log.debug("Item found in cache with owner: " + owner);
			if (!accountHash.equals(owner)) {
				// Player is not the owner, prevent equipping
				log.debug("Player is not the owner, preventing equip.");
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[Equipment Lock]: Another member of your group has already claimed the right to this item, so you cannot equip it.", null);
				event.consume();
			} else {
				log.debug("Player is the owner, allowing equipping.");
			}
		} else {
			// Item not found in cache, fetch from server
			log.debug("Item not found in cache, fetching from server.");
			Map<String, Object> payload = new HashMap<>();
			payload.put("action", "getItem");
			payload.put("group_item", cacheKey);

			try {
				String response = awsLambdaClient.callLambda(payload); // Synchronous call
				log.debug("Response from getItem: " + response);

				// Parse the response (assuming the response contains the owner information)
				Map<String, Object> responseMap = gson.fromJson(response, new TypeToken<Map<String, Object>>() {}.getType());
				Map<String, Object> body = responseMap.containsKey("body") ? gson.fromJson(responseMap.get("body").toString(), new TypeToken<Map<String, Object>>() {}.getType()) : null;
				Map<String, String> item = body != null ? (Map<String, String>) body.get("Item") : null;

				log.debug("Parsed item: " + item);
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
					log.debug("Player is allowed to equip the item.");

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
								log.debug("Item added to DynamoDB: " + putResponse);
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
			log.debug("Group ID changed: " + newGroupId);
			cacheData(newGroupId);
		}
	}

	private void cacheData(String groupId) {
		// Fetch and cache data asynchronously
		CompletableFuture.runAsync(() -> {
			try {
				log.debug("Starting cache data process for groupId: " + groupId);

				Map<String, Object> payload = new HashMap<>();
				payload.put("action", "getAllItemsForGroup");
				payload.put("groupPrefix", groupId + "_"); // Adding underscore to group prefix

				log.debug("Payload being sent: " + payload);

				String response = awsLambdaClient.callLambda(payload);
				log.debug("Cache response: " + response);

				Map<String, Object> responseMap = gson.fromJson(response, new TypeToken<Map<String, Object>>() {}.getType());
				log.debug("Parsed response map: " + responseMap);

				Map<String, Object> body = responseMap.containsKey("body") ? gson.fromJson(responseMap.get("body").toString(), new TypeToken<Map<String, Object>>() {}.getType()) : null;
				log.debug("Parsed body: " + body);

				List<Map<String, String>> items = body != null ? (List<Map<String, String>>) body.get("Items") : null;
				log.debug("Parsed items: " + items);

				if (items != null) {
					for (Map<String, String> item : items) {
						String key = item.get("group_item");
						String owner = item.get(ATTRIBUTE_OWNER);
						localCache.put(key, owner);
						log.debug("Cached item - Key: " + key + ", Owner: " + owner);
					}
				}

				log.debug("Cache updated with items: " + localCache);
			} catch (Exception e) {
				log.error("Error caching data", e);
			}
		});
	}
}
