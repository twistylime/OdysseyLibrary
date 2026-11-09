package com.lime.odyssey.lib.api.ui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The container behind a {@link Screen}. Looks like a vanilla chest to the client (no client mod needed).
 * Locked slots can never be picked up or placed into; only editable slots behave normally.
 * Blocked click types: CLONE, PICKUP_ALL everywhere; anything on a locked slot except PICKUP/QUICK_MOVE
 * (which become button clicks); everything in the player inventory unless the screen allows it.
 */
public class OdysseyMenu extends AbstractContainerMenu {
    final Screen screen;
    private final int topSize;

    OdysseyMenu(Screen screen, int id, Inventory inv) {
        super(type(screen.rows), id);
        this.screen = screen;
        this.topSize = screen.rows * 9;
        for (int r = 0; r < screen.rows; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new ScreenSlot(screen, screen.container, r * 9 + c, 8 + c * 18, 18 + r * 18));
        int off = (screen.rows - 4) * 18;
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 103 + r * 18 + off));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, 161 + off));
    }

    private static MenuType<?> type(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    @Override
    public boolean stillValid(Player player) {
        return !screen.closed && player.isAlive();
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (type == ClickType.CLONE || type == ClickType.PICKUP_ALL) return;
        if (slotId >= 0 && slotId < topSize) {
            if (screen.isEditable(slotId)) {
                if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE || type == ClickType.SWAP
                        || type == ClickType.QUICK_CRAFT || type == ClickType.THROW) {
                    super.clicked(slotId, button, type, player);
                    screen.onEditableChanged();
                }
                return;
            }
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) UiManager.queue(screen, new Click(sp, slotId, button, type));
            return;
        }
        if (!screen.allowPlayerInventory()) return;
        super.clicked(slotId, button, type, player);
        if (type == ClickType.QUICK_MOVE || type == ClickType.QUICK_CRAFT) screen.onEditableChanged();
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof ScreenSlot) || screen.isEditable(slot.index);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof ScreenSlot) || screen.isEditable(slot.index);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        if (index < topSize) {
            if (!screen.isEditable(index)) return ItemStack.EMPTY;
            moveItemStackTo(stack, topSize, slots.size(), true);
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
            return ItemStack.EMPTY;
        }
        if (IconTag.is(stack)) return ItemStack.EMPTY;
        for (int i = 0; i < topSize && !stack.isEmpty(); i++) {
            if (!screen.isEditable(i)) continue;
            Slot t = slots.get(i);
            ItemStack ts = t.getItem();
            if (!ts.isEmpty() && ItemStack.isSameItemSameTags(ts, stack) && t.mayPlace(stack)) {
                int max = Math.min(t.getMaxStackSize(stack), ts.getMaxStackSize());
                int move = Math.min(stack.getCount(), max - ts.getCount());
                if (move > 0) {
                    ts.grow(move);
                    stack.shrink(move);
                    t.setChanged();
                }
            }
        }
        for (int i = 0; i < topSize && !stack.isEmpty(); i++) {
            if (!screen.isEditable(i)) continue;
            Slot t = slots.get(i);
            if (!t.hasItem() && t.mayPlace(stack)) {
                int move = Math.min(stack.getCount(), t.getMaxStackSize(stack));
                t.set(stack.split(move));
            }
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return ItemStack.EMPTY;
    }

    @Override
    public void removed(Player player) {
        ItemStack carried = getCarried();
        if (!carried.isEmpty()) {
            setCarried(ItemStack.EMPTY);
            if (!IconTag.is(carried) && player instanceof ServerPlayer sp) UiManager.returnItem(sp, carried);
        }
        super.removed(player);
        screen.handleClose();
    }

    static final class ScreenSlot extends Slot {
        private final Screen screen;

        ScreenSlot(Screen screen, Container container, int idx, int x, int y) {
            super(container, idx, x, y);
            this.screen = screen;
        }

        @Override
        public boolean mayPickup(Player player) {
            return screen.isEditable(getContainerSlot());
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return screen.isEditable(getContainerSlot()) && screen.mayPlace(getContainerSlot(), stack);
        }
    }
}
