package com.lime.odyssey.lib.api.config;

import com.lime.odyssey.lib.api.data.DataStore;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Registry of every open {@link ConfigFile}, keyed by name (usually the module id). */
public final class Configs {
    private static final Map<String, ConfigFile<?>> OPEN = Collections.synchronizedMap(new LinkedHashMap<>());

    private Configs() {}

    /** Opens (or returns the already open) {@code config/odyssey/<name>.json}. */
    @SuppressWarnings("unchecked")
    public static <T> ConfigFile<T> open(String module, String name, Class<T> type) {
        ConfigFile<?> existing = OPEN.get(name);
        if (existing != null) {
            if (existing.type() != type) throw new IllegalStateException("Config '" + name + "' is already open with another type");
            return (ConfigFile<T>) existing;
        }
        ConfigFile<T> f = new ConfigFile<>(module, DataStore.configDir().resolve(name + ".json"), type);
        OPEN.put(name, f);
        return f;
    }

    public static ConfigFile<?> get(String name) {
        return OPEN.get(name);
    }

    public static Collection<ConfigFile<?>> all() {
        synchronized (OPEN) {
            return java.util.List.copyOf(OPEN.values());
        }
    }

    static ConfigFile<?> byFileName(String fileName) {
        synchronized (OPEN) {
            for (ConfigFile<?> f : OPEN.values()) if (f.path().getFileName().toString().equals(fileName)) return f;
        }
        return null;
    }

    public static void closeAll() {
        OPEN.clear();
    }
}
