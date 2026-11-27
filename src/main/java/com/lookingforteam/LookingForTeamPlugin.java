package com.lookingforteam;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Friend;
import net.runelite.api.GameState;
import net.runelite.api.NameableContainer;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@Slf4j
@PluginDescriptor(
	name = "Looking For Team",
	description = "Mark the raids you want a team for and see which friends and clanmates want the same",
	tags = {"lft", "raids", "cox", "tob", "toa", "tfa", "team", "group", "friends", "clan"}
)
public class LookingForTeamPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private LookingForTeamConfig config;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private LftApiClient api;

	@Inject
	private ChatMessageManager chatMessageManager;

	private static final Color CHAT_COLOR = new Color(0x00B000);

	private final MatchTracker matchTracker = new MatchTracker();

	// Client thread only.
	private boolean wasInRaid;
	private int lastRegion = -1;
	private int lastToaParty = -1;
	private int lastTfaParty = -1;

	private LookingForTeamPanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> refreshFuture;

	private static final int MAX_EXTRA_REFRESHES_PER_MINUTE = 3;
	// Guarded by this.
	private final Deque<Long> recentUserRefreshes = new ArrayDeque<>();
	private ScheduledFuture<?> pendingUserRefresh;

	/** Id we last published a status under, so we can clear it on logout. */
	private volatile String publishedId;
	/** Whether that last publish was "not looking". */
	private volatile boolean publishedEmpty;

	/** Bumped whenever results become stale (logout, disable) so late HTTP responses are dropped. */
	private final AtomicInteger generation = new AtomicInteger();

	@Override
	protected void startUp()
	{
		// Covers the client being closed or crashing while logged in, where we never saw the login screen.
		if (!config.persistSelections() && client.getGameState() != GameState.LOGGED_IN)
		{
			clearSelections();
		}

		panel = new LookingForTeamPanel(this);
		navButton = NavigationButton.builder()
			.tooltip("Looking For Team")
			.icon(createIcon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		scheduleRefresh();
	}

	@Override
	protected void shutDown()
	{
		if (refreshFuture != null)
		{
			refreshFuture.cancel(false);
			refreshFuture = null;
		}
		synchronized (this)
		{
			if (pendingUserRefresh != null)
			{
				pendingUserRefresh.cancel(false);
				pendingUserRefresh = null;
			}
			recentUserRefreshes.clear();
		}
		generation.incrementAndGet();
		clearPublishedStatus();
		resetNotifications();
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
	}

	@Provides
	LookingForTeamConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LookingForTeamConfig.class);
	}

	// ---- Activity selection (stored in config, edited from the panel) ----

	boolean isLookingFor(Activity activity)
	{
		return Boolean.parseBoolean(configManager.getConfiguration(LookingForTeamConfig.GROUP, activity.configKey()));
	}

	void setLookingFor(Activity activity, boolean lookingFor)
	{
		if (lookingFor)
		{
			configManager.setConfiguration(LookingForTeamConfig.GROUP, activity.configKey(), true);
		}
		else
		{
			configManager.unsetConfiguration(LookingForTeamConfig.GROUP, activity.configKey());
		}
		// The resulting ConfigChanged event triggers a refresh.
	}

	private void clearSelections()
	{
		for (Activity activity : Activity.values())
		{
			if (isLookingFor(activity))
			{
				configManager.unsetConfiguration(LookingForTeamConfig.GROUP, activity.configKey());
			}
		}
	}

	private Set<Activity> selectedActivities()
	{
		Set<Activity> selected = EnumSet.noneOf(Activity.class);
		for (Activity activity : Activity.values())
		{
			if (isLookingFor(activity))
			{
				selected.add(activity);
			}
		}
		return selected;
	}

	// ---- Events ----

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!LookingForTeamConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		if ("refreshInterval".equals(event.getKey()))
		{
			scheduleRefresh();
			return;
		}

		if ("enableOnline".equals(event.getKey()) && !config.enableOnline())
		{
			generation.incrementAndGet();
			clearPublishedStatus();
		}

		if (event.getKey().startsWith("activity."))
		{
			// Keep the panel in sync if the value changed elsewhere (e.g. profile switch).
			Activity activity = Activity.fromWire(event.getKey().substring("activity.".length()));
			if (activity != null)
			{
				boolean selected = isLookingFor(activity);
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null)
					{
						panel.setSelected(activity, selected);
					}
				});
			}
			requestUserRefresh();
			return;
		}

		requestRefresh();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			requestRefresh();
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			generation.incrementAndGet();
			clearPublishedStatus();
			resetNotifications();
			if (!config.persistSelections())
			{
				clearSelections();
			}
			updatePanel("Log in to see who's looking.", Collections.emptyList(), Collections.emptySet());
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		boolean inRaid = RaidDetector.inRaid(client);

		if (log.isDebugEnabled())
		{
			// Raw values, to confirm the raid detection in-game.
			int region = RaidDetector.currentRegion(client);
			int toaParty = client.getVarbitValue(VarbitID.TOA_CLIENT_PARTYSTATUS);
			int tfaParty = client.getVarbitValue(VarbitID.TFA_CLIENT_PARTYSTATUS);
			if (region != lastRegion || toaParty != lastToaParty || tfaParty != lastTfaParty)
			{
				log.debug("LFT region={} instance={} toaParty={} tfaParty={} -> cox={} tob={} toa={} tfa={}",
					region, client.getTopLevelWorldView().isInstance(), toaParty, tfaParty,
					RaidDetector.inChambers(client), RaidDetector.inTheatre(client),
					RaidDetector.inTombs(client), RaidDetector.inArchive(client));
				lastRegion = region;
				lastToaParty = toaParty;
				lastTfaParty = tfaParty;
			}
		}

		// Act only on the moment you go through the entrance, so you can still re-tick inside if you want.
		if (inRaid && !wasInRaid && !selectedActivities().isEmpty())
		{
			log.debug("Entered a raid, clearing LFT selections");
			clearSelections();
			sendChatMessage("You entered a raid, so you're no longer listed as looking for a team.");
		}
		wasInRaid = inRaid;
	}

	@Subscribe
	public void onClanChannelChanged(ClanChannelChanged event)
	{
		requestRefresh();
	}

	// ---- Refresh loop ----

	private void scheduleRefresh()
	{
		if (refreshFuture != null)
		{
			refreshFuture.cancel(false);
		}
		int interval = Math.max(10, config.refreshInterval());
		refreshFuture = executor.scheduleWithFixedDelay(this::requestRefresh, 0, interval, TimeUnit.SECONDS);
	}

	/**
	 * Refresh soon after you tick or untick a raid. Clicks within half a second
	 * are combined into one refresh, and at most {@link #MAX_EXTRA_REFRESHES_PER_MINUTE}
	 * happen per minute on top of the regular schedule; beyond that the change goes
	 * out with the next scheduled refresh.
	 */
	private synchronized void requestUserRefresh()
	{
		if (pendingUserRefresh != null && !pendingUserRefresh.isDone())
		{
			return; // one is already coming and will pick up this change too
		}

		long now = System.currentTimeMillis();
		while (!recentUserRefreshes.isEmpty() && now - recentUserRefreshes.peekFirst() > 60_000)
		{
			recentUserRefreshes.pollFirst();
		}
		if (recentUserRefreshes.size() >= MAX_EXTRA_REFRESHES_PER_MINUTE)
		{
			return;
		}

		recentUserRefreshes.addLast(now);
		pendingUserRefresh = executor.schedule(this::requestRefresh, 500, TimeUnit.MILLISECONDS);
	}

	private void requestRefresh()
	{
		// Friend/clan lists must be read on the client thread.
		clientThread.invokeLater(this::refresh);
	}

	private void refresh()
	{
		Set<Activity> mine = selectedActivities();

		if (!config.enableOnline())
		{
			updatePanel("Online features are off. Turn on \"Enable online features\" in this plugin's settings.",
				Collections.emptyList(), mine);
			return;
		}

		Player local = client.getLocalPlayer();
		if (client.getGameState() != GameState.LOGGED_IN || local == null || local.getName() == null)
		{
			updatePanel("Log in to see who's looking.", Collections.emptyList(), mine);
			return;
		}

		String selfName = local.getName();
		String selfId = NameHasher.hash(selfName);
		List<Candidate> candidates = collectCandidates(selfName);
		int gen = generation.get();

		// Publish: "each of these people may see my status (friends + clanmates).
		List<String> publishKeys = new ArrayList<>();
		// Lookup: "has this friend allowed me to see theirs?", keyed back to the friend.
		Map<String, Candidate> lookupKeys = new HashMap<>();
		for (Candidate candidate : candidates)
		{
			publishKeys.add(NameHasher.visibilityKey(selfName, candidate.name));
			lookupKeys.put(NameHasher.visibilityKey(candidate.name, selfName), candidate);
		}

		// While not looking, only tell the server once; repeating "not looking" every minute is wasted traffic.
		boolean alreadyCleared = mine.isEmpty() && publishedEmpty && selfId.equals(publishedId);
		if (!alreadyCleared)
		{
			publishedId = selfId;
			publishedEmpty = mine.isEmpty();
			api.publish(selfId, publishKeys, mine, error -> updatePanel(error, null, mine));
		}

		if (mine.isEmpty())
		{
			// You only see people who want what you want, so with nothing ticked there's nothing to look up.
			matchTracker.update(Collections.emptyList(), mine);
			updatePanel("Tick a raid above to see friends and clanmates looking for the same.",
				Collections.emptyList(), mine);
			return;
		}

		api.lookup(lookupKeys.keySet(),
			statuses ->
			{
				if (gen != generation.get())
				{
					return;
				}
				List<LftEntry> entries = new ArrayList<>();
				for (LftApiClient.StatusDto status : statuses)
				{
					Candidate candidate = status.key == null ? null : lookupKeys.get(status.key);
					if (candidate == null || status.activities == null)
					{
						continue;
					}
					Set<Activity> activities = EnumSet.noneOf(Activity.class);
					for (String name : status.activities)
					{
						Activity activity = Activity.fromWire(name);
						if (activity != null)
						{
							activities.add(activity);
						}
					}
					// Only the raids you're both looking for; people with none in common aren't shown.
					activities.retainAll(mine);
					if (!activities.isEmpty())
					{
						entries.add(new LftEntry(candidate.name, candidate.friend, candidate.clanmate, candidate.world, activities));
					}
				}
				updatePanel(entries.isEmpty() ? "No friends or clanmates online are looking for the same raids right now." : "",
					entries, mine);

				// Always track, even with chat off, so turning it on later doesn't replay old matches.
				List<String> messages = matchTracker.update(entries, mine);
				if (config.chatNotifications() && !messages.isEmpty())
				{
					clientThread.invokeLater(() -> messages.forEach(this::sendChatMessage));
				}
			},
			error ->
			{
				if (gen == generation.get())
				{
					updatePanel(error, null, mine);
				}
			});
	}

	/** Online friends and clanmates, excluding yourself, merged by name. Client thread only. */
	private List<Candidate> collectCandidates(String selfName)
	{
		Map<String, Candidate> candidates = new HashMap<>();

		if (config.includeFriends())
		{
			NameableContainer<Friend> friends = client.getFriendContainer();
			if (friends != null)
			{
				for (Friend friend : friends.getMembers())
				{
					if (friend != null && friend.getName() != null && friend.getWorld() > 0)
					{
						candidates.computeIfAbsent(NameHasher.normalize(friend.getName()),
							id -> new Candidate(friend.getName(), friend.getWorld())).friend = true;
					}
				}
			}
		}

		if (config.includeClan())
		{
			ClanChannel clan = client.getClanChannel();
			if (clan != null)
			{
				for (ClanChannelMember member : clan.getMembers())
				{
					if (member != null && member.getName() != null)
					{
						candidates.computeIfAbsent(NameHasher.normalize(member.getName()),
							id -> new Candidate(member.getName(), member.getWorld())).clanmate = true;
					}
				}
			}
		}

		candidates.remove(NameHasher.normalize(selfName));
		return new ArrayList<>(candidates.values());
	}

	private void sendChatMessage(String text)
	{
		String message = new ChatMessageBuilder()
			.append(CHAT_COLOR, "[LFT] " + text)
			.build();
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(message)
			.build());
	}

	private void resetNotifications()
	{
		matchTracker.reset();
	}

	private void clearPublishedStatus()
	{
		String id = publishedId;
		publishedId = null;
		if (id != null)
		{
			api.publish(id, Collections.emptyList(), EnumSet.noneOf(Activity.class),
				error -> log.debug("Failed to clear LFT status: {}", error));
		}
	}

	/** @param entries null to leave the current list as is */
	private void updatePanel(String status, List<LftEntry> entries, Set<Activity> mine)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (panel == null)
			{
				return;
			}
			panel.setStatus(status);
			if (entries != null)
			{
				panel.showEntries(entries);
			}
		});
	}

	private static BufferedImage createIcon()
	{
		// Two little people: drawn in code so there's no image asset to ship.
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(0xFF981F));
		g.fillOval(2, 2, 5, 5);
		g.fillOval(9, 2, 5, 5);
		g.fillArc(0, 8, 9, 10, 0, 180);
		g.fillArc(7, 8, 9, 10, 0, 180);
		g.dispose();
		return image;
	}

	private static final class Candidate
	{
		final String name;
		final int world;
		boolean friend;
		boolean clanmate;

		Candidate(String name, int world)
		{
			this.name = name;
			this.world = world;
		}
	}
}
