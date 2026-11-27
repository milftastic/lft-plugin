# Looking For Team

Tick the raids (CoX, ToB, ToA, TFA) you want a team for, and see which of your online
friends and clanmates are looking for the same ones.

## How it works

The plugin talks to a small LFT server. Online features are **off by default**:
turn on *Enable online features* in the plugin settings to use them.

Every refresh (default every 15 seconds, while you have a raid ticked), the plugin:

1. Publishes **your** status: the raids you ticked, plus one opaque key per
   online friend and clanmate saying "this person may see my status"
   (`SHA-256(you → them)`). Nothing is sent about friends while you're not looking.
2. Looks up, for each online friend and clanmate, the key `SHA-256(them → you)`.
   The server only has that key if *they* allowed you, and the plugin maps
   matches back to names locally.

So you only see someone who has you on their list **and** is on yours (or a
shared clan). If A has B as a friend but B doesn't have A, neither sees the other.

By default your selections are cleared when you log out, so being listed as
looking always means you chose it this session. Turn on *Remember selections
between logins* to keep them. Entering any raid also clears all your selections.

When a friend or clanmate starts looking for something you're looking for, you
get a green `[LFT]` chat message (turn off with *Chat notifications*).

No player name is ever sent in plain text, and the server only stores data for
players who opted in themselves. When you log out or disable the plugin, your
status is cleared.

Note: display names are short and guessable, so hashing keeps names out of
casual view and server logs but isn't strong anonymity.

## Privacy

- **Opt-in.** Nothing is sent until you turn on *Enable online features*.
- **What's sent, while online features are on:** your raid choices, a hash of
  your own name, and opaque hashed keys for your *online* friends and
  clanmates (only while you're looking). Plain names are never sent.
- **What the server keeps:** only that, in memory, replaced on every refresh.
  Logging out, unticking everything, turning off the plugin or entering a raid
  deletes it right away. If your client can't say goodbye (crash, lost
  connection), it's hidden 5 minutes after your last refresh and deleted from
  memory within about 6. There is no database, and the server's code doesn't
  log requests (the hosting provider may keep standard network logs).
- **Who sees you:** only people who have you on their friends list *and* are on
  yours, or who share your clan.
- **Your IP address** is seen by the server (as with any web request) and used
  only for rate limiting, in memory.
- The server is open source: [lft-server](https://github.com/CHANGE-ME/lft-server).
  You can also run your own and set *Server URL* to it.

## Server API (v1)

```
PUT  /v1/status         {"id": "<owner hash>", "keys": ["<visibility key>", ...], "activities": ["COX", "TOB"]}
     -> 204. Replaces the owner's previous keys. Empty activities = not looking.
     Statuses not refreshed for 5 minutes expire.

POST /v1/status/lookup  {"keys": ["<visibility key>", ...]}
     -> 200 {"statuses": [{"key": "<visibility key>", "activities": ["COX"]}]}
```

Activity names are the constants in `Activity.java`.

## Development

```
./gradlew run      # launch RuneLite with this plugin loaded
./gradlew test
```
