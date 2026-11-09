package com.lime.odyssey.lib.api.config;

import com.lime.odyssey.lib.OdysseyLib;
import com.lime.odyssey.lib.api.data.SafeFiles;
import com.lime.odyssey.lib.api.sched.Scheduler;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Watches {@code config/odyssey/} for manual edits and applies them live (debounced 1 s).
 * Runs on its own daemon thread; files are read off-thread and applied on the server thread.
 */
public final class ConfigWatcher {
    private static final long DEBOUNCE_MS = 1000;
    private static Thread thread;
    private static volatile boolean running;

    private ConfigWatcher() {}

    public static void start(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            OdysseyLib.LOGGER.error("[Odyssey] Could not create {}", dir, e);
            return;
        }
        running = true;
        thread = new Thread(() -> loop(dir), "odyssey-config-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    public static void stop() {
        running = false;
        if (thread != null) thread.interrupt();
        thread = null;
    }

    private static void loop(Path dir) {
        Map<String, Long> pending = new ConcurrentHashMap<>();
        try (WatchService ws = FileSystems.getDefault().newWatchService()) {
            dir.register(ws, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
            while (running) {
                WatchKey key = ws.poll(250, TimeUnit.MILLISECONDS);
                if (key != null) {
                    for (WatchEvent<?> ev : key.pollEvents()) {
                        if (ev.context() instanceof Path p && p.toString().endsWith(".json")) {
                            pending.put(p.toString(), System.currentTimeMillis());
                        }
                    }
                    key.reset();
                }
                long now = System.currentTimeMillis();
                for (Map.Entry<String, Long> e : pending.entrySet()) {
                    if (now - e.getValue() < DEBOUNCE_MS) continue;
                    pending.remove(e.getKey());
                    Path file = dir.resolve(e.getKey());
                    try {
                        String json = SafeFiles.read(file);
                        Scheduler.sync(() -> {
                            ConfigFile<?> cfg = Configs.byFileName(e.getKey());
                            if (cfg != null) cfg.reloadFromDisk(json);
                        });
                    } catch (Exception ex) {
                        OdysseyLib.LOGGER.warn("[Odyssey] Could not read edited config {}", file, ex);
                    }
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            OdysseyLib.LOGGER.error("[Odyssey] Config watcher stopped", e);
        }
    }
}
