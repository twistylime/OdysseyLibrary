package com.lime.odyssey.lib.api.ui;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Shared icons so every Odyssey screen looks the same. */
public final class Icons {
    private Icons() {}

    public static ItemStack filler() {
        return ItemBuilder.of(Items.GRAY_STAINED_GLASS_PANE).name(" ").hideAll().build();
    }

    public static ItemStack filler(Item pane) {
        return ItemBuilder.of(pane).name(" ").hideAll().build();
    }

    public static ItemStack back() {
        return ItemBuilder.of(Items.ARROW).name("&e← Back").lore("&7Return to the previous menu").build();
    }

    public static ItemStack close() {
        return ItemBuilder.of(Items.BARRIER).name("&cClose").build();
    }

    public static ItemStack previous(int page, int pages) {
        return ItemBuilder.of(Items.SPECTRAL_ARROW).name("&e← Previous page").lore("&7Page " + page + " of " + pages).build();
    }

    public static ItemStack next(int page, int pages) {
        return ItemBuilder.of(Items.SPECTRAL_ARROW).name("&eNext page →").lore("&7Page " + page + " of " + pages).build();
    }

    public static ItemStack toggle(String label, boolean on, String... lore) {
        ItemBuilder b = ItemBuilder.of(on ? Items.LIME_DYE : Items.GRAY_DYE)
                .name((on ? "&a" : "&c") + label + ": " + (on ? "ON" : "OFF"));
        b.lore(lore);
        b.lore("", "&eClick to turn " + (on ? "off" : "on"));
        return b.build();
    }

    public static ItemStack info(String title, String... lore) {
        return ItemBuilder.of(Items.BOOK).name("&e" + title).lore(lore).hideAll().build();
    }

    public static ItemStack confirm(String label) {
        return ItemBuilder.of(Items.LIME_CONCRETE).name("&a" + label).build();
    }

    public static ItemStack cancel(String label) {
        return ItemBuilder.of(Items.RED_CONCRETE).name("&c" + label).build();
    }
}
