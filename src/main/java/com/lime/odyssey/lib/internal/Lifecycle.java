package com.lime.odyssey.lib.internal;

import com.lime.odyssey.lib.OdysseyLib;
import com.lime.odyssey.lib.api.audit.Audit;
import com.lime.odyssey.lib.api.command.OdysseyCommands;
import com.lime.odyssey.lib.api.config.ConfigWatcher;
import com.lime.odyssey.lib.api.config.Configs;
import com.lime.odyssey.lib.api.data.DataStore;
import com.lime.odyssey.lib.api.event.ConfigChangedEvent;
import com.lime.odyssey.lib.api.migrate.Migrations;
import com.lime.odyssey.lib.api.module.Modules;
import com.lime.odyssey.lib.api.notify.Notify;
import com.lime.odyssey.lib.api.perf.Perf;
import com.lime.odyssey.lib.api.perm.Perms;
import com.lime.odyssey.lib.api.region.EventRegions;
import com.lime.odyssey.lib.api.sched.Scheduler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;

/**
 * Starts and stops every Library service in a fixed order and drives the tick loop.
 * <pre>
 * about-to-start: scheduler, data store, library config, audit, notifications, perf
 * started (HIGH): points, mailbox, snapshots (service hooks)
 * started:        modules (+ their configs/data), aliases, config watcher, legacy migrations
 * stopping:       HIGHEST modules -> HIGH ui -> LOW points/mailbox -> LOWEST flush + scheduler shutdown
 * </pre>
 */
public final class Lifecycle {
    private int saveCountdown;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAboutToStart(ServerAboutToStartEvent e) {
        MinecraftServer server = e.getServer();
        Scheduler.start(server);
        DataStore.start(server);
        Lib.setConfig(Configs.open(Lib.MODULE, "library", LibraryConfig.class));
        Audit.start();
        Notify.start();
        Perf.start(server);
    }

    @SubscribeEvent
    public void onStarted(ServerStartedEvent e) {
        MinecraftServer server = e.getServer();
        Modules.start(server);
        Migrations.runAll(server);
        OdysseyCommands.startAliases(server);
        ConfigWatcher.start(DataStore.configDir());
        OdysseyLib.LOGGER.info("[Odyssey] Library ready with {} module(s)", Modules.all().size());
    }

    /** Modules stop first, while every service is still running. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onStoppingModules(ServerStoppingEvent e) {
        ConfigWatcher.stop();
        Modules.stop();
    }

    /** Services are stopped by their own hooks (HIGH..LOW); then everything is flushed. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onStopping(ServerStoppingEvent e) {
        OdysseyCommands.stopAliases();
        EventRegions.clear();
        DataStore.flushAll();
        Notify.stop();
        Audit.stop();
        Perf.stop();
        Scheduler.stop(Lib.config().shutdownFlushSeconds);
        Configs.closeAll();
    }

    @SubscribeEvent
    public void onStopped(ServerStoppedEvent e) {
        Lib.setConfig(null);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (Scheduler.server() == null) return;
        if (e.phase == TickEvent.Phase.START) {
            Scheduler.tick();
            return;
        }
        Perf.endTick();
        DataStore.processFlushRequests();
        if (--saveCountdown <= 0) {
            saveCountdown = Lib.config().saveIntervalSeconds * 20;
            DataStore.saveDirty();
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent e) {
        OdysseyCommands.register(e.getDispatcher(), e.getBuildContext());
    }

    @SubscribeEvent
    public void onPermissions(PermissionGatherEvent.Nodes e) {
        Perms.onGather(e);
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent e) {
        OdysseyCommands.onConfigChanged(e);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (Perms.isAdmin(p)) Notify.onAdminLogin(p);
    }
}
