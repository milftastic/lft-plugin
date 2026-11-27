package com.lookingforteam;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

/**
 * The raids you're looking for are edited from the side panel, not here. They're
 * kept in memory, and saved per account only with "Remember between logins".
 */
@ConfigGroup(LookingForTeamConfig.GROUP)
public interface LookingForTeamConfig extends Config
{
	String GROUP = "lookingforteam";

	@ConfigSection(
		name = "Server",
		description = "Settings for the LFT server",
		position = 0
	)
	String serverSection = "server";

	@ConfigItem(
		keyName = "enableOnline",
		name = "Enable online features",
		description = "Share your LFT status and see friends' and clanmates' statuses. "
			+ "Only hashed player names are sent, never plain names.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = serverSection,
		position = 0
	)
	default boolean enableOnline()
	{
		return false;
	}

	@ConfigItem(
		keyName = "includeFriends",
		name = "Check friends",
		description = "Look up online friends",
		position = 1
	)
	default boolean includeFriends()
	{
		return true;
	}

	@ConfigItem(
		keyName = "includeClan",
		name = "Check clan",
		description = "Look up online clanmates",
		position = 2
	)
	default boolean includeClan()
	{
		return true;
	}

	@ConfigItem(
		keyName = "persistSelections",
		name = "Remember between logins",
		description = "Keep what you're looking for ticked after you log out.",
		position = 3
	)
	default boolean persistSelections()
	{
		return false;
	}

	@ConfigItem(
		keyName = "chatNotifications",
		name = "Chat notifications",
		description = "Post a chat message when a friend or clanmate is looking for something you're looking for",
		position = 4
	)
	default boolean chatNotifications()
	{
		return true;
	}
}
