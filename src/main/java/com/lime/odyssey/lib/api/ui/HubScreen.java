package com.lime.odyssey.lib.api.ui;

import com.lime.odyssey.lib.api.audit.Audit;
import com.lime.odyssey.lib.api.config.Configs;
import com.lime.odyssey.lib.api.module.HubEntry;
import com.lime.odyssey.lib.api.module.Modules;
import com.lime.odyssey.lib.api.module.OdysseyModule;
import com.lime.odyssey.lib.api.msg.Msg;
import com.lime.odyssey.lib.api.perf.Perf;
import com.lime.odyssey.lib.api.perm.Perms;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Hub layout: row 1 header (player head + service buttons), rows 2-5 module icons (paged, 36 per page),
 * row 6 navigation and admin tools (only for admins).
 */
final class HubScreen extends Screen {
    private static final int PER_PAGE = 36;
    private int page;

    HubScreen(ServerPlayer viewer) {
        super("library", viewer, 6, "&8&lOdyssey");
    }

    private record Entry(ItemStack icon, java.util.function.Consumer<ServerPlayer> open) {}

    private List<Entry> entries() {
        boolean admin = Perms.isAdmin(viewer);
        List<Entry> out = new ArrayList<>();
        for (OdysseyModule m : Modules.all()) {
            boolean on = Modules.isEnabled(m.info().id());
            if (!on && !admin) continue;
            List<HubEntry> extra = on ? safeEntries(m) : List.of();
            if (!extra.isEmpty()) {
                for (HubEntry e : extra) out.add(new Entry(e.icon(), e.open()));
                continue;
            }
            ItemStack icon = safeIcon(m);
            ItemBuilder b = ItemBuilder.of(icon).name((on ? "&6" : "&8") + m.info().name())
                    .lore("&7" + m.info().description(), "&8" + m.info().category().label() + " • v" + m.info().version());
            if (!on) b.lore("", "&cDisabled");
            else b.lore("", "&eClick to open");
            out.add(new Entry(b.hideAll().build(), p -> {
                if (!Modules.isEnabled(m.info().id())) {
                    Msg.send(p, "&c" + m.info().name() + " is disabled.");
                    return;
                }
                if (!m.openMain(p)) Msg.send(p, "&7" + m.info().name() + " has no menu for you.");
            }));
        }
        return out;
    }

    private List<HubEntry> safeEntries(OdysseyModule m) {
        try {
            List<HubEntry> l = m.hubEntries(viewer);
            return l == null ? List.of() : l;
        } catch (Exception e) {
            com.lime.odyssey.lib.OdysseyLib.LOGGER.error("[Odyssey] Hub entries of '{}' failed", m.info().id(), e);
            return List.of();
        }
    }

    private ItemStack safeIcon(OdysseyModule m) {
        try {
            ItemStack s = m.info().icon().get();
            return s == null || s.isEmpty() ? new ItemStack(Items.ENCHANTED_BOOK) : s;
        } catch (Exception e) {
            return new ItemStack(Items.ENCHANTED_BOOK);
        }
    }

    @Override
    protected void draw() {
        fillRow(0, Items.BLACK_STAINED_GLASS_PANE);
        set(4, ItemBuilder.of(Items.PLAYER_HEAD).skull(viewer.getGameProfile().getName())
                .name("&6" + viewer.getGameProfile().getName()).lore("&7Welcome to Odyssey").build());
        for (Map.Entry<Integer, Function<ServerPlayer, Btn>> h : Hub.headers().entrySet()) {
            Btn b = h.getValue().apply(viewer);
            if (b != null) set(h.getKey(), b.icon(), b.action());
        }
        List<Entry> list = entries();
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE && page * PER_PAGE + i < list.size(); i++) {
            Entry e = list.get(page * PER_PAGE + i);
            set(9 + i, e.icon(), c -> e.open().accept(c.player()));
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
        set(49, Icons.close(), c -> close());
        if (Perms.isAdmin(viewer)) {
            set(46, ItemBuilder.of(Items.COMMAND_BLOCK).name("&cModules").lore("&7Enable or disable modules live").build(),
                    Perms.ADMIN, c -> modules().open());
            set(47, ItemBuilder.of(Items.WRITABLE_BOOK).name("&cSettings").lore("&7Every Odyssey config, editable live").build(),
                    Perms.ADMIN, c -> configs().open());
            set(51, ItemBuilder.of(Items.FILLED_MAP).name("&cAudit log").lore("&7Recent admin actions").build(),
                    Perms.ADMIN, c -> audit().open());
            set(52, ItemBuilder.of(Items.CLOCK).name("&cPerformance").lore("&7TPS, MSPT and module costs").build(),
                    Perms.ADMIN, c -> perf().open());
        }
    }

    private Screen modules() {
        PagedScreen<OdysseyModule> s = PagedScreen.of("library", viewer, "&8Modules", Modules::all, m -> {
            boolean on = Modules.isEnabled(m.info().id());
            return Icons.toggle(m.info().name(), on, "&7" + m.info().id() + " • " + m.info().category().label());
        }, (m, c) -> {
            boolean on = Modules.isEnabled(m.info().id());
            Screen current = UiManager.current(viewer);
            new ConfirmScreen("library", viewer, (on ? "Disable " : "Enable ") + m.info().name() + "?", null, () -> {
                String actor = Audit.actor(viewer);
                String err = on ? Modules.disable(m.info().id(), actor) : Modules.enable(m.info().id(), actor);
                Msg.send(viewer, err == null ? (on ? "&cDisabled " : "&aEnabled ") + m.info().name() : "&c" + err);
                if (current != null) current.open();
            }, () -> {
                if (current != null) current.open();
            }).open();
        });
        s.back(this::open);
        return s;
    }

    private Screen configs() {
        PagedScreen<com.lime.odyssey.lib.api.config.ConfigFile<?>> s = PagedScreen.of("library", viewer, "&8Settings",
                () -> new ArrayList<>(Configs.all()),
                f -> ItemBuilder.of(Items.PAPER).name("&e" + f.name()).lore("&7" + f.fields().size() + " settings", "&8module: " + f.module()).build(),
                (f, c) -> {
                    ConfigEditorScreen e = new ConfigEditorScreen(viewer, f);
                    Screen current = UiManager.current(viewer);
                    e.back(() -> {
                        if (current != null) current.open();
                    });
                    e.open();
                });
        s.back(this::open);
        return s;
    }

    private Screen audit() {
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm");
        PagedScreen<Audit.Entry> s = PagedScreen.of("library", viewer, "&8Audit log", Audit::recent,
                e -> ItemBuilder.of(Items.PAPER).name("&e" + e.action() + " &7(" + e.module() + ")")
                        .lore("&7By: &f" + e.actor().replaceAll("\\(.*\\)", ""), "&7When: &f" + fmt.format(new Date(e.time())),
                                "&7Target: &f" + e.target(), e.details().isEmpty() ? null : "&7" + e.details()).build(),
                (e, c) -> {});
        s.back(this::open);
        return s;
    }

    private Screen perf() {
        PagedScreen<Map.Entry<String, Perf.Stat>> s = new PagedScreen<>("library", viewer, "&8Performance") {
            @Override
            protected List<Map.Entry<String, Perf.Stat>> entries() {
                return Perf.ranking();
            }

            @Override
            protected ItemStack icon(Map.Entry<String, Perf.Stat> e) {
                return ItemBuilder.of(Items.REDSTONE).name("&e" + e.getKey())
                        .lore(String.format("&7Average: &f%.3f ms/tick", e.getValue().avgMs()),
                                String.format("&7Worst: &f%.2f ms", e.getValue().maxMs()),
                                "&7Over budget: &f" + e.getValue().overBudget() + " ticks").build();
            }

            @Override
            protected void onClick(Map.Entry<String, Perf.Stat> e, Click click) {}

            @Override
            protected void drawFooter() {
                Perf.Snapshot p = Perf.snapshot();
                set(47, ItemBuilder.of(Items.CLOCK).name(String.format("&eTPS %.1f", p.tps()))
                        .lore(String.format("&7MSPT avg &f%.1f &7max &f%.1f", p.msptAvg(), p.msptMax()), "&8Refreshes every second").build());
            }

            @Override
            public void onSecond() {
                refresh();
            }
        };
        s.back(this::open);
        return s;
    }
}
