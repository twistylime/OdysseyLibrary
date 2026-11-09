package com.lime.odyssey.lib.api.perf;

import com.lime.odyssey.lib.OdysseyLib;
import com.lime.odyssey.lib.api.notify.Notify;
import com.lime.odyssey.lib.api.notify.Severity;
import com.lime.odyssey.lib.internal.Lib;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Performance guard. Records time spent per owner (module) per tick, warns when a module exceeds its
 * budget (rate-limited), and keeps rolling statistics for {@code /odyssey perf} and Web Controls.
 * Server thread only.
 */
public final class Perf {
    private static final int WINDOW = 200;
    private static final long WARN_COOLDOWN_MS = 60_000;
    private static final Map<String, Long> THIS_TICK = new HashMap<>();
    private static final Map<String, Stat> STATS = new HashMap<>();
    private static final Map<String, Long> LAST_WARN = new HashMap<>();
    private static MinecraftServer server;

    private Perf() {}

    public static final class Stat {
        long totalNanos;
        long maxNanos;
        long samples;
        long overBudget;

        public double avgMs() {
            return samples == 0 ? 0 : totalNanos / 1e6 / samples;
        }

        public double maxMs() {
            return maxNanos / 1e6;
        }

        public long overBudget() {
            return overBudget;
        }
    }

    public record Snapshot(double tps, double msptAvg, double msptMax, Map<String, Stat> modules) {}

    public static void start(MinecraftServer s) {
        server = s;
    }

    public static void stop() {
        server = null;
        THIS_TICK.clear();
        STATS.clear();
        LAST_WARN.clear();
    }

    public static void record(String owner, long nanos) {
        THIS_TICK.merge(owner, nanos, Long::sum);
    }

    /** Wraps a listener body so its cost is attributed to {@code owner}. */
    public static void timed(String owner, Runnable r) {
        long start = System.nanoTime();
        try {
            r.run();
        } finally {
            record(owner, System.nanoTime() - start);
        }
    }

    public static void reportFailure(String owner, String message) {
        Notify.admins(owner, message, Severity.ALERT);
    }

    /** Called at the end of every server tick. */
    public static void endTick() {
        if (THIS_TICK.isEmpty()) return;
        long budget = Lib.config().taskBudgetMs * 1_000_000L;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> e : THIS_TICK.entrySet()) {
            Stat s = STATS.computeIfAbsent(e.getKey(), k -> new Stat());
            long n = e.getValue();
            if (s.samples >= WINDOW) {
                s.totalNanos = s.totalNanos / 2;
                s.samples = s.samples / 2;
                s.maxNanos = Math.max(n, s.maxNanos / 2);
            }
            s.totalNanos += n;
            s.samples++;
            s.maxNanos = Math.max(s.maxNanos, n);
            if (n > budget) {
                s.overBudget++;
                Long last = LAST_WARN.get(e.getKey());
                if (last == null || now - last > WARN_COOLDOWN_MS) {
                    LAST_WARN.put(e.getKey(), now);
                    OdysseyLib.LOGGER.warn("[Odyssey] '{}' used {} ms in one tick (budget {} ms)", e.getKey(),
                            String.format("%.2f", n / 1e6), Lib.config().taskBudgetMs);
                }
            }
        }
        THIS_TICK.clear();
    }

    public static Snapshot snapshot() {
        double avg = 0, max = 0;
        if (server != null) {
            long[] times = server.tickTimes;
            long sum = 0, mx = 0;
            for (long t : times) {
                sum += t;
                mx = Math.max(mx, t);
            }
            avg = times.length == 0 ? 0 : sum / (double) times.length / 1e6;
            max = mx / 1e6;
        }
        double tps = avg <= 0 ? 20 : Math.min(20, 1000.0 / avg);
        return new Snapshot(tps, avg, max, Map.copyOf(STATS));
    }

    /** Modules sorted by average cost, most expensive first. */
    public static List<Map.Entry<String, Stat>> ranking() {
        List<Map.Entry<String, Stat>> l = new ArrayList<>(STATS.entrySet());
        l.sort(Comparator.comparingDouble((Map.Entry<String, Stat> e) -> e.getValue().avgMs()).reversed());
        return l;
    }
}
