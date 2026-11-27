package com.lookingforteam;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

/**
 * Per-activity "looking for" toggles are stored in this group under
 * {@link Activity#configKey()} and edited from the side panel, not here.
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
		keyName = "serverUrl",
		name = "Server URL",
		description = "Base URL of the LFT server",
		section = serverSection,
		position = 1
	)
	default String serverUrl()
	{
		return "https://osrs-lft.fly.dev";
	}

	@Range(min = 10, max = 120)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "refreshInterval",
		name = "Refresh interval",
		description = "How often to check for friends looking for the same raids. "
			+ "Nothing is sent while you're not looking for anything.",
		section = serverSection,
		position = 2
	)
	default int refreshInterval()
	{
		return 15;
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
		name = "Remember selections between logins",
		description = "Keep what you're looking for ticked after you log out. "
			+ "Off by default, so nobody stays listed as looking by accident.",
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
