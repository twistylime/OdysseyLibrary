package com.lime.odyssey.lib.api.event;

import net.minecraftforge.eventbus.api.Event;

/** Posted after a module was enabled (server start or admin action). */
public class ModuleEnabledEvent extends Event {
    private final String module;

    public ModuleEnabledEvent(String module) {
        this.module = module;
    }

    public String module() {
        return module;
    }
}
