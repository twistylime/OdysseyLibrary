package com.lime.odyssey.lib.api.module;

import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * Static description of a module.
 *
 * @param id          lowercase id, also the {@code /odyssey <id>} literal and permission segment (e.g. {@code bosses})
 * @param name        display name (e.g. "Boss Events")
 * @param category    suite category
 * @param version     module version
 * @param description one line for the hub
 * @param icon        hub icon
 */
public record ModuleInfo(String id, String name, ModuleCategory category, String version, String description,
                         Supplier<ItemStack> icon) {
    public ModuleInfo {
        if (id == null || !id.matches("[a-z][a-z0-9_]{1,31}")) throw new IllegalArgumentException("Invalid module id: " + id);
    }
}
