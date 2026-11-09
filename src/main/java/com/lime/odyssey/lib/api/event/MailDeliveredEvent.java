package com.lime.odyssey.lib.api.event;

import com.lime.odyssey.lib.api.mailbox.MailEntry;
import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

/** A mail entry was added to a player's mailbox. */
public class MailDeliveredEvent extends Event {
    private final UUID player;
    private final MailEntry entry;

    public MailDeliveredEvent(UUID player, MailEntry entry) {
        this.player = player;
        this.entry = entry;
    }

    public UUID player() {
        return player;
    }

    public MailEntry entry() {
        return entry;
    }
}
