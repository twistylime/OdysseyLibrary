package com.lime.odyssey.lib.api.sched;

import com.lime.odyssey.lib.OdysseyLib;
import com.lime.odyssey.lib.api.perf.Perf;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tick scheduler and async executor.
 * <ul>
 *   <li>{@link #later}, {@link #every}: run on the server thread, timed per owner, exceptions isolated.</li>
 *   <li>{@link #async}: runs on the {@code odyssey-io} pool. Never touch the world from there.</li>
 *   <li>{@link #sync}: hand work back to the server thread (drained at the start of each tick).</li>
 * </ul>
 * A repeating task that throws three times in a row is cancelled and reported.
 */
public final class Scheduler {
    private static final int MAX_FAILURES = 3;
    private static final ConcurrentLinkedQueue<Runnable> SYNC = new ConcurrentLinkedQueue<>();
    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();
    private static ExecutorService io;
    private static MinecraftServer server;
    private static long tick;

    private Scheduler() {}

    public static void start(MinecraftServer s) {
        server = s;
        tick = 0;
        AtomicInteger n = new AtomicInteger();
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "odyssey-io-" + n.incrementAndGet());
            t.setDaemon(false);
            return t;
        };
        io = Executors.newFixedThreadPool(2, tf);
    }

    /** Stops accepting async work and waits up to {@code seconds} for queued work to finish. */
    public static void stop(int seconds) {
        drainSync();
        synchronized (TASKS) {
            TASKS.clear();
            PENDING.clear();
        }
        if (io != null) {
            io.shutdown();
            try {
                if (!io.awaitTermination(seconds, TimeUnit.SECONDS)) {
                    OdysseyLib.LOGGER.warn("[Odyssey] IO tasks still running after {}s at shutdown", seconds);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            io = null;
        }
        server = null;
    }

    public static MinecraftServer server() {
        return server;
    }

    public static long currentTick() {
        return tick;
    }

    public static boolean isServerThread() {
        return server != null && server.isSameThread();
    }

    public static TaskHandle later(String owner, int ticks, Runnable r) {
        return add(new Task(owner, r, Math.max(1, ticks), 0));
    }

    public static TaskHandle every(String owner, int periodTicks, Runnable r) {
        return every(owner, periodTicks, periodTicks, r);
    }

    public static TaskHandle every(String owner, int delayTicks, int periodTicks, Runnable r) {
        return add(new Task(owner, r, Math.max(1, delayTicks), Math.max(1, periodTicks)));
    }

    /** Runs on the server thread at the start of the next tick (immediately queued from any thread). */
    public static void sync(Runnable r) {
        SYNC.add(r);
    }

    public static <T> CompletableFuture<T> async(Callable<T> c) {
        ExecutorService ex = io;
        if (ex == null || ex.isShutdown()) {
            CompletableFuture<T> f = new CompletableFuture<>();
            try {
                f.complete(c.call());
            } catch (Exception e) {
                f.completeExceptionally(e);
            }
            return f;
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return c.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, ex);
    }

    public static CompletableFuture<Void> async(Runnable r) {
        return async(() -> {
            r.run();
            return null;
        });
    }

    private static TaskHandle add(Task t) {
        synchronized (TASKS) {
            PENDING.add(t);
        }
        return t;
    }

    /** Called at the start of every server tick by the Library. */
    public static void tick() {
        tick++;
        drainSync();
        List<Task> due = new ArrayList<>();
        synchronized (TASKS) {
            TASKS.addAll(PENDING);
            PENDING.clear();
            Iterator<Task> it = TASKS.iterator();
            while (it.hasNext()) {
                Task t = it.next();
                if (t.cancelled) {
                    it.remove();
                    continue;
                }
                if (t.nextTick <= tick) {
                    due.add(t);
                    if (t.period > 0) t.nextTick = tick + t.period;
                    else it.remove();
                }
            }
        }
        for (Task t : due) run(t);
    }

    private static void run(Task t) {
        if (t.cancelled) return;
        long start = System.nanoTime();
        try {
            t.runnable.run();
            t.failures = 0;
        } catch (Throwable e) {
            t.failures++;
            OdysseyLib.LOGGER.error("[Odyssey] Task from '{}' failed ({}/{})", t.owner, t.failures, MAX_FAILURES, e);
            if (t.period > 0 && t.failures >= MAX_FAILURES) {
                t.cancel();
                Perf.reportFailure(t.owner, "A repeating task of '" + t.owner + "' failed " + MAX_FAILURES + " times in a row and was stopped: " + e);
            }
        } finally {
            Perf.record(t.owner, System.nanoTime() - start);
        }
    }

    private static void drainSync() {
        Runnable r;
        int guard = 0;
        while ((r = SYNC.poll()) != null && guard++ < 10_000) {
            try {
                r.run();
            } catch (Throwable e) {
                OdysseyLib.LOGGER.error("[Odyssey] Sync task failed", e);
            }
        }
    }

    private static final class Task implements TaskHandle {
        final String owner;
        final Runnable runnable;
        final int period;
        long nextTick;
        int failures;
        volatile boolean cancelled;

        Task(String owner, Runnable runnable, int delay, int period) {
            this.owner = owner;
            this.runnable = runnable;
            this.period = period;
            this.nextTick = tick + delay;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
