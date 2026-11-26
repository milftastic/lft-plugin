package com.lookingforteam;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class MatchTrackerTest
{
	private static LftEntry entry(String name, Activity... activities)
	{
		return new LftEntry(name, true, false, 330, EnumSet.of(activities[0], activities));
	}

	@Test
	public void announcesNewMatchOnce()
	{
		MatchTracker tracker = new MatchTracker();
		Set<Activity> mine = EnumSet.of(Activity.TOB);
		List<LftEntry> entries = List.of(entry("Bob", Activity.TOB));

		assertEquals(List.of("Bob is looking for a team for Theatre of Blood."), tracker.update(entries, mine));
		assertTrue(tracker.update(entries, mine).isEmpty());
	}

	@Test
	public void ignoresActivitiesYouDontWant()
	{
		MatchTracker tracker = new MatchTracker();
		assertTrue(tracker.update(List.of(entry("Bob", Activity.COX)), EnumSet.of(Activity.TOB)).isEmpty());
	}

	@Test
	public void announcesWhenYouTickSomethingTheyAlreadyWant()
	{
		MatchTracker tracker = new MatchTracker();
		List<LftEntry> entries = List.of(entry("Bob", Activity.COX, Activity.TOB));
		tracker.update(entries, EnumSet.of(Activity.TOB));

		assertEquals(List.of("Bob is looking for a team for Chambers of Xeric."),
			tracker.update(entries, EnumSet.of(Activity.TOB, Activity.COX)));
	}

	@Test
	public void reannouncesAfterTheyStopAndStartAgain()
	{
		MatchTracker tracker = new MatchTracker();
		Set<Activity> mine = EnumSet.of(Activity.TOB);
		tracker.update(List.of(entry("Bob", Activity.TOB)), mine);
		tracker.update(List.of(), mine);

		assertEquals(1, tracker.update(List.of(entry("Bob", Activity.TOB)), mine).size());
	}

	@Test
	public void summarizesManyMatches()
	{
		MatchTracker tracker = new MatchTracker();
		List<LftEntry> entries = new ArrayList<>();
		for (int i = 0; i <= MatchTracker.MAX_INDIVIDUAL_MESSAGES; i++)
		{
			entries.add(entry("Player" + i, Activity.TOA));
		}

		List<String> messages = tracker.update(entries, EnumSet.of(Activity.TOA));
		assertEquals(1, messages.size());
		assertTrue(messages.get(0).startsWith((MatchTracker.MAX_INDIVIDUAL_MESSAGES + 1) + " friends"));
	}
}
