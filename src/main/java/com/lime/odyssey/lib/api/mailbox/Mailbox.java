package com.lime.odyssey.lib.api.mailbox;

import com.lime.odyssey.lib.OdysseyLib;
import com.lime.odyssey.lib.api.audit.Audit;
import com.lime.odyssey.lib.api.data.DataFile;
import com.lime.odyssey.lib.api.data.DataStore;
import com.lime.odyssey.lib.api.event.MailDeliveredEvent;
import com.lime.odyssey.lib.api.msg.Msg;
import com.lime.odyssey.lib.api.points.Points;
import com.lime.odyssey.lib.api.sched.Scheduler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Odyssey Mailbox: every reward and returned item from every mod goes here, never straight into an
 * inventory. Works for offline players; entries never expire. Server thread only.
 * <p>Claiming is all-or-partial and never drops items: whatever does not fit stays in the entry.
 * The mailbox file is saved immediately after each claim so a crash cannot duplicate a claimed item.</p>
 */
public final class Mailbox {
    private static DataFile<Data> data;

    private Mailbox() {}

    public static final class Data {
        public Map<UUID, List<MailEntry>> boxes = new HashMap<>();
    }

    public static void start() {
        data = DataStore.world("library", "mailbox", Data.class, Data::new);
    }

    public static void stop() {
        data = null;
    }

    public static boolean running() {
        return data != null;
    }

    public static List<MailEntry> entries(UUID player) {
        if (data == null) return List.of();
        List<MailEntry> l = data.get().boxes.get(player);
        return l == null ? List.of() : List.copyOf(l);
    }

    public static int count(UUID player) {
        return entries(player).size();
    }

    public static int unseen(UUID player) {
        int n = 0;
        for (MailEntry e : entries(player)) if (!e.seen) n++;
        return n;
    }

    /** Delivers an entry (online or offline player). Empty entries are ignored. */
    public static void send(UUID player, MailEntry entry) {
        if (entry == null || entry.isEmpty()) return;
        if (!Scheduler.isServerThread()) {
            Scheduler.sync(() -> send(player, entry));
            return;
        }
        if (data == null) {
            OdysseyLib.LOGGER.error("[Odyssey] Mail for {} lost: the mailbox is not running ({})", player, entry.title);
            return;
        }
        data.get().boxes.computeIfAbsent(player, k -> new ArrayList<>()).add(entry);
        data.flush();
        MinecraftForge.EVENT_BUS.post(new MailDeliveredEvent(player, entry));
        MinecraftServer server = Scheduler.server();
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(player);
        if (p != null) notifyNew(p, entry);
    }

    /** Convenience: one item returned to a player (e.g. from a closed menu). */
    public static void returnItem(UUID player, ItemStack stack, String source) {
        if (stack == null || stack.isEmpty()) return;
        send(player, MailEntry.of(source, "Returned item").item(stack));
    }

    private static void notifyNew(ServerPlayer p, MailEntry e) {
        p.sendSystemMessage(Msg.prefixed(null, "&eYou have new mail: &f" + e.title + " ")
                .append(Msg.button("&b[Open Mailbox]", "/odyssey mailbox", "Open your mailbox")));
        Msg.title(p, "", "&e✉ New mail: &f" + e.title, 5, 40, 10);
        Msg.sound(p, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
    }

    /** On login: tell the player about unclaimed mail. */
    public static void onLogin(ServerPlayer p) {
        int n = count(p.getUUID());
        if (n == 0) return;
        p.sendSystemMessage(Msg.prefixed(null, "&eYou have &f" + n + " &eunclaimed mail" + (n == 1 ? "" : "s") + ". ")
                .append(Msg.button("&b[Open Mailbox]", "/odyssey mailbox", "Open your mailbox")));
    }

    public static void markSeen(UUID player) {
        if (data == null) return;
        List<MailEntry> l = data.get().boxes.get(player);
        if (l == null) return;
        boolean changed = false;
        for (MailEntry e : l) {
            if (!e.seen) {
                e.seen = true;
                changed = true;
            }
        }
        if (changed) data.markDirty();
    }

    public enum ClaimResult { CLAIMED, PARTIAL, NOTHING_FIT, GONE }

    /** Claims one entry for its owner. */
    public static ClaimResult claim(ServerPlayer p, UUID entryId) {
        if (data == null) return ClaimResult.GONE;
        List<MailEntry> l = data.get().boxes.get(p.getUUID());
        if (l == null) return ClaimResult.GONE;
        MailEntry e = null;
        for (MailEntry x : l) if (x.id.equals(entryId)) e = x;
        if (e == null) return ClaimResult.GONE;
        boolean anything = false;
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack s : e.items) {
            ItemStack copy = s.copy();
            int before = copy.getCount();
            p.getInventory().add(copy);
            if (copy.getCount() < before) anything = true;
            if (!copy.isEmpty()) left.add(copy);
        }
        e.items = left;
        if (e.points > 0) {
            Points.add(p.getUUID(), e.points, e.source, e.title);
            Msg.send(p, "&6+" + Msg.number(e.points) + " Odyssey Points");
            e.points = 0;
            anything = true;
        }
        if (e.xp > 0) {
            p.giveExperiencePoints(e.xp);
            e.xp = 0;
            anything = true;
        }
        if (!e.commands.isEmpty()) {
            List<String> cmds = new ArrayList<>(e.commands);
            e.commands.clear();
            anything = true;
            for (String c : cmds) runCommand(p, c);
        }
        ClaimResult result;
        if (e.isEmpty()) {
            l.remove(e);
            if (l.isEmpty()) data.get().boxes.remove(p.getUUID());
            result = ClaimResult.CLAIMED;
        } else {
            result = anything ? ClaimResult.PARTIAL : ClaimResult.NOTHING_FIT;
        }
        if (anything) {
            p.inventoryMenu.broadcastChanges();
            data.flush();
        }
        return result;
    }

    /** Claims everything that fits. Returns the number of fully claimed entries. */
    public static int claimAll(ServerPlayer p) {
        int n = 0;
        for (MailEntry e : entries(p.getUUID())) {
            ClaimResult r = claim(p, e.id);
            if (r == ClaimResult.CLAIMED) n++;
            if (r == ClaimResult.NOTHING_FIT || r == ClaimResult.PARTIAL) break;
        }
        return n;
    }

    /** Admin removal of one entry (audited). */
    public static boolean delete(UUID player, UUID entryId, String actor) {
        if (data == null) return false;
        List<MailEntry> l = data.get().boxes.get(player);
        if (l == null || !l.removeIf(x -> x.id.equals(entryId))) return false;
        data.flush();
        Audit.record(actor, "library", "mailbox.delete", player.toString(), entryId.toString());
        return true;
    }

    /** Admin purge of a whole mailbox (audited). */
    public static int purge(UUID player, String actor) {
        if (data == null) return 0;
        List<MailEntry> l = data.get().boxes.remove(player);
        int n = l == null ? 0 : l.size();
        data.flush();
        Audit.record(actor, "library", "mailbox.purge", player.toString(), n + " entries");
        return n;
    }

    private static void runCommand(ServerPlayer p, String raw) {
        String cmd = raw.replace("{player}", p.getGameProfile().getName()).replace("{uuid}", p.getUUID().toString());
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        try {
            MinecraftServer server = p.server;
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
        } catch (Exception ex) {
            OdysseyLib.LOGGER.error("[Odyssey] Reward command failed: {}", cmd, ex);
            Msg.send(p, Component.literal("A reward command failed; an admin was notified."));
            com.lime.odyssey.lib.api.notify.Notify.admins("library", "Reward command for " + p.getGameProfile().getName()
                    + " failed: /" + cmd, com.lime.odyssey.lib.api.notify.Severity.WARN);
        }
    }
}
