# Permissions

Nodes follow `odyssey.<module>.<action>`. The server uses vanilla OP levels: *admin* means OP level ≥
`adminOpLevel` in `library.json` (default **2**). If a permission mod is ever installed, it can manage
these nodes through Forge's permission API.

| Node | Default | Allows |
|---|---|---|
| `odyssey.library.hub` | everyone | `/odyssey` and the hub |
| `odyssey.library.mailbox` | everyone | Own mailbox |
| `odyssey.library.points` | everyone | Own balance |
| `odyssey.library.afk` | everyone | `/odyssey afk` |
| `odyssey.library.admin` | admin | Modules, settings, audit, performance, admin tools in the hub |
| `odyssey.library.mailbox.admin` | admin | Send, view, delete and purge mail |
| `odyssey.library.points.admin` | admin | Change balances |
| `odyssey.library.snapshot.admin` | admin | View and restore inventory backups |

Every button in every Odyssey menu re-checks its permission when clicked.
