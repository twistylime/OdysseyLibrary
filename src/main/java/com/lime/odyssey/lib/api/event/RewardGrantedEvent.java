package com.lime.odyssey.lib.api.event;

import com.lime.odyssey.lib.api.mailbox.MailEntry;
import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

/** A reward was granted (it is now in the player's mailbox). */
public class RewardGrantedEvent extends Event {
    private final UUID player;
    private final String source;
    private final MailEntry entry;

    public RewardGrantedEvent(UUID player, String source, MailEntry entry) {
        this.player = player;
        this.source = source;
        this.entry = entry;
    }

    public UUID player() {
        return player;
    }

    public String source() {
        return source;
    }

    public MailEntry entry() {
        return entry;
    }
}
