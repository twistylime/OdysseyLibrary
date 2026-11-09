package com.lime.odyssey.lib.api.reward;

import com.google.gson.JsonElement;
import com.lime.odyssey.lib.OdysseyLib;
import com.lime.odyssey.lib.api.event.RewardGrantedEvent;
import com.lime.odyssey.lib.api.mailbox.MailEntry;
import com.lime.odyssey.lib.api.mailbox.Mailbox;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/** Grants rewards. Every reward ends up in the {@link Mailbox}. */
public final class Rewards {
    private static final Map<String, BiFunction<UUID, JsonElement, List<ItemStack>>> TYPES = new ConcurrentHashMap<>();
    private static final Random RANDOM = new Random();

    private Rewards() {}

    /**
     * Registers a custom reward type that turns its data into items for the recipient at grant time
     * (e.g. {@code odysseyorigins:origin_item} creates a soulbound origin item).
     */
    public static void registerType(String id, BiFunction<UUID, JsonElement, List<ItemStack>> factory) {
        TYPES.put(id, factory);
    }

    public static boolean hasType(String id) {
        return TYPES.containsKey(id);
    }

    /** Puts the reward into the player's mailbox. Returns the created entry (null if empty). */
    public static MailEntry grant(UUID player, RewardSpec spec, String source, String title) {
        if (spec == null || spec.isEmpty()) return null;
        RewardSpec r = spec.copy();
        MailEntry e = MailEntry.of(source, title).items(r.items).points(r.points).xp(r.xp);
        for (String c : r.commands) e.command(c);
        for (RewardSpec.Custom c : r.custom) {
            BiFunction<UUID, JsonElement, List<ItemStack>> f = TYPES.get(c.type);
            if (f == null) {
                OdysseyLib.LOGGER.warn("[Odyssey] Reward type '{}' is not installed; part of '{}' skipped for {}", c.type, title, player);
                continue;
            }
            try {
                List<ItemStack> made = f.apply(player, c.data);
                if (made != null) e.items(made);
            } catch (Exception ex) {
                OdysseyLib.LOGGER.error("[Odyssey] Reward type '{}' failed for {}", c.type, player, ex);
            }
        }
        if (e.isEmpty()) return null;
        Mailbox.send(player, e);
        MinecraftForge.EVENT_BUS.post(new RewardGrantedEvent(player, source, e));
        return e;
    }

    public static MailEntry grant(UUID player, RewardPool pool, String source, String title) {
        return pool == null ? null : grant(player, pool.roll(RANDOM), source, title);
    }
}
