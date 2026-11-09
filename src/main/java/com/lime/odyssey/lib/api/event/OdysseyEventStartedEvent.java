package com.lime.odyssey.lib.api.event;

import com.lime.odyssey.lib.api.region.Region;
import net.minecraftforge.eventbus.api.Event;

/** An Odyssey event (boss fight, mini event...) started. {@link #region()} may be null. */
public class OdysseyEventStartedEvent extends Event {
    private final String module;
    private final String eventId;
    private final String displayName;
    private final Region region;

    public OdysseyEventStartedEvent(String module, String eventId, String displayName, Region region) {
        this.module = module;
        this.eventId = eventId;
        this.displayName = displayName;
        this.region = region;
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

    public Region region() {
        return region;
    }
}
