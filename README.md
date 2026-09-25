<div align="center">

# 💎 Amethyst Client

**Stop re-downloading the same server resource pack on every join.**

Amethyst Client remembers each server's resource pack and only downloads it again when the server
actually changes it. Joining becomes instant: no prompt, no download bar, no reload screen.

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1%20%7C%201.21.11%20%7C%2026.1.2%20%7C%2026.2-62b47a?style=flat-square)](#-download)
[![Fabric](https://img.shields.io/badge/Mod%20loader-Fabric-dbb69b?style=flat-square)](https://fabricmc.net/)
[![Client side](https://img.shields.io/badge/Side-Client%20only-9b6bd6?style=flat-square)](#-for-server-owners)
[![Downloads](https://img.shields.io/github/downloads/3aiya/amethyst-client/total?style=flat-square&color=9b6bd6)](https://github.com/3aiya/amethyst-client/releases)

</div>

---

## ✨ Why?

Many servers push their resource pack to every player on **every** join. Without this mod, each
join means:

1. a "download this resource pack?" prompt,
2. downloading the whole pack again,
3. a full resource reload screen,

even when the pack hasn't changed since yesterday.

**With Amethyst Client:**

| | Vanilla | Amethyst Client |
|---|---|---|
| Pack unchanged since last time | Prompt, full download, reload | **Nothing. You're in straight away.** |
| Server updated its pack | Prompt, full download, reload | Download once, apply, remember it |
| Pack loaded when? | After you join | **Already loaded when the game starts** |

## 🚀 Features

- **Instant joins.** The server's pack is loaded while the game starts up. When you join and the
  server's pack hasn't changed, nothing happens at all.
- **Only downloads real updates.** Every server sends a SHA-1 fingerprint of its pack. The mod
  compares it with your saved copy and only downloads when they differ.
- **Separate cache per server.** Play on several servers without one server's pack showing up on
  another.
- **Safe by design.**
  - Every download is checked against the server's fingerprint before it's saved.
  - A damaged or missing cache is detected and simply downloaded again.
  - If anything unusual happens, the mod steps aside and Minecraft's normal behaviour takes over.
- **Zero setup.** No config screen, no settings. Install it and play.

## 📥 Download

Pick the file for **your** Minecraft version. All versions are also on the
[**Releases**](https://github.com/3aiya/amethyst-client/releases) page.

| Minecraft | Java | Fabric Loader | Download |
|---|---|---|---|
| **26.2** | 25+ | 0.18.4+ | [⬇ amethystclient-1.0.0+mc26.2.jar](https://github.com/3aiya/amethyst-client/releases/download/v1.0.0-mc26.2/amethystclient-1.0.0+mc26.2.jar) · [release notes](https://github.com/3aiya/amethyst-client/releases/tag/v1.0.0-mc26.2) |
| **26.1.2** | 25+ | 0.18.4+ | [⬇ amethystclient-1.0.0+mc26.1.2.jar](https://github.com/3aiya/amethyst-client/releases/download/v1.0.0-mc26.1.2/amethystclient-1.0.0+mc26.1.2.jar) · [release notes](https://github.com/3aiya/amethyst-client/releases/tag/v1.0.0-mc26.1.2) |
| **1.21.11** | 21+ | 0.16.0+ | [⬇ amethystclient-1.0.0+mc1.21.11.jar](https://github.com/3aiya/amethyst-client/releases/download/v1.0.0-mc1.21.11/amethystclient-1.0.0+mc1.21.11.jar) · [release notes](https://github.com/3aiya/amethyst-client/releases/tag/v1.0.0-mc1.21.11) |
| **1.21.1** | 21+ | 0.16.0+ | [⬇ amethystclient-1.0.0+mc1.21.1.jar](https://github.com/3aiya/amethyst-client/releases/download/v1.0.0-mc1.21.1/amethystclient-1.0.0+mc1.21.1.jar) · [release notes](https://github.com/3aiya/amethyst-client/releases/tag/v1.0.0-mc1.21.1) |

Release files are named `amethystclient-<version>+mc<minecraft>.jar`.

## 🛠️ Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for your Minecraft version.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for the same version.
3. Put **Fabric API** and the **Amethyst Client** jar into your `.minecraft/mods/` folder.
4. Start the game and join your server.

> **Updating:** delete the old `amethystclient-…jar` from `mods/` before adding the new one.

The first join downloads the pack as usual. Every join after that is instant.

## 🖥️ For server owners

- **Nothing to install on the server.** This is a client-only mod.
- **Players without the mod** get normal vanilla behaviour. Nothing changes for them.
- It works with **any setup that sends a SHA-1 hash** with the pack. That includes vanilla
  `server.properties` (`resource-pack-sha1=`) and resource pack plugins that include a hash.
- **Required packs** (`require-resource-pack=true`) work. The mod reports the pack as loaded, just
  like vanilla does.
- **If your server sends no hash**, the mod can't tell whether the pack changed, so it falls back to
  normal vanilla behaviour. Send a hash to get instant joins.

## ⚙️ How it works

```
Game launch ─► load the saved pack of the last server you played on
                (included in the normal startup load, no extra reload)

Join server ─► server sends its pack + SHA-1 fingerprint
               ├─ same as the saved pack?  ─► do nothing ✔
               ├─ saved on disk already?   ─► apply it, no download
               └─ new or changed?          ─► download, verify, save, apply
```

Packs are stored in `.minecraft/resourcepack-cache/`, with one folder per server.
**It's safe to delete this folder at any time.** The mod rebuilds it on your next join.

<details>
<summary><b>Logs (for troubleshooting)</b></summary>

The mod writes to the normal game log (`logs/latest.log`) under the name `AmethystClient/PackCache`:

| Log message | Meaning |
|---|---|
| `APPLIED FROM LAUNCH CACHE` | The saved pack was loaded at game startup |
| `HASH MATCH … nothing to do` | The server's pack hasn't changed; the join was instant |
| `HASH CHANGED` → `DOWNLOADED + APPLIED` | The server updated its pack; the new version was downloaded |
| `using vanilla handling` / `falling back to vanilla` | Something was unusual, so normal Minecraft behaviour took over |

</details>

## ❓ FAQ

**Why is a server's pack active in the menu and in singleplayer?**
The mod loads the pack of the last server you played on when the game starts. That's what makes
the next join instant, and it stays active in menus and singleplayer. Joining a different server
switches to that server's pack, or removes it if that server doesn't use one.

**Will it prompt me to accept the pack?**
No, installing the mod counts as accepting server packs. If you set a server's
**Server Resource Packs** option to **Disabled** in the server list, the mod respects that and
leaves it to vanilla.

**A server stopped using a resource pack, but I still see it.**
Delete that server's folder inside `.minecraft/resourcepack-cache/`.

**Does it work with Sodium, Iris and other mods?**
Amethyst Client only touches how the server resource pack is downloaded and loaded, so it should
work alongside most mods. If you find a conflict, please
[open an issue](https://github.com/3aiya/amethyst-client/issues).

## 🐞 Bugs & suggestions

Found a problem or have an idea? [Open an issue](https://github.com/3aiya/amethyst-client/issues).
Please include your Minecraft version, your mod list and your `logs/latest.log`.

---

<details>
<summary><b>🔧 Building from source</b></summary>

Each Minecraft version is its own Gradle project:

| Folder | Minecraft | Java | Fabric API | Mappings |
|---|---|---|---|---|
| [`1.21.1/`](1.21.1/) | 1.21.1 | 21 | 0.116.17+1.21.1 | Yarn |
| [`1.21.11/`](1.21.11/) | 1.21.11 | 21 | 0.141.6+1.21.11 | Yarn |
| [`26.1.2/`](26.1.2/) | 26.1.2 | 25 | 0.155.3+26.1.2 | Mojang (unobfuscated) |
| [`26.2/`](26.2/) | 26.2 | 25 | 0.161.0+26.2 | Mojang (unobfuscated) |

```sh
cd 26.2
./gradlew build        # → build/libs/amethystclient-<version>.jar
./gradlew runClient    # start a development client
```

Building needs JDK 25 (it also compiles the Java 21 versions).

</details>

<details>
<summary><b>📦 Publishing a release (maintainers)</b></summary>

```powershell
.\build-release.ps1 -Version 1.0.2
```

1. Sets `mod_version` in every version folder, builds everything, then commits and pushes.
2. Publishes one GitHub release per Minecraft version, e.g. **Amethyst Client 26.1.2 v1.0.2**
   (tag `v1.0.2-mc26.1.2`).
3. Once the new release is up, deletes the older release and tag for that Minecraft version.
4. Updates the download links in this README to the new version.

| Option | Effect |
|---|---|
| *(no `-Version`)* | Rebuild the current version and replace the jars on its releases |
| `-McVersions 26.2,26.1.2` | Only release these Minecraft versions |
| `-SkipUpload` | Build into `dist\` only; don't commit, push or publish |

Requires the [GitHub CLI](https://cli.github.com/) (`gh auth login`).

</details>
