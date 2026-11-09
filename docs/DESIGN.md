# Odyssey Library — Design

| | |
|---|---|
| Repo | github.com/twistylime/OdysseyLibrary |
| modid | `odysseylib` (provisional) |
| Category | Library |
| Status | **Implemented (1.0.0-alpha.4), awaiting in-game testing** |
| Depends on | — |
| Command root | `/odyssey` |
| Permission root | `odyssey.*` |

## Summary
Core mod every Odyssey mod depends on. Owns the `/odyssey` command root and provides shared services: module registry, permissions, live config, data store, chest/anvil UI framework with renderer-agnostic UI models, scheduler/async executor, event bus, rewards, messages, admin notifications, audit log, serialization, performance guard and the Web Controls bridge.

## Requirements (confirmed by owner)
- Single `/odyssey` root; `/odyssey` alone opens a hub UI listing installed modules
- Every service usable by future mods without changes to the Library
- All config/data changes live — no restart or /reload
- Must never be the cause of lag or crashes; strict async I/O rules
- Permissions via Forge PermissionAPI with vanilla OP fallback (no perms mod on server)
- **Odyssey Points** currency: earned by active playtime, AFK earns nothing; API for a future shop
- **Mailbox** (`/odyssey mailbox`): every reward from every mod lands here, never lost on full inventory
- Points earning: flat rate per active interval + daily-login & streak bonuses; AFK = no movement/rotation/chat/interaction for N min, plus manual AFK toggle with tab-list tag
- Access: vanilla OP levels only (admin level configurable, default 2)
- Mailbox: rewards never expire (manual admin purge); new-mail notice = clickable chat on join/arrival + title/action bar + sound; admins can send items/Points to one or all players (UI + command, audited)
- Configurable aliases `/afk`, `/mailbox`, `/points` → `/odyssey ...` (can be disabled)
- **Soulbound reward items**: bound to a player UUID; if dropped, picked up by others, burned, destroyed or despawned they return to the owner's Mailbox
- **Hub UI** (`/odyssey`): paged chest UI, one icon per installed module (pages grow as mods are added), player's Mailbox + Points balance, admin tools visible only to admins
- Rewards usually combine items + Odyssey Points; reward types are pluggable (Origins adds custom-origin items)
- **Legacy migration framework**: modules register importers for old data; run once, verify, move old files to `legacy-backup/` (D42)
- JSON for all config/data; message prefix `[Odyssey]` gold on dark gray

## Extraction sources (`reference/`)
The owner's MiniEvents and ChatGames mods share a proven infrastructure that becomes the Library baseline
(rewritten to suite conventions, JSON instead of NBT files):
| Library service | From |
|---|---|
| UI framework (Gui, GuiMenu, GuiManager, Click, PagedGui, ConfirmGui) | MiniEvents `gui/`, ChatGames `gui/` |
| Anvil text input (TextInput, TextInputMenu) | MiniEvents + ChatGames |
| Mailbox | MiniEvents `mailbox/MailboxManager` |
| Rewards (pools, categories) | MiniEvents `reward/`, ChatGames `RewardDispenser` |
| Data store (atomic tmp → .bak → move, read fallback to .bak) | MiniEvents `data/Storage`, ChatGames `io/SafeIO` |
| Scheduler | both `util/Scheduler` |
| Permissions | both `Perms` |
| Messages / text formatting | MiniEvents `util/Text`, ChatGames `util/MiniText` |
| **Player state snapshot** (inventory + Curios + backpacks + Origins pause/restore, crash-safe) — confirmed (D39) | MiniEvents `backup/`, `compat/` |

## Library services used
- Full service list: see `../../docs/ARCHITECTURE.md` section 2

## Commands (proposed)
- `/odyssey`
- `/odyssey modules`
- `/odyssey module <id> enable|disable`
- `/odyssey config <module>`
- `/odyssey mailbox`
- `/odyssey mailbox send <player|all>`
- `/odyssey mailbox purge <player>`
- `/odyssey points [player]`
- `/odyssey afk`
- `/odyssey perf`
- `/odyssey audit`

## Permissions (proposed)
_To be filled once commands are final._

## Configuration (proposed)
_To be filled after interview._

## Data & persistence
_To be filled after interview._

## Integration with other Odyssey mods
_To be filled after interview._

## Open questions
- [x] Client part: only what Better AntiCheat needs (handshake/channel); everything else server-only (D10)
