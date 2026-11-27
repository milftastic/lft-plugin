package com.lookingforteam;

/**
 * Sends items one at a time, and when several are submitted while one is in
 * flight, sends only the newest afterwards.
 * <p>
 * Used for status publishes: if two were in flight at once, the network could
 * deliver them out of order and leave an old "looking for ToB" on the server
 * after a newer "not looking" (for example, right as you log out).
 */
final class LatestOnlySender<T>
{
	interface Sender<T>
	{
		/** Send {@code item}, then call {@code done} exactly once, success or failure. */
		void send(T item, Runnable done);
	}

	private final Sender<T> sender;
	private boolean busy;
	private T pending;

	LatestOnlySender(Sender<T> sender)
	{
		this.sender = sender;
	}

	void submit(T item)
	{
		synchronized (this)
		{
			if (busy)
			{
				pending = item; // replaces anything older that was waiting
				return;
			}
			busy = true;
		}
		sender.send(item, this::done);
	}

	private void done()
	{
		T next;
		synchronized (this)
		{
			next = pending;
			pending = null;
			if (next == null)
			{
				busy = false;
				return;
			}
		}
		sender.send(next, this::done);
	}
}
