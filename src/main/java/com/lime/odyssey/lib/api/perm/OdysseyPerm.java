package com.lime.odyssey.lib.api.perm;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.permission.nodes.PermissionNode;

import java.util.function.Predicate;

/**
 * A permission node {@code odyssey.<module>.<action>}. Declare it with {@link Perms#declare} while the
 * mod is constructed (before server start). Checked through Forge's PermissionAPI, so a permission mod
 * can manage it; without one the {@link PermDefault} (vanilla OP level) applies.
 */
public final class OdysseyPerm {
    private final String node;
    private final PermDefault def;
    PermissionNode<Boolean> forgeNode;

    OdysseyPerm(String node, PermDefault def) {
        this.node = node;
        this.def = def;
    }

    public String node() {
        return node;
    }

    public PermDefault defaultLevel() {
        return def;
    }

    public boolean check(ServerPlayer p) {
        return Perms.check(this, p);
    }

    public boolean check(CommandSourceStack src) {
        if (src.getEntity() instanceof ServerPlayer p) return check(p);
        return src.hasPermission(Perms.levelFor(def));
    }

    /** For Brigadier {@code requires(...)}. */
    public Predicate<CommandSourceStack> requirement() {
        return this::check;
    }

    @Override
    public String toString() {
        return node;
    }
}
