package com.lime.odyssey.lib.api.points;

import com.lime.odyssey.lib.api.config.Comment;
import com.lime.odyssey.lib.api.config.Range;

/** {@code config/odyssey/points.json}. */
public final class PointsConfig {
    @Comment("Earn points for active playtime")
    public boolean earningEnabled = true;

    @Comment("Active minutes needed for one payout")
    @Range(min = 1, max = 1440)
    public int intervalMinutes = 10;

    @Comment("Points paid per completed interval")
    @Range(min = 0, max = 1000000)
    public long pointsPerInterval = 5;

    @Comment("Points for the first login of a day")
    @Range(min = 0, max = 1000000)
    public long dailyLoginBonus = 10;

    @Comment("Extra daily points per consecutive day logged in")
    @Range(min = 0, max = 1000000)
    public long streakBonusPerDay = 2;

    @Comment("Streak days that still increase the bonus")
    @Range(min = 1, max = 365)
    public int streakCap = 7;

    @Comment("Minutes without any activity before a player counts as AFK")
    @Range(min = 1, max = 120)
    public int afkMinutes = 5;

    @Comment("Tab-list suffix for AFK players ('&' colours)")
    public String afkTabSuffix = " &7[AFK]";

    @Comment("Announce AFK changes to the player")
    public boolean afkMessages = true;
}
