package com.lime.odyssey.lib.api.module;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/** A button a module adds to the {@code /odyssey} hub. */
public record HubEntry(ItemStack icon, Consumer<ServerPlayer> open) {}
