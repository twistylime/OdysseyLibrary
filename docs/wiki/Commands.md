# Commands

All Odyssey features live under `/odyssey`. Arguments in `<>` are required, `[]` optional.

| Command | Who | What it does |
|---|---|---|
| `/odyssey` | everyone | Opens the hub (in chat from the console: lists modules) |
| `/odyssey mailbox` | everyone | Opens your mailbox |
| `/odyssey points` | everyone | Shows your Odyssey Points, lifetime total and login streak |
| `/odyssey afk` | everyone | Toggles AFK |
| `/odyssey points <player>` | admin | Shows a player's balance |
| `/odyssey points <player> add\|remove\|set <amount>` | admin | Changes a balance (audited) |
| `/odyssey mailbox send [player]` | admin | Opens the compose screen (no player = choose in the menu) |
| `/odyssey mailbox view <player>` | admin | Opens someone's mailbox (inspect / delete) |
| `/odyssey mailbox purge <player>` | admin | Deletes every mail of a player (audited) |
| `/odyssey modules` | admin | Lists modules and whether they are enabled |
| `/odyssey module <id> enable\|disable` | admin | Turns a module on or off live |
| `/odyssey config <name>` | admin | Opens the settings editor for that config |
| `/odyssey config <name> <key> <value>` | admin | Changes one setting live |
| `/odyssey snapshots <player>` | admin | Inventory backups of an online player, with restore |
| `/odyssey audit` | admin | Last admin actions |
| `/odyssey perf` | admin | TPS, MSPT and the cost of each module |
| `/odyssey reload-messages` | admin | Re-reads the message files in `config/odyssey/lang/` |

## Aliases
Short commands from `config/odyssey/aliases.json` (default: `/afk`, `/mailbox`, `/points`, `/mev`).
They take effect immediately when the file or setting changes.
