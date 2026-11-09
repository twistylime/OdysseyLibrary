# Odyssey Points

The server currency. Points are earned by playing and from event rewards; a shop mod will let players spend them.

- **Playtime**: every `intervalMinutes` of *active* play pays `pointsPerInterval` (default 5 points / 10 min).
  Unfinished minutes carry over to the next session.
- **Daily login**: first login of the day pays `dailyLoginBonus` + `streakBonusPerDay` × (streak − 1), up to `streakCap` days.
- **AFK earns nothing.** You are AFK after `afkMinutes` without moving, turning, chatting, using commands
  or interacting, or after `/odyssey afk`. Any activity ends AFK. AFK players show ` [AFK]` in the tab list.
- **Events**: rewards from Odyssey mods can include points; they are added when you claim the mail.

![Action bar after a payout: "+5 Odyssey Points (10 min played)"](images/library-points-payout.png)
