package com.lime.odyssey.lib.api.reward;

import com.lime.odyssey.lib.api.msg.Msg;
import com.lime.odyssey.lib.api.ui.AnvilInput;
import com.lime.odyssey.lib.api.ui.Click;
import com.lime.odyssey.lib.api.ui.IconTag;
import com.lime.odyssey.lib.api.ui.Icons;
import com.lime.odyssey.lib.api.ui.ItemBuilder;
import com.lime.odyssey.lib.api.ui.PagedScreen;
import com.lime.odyssey.lib.api.ui.Screen;
import com.lime.odyssey.lib.api.ui.UiManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Consumer;

/**
 * Edits a {@link RewardSpec} template. The top four rows hold the reward items: admins place copies of
 * their own items there; when the editor closes the admin gets every item back (the template stores copies).
 * Points, XP and commands are set in the footer. {@code onSave} receives the new spec.
 */
public class RewardEditorScreen extends Screen {
    private static final int ITEM_SLOTS = 36;
    private final RewardSpec spec;
    private final Consumer<RewardSpec> onSave;

    public RewardEditorScreen(String owner, ServerPlayer viewer, String title, RewardSpec current, Consumer<RewardSpec> onSave) {
        super(owner, viewer, 6, title);
        this.spec = current == null ? new RewardSpec() : current.copy();
        this.onSave = onSave;
    }

    @Override
    public boolean isEditable(int slot) {
        return slot < ITEM_SLOTS;
    }

    @Override
    public boolean allowPlayerInventory() {
        return true;
    }

    @Override
    protected void init() {
        for (int i = 0; i < spec.items.size() && i < ITEM_SLOTS; i++) container.setItem(i, spec.items.get(i).copy());
    }

    @Override
    protected void draw() {
        fillRow(4, Items.BLACK_STAINED_GLASS_PANE);
        fillRow(5, Items.BLACK_STAINED_GLASS_PANE);
        set(37, Icons.info("Reward items", "&7Put items in the rows above.", "&7They are copied into the reward;",
                "&7you get your items back when you leave."));
        set(46, ItemBuilder.of(Items.SUNFLOWER).name("&6Odyssey Points: &f" + Msg.number(spec.points)).lore("&eClick to change").build(), c -> {
            suspend();
            AnvilInput.number(viewer, "&8Odyssey Points", spec.points, 0, 1_000_000_000L, (p, v) -> {
                spec.points = v;
                open();
            }, p -> open());
        });
        set(47, ItemBuilder.of(Items.EXPERIENCE_BOTTLE).name("&aXP: &f" + spec.xp).lore("&eClick to change").build(), c -> {
            suspend();
            AnvilInput.number(viewer, "&8Experience points", spec.xp, 0, 1_000_000, (p, v) -> {
                spec.xp = v.intValue();
                open();
            }, p -> open());
        });
        set(48, ItemBuilder.of(Items.COMMAND_BLOCK).name("&bCommands: &f" + spec.commands.size())
                .lore("&7Run as console when claimed.", "&7{player} = player name", "", "&eClick to edit").build(), c -> commands());
        backButton(49);
        set(51, Icons.confirm("Save reward"), c -> save());
        set(52, Icons.info("Summary", "&7" + spec.summary()));
    }

    private void readItems() {
        spec.items.clear();
        for (int i = 0; i < ITEM_SLOTS; i++) {
            ItemStack s = container.getItem(i);
            if (!s.isEmpty() && !IconTag.is(s)) spec.items.add(s.copy());
        }
    }

    private void save() {
        readItems();
        onSave.accept(spec.copy());
        msg("&aReward saved: &f" + spec.summary());
        if (back != null) back.run();
        else close();
    }

    private void commands() {
        readItems();
        suspend();
        PagedScreen<String> s = new PagedScreen<>(owner(), viewer, "&8Reward commands") {
            @Override
            protected List<String> entries() {
                return spec.commands;
            }

            @Override
            protected ItemStack icon(String cmd) {
                return ItemBuilder.of(Items.PAPER).name("&f/" + cmd).lore("&cClick to remove").build();
            }

            @Override
            protected void onClick(String cmd, Click click) {
                spec.commands.remove(cmd);
                refresh();
            }

            @Override
            protected void drawFooter() {
                set(47, ItemBuilder.of(Items.LIME_DYE).name("&aAdd command").build(), c ->
                        AnvilInput.text(viewer, "&8Command (no /)", "give {player} diamond 1", (p, t) -> {
                            if (!t.isBlank()) spec.commands.add(t.startsWith("/") ? t.substring(1) : t);
                            open();
                        }, p -> open()));
            }
        };
        s.back(this::open);
        s.open();
    }

    @Override
    protected void onClose() {
        for (int i = 0; i < ITEM_SLOTS; i++) {
            ItemStack s = container.getItem(i);
            if (!s.isEmpty()) {
                container.setItem(i, ItemStack.EMPTY);
                if (!IconTag.is(s)) UiManager.returnItem(viewer, s);
            }
        }
    }
}
