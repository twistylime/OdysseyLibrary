package com.lime.odyssey.lib.api.data;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.lime.odyssey.lib.OdysseyLib;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Opens and tracks every {@link DataFile}. World data lives in {@code <world>/odyssey/<module>/},
 * global data in {@code config/odyssey/}.
 */
public final class DataStore {
    private static final List<DataFile<?>> FILES = new CopyOnWriteArrayList<>();
    private static final java.util.Set<DataFile<?>> FLUSH = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private static Path worldRoot;
    private static Path configRoot;

    private DataStore() {}

    public static void start(MinecraftServer server) {
        worldRoot = server.getWorldPath(LevelResource.ROOT).resolve("odyssey").normalize();
        configRoot = server.getServerDirectory().toPath().resolve("config").resolve("odyssey").normalize();
    }

    public static Path worldDir(String module) {
        return worldRoot.resolve(module);
    }

    public static Path configDir() {
        return configRoot;
    }

    public static Path logsDir(String module) {
        return configRoot.getParent().getParent().resolve("logs").resolve("odyssey").resolve(module);
    }

    /** Opens {@code <world>/odyssey/<module>/<name>.json}. */
    public static <T> DataFile<T> world(String module, String name, Class<T> type, Supplier<T> defaults) {
        return open(module, worldDir(module).resolve(name + ".json"), TypeToken.get(type).getType(), 1, defaults, null);
    }

    public static <T> DataFile<T> world(String module, String name, TypeToken<T> type, Supplier<T> defaults) {
        return open(module, worldDir(module).resolve(name + ".json"), type.getType(), 1, defaults, null);
    }

    public static <T> DataFile<T> open(String owner, Path path, Type type, int schema, Supplier<T> defaults,
                                       Function<JsonElement, JsonElement> migrator) {
        DataFile<T> f = new DataFile<>(owner, path, type, schema, defaults, migrator);
        FILES.add(f);
        return f;
    }

    public static void close(DataFile<?> f) {
        f.saveNow();
        FILES.remove(f);
    }

    static void requestFlush(DataFile<?> f) {
        FLUSH.add(f);
    }

    /** End of tick: writes files that asked for an immediate save (server thread). */
    public static void processFlushRequests() {
        if (FLUSH.isEmpty()) return;
        List<DataFile<?>> l = new ArrayList<>(FLUSH);
        FLUSH.clear();
        for (DataFile<?> f : l) {
            try {
                f.saveAsync();
            } catch (Exception e) {
                OdysseyLib.LOGGER.error("[Odyssey] Could not snapshot {}", f.path(), e);
            }
        }
    }

    /** Saves every dirty file now, on the calling thread, keeping them open. */
    public static void saveAllNow() {
        FLUSH.clear();
        for (DataFile<?> f : FILES) {
            try {
                f.saveNow();
            } catch (Exception e) {
                OdysseyLib.LOGGER.error("[Odyssey] Could not save {}", f.path(), e);
            }
        }
    }

    /** Periodic save of dirty files (server thread). */
    public static void saveDirty() {
        for (DataFile<?> f : FILES) {
            try {
                f.saveAsync();
            } catch (Exception e) {
                OdysseyLib.LOGGER.error("[Odyssey] Could not snapshot {}", f.path(), e);
            }
        }
    }

    /** Final synchronous flush at shutdown; closes every file. */
    public static void flushAll() {
        FLUSH.clear();
        for (DataFile<?> f : new ArrayList<>(FILES)) {
            try {
                f.saveNow();
            } catch (Exception e) {
                OdysseyLib.LOGGER.error("[Odyssey] Could not save {} at shutdown", f.path(), e);
            }
        }
        FILES.clear();
    }
}
