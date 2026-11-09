package com.lime.odyssey.lib.api.data;

import com.lime.odyssey.lib.OdysseyLib;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

/** ItemStack <-> SNBT string. Keeps every tag (enchantments, names, modded data). */
public final class ItemCodec {
    private ItemCodec() {}

    public static String encode(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return stack.save(new CompoundTag()).toString();
    }

    public static ItemStack decode(String snbt) {
        if (snbt == null || snbt.isBlank()) return ItemStack.EMPTY;
        try {
            return ItemStack.of(TagParser.parseTag(snbt));
        } catch (Exception e) {
            OdysseyLib.LOGGER.error("[Odyssey] Could not read item data: {}", snbt, e);
            return ItemStack.EMPTY;
        }
    }
}
