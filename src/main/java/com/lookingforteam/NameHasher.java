package com.lookingforteam;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.runelite.client.util.Text;

/**
 * Turns display names into the opaque ids the server sees. Names that the game
 * treats as the same player (case, spaces vs underscores, non-breaking spaces)
 * produce the same ids.
 * <p>
 * Note: RSNs are short and guessable, so this hides names from casual viewing
 * and keeps them out of server logs. It is not strong anonymity.
 */
final class NameHasher
{
	// Bump the versions if normalisation ever changes, so old and new ids don't silently mismatch.
	private static final String OWNER_PREFIX = "lft-v1:";
	private static final String VISIBILITY_PREFIX = "lft-vis-v1:";

	private NameHasher()
	{
	}

	static String normalize(String name)
	{
		return Text.toJagexName(name)
			.toLowerCase(Locale.ROOT)
			.replace('_', ' ');
	}

	/** Parses a comma-separated list of names into normalized names, ignoring blanks. */
	static Set<String> normalizeList(String commaSeparated)
	{
		Set<String> names = new HashSet<>();
		if (commaSeparated == null)
		{
			return names;
		}
		for (String part : commaSeparated.split(","))
		{
			String name = normalize(part).trim();
			if (!name.isEmpty())
			{
				names.add(name);
			}
		}
		return names;
	}

	/** Identifies a player's own status record on the server (so it can be replaced or cleared). */
	static String hash(String name)
	{
		return sha256(OWNER_PREFIX + normalize(name));
	}

	/**
	 * Key meaning "{@code owner}'s status may be seen by {@code viewer}". The owner
	 * publishes one per friend/clanmate; a viewer looks up {@code visibilityKey(friend, self)}.
	 * Direction matters: A having B as a friend doesn't make B's status visible to A.
	 */
	static String visibilityKey(String owner, String viewer)
	{
		return sha256(VISIBILITY_PREFIX + normalize(owner) + "\n" + normalize(viewer));
	}

	private static String sha256(String input)
	{
		return Hashing.sha256().hashString(input, StandardCharsets.UTF_8).toString();
	}
}
