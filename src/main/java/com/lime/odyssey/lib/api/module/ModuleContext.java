package com.lime.odyssey.lib.api.module;

import com.lime.odyssey.lib.OdysseyLib;
import com.lime.odyssey.lib.api.config.ConfigFile;
import com.lime.odyssey.lib.api.config.Configs;
import com.lime.odyssey.lib.api.data.DataFile;
import com.lime.odyssey.lib.api.data.DataStore;
import com.lime.odyssey.lib.api.data.LogFile;
import com.lime.odyssey.lib.api.msg.Lang;
import com.lime.odyssey.lib.api.sched.Scheduler;
import com.lime.odyssey.lib.api.sched.TaskHandle;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Scoped access to Library services for one enabled module. Listeners and tasks created here are
 * removed and data files saved when the module is disabled.
 */
public final class ModuleContext {
    private final ModuleInfo info;
    private final MinecraftServer server;
    private final Logger log;
    private final List<Object> listeners = new ArrayList<>();
    private final List<TaskHandle> tasks = new ArrayList<>();
    private final List<DataFile<?>> files = new ArrayList<>();
    private boolean closed;

    ModuleContext(ModuleInfo info, MinecraftServer server) {
        this.info = info;
        this.server = server;
        this.log = LoggerFactory.getLogger("Odyssey/" + info.name());
    }

    public ModuleInfo info() {
        return info;
    }

    public String id() {
        return info.id();
    }

    public MinecraftServer server() {
        return server;
    }

    public Logger log() {
        return log;
    }

    /** Registers an object with {@code @SubscribeEvent} methods on the Forge bus until disable. */
    public void listen(Object listener) {
        MinecraftForge.EVENT_BUS.register(listener);
        listeners.add(listener);
    }

    public TaskHandle every(int periodTicks, Runnable r) {
        TaskHandle h = Scheduler.every(info.id(), periodTicks, r);
        tasks.add(h);
        return h;
    }

    public TaskHandle later(int ticks, Runnable r) {
        TaskHandle h = Scheduler.later(info.id(), ticks, r);
        tasks.add(h);
        return h;
    }

    /** {@code config/odyssey/<module>.json}. */
    public <T> ConfigFile<T> config(Class<T> type) {
        return Configs.open(info.id(), info.id(), type);
    }

    /** {@code config/odyssey/<module>-<name>.json}. */
    public <T> ConfigFile<T> config(String name, Class<T> type) {
        return Configs.open(info.id(), info.id() + "-" + name, type);
    }

    /** {@code <world>/odyssey/<module>/<name>.json}. */
    public <T> DataFile<T> data(String name, Class<T> type, Supplier<T> defaults) {
        DataFile<T> f = DataStore.world(info.id(), name, type, defaults);
        files.add(f);
        return f;
    }

    public Path dataDir() {
        return DataStore.worldDir(info.id());
    }

    public LogFile logFile(String name, int retentionDays) {
        LogFile l = new LogFile(info.id(), name, retentionDays);
        l.prune();
        return l;
    }

    public void messages(Map<String, String> defaults) {
        Lang.register(info.id(), defaults);
    }

    public String msg(String key, Object... args) {
        return Lang.get(info.id(), key, args);
    }

    void close() {
        if (closed) return;
        closed = true;
        for (Object l : listeners) {
            try {
                MinecraftForge.EVENT_BUS.unregister(l);
            } catch (Exception e) {
                OdysseyLib.LOGGER.warn("[Odyssey] Could not unregister a listener of {}", info.id(), e);
            }
        }
        listeners.clear();
        for (TaskHandle t : tasks) t.cancel();
        tasks.clear();
        for (DataFile<?> f : files) DataStore.close(f);
        files.clear();
    }
}
