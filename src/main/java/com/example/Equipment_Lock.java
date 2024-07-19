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

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;

@Slf4j
@PluginDescriptor(
		name = "Equipment Lock"
)
public class Equipment_Lock extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private Equipment_Lock_Config config;

	private DynamoDbClient dynamoDbClient;
	private static final String TABLE_NAME = "Item_Assignments";
	private static final String PARTITION_KEY = "group_item"; // Composite key for item and group
	private static final String ATTRIBUTE_ITEM_NAME = "item_name";
	private static final String ATTRIBUTE_OWNER = "owner";
	private static final String ATTRIBUTE_GROUP_ID = "groupId";

	// Whitelist of items required for quests
	private static final Set<String> QUEST_ITEMS_WHITELIST = new HashSet<>(Arrays.asList(
			"Spiked boots", "Desert disguise", "Dramen staff", "Ghostspeak amulet", "Glarial's amulet",
			"Ham hood", "Ham shirt", "Ham robe", "Ham boots", "Ham cloak", "Ham logo", "Ice gloves",
			"M'speak amulet", "Monkey greegree", "Zombie monkey greegree", "Ninja monkey greegree",
			"Kruk monkey greegree", "Bearded gorilla greegree", "Gorilla greegree", "Monkey talisman",
			"Catspeak amulet", "Gas mask", "Mourner top", "Mourner trousers", "Mourner boots",
			"Mourner gloves", "Mourner cloak", "Ogre bow", "SilverLight", "Karamjan monkey greegree",
			"Ogre bow", "SilverLight", "Excalibur", "Ring of visibility", "Ivandis flail", "Blisterwood flail",
			"Bronze med helm", "Iron chainbody", "Plague jacket", "Plague trousers", "Climbing boots",
			"Gold helmet", "Fixed device", "Ice arrows", "Lunar helm", "Lunar amulet", "Lunar ring",
			"Lunar staff", "Clockwork suit", "Silly jester hat", "Silly jester top", "Silly jester tights",
			"Silly jester boots", "Builder's hat", "Builder's shirt", "Builder's trousers", "Builder's boots",
			"Bomber cap", "Bomber jacket", "Ahrim's book", "Torag's hammers", "Verac's flail", "Karil's crossbow",
			"Dharok's axe", "Guthan's spear", "Ahrim's staff", "Black full helm", "Black platebody",
			"Black platelegs", "Elite black platebody", "Elite black platelegs", "Dark squall hood",
			"Dark squall robe top", "Dark squall robe bottom", "Slave shirt", "Slave robe", "Slave boots",
			"Desert shirt", "Desert robes", "Desert boots", "Anti-dragon shield", "Khazard armour",
			"Khazard helmet", "Ogre boots", "White goblin mail", "Yellow goblin mail", "Black goblin mail",
			"Blue goblin mail", "Orange goblin mail", "Purple goblin mail", "Ring of charos(a)", "Leather boots",
			"Priest gown","Magic secateurs","Vyre noble shoes","Vyre noble legs","Vyre noble top","Zamorak staff",
			"Guthix staff","Saradomin staff","Dawnbringer", "Leather gloves"
			// Add more items here as needed
	));


	@Override
	protected void startUp() throws Exception
	{
		log.info("Equipment Lock started!");
		connectToDynamoDB();
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Equipment Lock stopped!");
		if (dynamoDbClient != null) {
			dynamoDbClient.close();
		}
	}

	private void connectToDynamoDB() {
		String accessKey = "AKIA47CRXKA3SDKWIEGS";
		String secretKey = "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";

		AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKey, secretKey);
		dynamoDbClient = DynamoDbClient.builder()
				.region(Region.US_EAST_2)
				.credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
				.build();
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
		// Print the equipped item's name and the username of the player for debugging
		log.info("Equipped item name: " + itemName);
		log.info("Player name: " + playerName);
		log.info("Group ID: " + groupId);

		// Check if the item is in the whitelist and the checkbox is checked
		if (config.excludeQuestItems() && QUEST_ITEMS_WHITELIST.contains(itemName)) {
			log.info("Item is whitelisted and quest item exclusion is enabled, bypassing server check.");
			return; // Allow equipping without interacting with the server
		}

		// Check if the item exists in the DynamoDB table
		Map<String, AttributeValue> key = new HashMap<>();
		key.put(PARTITION_KEY, AttributeValue.builder().s(groupId + "_" + itemName).build());

		GetItemRequest getItemRequest = GetItemRequest.builder()
				.tableName(TABLE_NAME)
				.key(key)
				.build();

		GetItemResponse getItemResponse = dynamoDbClient.getItem(getItemRequest);
		Map<String, AttributeValue> item = getItemResponse.item();

		if (item == null || !item.containsKey(ATTRIBUTE_OWNER)) {
			// Item does not exist, add it to the table
			Map<String, AttributeValue> newItem = new HashMap<>();
			newItem.put(PARTITION_KEY, AttributeValue.builder().s(groupId + "_" + itemName).build());
			newItem.put(ATTRIBUTE_OWNER, AttributeValue.builder().s(playerName).build());

			PutItemRequest putItemRequest = PutItemRequest.builder()
					.tableName(TABLE_NAME)
					.item(newItem)
					.build();

			PutItemResponse putItemResponse = dynamoDbClient.putItem(putItemRequest);
			log.info("Item added to DynamoDB: " + putItemResponse);
		} else {
			// Item exists, check ownership
			String owner = item.get(ATTRIBUTE_OWNER).s();
			if (!playerName.equals(owner)) {
				// Player is not the owner, prevent equipping
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "In your group, " + owner + " has claimed the right to this item, so you cannot equip it.", null);
				event.consume();
			}
		}
	}

	@Provides
	Equipment_Lock_Config provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(Equipment_Lock_Config.class);
	}
}
