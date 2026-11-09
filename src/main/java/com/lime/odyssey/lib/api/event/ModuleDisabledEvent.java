package com.lime.odyssey.lib.api.event;

import net.minecraftforge.eventbus.api.Event;

/** Posted after a module was disabled. Open screens owned by it are closed by the Library. */
public class ModuleDisabledEvent extends Event {
    private final String module;

    public ModuleDisabledEvent(String module) {
        this.module = module;
    }

    public String module() {
        return module;
    }
}
