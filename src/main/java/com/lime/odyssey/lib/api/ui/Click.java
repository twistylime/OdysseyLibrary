package com.lime.odyssey.lib.api.ui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;

/** A click on a screen button. */
public record Click(ServerPlayer player, int slot, int button, ClickType type) {
    public boolean left() {
        return type == ClickType.PICKUP && button == 0;
    }

    public boolean right() {
        return type == ClickType.PICKUP && button == 1;
    }

    public boolean shift() {
        return type == ClickType.QUICK_MOVE;
    }

    public boolean shiftLeft() {
        return type == ClickType.QUICK_MOVE && button == 0;
    }

    public boolean shiftRight() {
        return type == ClickType.QUICK_MOVE && button == 1;
    }
}
