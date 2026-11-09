package com.lime.odyssey.lib.internal;

import com.lime.odyssey.lib.api.command.OdysseyCommands;
import com.lime.odyssey.lib.api.event.ModuleDisabledEvent;
import com.lime.odyssey.lib.api.sched.Scheduler;
import com.lime.odyssey.lib.api.ui.ConfigEditorScreen;
import com.lime.odyssey.lib.api.ui.EditLocks;
import com.lime.odyssey.lib.api.ui.Hub;
import com.lime.odyssey.lib.api.ui.IconTag;
import com.lime.odyssey.lib.api.ui.UiManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Wires the UI framework into the server lifecycle. */
public final class UiHooks {
    public UiHooks() {
        OdysseyCommands.setHubOpener(Hub::open);
        OdysseyCommands.setConfigOpener((p, cfg) -> new ConfigEditorScreen(p, cfg).open());
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.START && Scheduler.server() != null) UiManager.tick();
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) IconTag.purge(p);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            UiManager.onLogout(p);
            EditLocks.releaseAll(p.getUUID());
        }
    }

    @SubscribeEvent
    public void onModuleDisabled(ModuleDisabledEvent e) {
        UiManager.closeOwnedBy(e.module());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onStopping(ServerStoppingEvent e) {
        UiManager.closeAll(e.getServer());
        EditLocks.clear();
    }
}
