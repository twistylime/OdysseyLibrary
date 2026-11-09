package com.lime.odyssey.lib.api.soulbound;

import com.lime.odyssey.lib.api.mailbox.Mailbox;
import com.lime.odyssey.lib.api.msg.Msg;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/**
 * Items bound to one player. A soulbound item never exists as a dropped item: the moment one would appear
 * in the world (tossed, dropped on death, spilled from a container, loaded from an old chunk...) it goes
 * back to its owner's Mailbox instead. This also rules out pickup by others, burning, explosions, despawning,
 * hoppers and mobs. A soulbound item found in another player's inventory (e.g. taken from a shared chest)
 * is sent home too. Use {@link #bind} to make an item soulbound.
 */
public final class Soulbound {
    public static final String KEY = "odyssey_soulbound";

    /** The Library registers one instance as event listener; use the static methods. */
    public Soulbound() {}

    public static ItemStack bind(ItemStack stack, UUID owner, String ownerName) {
        CompoundTag t = new CompoundTag();
        t.putUUID("owner", owner);
        t.putUUID("id", UUID.randomUUID());
        t.putString("name", ownerName == null ? "" : ownerName);
        stack.getOrCreateTag().put(KEY, t);
        return stack;
    }

    public static UUID owner(ItemStack s) {
        if (s == null || s.isEmpty() || s.getTag() == null || !s.getTag().contains(KEY)) return null;
        CompoundTag t = s.getTag().getCompound(KEY);
        return t.hasUUID("owner") ? t.getUUID("owner") : null;
    }

    public static boolean isSoulbound(ItemStack s) {
        return owner(s) != null;
    }

    /** Returns false (and leaves the stack alone) while the Mailbox is not running yet, e.g. during spawn chunk loading. */
    private static boolean sendHome(ItemStack s) {
        UUID owner = owner(s);
        if (owner == null || s.isEmpty() || !Mailbox.running()) return false;
        Mailbox.returnItem(owner, s.copy(), "soulbound");
        s.setCount(0);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onToss(ItemTossEvent e) {
        ItemStack s = e.getEntity().getItem();
        if (e.isCanceled() || !isSoulbound(s)) return;
        if (!sendHome(s)) return;
        if (e.getPlayer() instanceof ServerPlayer p) Msg.send(p, "&7Soulbound items can't be dropped; it went to your mailbox.");
        e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDeathDrops(LivingDropsEvent e) {
        if (e.isCanceled()) return;
        e.getDrops().removeIf(item -> {
            ItemStack s = item.getItem();
            return isSoulbound(s) && sendHome(s);
        });
    }

    /** Catches every other way a soulbound item could appear in the world. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof ItemEntity item)) return;
        ItemStack s = item.getItem();
        if (isSoulbound(s) && sendHome(s)) e.setCanceled(true);
    }

    /** A soulbound item in someone else's inventory goes home (run periodically and at login). */
    public static void scan(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            UUID owner = owner(s);
            if (owner != null && !owner.equals(p.getUUID()) && Mailbox.running()) {
                inv.setItem(i, ItemStack.EMPTY);
                Mailbox.returnItem(owner, s.copy(), "soulbound");
            }
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) scan(p);
    }
}
