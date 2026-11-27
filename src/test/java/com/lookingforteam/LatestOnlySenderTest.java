package com.lookingforteam;

import static org.junit.Assert.assertEquals;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class LatestOnlySenderTest
{
	/** Records sends and lets the test decide when each one finishes. */
	private static final class ManualSender implements LatestOnlySender.Sender<String>
	{
		final List<String> sent = new ArrayList<>();
		final List<Runnable> waiting = new ArrayList<>();

		@Override
		public void send(String item, Runnable done)
		{
			sent.add(item);
			waiting.add(done);
		}

		void finishOldest()
		{
			waiting.remove(0).run();
		}
	}

	@Test
	public void sendsOneAtATimeAndOnlyTheNewestPending()
	{
		ManualSender network = new ManualSender();
		LatestOnlySender<String> sender = new LatestOnlySender<>(network);

		sender.submit("looking: TOB");
		sender.submit("looking: TOB, COX"); // superseded before it was sent
		sender.submit("not looking");
		assertEquals(List.of("looking: TOB"), network.sent);

		network.finishOldest();
		assertEquals(List.of("looking: TOB", "not looking"), network.sent);

		network.finishOldest();
		sender.submit("looking: TOA"); // idle again, so it goes straight out
		assertEquals(List.of("looking: TOB", "not looking", "looking: TOA"), network.sent);
	}

	@Test
	public void survivesSendersThatFinishImmediately()
	{
		List<String> sent = new ArrayList<>();
		LatestOnlySender<String> sender = new LatestOnlySender<>((item, done) ->
		{
			sent.add(item);
			done.run();
		});

		sender.submit("a");
		sender.submit("b");
		assertEquals(List.of("a", "b"), sent);
	}
}
