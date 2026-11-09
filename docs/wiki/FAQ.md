# FAQ

**Do players need to install the Library?** No. It is server-side only.

**Do I need to restart after changing a setting?** No. Every setting applies immediately, including manual file edits.

**A config file has a mistake. What happens?** The change is rejected, the previous value stays active and online admins get a message.
If the file is broken at startup, defaults are used for that run and the file is left untouched so you can fix it.

**A data file got corrupted.** It is moved to `quarantine/` next to it and the newest backup is loaded. Admins are notified.

**A module misbehaves. Can I turn it off without a restart?** Yes: `/odyssey module <id> disable` (or Hub → Modules).

**Where can I see what admins changed?** `/odyssey audit`, the hub's Audit log, or `logs/odyssey/library/audit-*.log`.

**The server lags. Is it an Odyssey mod?** `/odyssey perf` shows the time each module uses per tick. Modules over budget are also logged.
