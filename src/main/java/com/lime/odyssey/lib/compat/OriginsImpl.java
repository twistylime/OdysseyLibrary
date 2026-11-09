package com.lime.odyssey.lib.compat;

import io.github.edwinmindcraft.apoli.api.ApoliAPI;
import io.github.edwinmindcraft.apoli.api.component.IPowerContainer;
import io.github.edwinmindcraft.apoli.api.power.IInventoryPower;
import io.github.edwinmindcraft.apoli.api.power.configuration.ConfiguredPower;
import io.github.edwinmindcraft.origins.api.capabilities.IOriginContainer;
import io.github.edwinmindcraft.origins.api.origin.Origin;
import io.github.edwinmindcraft.origins.api.origin.OriginLayer;
import io.github.edwinmindcraft.origins.common.registry.OriginRegisters;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;

import java.util.ArrayList;
import java.util.List;

/** Only loaded when Origins is present (see {@link OriginsBridge}). */
final class OriginsImpl {
    private OriginsImpl() {}

    private static IOriginContainer origins(ServerPlayer p) {
        return IOriginContainer.get(p).resolve().orElse(null);
    }

    private static IPowerContainer powers(ServerPlayer p) {
        return IPowerContainer.get(p).resolve().orElse(null);
    }

    private static boolean isEmpty(ResourceKey<Origin> k) {
        return k == null || k.location().equals(OriginRegisters.EMPTY.getId());
    }

    static CompoundTag snapshot(ServerPlayer p) {
        IOriginContainer o = origins(p);
        IPowerContainer pw = powers(p);
        if (o == null || pw == null) return null;
        boolean any = false;
        for (ResourceKey<Origin> k : o.getOrigins().values()) if (!isEmpty(k)) any = true;
        if (!any) return null;
        CompoundTag t = new CompoundTag();
        t.put("origins", o.serializeNBT());
        t.put("powers", pw.writeToNbt(new CompoundTag()));
        return t;
    }

    static void strip(ServerPlayer p) {
        IOriginContainer o = origins(p);
        IPowerContainer pw = powers(p);
        if (o == null || pw == null) throw new IllegalStateException("no origin/power container");
        // Power inventories (e.g. Shulk storage) would spill into the player inventory when the power is
        // removed. Their contents are in the snapshot, so empty them first: nothing can drop or duplicate.
        emptyPowerInventories(p, pw);
        ResourceKey<Origin> empty = OriginRegisters.EMPTY.getKey();
        List<ResourceKey<OriginLayer>> layers = new ArrayList<>(o.getOrigins().keySet());
        for (ResourceKey<OriginLayer> layer : layers) if (!isEmpty(o.getOrigin(layer))) o.setOrigin(layer, empty);
        o.synchronize();
        ApoliAPI.synchronizePowerContainer(p);
        for (ResourceKey<OriginLayer> layer : layers) {
            if (!isEmpty(o.getOrigin(layer))) throw new IllegalStateException("layer " + layer.location() + " kept its origin");
        }
    }

    static void restore(ServerPlayer p, CompoundTag t) {
        IOriginContainer o = origins(p);
        IPowerContainer pw = powers(p);
        if (o == null || pw == null) throw new IllegalStateException("no origin/power container");
        if (!t.contains("origins") || !t.contains("powers")) throw new IllegalStateException("saved origin data is incomplete");
        emptyPowerInventories(p, pw);
        o.deserializeNBT(t.get("origins"));
        // Replaces every power with the saved ones (with their data) using onAdded only, so starting-item
        // powers do not hand out their items again.
        pw.readNbt(t.getCompound("powers"), true);
        try {
            o.synchronize();
            ApoliAPI.synchronizePowerContainer(p);
        } catch (Throwable ignored) {
            // Client sync only; the data is already restored.
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void emptyPowerInventories(ServerPlayer p, IPowerContainer pw) {
        for (Holder<ConfiguredPower<?, ?>> h : new ArrayList<>(pw.getPowers())) {
            if (!h.isBound()) continue;
            ConfiguredPower cp = h.value();
            if (cp.getFactory() instanceof IInventoryPower ip) {
                Container c = ip.getInventory(cp, p);
                if (c != null) {
                    c.clearContent();
                    c.setChanged();
                }
            }
        }
    }
}
