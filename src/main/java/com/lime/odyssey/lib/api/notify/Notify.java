package com.lime.odyssey.lib.api.notify;

import com.lime.odyssey.lib.OdysseyLib;
import com.lime.odyssey.lib.api.data.DataFile;
import com.lime.odyssey.lib.api.data.DataStore;
import com.lime.odyssey.lib.api.msg.Msg;
import com.lime.odyssey.lib.api.perm.Perms;
import com.lime.odyssey.lib.api.sched.Scheduler;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * Admin notifications. Sent to online admins immediately; kept (max 50) for admins who are offline and
 * shown at their next login. Every notification also goes to registered listeners (Web Controls feed).
 */
public final class Notify {
    private static final int KEEP = 50;
    private static final Deque<Note> RECENT = new ArrayDeque<>();
    private static final List<Consumer<Note>> LISTENERS = new ArrayList<>();
    private static DataFile<Pending> pending;

    private Notify() {}

    public record Note(long time, String module, String message, Severity severity, String command) {}

    /** Persisted notifications raised while no admin was online. */
    public static final class Pending {
        public List<Note> unseen = new ArrayList<>();
    }

    public static void start() {
        pending = DataStore.world("library", "notifications", Pending.class, Pending::new);
    }

    public static void stop() {
        pending = null;
        synchronized (RECENT) {
            RECENT.clear();
        }
    }

    public static void addListener(Consumer<Note> l) {
        synchronized (LISTENERS) {
            LISTENERS.add(l);
        }
    }

    public static void removeListener(Consumer<Note> l) {
        synchronized (LISTENERS) {
            LISTENERS.remove(l);
        }
    }

    public static void admins(String module, String message, Severity severity) {
        admins(module, message, severity, null);
    }

    /**
     * @param command optional command (with leading slash) offered as a clickable {@code [Open]} button
     */
    public static void admins(String module, String message, Severity severity, String command) {
        Note n = new Note(System.currentTimeMillis(), module, message, severity, command);
        if (!Scheduler.isServerThread()) {
            Scheduler.sync(() -> deliver(n));
            return;
        }
        deliver(n);
    }

    private static void deliver(Note n) {
        OdysseyLib.LOGGER.info("[Odyssey] [{}] {}: {}", n.severity(), n.module(), n.message());
        synchronized (RECENT) {
            RECENT.addFirst(n);
            while (RECENT.size() > KEEP) RECENT.removeLast();
        }
        List<Consumer<Note>> ls;
        synchronized (LISTENERS) {
            ls = new ArrayList<>(LISTENERS);
        }
        for (Consumer<Note> l : ls) {
            try {
                l.accept(n);
            } catch (Exception e) {
                OdysseyLib.LOGGER.error("[Odyssey] Notification listener failed", e);
            }
        }
        MinecraftServer server = Scheduler.server();
        boolean anyAdmin = false;
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!Perms.isAdmin(p)) continue;
                anyAdmin = true;
                show(p, n);
            }
        }
        if (!anyAdmin && pending != null && n.severity() != Severity.INFO) {
            pending.get().unseen.add(n);
            while (pending.get().unseen.size() > KEEP) pending.get().unseen.remove(0);
            pending.markDirty();
        }
    }

    private static void show(ServerPlayer p, Note n) {
        String color = switch (n.severity()) {
            case INFO -> "&7";
            case WARN -> "&e";
            case ALERT -> "&c";
        };
        MutableComponent c = Msg.prefixed(n.module(), color + n.message());
        if (n.command() != null) c.append(Component.literal(" ")).append(Msg.button("&b[Open]", n.command(), n.command()));
        p.sendSystemMessage(c);
        if (n.severity() != Severity.INFO) Msg.sound(p, SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, n.severity() == Severity.ALERT ? 0.6f : 1.2f);
        if (n.severity() == Severity.ALERT) Msg.actionBar(p, "&c&lOdyssey alert: &f" + Msg.strip(n.message()));
    }

    /** Shows stored notifications to an admin who just logged in. */
    public static void onAdminLogin(ServerPlayer p) {
        if (pending == null || pending.get().unseen.isEmpty()) return;
        List<Note> notes = new ArrayList<>(pending.get().unseen);
        pending.get().unseen.clear();
        pending.markDirty();
        Msg.send(p, "&e" + notes.size() + " admin notification(s) while no admin was online:");
        for (Note n : notes) show(p, n);
    }

    public static List<Note> recent() {
        synchronized (RECENT) {
            return new ArrayList<>(RECENT);
        }
    }
}
