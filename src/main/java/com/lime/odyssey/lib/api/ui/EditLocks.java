package com.lime.odyssey.lib.api.ui;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Prevents two admins from editing the same object at once. Acquire when an editor opens, release in
 * {@link Screen#onClose}. A lock held by a player who is no longer online is released automatically.
 */
public final class EditLocks {
    private static final Map<String, UUID> LOCKS = new HashMap<>();
    private static final Map<UUID, String> NAMES = new HashMap<>();

    private EditLocks() {}

    /** @return null when acquired, otherwise the name of the player holding the lock */
    public static String acquire(String key, ServerPlayer p) {
        UUID holder = LOCKS.get(key);
        if (holder != null && !holder.equals(p.getUUID()) && p.server.getPlayerList().getPlayer(holder) != null) {
            return NAMES.getOrDefault(holder, "another admin");
        }
        LOCKS.put(key, p.getUUID());
        NAMES.put(p.getUUID(), p.getGameProfile().getName());
        return null;
    }

    public static void release(String key, ServerPlayer p) {
        if (p.getUUID().equals(LOCKS.get(key))) LOCKS.remove(key);
    }

    public static void releaseAll(UUID player) {
        LOCKS.values().removeIf(player::equals);
    }

    public static void clear() {
        LOCKS.clear();
        NAMES.clear();
    }
}
