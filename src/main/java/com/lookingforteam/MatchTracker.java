package com.lookingforteam;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Works out which matches are new since the last refresh, so each player is
 * announced once per shared activity. If they stop and start again, or you
 * untick and re-tick, they're announced again.
 */
final class MatchTracker
{
	/** More new matches than this in one refresh get a single summary line instead. */
	static final int MAX_INDIVIDUAL_MESSAGES = 3;

	/** Normalized name -> activities we've already announced for that player. */
	private final Map<String, Set<Activity>> announced = new HashMap<>();

	/** @return chat messages to show; empty if nothing is new */
	synchronized List<String> update(List<LftEntry> entries, Set<Activity> mine)
	{
		Map<String, Set<Activity>> current = new HashMap<>();
		List<String> messages = new ArrayList<>();

		for (LftEntry entry : entries)
		{
			Set<Activity> shared = EnumSet.noneOf(Activity.class);
			shared.addAll(entry.getActivities());
			shared.retainAll(mine);
			if (shared.isEmpty())
			{
				continue;
			}

			String key = NameHasher.normalize(entry.getName());
			Set<Activity> fresh = EnumSet.copyOf(shared);
			Set<Activity> before = announced.get(key);
			if (before != null)
			{
				fresh.removeAll(before);
			}
			if (!fresh.isEmpty())
			{
				messages.add(entry.getName() + " is looking for a team for " + describe(fresh) + ".");
			}
			current.put(key, shared);
		}

		announced.clear();
		announced.putAll(current);

		if (messages.size() > MAX_INDIVIDUAL_MESSAGES)
		{
			return List.of(messages.size() + " friends and clanmates are looking for a team for what you want."
				+ " See the Looking For Team panel.");
		}
		return messages;
	}

	synchronized void reset()
	{
		announced.clear();
	}

	private static String describe(Set<Activity> activities)
	{
		return activities.stream()
			.map(Activity::getDisplayName)
			.collect(Collectors.joining(", "));
	}
}
