# Configuration

Files live in `config/odyssey/`. Every change applies **immediately**, whether you change it from the
settings menu (`/odyssey` → Settings), with `/odyssey config`, or by editing the file and saving it.
Invalid values are rejected and the previous value stays active; admins are told why.
Each file has a `_comments` block explaining its settings.

## library.json
| Key | Default | Meaning |
|---|---|---|
| `adminOpLevel` | 2 | OP level that counts as Odyssey admin (1-4) |
| `saveIntervalSeconds` | 30 | How often changed data is saved |
| `taskBudgetMs` | 5 | Per-module time per tick before a lag warning is logged |
| `prefix` | `&8[&6Odyssey&8] &r` | Chat prefix |
| `modulePrefix` | `&8[&6Odyssey &8\| &e{name}&8] &r` | Prefix used by modules |
| `shutdownFlushSeconds` | 10 | Time allowed for final saves at server stop |
| `debug` | false | Extra log output |

## points.json
| Key | Default | Meaning |
|---|---|---|
| `earningEnabled` | true | Earn points for playtime and daily logins |
| `intervalMinutes` | 10 | Active minutes per payout |
| `pointsPerInterval` | 5 | Points per payout |
| `dailyLoginBonus` | 10 | Points for the first login of a day |
| `streakBonusPerDay` | 2 | Extra per consecutive day |
| `streakCap` | 7 | Streak days that still raise the bonus |
| `afkMinutes` | 5 | Inactivity before a player counts as AFK |
| `afkTabSuffix` | ` &7[AFK]` | Tab-list suffix |
| `afkMessages` | true | Tell players when they become AFK / active |

## aliases.json
`enabled` turns all aliases on/off; `aliases` maps alias → target, e.g. `"afk": "odyssey afk"`.

## modules.json
Remembers which modules are enabled (changed with `/odyssey module <id> enable|disable`).

## lang/<module>.json
Every message of a module. Edit and run `/odyssey reload-messages`.

## Where data is stored
| Path | Content |
|---|---|
| `<world>/odyssey/library/points.json` | Balances and recent transactions |
| `<world>/odyssey/library/mailbox.json` | Every unclaimed mail |
| `<world>/odyssey/library/snapshots/` | Inventory backup history (30 days) |
| `<world>/odyssey/<module>/` | Data of each module |
| `logs/odyssey/` | Audit log (90 days) and module logs |

Every data file keeps 3 backups (`.bak1`-`.bak3`). A damaged file is moved to `quarantine/` and the newest
backup is loaded; it is never overwritten.
