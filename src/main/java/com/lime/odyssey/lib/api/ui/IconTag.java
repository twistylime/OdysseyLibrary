package com.lime.odyssey.lib.api.ui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Marks display-only icons so they can never exist in a real inventory. */
public final class IconTag {
    public static final String KEY = "odyssey_icon";

    private IconTag() {}

    public static ItemStack mark(ItemStack s) {
        if (!s.isEmpty()) s.getOrCreateTag().putBoolean(KEY, true);
        return s;
    }

    public static boolean is(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        CompoundTag t = s.getTag();
        return t != null && t.contains(KEY);
    }

    /** Removes any icon that slipped into a player's inventory or cursor. */
    public static void purge(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (is(inv.getItem(i))) inv.setItem(i, ItemStack.EMPTY);
        if (is(p.containerMenu.getCarried())) p.containerMenu.setCarried(ItemStack.EMPTY);
    }
}
