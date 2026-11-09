package com.lime.odyssey.lib.api.module;

import com.lime.odyssey.lib.OdysseyLib;
import com.lime.odyssey.lib.api.audit.Audit;
import com.lime.odyssey.lib.api.data.DataFile;
import com.lime.odyssey.lib.api.data.DataStore;
import com.lime.odyssey.lib.api.event.ModuleDisabledEvent;
import com.lime.odyssey.lib.api.event.ModuleEnabledEvent;
import com.lime.odyssey.lib.api.notify.Notify;
import com.lime.odyssey.lib.api.notify.Severity;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Registry and lifecycle of all Odyssey modules. */
public final class Modules {
    private static final Map<String, OdysseyModule> REGISTERED = new LinkedHashMap<>();
    private static final Map<String, ModuleContext> ACTIVE = new HashMap<>();
    private static DataFile<State> state;
    private static MinecraftServer server;

    private Modules() {}

    /** Persisted enabled/disabled state ({@code config/odyssey/modules.json}). */
    public static final class State {
        public Map<String, Boolean> enabled = new LinkedHashMap<>();
    }

    /** Call from the module's {@code @Mod} constructor. */
    public static synchronized void register(OdysseyModule module) {
        String id = module.info().id();
        if (REGISTERED.containsKey(id)) {
            throw new IllegalStateException("Two Odyssey modules use the id '" + id + "': "
                    + REGISTERED.get(id).getClass().getName() + " and " + module.getClass().getName());
        }
        REGISTERED.put(id, module);
    }

    public static synchronized List<OdysseyModule> all() {
        List<OdysseyModule> l = new ArrayList<>(REGISTERED.values());
        l.sort(Comparator.comparing((OdysseyModule m) -> m.info().category()).thenComparing(m -> m.info().name()));
        return l;
    }

    public static synchronized Optional<OdysseyModule> get(String id) {
        return Optional.ofNullable(REGISTERED.get(id));
    }

    public static synchronized boolean isEnabled(String id) {
        return ACTIVE.containsKey(id);
    }

    public static synchronized Optional<ModuleContext> context(String id) {
        return Optional.ofNullable(ACTIVE.get(id));
    }

    public static void start(MinecraftServer s) {
        server = s;
        state = DataStore.open("library", DataStore.configDir().resolve("modules.json"), State.class, 1, State::new, null);
        for (OdysseyModule m : all()) {
            if (state.get().enabled.getOrDefault(m.info().id(), true)) enable(m, false);
        }
    }

    public static void stop() {
        List<String> ids;
        synchronized (Modules.class) {
            ids = new ArrayList<>(ACTIVE.keySet());
        }
        for (String id : ids) get(id).ifPresent(m -> disable(m, false));
        server = null;
    }

    /** Enables a module live. Returns an error message, or null on success. */
    public static String enable(String id, String actor) {
        OdysseyModule m = REGISTERED.get(id);
        if (m == null) return "Unknown module '" + id + "'";
        if (isEnabled(id)) return m.info().name() + " is already enabled";
        if (!enable(m, true)) return m.info().name() + " failed to start; see the server log";
        Audit.record(actor, "library", "module.enable", id, "");
        return null;
    }

    /** Disables a module live. Returns an error message, or null on success. */
    public static String disable(String id, String actor) {
        OdysseyModule m = REGISTERED.get(id);
        if (m == null) return "Unknown module '" + id + "'";
        if (!isEnabled(id)) return m.info().name() + " is already disabled";
        disable(m, true);
        Audit.record(actor, "library", "module.disable", id, "");
        return null;
    }

    private static boolean enable(OdysseyModule m, boolean persist) {
        String id = m.info().id();
        ModuleContext ctx = new ModuleContext(m.info(), server);
        try {
            m.onEnable(ctx);
        } catch (Throwable e) {
            OdysseyLib.LOGGER.error("[Odyssey] Module '{}' failed to enable", id, e);
            ctx.close();
            Notify.admins("library", "Module " + m.info().name() + " failed to start: " + e + ". It stays disabled.", Severity.ALERT);
            return false;
        }
        synchronized (Modules.class) {
            ACTIVE.put(id, ctx);
        }
        if (persist) setPersisted(id, true);
        MinecraftForge.EVENT_BUS.post(new ModuleEnabledEvent(id));
        refreshCommands();
        return true;
    }

    private static void disable(OdysseyModule m, boolean persist) {
        String id = m.info().id();
        ModuleContext ctx;
        synchronized (Modules.class) {
            ctx = ACTIVE.remove(id);
        }
        if (ctx == null) return;
        try {
            m.onDisable();
        } catch (Throwable e) {
            OdysseyLib.LOGGER.error("[Odyssey] Module '{}' failed while disabling", id, e);
        }
        ctx.close();
        if (persist) setPersisted(id, false);
        MinecraftForge.EVENT_BUS.post(new ModuleDisabledEvent(id));
        refreshCommands();
    }

    private static void setPersisted(String id, boolean on) {
        if (state == null) return;
        state.get().enabled.put(id, on);
        state.markDirty();
    }

    private static void refreshCommands() {
        if (server == null) return;
        for (var p : server.getPlayerList().getPlayers()) server.getCommands().sendCommands(p);
    }
}
