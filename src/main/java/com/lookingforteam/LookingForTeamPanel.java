package com.lookingforteam;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/** Side panel. All methods must be called on the Swing EDT. */
class LookingForTeamPanel extends PluginPanel
{
	private static final Color MATCH_COLOR = new Color(0x4CAF50);

	private final Map<Activity, JCheckBox> checkBoxes = new EnumMap<>(Activity.class);
	private final JLabel statusLabel = new JLabel();
	private final JPanel resultsPanel = new JPanel();

	LookingForTeamPanel(LookingForTeamPlugin plugin)
	{
		setLayout(new BorderLayout());

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

		content.add(header("I'm looking for a team for:"));
		for (Activity activity : Activity.values())
		{
			JCheckBox box = new JCheckBox(activity.getDisplayName());
			box.setAlignmentX(Component.LEFT_ALIGNMENT);
			box.setSelected(plugin.isLookingFor(activity));
			box.addActionListener(e -> plugin.setLookingFor(activity, box.isSelected()));
			checkBoxes.put(activity, box);
			content.add(box);
		}

		JLabel friendsHeader = header("Friends & clanmates looking:");
		friendsHeader.setBorder(new EmptyBorder(12, 0, 4, 0));
		content.add(friendsHeader);

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(statusLabel);

		resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
		resultsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(resultsPanel);

		add(content, BorderLayout.NORTH);
	}

	void setSelected(Activity activity, boolean selected)
	{
		JCheckBox box = checkBoxes.get(activity);
		if (box != null && box.isSelected() != selected)
		{
			box.setSelected(selected);
		}
	}

	void setStatus(String text)
	{
		statusLabel.setText(text);
	}

	void showEntries(List<LftEntry> entries, Set<Activity> mine)
	{
		resultsPanel.removeAll();

		// People who want something you want go first, then alphabetical.
		List<LftEntry> sorted = new ArrayList<>(entries);
		sorted.sort(Comparator
			.comparing((LftEntry e) -> !overlaps(e.getActivities(), mine))
			.thenComparing(e -> e.getName().toLowerCase()));

		for (LftEntry entry : sorted)
		{
			resultsPanel.add(entryRow(entry, mine));
		}

		resultsPanel.revalidate();
		resultsPanel.repaint();
	}

	private JPanel entryRow(LftEntry entry, Set<Activity> mine)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			new EmptyBorder(4, 0, 0, 0),
			new EmptyBorder(6, 8, 6, 8)));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel name = new JLabel(entry.getName());
		name.setFont(FontManager.getRunescapeBoldFont());
		if (overlaps(entry.getActivities(), mine))
		{
			name.setForeground(MATCH_COLOR);
		}
		row.add(name);

		String source = entry.isFriend() && entry.isClanmate() ? "Friend, clan"
			: entry.isFriend() ? "Friend" : "Clan";
		JLabel meta = new JLabel(source + (entry.getWorld() > 0 ? " · W" + entry.getWorld() : ""));
		meta.setFont(FontManager.getRunescapeSmallFont());
		meta.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(meta);

		String activities = entry.getActivities().stream()
			.sorted()
			.map(a -> mine.contains(a) ? "<b>" + a.getShortName() + "</b>" : a.getShortName())
			.collect(Collectors.joining(", "));
		row.add(new JLabel("<html><body style='width:150px'>" + activities + "</body></html>"));

		return row;
	}

	private static boolean overlaps(Set<Activity> theirs, Set<Activity> mine)
	{
		return theirs.stream().anyMatch(mine::contains);
	}

	private static JLabel header(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(Color.WHITE);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}
}
