package com.lime.odyssey.lib.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/** A player became AFK or active again. */
public class PlayerAfkChangedEvent extends Event {
    private final ServerPlayer player;
    private final boolean afk;
    private final boolean manual;

    public PlayerAfkChangedEvent(ServerPlayer player, boolean afk, boolean manual) {
        this.player = player;
        this.afk = afk;
        this.manual = manual;
    }

    public ServerPlayer player() {
        return player;
    }

    public boolean afk() {
        return afk;
    }

    public boolean manual() {
        return manual;
    }
}
