package com.lime.odyssey.lib.api.ui;

import com.lime.odyssey.lib.api.msg.Msg;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Consumer;

/** Fluent builder for named, lored stacks (icons, rewards, kits). Text uses {@code &} colour codes. */
public final class ItemBuilder {
    private final ItemStack stack;

    private ItemBuilder(ItemStack stack) {
        this.stack = stack;
    }

    public static ItemBuilder of(Item item) {
        return new ItemBuilder(new ItemStack(item));
    }

    public static ItemBuilder of(ItemStack stack) {
        return new ItemBuilder(stack.copy());
    }

    public ItemBuilder count(int c) {
        stack.setCount(Math.max(1, Math.min(c, stack.getMaxStackSize())));
        return this;
    }

    public ItemBuilder name(String s) {
        stack.setHoverName(Msg.item(s));
        return this;
    }

    public ItemBuilder name(Component c) {
        stack.setHoverName(c);
        return this;
    }

    public ItemBuilder lore(String... lines) {
        CompoundTag display = stack.getOrCreateTagElement("display");
        ListTag lore = display.contains("Lore", Tag.TAG_LIST) ? display.getList("Lore", Tag.TAG_STRING) : new ListTag();
        for (String l : lines) {
            if (l == null) continue;
            for (String part : l.split("\n")) lore.add(StringTag.valueOf(Component.Serializer.toJson(Msg.item(part))));
        }
        display.put("Lore", lore);
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        return lore(lines.toArray(new String[0]));
    }

    public ItemBuilder glow(boolean glow) {
        if (!glow) return this;
        CompoundTag tag = stack.getOrCreateTag();
        ListTag ench = new ListTag();
        CompoundTag e = new CompoundTag();
        e.putString("id", "minecraft:unbreaking");
        e.putShort("lvl", (short) 1);
        ench.add(e);
        tag.put("Enchantments", ench);
        tag.putInt("HideFlags", tag.getInt("HideFlags") | 1);
        return this;
    }

    public ItemBuilder hideAll() {
        stack.getOrCreateTag().putInt("HideFlags", 255);
        return this;
    }

    public ItemBuilder skull(String playerName) {
        if (stack.getItem() == Items.PLAYER_HEAD && playerName != null && !playerName.isEmpty()) {
            stack.getOrCreateTag().putString("SkullOwner", playerName);
        }
        return this;
    }

    public ItemBuilder edit(Consumer<ItemStack> c) {
        c.accept(stack);
        return this;
    }

    public ItemStack build() {
        return stack;
    }
}
