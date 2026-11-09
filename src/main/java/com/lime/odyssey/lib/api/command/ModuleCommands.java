package com.lime.odyssey.lib.api.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;

/** Lets a module attach its subcommands under {@code /odyssey}. */
public interface ModuleCommands {
    /**
     * Adds {@code /odyssey <literal> ...}. The node is automatically hidden and unusable while the module is
     * disabled. Several literals may be added (e.g. Sweeper adds {@code sweeper} and {@code bin}).
     */
    void add(LiteralArgumentBuilder<CommandSourceStack> subcommand);

    /** Needed for item/block arguments. */
    CommandBuildContext buildContext();
}
