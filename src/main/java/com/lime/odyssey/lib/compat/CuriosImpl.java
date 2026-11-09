package com.lime.odyssey.lib.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Only loaded when Curios is present (see {@link CuriosBridge}). */
final class CuriosImpl {
    private CuriosImpl() {}

    private static ICuriosItemHandler handler(LivingEntity e) {
        return CuriosApi.getCuriosInventory(e).resolve().orElse(null);
    }

    static CompoundTag save(ServerPlayer p) {
        CompoundTag out = new CompoundTag();
        ICuriosItemHandler h = handler(p);
        if (h == null) return out;
        for (Map.Entry<String, ICurioStacksHandler> e : h.getCurios().entrySet()) {
            CompoundTag slot = new CompoundTag();
            slot.put("stacks", saveStacks(e.getValue().getStacks()));
            slot.put("cosmetic", saveStacks(e.getValue().getCosmeticStacks()));
            out.put(e.getKey(), slot);
        }
        return out;
    }

    private static ListTag saveStacks(IDynamicStackHandler s) {
        ListTag l = new ListTag();
        for (int i = 0; i < s.getSlots(); i++) {
            ItemStack st = s.getStackInSlot(i);
            if (st.isEmpty()) continue;
            CompoundTag t = st.save(new CompoundTag());
            t.putInt("Slot", i);
            l.add(t);
        }
        return l;
    }

    static void clear(ServerPlayer p) {
        ICuriosItemHandler h = handler(p);
        if (h == null) return;
        for (ICurioStacksHandler s : h.getCurios().values()) {
            clearStacks(s.getStacks());
            clearStacks(s.getCosmeticStacks());
        }
    }

    private static void clearStacks(IDynamicStackHandler s) {
        for (int i = 0; i < s.getSlots(); i++) if (!s.getStackInSlot(i).isEmpty()) s.setStackInSlot(i, ItemStack.EMPTY);
    }

    static List<CuriosBridge.Leftover> allAsLeftovers(CompoundTag tag) {
        List<CuriosBridge.Leftover> out = new ArrayList<>();
        for (String id : tag.getAllKeys()) {
            CompoundTag slot = tag.getCompound(id);
            collect(null, id, false, slot.getList("stacks", Tag.TAG_COMPOUND), out);
            collect(null, id, true, slot.getList("cosmetic", Tag.TAG_COMPOUND), out);
        }
        return out;
    }

    static List<CuriosBridge.Leftover> restore(ServerPlayer p, CompoundTag tag) {
        ICuriosItemHandler h = handler(p);
        if (h == null) return allAsLeftovers(tag);
        clear(p);
        List<CuriosBridge.Leftover> out = new ArrayList<>();
        for (String id : tag.getAllKeys()) {
            CompoundTag slot = tag.getCompound(id);
            ICurioStacksHandler sh = h.getCurios().get(id);
            collect(sh == null ? null : sh.getStacks(), id, false, slot.getList("stacks", Tag.TAG_COMPOUND), out);
            collect(sh == null ? null : sh.getCosmeticStacks(), id, true, slot.getList("cosmetic", Tag.TAG_COMPOUND), out);
        }
        return out;
    }

    private static void collect(IDynamicStackHandler s, String id, boolean cosmetic, ListTag list, List<CuriosBridge.Leftover> out) {
        for (Tag t : list) {
            CompoundTag ct = (CompoundTag) t;
            ItemStack st = ItemStack.of(ct);
            if (st.isEmpty()) continue;
            int i = ct.getInt("Slot");
            if (s != null && i >= 0 && i < s.getSlots() && s.getStackInSlot(i).isEmpty()) s.setStackInSlot(i, st);
            else out.add(new CuriosBridge.Leftover(id, cosmetic, i, st));
        }
    }

    static boolean place(ServerPlayer p, String id, boolean cosmetic, int index, ItemStack st, boolean anyIndex) {
        ICuriosItemHandler h = handler(p);
        if (h == null) return false;
        ICurioStacksHandler sh = h.getCurios().get(id);
        if (sh == null) return false;
        IDynamicStackHandler s = cosmetic ? sh.getCosmeticStacks() : sh.getStacks();
        if (index >= 0 && index < s.getSlots() && s.getStackInSlot(index).isEmpty()) {
            s.setStackInSlot(index, st);
            return true;
        }
        if (!anyIndex) return false;
        for (int i = 0; i < s.getSlots(); i++) {
            if (s.getStackInSlot(i).isEmpty()) {
                s.setStackInSlot(i, st);
                return true;
            }
        }
        return false;
    }
}
