package com.lime.odyssey.lib.api.audit;

import com.lime.odyssey.lib.api.data.LogFile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Who changed what, when. Every admin action from any Odyssey mod is recorded here:
 * daily files {@code logs/odyssey/library/audit-YYYY-MM-DD.log} (90 days) plus the last 500 entries in memory.
 */
public final class Audit {
    private static final int MEMORY = 500;
    private static final Deque<Entry> RECENT = new ArrayDeque<>();
    private static LogFile log;

    private Audit() {}

    public record Entry(long time, String actor, String module, String action, String target, String details) {}

    public static void start() {
        log = new LogFile("library", "audit", 90);
        log.prune();
    }

    public static void stop() {
        log = null;
        synchronized (RECENT) {
            RECENT.clear();
        }
    }

    public static String actor(ServerPlayer p) {
        return p == null ? "console" : p.getGameProfile().getName() + "(" + p.getUUID() + ")";
    }

    public static String actor(CommandSourceStack src) {
        return src.getEntity() instanceof ServerPlayer p ? actor(p) : src.getTextName();
    }

    public static void record(String actor, String module, String action, String target, String details) {
        Entry e = new Entry(System.currentTimeMillis(), actor, module, action, target == null ? "" : target, details == null ? "" : details);
        synchronized (RECENT) {
            RECENT.addFirst(e);
            while (RECENT.size() > MEMORY) RECENT.removeLast();
        }
        LogFile l = log;
        if (l != null) l.write(e.actor + " | " + e.module + " | " + e.action + " | " + e.target + " | " + e.details);
    }

    public static List<Entry> recent() {
        synchronized (RECENT) {
            return new ArrayList<>(RECENT);
        }
    }
}
