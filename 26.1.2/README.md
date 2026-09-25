# Amethyst Client: server resource pack cache

A client-side Fabric mod that stores each server's resource pack locally. A pack is only downloaded
and re-applied when the server's SHA-1 hash changes, not on every join.

**Target:** Minecraft 26.1.2 · Fabric Loader ≥ 0.18.4 · Fabric API 0.155.3+26.1.2 · Java 25 (unobfuscated, Mojang names)

## How it works

| When | What happens | Log line (`AmethystClient/PackCache`) |
|---|---|---|
| Game launch | Reads `last-server.txt`, re-hashes that server's cached zip, and adds it to the **first** resource load (no extra reload). | `[launch] APPLIED FROM LAUNCH CACHE` |
| Join, same hash | Replies ACCEPTED → DOWNLOADED → SUCCESSFULLY_LOADED right away. No prompt, no download, no reload. | `[join] HASH MATCH ... nothing to do` |
| Join, hash on disk but not active (e.g. you switched servers) | Applies the cached copy without downloading. | `[join] HASH MATCH with disk cache` |
| Join, new/changed hash | Downloads the pack (verified against the SHA-1), caches it, reloads, then deletes the old version. | `[join] HASH CHANGED` → `[download] DOWNLOADED + APPLIED` |
| Anything unusual | Hands the packet back to vanilla's normal prompt/download/reload flow. | `... using vanilla handling` / `falling back to vanilla` |

Vanilla falls back like this when:
- the server sends no SHA-1
- server packs are set to **Disabled** for that server in the server list
- the URL is invalid
- the download fails or the hash doesn't match
- the server sends a second pack in the same session (only one pack per server is cached)

## Cache layout

```
.minecraft/resourcepack-cache/
├── last-server.txt              server whose pack is applied at launch
└── <server-address>/            e.g. play.example.com, or 1.2.3.4_25566 (default port dropped)
    ├── hash.txt                 SHA-1 of the current pack
    └── pack-<sha1>.zip          the pack
```

The zip is named after its hash instead of a fixed `pack.zip`. The active zip is held open by the
game, and Windows can't overwrite an open file. A new version is written alongside the old one,
and the old one is deleted after the reload.

A missing, corrupted or hand-edited cache is detected by re-hashing and discarded, and the pack is
downloaded normally on the next join. Deleting the whole folder is safe.

## Notes / limitations

- Having the mod installed counts as accepting server packs (no prompt), unless you set that
  server's pack policy to **Disabled**.
- Singleplayer/LAN keeps whichever server pack is loaded. Joining a *different* server swaps in
  that server's cached pack, or removes the pack if that server has none cached.
- If a server stops sending a pack altogether, its cached pack stays applied until the cache
  folder is deleted.
- If a resource reload fails while the cached pack is active, the pack is disabled and its cache
  deleted, so a broken pack can't cause a reload loop.

## Building

```
./gradlew build          # → build/libs/amethystclient-1.0.0.jar
./gradlew runClient      # dev client
```
