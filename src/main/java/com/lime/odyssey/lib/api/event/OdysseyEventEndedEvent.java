package com.lime.odyssey.lib.api.event;

import net.minecraftforge.eventbus.api.Event;

/** An Odyssey event ended (completed, cancelled or stopped). */
public class OdysseyEventEndedEvent extends Event {
    private final String module;
    private final String eventId;
    private final String displayName;

    public OdysseyEventEndedEvent(String module, String eventId, String displayName) {
        this.module = module;
        this.eventId = eventId;
        this.displayName = displayName;
    }

    public String module() {
        return module;
    }

    public String eventId() {
        return eventId;
    }

    public String displayName() {
        return displayName;
    }
}
