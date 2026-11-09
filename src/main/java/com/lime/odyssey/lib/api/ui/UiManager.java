package com.lime.odyssey.lib.api.ui;

import com.lime.odyssey.lib.OdysseyLib;
import com.lime.odyssey.lib.api.sched.Scheduler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Tracks open screens, runs queued clicks, ticks screens every second and closes screens of disabled modules. */
public final class UiManager {
    private static final Map<UUID, Screen> OPEN = new HashMap<>();
    private static final Set<Screen> CLICKED_THIS_TICK = new HashSet<>();
    private static final Map<UUID, java.util.List<Screen>> SUSPENDED = new HashMap<>();
    private static BiConsumer<ServerPlayer, ItemStack> returner = UiManager::giveOrDrop;
    private static int ticker;

    private UiManager() {}

    static void opened(ServerPlayer p, Screen s) {
        OPEN.put(p.getUUID(), s);
    }

    static void closed(ServerPlayer p, Screen s) {
        if (OPEN.get(p.getUUID()) == s) OPEN.remove(p.getUUID());
    }

    static void suspend(ServerPlayer p, Screen s) {
        java.util.List<Screen> l = SUSPENDED.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
        if (!l.contains(s)) l.add(s);
    }

    static void unsuspend(ServerPlayer p, Screen s) {
        java.util.List<Screen> l = SUSPENDED.get(p.getUUID());
        if (l != null) {
            l.remove(s);
            if (l.isEmpty()) SUSPENDED.remove(p.getUUID());
        }
    }

    static boolean isSuspended(ServerPlayer p, Screen s) {
        java.util.List<Screen> l = SUSPENDED.get(p.getUUID());
        return l != null && l.contains(s);
    }

    /** Drops a suspended screen without calling its callbacks (it finished elsewhere). */
    public static void forget(ServerPlayer p, Screen s) {
        unsuspend(p, s);
    }

    private static void abandonSuspended(UUID player) {
        java.util.List<Screen> l = SUSPENDED.remove(player);
        if (l != null) for (Screen s : l) s.abandon();
    }

    public static Screen current(ServerPlayer p) {
        return OPEN.get(p.getUUID());
    }

    /** At most one button click per screen per tick; runs at the next tick. */
    static void queue(Screen s, Click click) {
        if (!CLICKED_THIS_TICK.add(s)) return;
        Scheduler.sync(() -> s.dispatch(click));
    }

    /** Closes the current Odyssey menu server-side only, so the next screen replaces it without a cursor jump. */
    static void closeCurrentQuietly(ServerPlayer p) {
        if (p.containerMenu instanceof OdysseyMenu || p.containerMenu instanceof AnvilInputMenu) p.doCloseContainer();
        else if (p.containerMenu != p.inventoryMenu) p.closeContainer();
    }

    /** Where items returned from a screen go (the Mailbox once it is running). */
    public static void setItemReturner(BiConsumer<ServerPlayer, ItemStack> r) {
        returner = r == null ? UiManager::giveOrDrop : r;
    }

    public static void returnItem(ServerPlayer p, ItemStack stack) {
        if (!stack.isEmpty()) returner.accept(p, stack.copy());
    }

    private static void giveOrDrop(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack) && !stack.isEmpty()) p.drop(stack, false);
    }

    public static void tick() {
        CLICKED_THIS_TICK.clear();
        if (++ticker % 20 != 0) return;
        for (Screen s : new ArrayList<>(OPEN.values())) {
            if (!s.isOpen()) {
                OPEN.values().remove(s);
                continue;
            }
            try {
                s.onSecond();
            } catch (Exception e) {
                OdysseyLib.LOGGER.error("[Odyssey] Screen tick failed ({})", s.getClass().getName(), e);
            }
        }
    }

    public static void refreshWhere(java.util.function.Predicate<Screen> filter) {
        for (Screen s : new ArrayList<>(OPEN.values())) if (filter.test(s)) s.refresh();
    }

    public static void onLogout(ServerPlayer p) {
        if (p.containerMenu instanceof OdysseyMenu || p.containerMenu instanceof AnvilInputMenu) p.doCloseContainer();
        OPEN.remove(p.getUUID());
        abandonSuspended(p.getUUID());
    }

    public static void closeAll(MinecraftServer server) {
        for (Screen s : new ArrayList<>(OPEN.values())) s.close();
        OPEN.clear();
        for (UUID id : new ArrayList<>(SUSPENDED.keySet())) abandonSuspended(id);
        CLICKED_THIS_TICK.clear();
    }

    /** Module disabled: close its screens and give back items held by its suspended screens. */
    public static void closeOwnedBy(String module) {
        for (Screen s : new ArrayList<>(OPEN.values())) if (module.equals(s.owner())) s.close();
        for (java.util.List<Screen> l : SUSPENDED.values()) {
            for (Screen s : new ArrayList<>(l)) {
                if (module.equals(s.owner())) {
                    l.remove(s);
                    s.abandon();
                }
            }
        }
        SUSPENDED.values().removeIf(java.util.List::isEmpty);
    }
}
