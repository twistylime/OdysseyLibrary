package com.lime.odyssey.lib.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/** Optional Curios support. Every call is a no-op when Curios is not installed. */
public final class CuriosBridge {
    private static Boolean loaded;

    private CuriosBridge() {}

    public static boolean loaded() {
        if (loaded == null) loaded = ModList.get().isLoaded("curios");
        return loaded;
    }

    /** Left-over curio that could not be put back: slot type, cosmetic flag, index and the stack. */
    public record Leftover(String slot, boolean cosmetic, int index, ItemStack stack) {}

    public static CompoundTag save(ServerPlayer p) {
        return loaded() ? CuriosImpl.save(p) : null;
    }

    public static void clear(ServerPlayer p) {
        if (loaded()) CuriosImpl.clear(p);
    }

    /** Restores what fits; returns what did not (e.g. slots granted by another curio that is not equipped yet). */
    public static List<Leftover> restore(ServerPlayer p, CompoundTag tag) {
        if (tag == null) return new ArrayList<>();
        if (!loaded()) return CuriosImpl.allAsLeftovers(tag);
        return CuriosImpl.restore(p, tag);
    }

    public static boolean place(ServerPlayer p, Leftover l, boolean anyIndex) {
        return loaded() && CuriosImpl.place(p, l.slot(), l.cosmetic(), l.index(), l.stack(), anyIndex);
    }
}
