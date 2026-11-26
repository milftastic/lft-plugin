package com.lookingforteam;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Raids a player can be looking for a team for.
 * <p>
 * The enum constant names are the wire format sent to the server and the
 * config key suffix, so never rename one. Add new constants freely.
 */
@Getter
@RequiredArgsConstructor
public enum Activity
{
	COX("Chambers of Xeric", "CoX"),
	COX_CM("Chambers of Xeric: CM", "CM"),
	TOB("Theatre of Blood", "ToB"),
	TOB_HM("Theatre of Blood: HM", "HMT"),
	TOA("Tombs of Amascut", "ToA"),
	TOA_EXPERT("Tombs of Amascut: Expert", "Expert ToA"),
	TFA("The Fractured Archive", "TFA");

	private final String displayName;
	private final String shortName;

	String configKey()
	{
		return "activity." + name();
	}

	/**
	 * @return the matching activity, or null for names this version doesn't know
	 * (e.g. added by a newer plugin version).
	 */
	static Activity fromWire(String name)
	{
		try
		{
			return Activity.valueOf(name);
		}
		catch (IllegalArgumentException | NullPointerException e)
		{
			return null;
		}
	}
}
