package com.lime.odyssey.lib.api.event;

import net.minecraftforge.eventbus.api.Event;

import java.util.Set;

/** Posted on the Forge bus (server thread) after a module's config was replaced. */
public class ConfigChangedEvent extends Event {
    private final String module;
    private final String config;
    private final Set<String> changedKeys;

    public ConfigChangedEvent(String module, String config, Set<String> changedKeys) {
        this.module = module;
        this.config = config;
        this.changedKeys = Set.copyOf(changedKeys);
    }

    /** Owning module id. */
    public String module() {
        return module;
    }

    /** Config name (file name without {@code .json}). */
    public String config() {
        return config;
    }

    public Set<String> changedKeys() {
        return changedKeys;
    }
}
