package com.lime.odyssey.lib.compat;

import com.lime.odyssey.lib.OdysseyLib;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/** Optional Origins support (pause and restore every origin layer with power data). */
public final class OriginsBridge {
    private static Boolean loaded;

    private OriginsBridge() {}

    public static boolean loaded() {
        if (loaded == null) loaded = ModList.get().isLoaded("origins");
        return loaded;
    }

    /** Origin layers + power data, or null when there is nothing to save. */
    public static CompoundTag snapshot(ServerPlayer p) {
        if (!loaded()) return null;
        try {
            return OriginsImpl.snapshot(p);
        } catch (Throwable t) {
            OdysseyLib.LOGGER.error("[Odyssey] Could not read the origins of {}", p.getGameProfile().getName(), t);
            return null;
        }
    }

    /** Removes every origin. Returns false (and changes nothing) on failure. */
    public static boolean strip(ServerPlayer p) {
        if (!loaded()) return true;
        try {
            OriginsImpl.strip(p);
            return true;
        } catch (Throwable t) {
            OdysseyLib.LOGGER.error("[Odyssey] Could not remove the origins of {}", p.getGameProfile().getName(), t);
            return false;
        }
    }

    public static boolean restore(ServerPlayer p, CompoundTag tag) {
        if (tag == null) return true;
        if (!loaded()) return false;
        try {
            OriginsImpl.restore(p, tag);
            return true;
        } catch (Throwable t) {
            OdysseyLib.LOGGER.error("[Odyssey] Could not restore the origins of {}", p.getGameProfile().getName(), t);
            return false;
        }
    }
}
