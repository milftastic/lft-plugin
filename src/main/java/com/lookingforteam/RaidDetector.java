package com.lookingforteam;

import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;

/**
 * Detects whether the local player is inside a raid (past the entrance, not in
 * the lobby or recruiting area). Only reads the local player's own state.
 */
final class RaidDetector
{
	/** ToB party status: 0 = no party, 1 = in party (outside), 2+ = inside the theatre. */
	private static final int TOB_INSIDE = 2;

	/** ToA raid rooms (nexus, paths, bosses, wardens, tomb). The lobby (13454) is deliberately excluded. */
	private static final Set<Integer> TOA_RAID_REGIONS = Set.of(
		14160, // Nexus
		15698, 15700, // Crondis path, Zebak
		14162, 14164, // Scabaras path, Kephri
		15186, 15188, // Apmeken path, Ba-Ba
		14674, 14676, // Het path, Akkha
		15184, 15696, // Wardens
		14672 // Tomb (loot room)
	);

	private RaidDetector()
	{
	}

	/** Client thread only. */
	static boolean inRaid(Client client)
	{
		return inChambers(client) || inTheatre(client) || inTombs(client) || inArchive(client);
	}

	/**
	 * The Fractured Archive. Unlike ToB, its party status stays at 1 inside the raid,
	 * so we combine "in a TFA party" with "in an instance": the lobby isn't instanced,
	 * the raid is (confirmed in-game).
	 */
	static boolean inArchive(Client client)
	{
		return client.getVarbitValue(VarbitID.TFA_CLIENT_PARTYSTATUS) >= 1
			&& client.getTopLevelWorldView().isInstance();
	}

	static boolean inChambers(Client client)
	{
		return client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) == 1;
	}

	static boolean inTheatre(Client client)
	{
		return client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS) >= TOB_INSIDE;
	}

	static boolean inTombs(Client client)
	{
		return TOA_RAID_REGIONS.contains(currentRegion(client));
	}

	/** Region id of the local player, resolved to the template region when in an instance. -1 if unknown. */
	static int currentRegion(Client client)
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return -1;
		}
		LocalPoint local = player.getLocalLocation();
		if (local == null)
		{
			return -1;
		}
		WorldPoint world = WorldPoint.fromLocalInstance(client, local);
		return world == null ? -1 : world.getRegionID();
	}
}
