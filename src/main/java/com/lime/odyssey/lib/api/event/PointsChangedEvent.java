package com.lime.odyssey.lib.api.event;

import net.minecraftforge.eventbus.api.Event;

import java.util.UUID;

/** A player's Odyssey Points balance changed. */
public class PointsChangedEvent extends Event {
    private final UUID player;
    private final long delta;
    private final long balance;
    private final String source;

    public PointsChangedEvent(UUID player, long delta, long balance, String source) {
        this.player = player;
        this.delta = delta;
        this.balance = balance;
        this.source = source;
    }

    public UUID player() {
        return player;
    }

    public long delta() {
        return delta;
    }

    public long balance() {
        return balance;
    }

    public String source() {
        return source;
    }
}
