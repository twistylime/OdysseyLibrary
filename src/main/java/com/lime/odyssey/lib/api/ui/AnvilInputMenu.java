package com.lime.odyssey.lib.api.ui;

import com.lime.odyssey.lib.api.msg.Msg;
import com.lime.odyssey.lib.api.sched.Scheduler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * An anvil used purely as a text box. Nothing in it can be moved; clicking the output slot submits.
 * Uses {@link ContainerLevelAccess#NULL}, so no anvil block is needed and nothing is ever dropped.
 */
public class AnvilInputMenu extends AnvilMenu {
    private final AnvilInput input;
    private String text;
    private boolean done;

    AnvilInputMenu(int id, Inventory inv, AnvilInput input) {
        super(id, inv, ContainerLevelAccess.NULL);
        this.input = input;
        ItemStack paper = IconTag.mark(new ItemStack(Items.PAPER));
        paper.setHoverName(Msg.item(input.initial));
        this.inputSlots.setItem(0, paper);
    }

    @Override
    public boolean setItemName(String name) {
        this.text = name;
        try {
            return super.setItemName(name);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId != RESULT_SLOT || done || !(player instanceof ServerPlayer sp)) return;
        done = true;
        String value = text == null ? input.initial : text;
        Scheduler.sync(() -> {
            try {
                input.onSubmit.accept(sp, value.trim());
            } finally {
                if (sp.containerMenu == this) sp.closeContainer();
            }
        });
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    @Override
    protected boolean mayPickup(Player player, boolean hasItem) {
        return false;
    }

    @Override
    public void removed(Player player) {
        inputSlots.clearContent();
        resultSlots.clearContent();
        setCarried(ItemStack.EMPTY);
        super.removed(player);
        if (!done && player instanceof ServerPlayer sp) {
            done = true;
            if (input.onCancel != null && !sp.hasDisconnected()) Scheduler.sync(() -> input.onCancel.accept(sp));
        }
    }
}
