package com.lookingforteam;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class LookingForTeamPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(LookingForTeamPlugin.class);
		RuneLite.main(args);
	}
}
