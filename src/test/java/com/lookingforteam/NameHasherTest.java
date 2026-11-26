package com.lookingforteam;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import org.junit.Test;

public class NameHasherTest
{
	@Test
	public void sameIdForEquivalentNames()
	{
		String id = NameHasher.hash("Iron Man");
		assertEquals(id, NameHasher.hash("iron man"));
		assertEquals(id, NameHasher.hash("Iron_Man"));
		assertEquals(id, NameHasher.hash("Iron Man"));
	}

	@Test
	public void differentIdForDifferentNames()
	{
		assertNotEquals(NameHasher.hash("Zezima"), NameHasher.hash("Zezima2"));
	}

	@Test
	public void matchesServerHash()
	{
		// Same value is pinned in lft-server's StatusStoreTest; change both together.
		assertEquals("ec3a40aeee6872f925767fd5379a49841348e7a91868b06d8a7bd8e37a17a47e", NameHasher.hash("Iron Man"));
	}

	@Test
	public void visibilityKeyMatchesServer()
	{
		// Same value is pinned in lft-server's StatusStoreTest; change both together.
		assertEquals("baae242592d0ad671242c62e309152e8243395e4b5d3fe5a2a140f40eb2a172e",
			NameHasher.visibilityKey("Iron Man", "Zezima"));
	}

	@Test
	public void visibilityKeyIsDirectional()
	{
		assertNotEquals(NameHasher.visibilityKey("Alice", "Bob"), NameHasher.visibilityKey("Bob", "Alice"));
		assertEquals(NameHasher.visibilityKey("Alice", "Bob"), NameHasher.visibilityKey("alice", "BOB"));
	}

	@Test
	public void idIsHexSha256()
	{
		assertEquals(64, NameHasher.hash("Zezima").length());
	}
}
