package com.lookingforteam;

import java.util.Set;
import lombok.Value;

/** A friend or clanmate who is currently looking for a team. */
@Value
class LftEntry
{
	String name;
	boolean friend;
	boolean clanmate;
	int world;
	Set<Activity> activities;
}
