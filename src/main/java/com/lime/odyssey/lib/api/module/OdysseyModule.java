package com.lime.odyssey.lib.api.module;

import com.lime.odyssey.lib.api.command.ModuleCommands;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * An Odyssey feature module. Register it from the mod constructor with {@link Modules#register}.
 * <ul>
 *   <li>{@link #commands} is called every time the command tree is built; the subtree is hidden and
 *       unusable while the module is disabled.</li>
 *   <li>{@link #onEnable} runs on server start (and when an admin enables the module). Everything created
 *       through the {@link ModuleContext} is torn down automatically on disable.</li>
 * </ul>
 */
public interface OdysseyModule {
    ModuleInfo info();

    default void commands(ModuleCommands commands) {}

    default void onEnable(ModuleContext ctx) {}

    default void onDisable() {}

    /** Extra hub buttons for this viewer (empty = the module icon opens {@link #openMain}). */
    default List<HubEntry> hubEntries(ServerPlayer viewer) {
        return List.of();
    }

    /** Opens the module's main screen from the hub. Return false if the viewer may not open it. */
    default boolean openMain(ServerPlayer viewer) {
        return false;
    }
}
