package com.lime.odyssey.lib.api.web;

import com.lime.odyssey.lib.api.module.Modules;
import com.lime.odyssey.lib.api.module.OdysseyModule;
import com.lime.odyssey.lib.api.notify.Notify;
import com.lime.odyssey.lib.api.perf.Perf;
import com.lime.odyssey.lib.api.sched.Scheduler;
import com.lime.odyssey.lib.api.ui.Screen;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * What Web Controls needs from the server, in one place. Every method may be called from any thread;
 * work is moved onto the server thread and results come back as futures.
 */
public final class WebBridge {
    private static final Map<String, ScreenFactory> SCREENS = new ConcurrentHashMap<>();

    private WebBridge() {}

    /** Creates a module screen for a viewer, for remote rendering. */
    public record ScreenFactory(String module, String title, Function<ServerPlayer, Screen> create) {}

    public record ModuleView(String id, String name, String category, String version, boolean enabled) {}

    public record PlayerView(String name, String uuid, String dimension, boolean afk) {}

    /** Modules register screens that Web Controls may open (id e.g. {@code bosses:main}). */
    public static void registerScreen(String id, ScreenFactory factory) {
        SCREENS.put(id, factory);
    }

    public static Map<String, ScreenFactory> screens() {
        return Map.copyOf(SCREENS);
    }

    public static CompletableFuture<List<ModuleView>> modules() {
        return onServer(s -> {
            List<ModuleView> out = new ArrayList<>();
            for (OdysseyModule m : Modules.all()) {
                out.add(new ModuleView(m.info().id(), m.info().name(), m.info().category().label(), m.info().version(),
                        Modules.isEnabled(m.info().id())));
            }
            return out;
        });
    }

    public static CompletableFuture<List<PlayerView>> onlinePlayers() {
        return onServer(s -> {
            List<PlayerView> out = new ArrayList<>();
            for (ServerPlayer p : s.getPlayerList().getPlayers()) {
                out.add(new PlayerView(p.getGameProfile().getName(), p.getUUID().toString(),
                        p.level().dimension().location().toString(), com.lime.odyssey.lib.api.points.Afk.isAfk(p)));
            }
            return out;
        });
    }

    public static CompletableFuture<Perf.Snapshot> stats() {
        return onServer(s -> Perf.snapshot());
    }

    /** Command completions for {@code partial} (without the leading slash), as seen by {@code source}. */
    public static CompletableFuture<List<String>> complete(Function<MinecraftServer, CommandSourceStack> source, String partial) {
        CompletableFuture<List<String>> out = new CompletableFuture<>();
        Scheduler.sync(() -> {
            MinecraftServer s = Scheduler.server();
            if (s == null) {
                out.complete(List.of());
                return;
            }
            ParseResults<CommandSourceStack> parse = s.getCommands().getDispatcher().parse(partial, source.apply(s));
            s.getCommands().getDispatcher().getCompletionSuggestions(parse).whenComplete((sug, err) -> {
                if (err != null || sug == null) {
                    out.complete(List.of());
                    return;
                }
                List<String> l = new ArrayList<>();
                for (Suggestion x : sug.getList()) l.add(x.apply(partial));
                out.complete(l);
            });
        });
        return out;
    }

    /** Runs a command (without the leading slash) as {@code source}; returns the command result. */
    public static CompletableFuture<Integer> execute(Function<MinecraftServer, CommandSourceStack> source, String command) {
        return onServer(s -> s.getCommands().performPrefixedCommand(source.apply(s), command));
    }

    public static void addNotificationListener(Consumer<Notify.Note> l) {
        Notify.addListener(l);
    }

    public static void removeNotificationListener(Consumer<Notify.Note> l) {
        Notify.removeListener(l);
    }

    private static <T> CompletableFuture<T> onServer(Function<MinecraftServer, T> f) {
        CompletableFuture<T> out = new CompletableFuture<>();
        Scheduler.sync(() -> {
            MinecraftServer s = Scheduler.server();
            if (s == null) {
                out.completeExceptionally(new IllegalStateException("server not running"));
                return;
            }
            try {
                out.complete(f.apply(s));
            } catch (Throwable t) {
                out.completeExceptionally(t);
            }
        });
        return out;
    }
}
