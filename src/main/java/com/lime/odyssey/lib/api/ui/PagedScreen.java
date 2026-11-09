package com.lime.odyssey.lib.api.ui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Consumer;

/**
 * A 6-row list screen: rows 1-5 show entries, row 6 holds navigation (previous / back / next) and up to
 * four extra buttons placed by {@link #drawFooter}.
 */
public abstract class PagedScreen<T> extends Screen {
    public static final int PER_PAGE = 45;
    protected int page;

    protected PagedScreen(String owner, ServerPlayer viewer, String title) {
        super(owner, viewer, 6, title);
    }

    protected abstract List<T> entries();

    protected abstract ItemStack icon(T entry);

    protected abstract void onClick(T entry, Click click);

    /** Footer slots 46, 47, 51, 52 are free for extra buttons. */
    protected void drawFooter() {}

    public int pages() {
        return Math.max(1, (entries().size() + PER_PAGE - 1) / PER_PAGE);
    }

    @Override
    protected void draw() {
        List<T> list = entries();
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < list.size(); i++) {
            T e = list.get(start + i);
            set(i, icon(e), c -> onClick(e, c));
        }
        fillRow(5, Items.BLACK_STAINED_GLASS_PANE);
        if (page > 0) set(45, Icons.previous(page, pages), c -> {
            page--;
            refresh();
        });
        if (page < pages - 1) set(53, Icons.next(page + 2, pages), c -> {
            page++;
            refresh();
        });
        backButton(49);
        drawFooter();
    }

    /** Convenience for simple lists without a subclass. */
    public static <T> PagedScreen<T> of(String owner, ServerPlayer viewer, String title, java.util.function.Supplier<List<T>> entries,
                                        java.util.function.Function<T, ItemStack> icon, java.util.function.BiConsumer<T, Click> onClick) {
        return new PagedScreen<>(owner, viewer, title) {
            @Override
            protected List<T> entries() {
                return entries.get();
            }

            @Override
            protected ItemStack icon(T entry) {
                return icon.apply(entry);
            }

            @Override
            protected void onClick(T entry, Click click) {
                onClick.accept(entry, click);
            }
        };
    }

    /** Picks an online player (head icons). */
    public static void pickPlayer(String owner, ServerPlayer viewer, String title, Consumer<ServerPlayer> onPick, Runnable back) {
        PagedScreen<ServerPlayer> s = of(owner, viewer, title,
                () -> viewer.server.getPlayerList().getPlayers().stream()
                        .sorted(java.util.Comparator.comparing(p -> p.getGameProfile().getName().toLowerCase())).toList(),
                p -> ItemBuilder.of(Items.PLAYER_HEAD).skull(p.getGameProfile().getName()).name("&f" + p.getGameProfile().getName()).build(),
                (p, c) -> onPick.accept(p));
        s.back(back);
        s.open();
    }
}
