package com.lime.odyssey.lib.api.ui;

import com.lime.odyssey.lib.api.data.ItemCodec;
import com.lime.odyssey.lib.api.msg.Msg;
import com.lime.odyssey.lib.api.perm.OdysseyPerm;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A server-side chest screen. The client just sees a vanilla chest; every click is validated here.
 * <ul>
 *   <li>Button actions run on the next server tick (never inside the click packet), at most one per tick,
 *       and only if the screen is still open.</li>
 *   <li>Only slots that {@link #isEditable} returns true for can hold real items; everything else is an
 *       icon marked with {@link IconTag} and can never be taken.</li>
 *   <li>Buttons may carry an {@link OdysseyPerm} that is re-checked on every click.</li>
 * </ul>
 * The same screen can be rendered remotely (Web Controls) through {@link #snapshot()} and {@link #remoteClick}.
 */
public abstract class Screen {
    protected final ServerPlayer viewer;
    protected final int rows;
    protected String title;
    protected final SimpleContainer container;
    private final Map<Integer, Consumer<Click>> actions = new HashMap<>();
    private final Map<Integer, OdysseyPerm> perms = new HashMap<>();
    private final String owner;
    OdysseyMenu menu;
    boolean closed = true;
    private boolean initialised;
    protected Runnable back;

    /**
     * @param owner module id; open screens of a module are closed when the module is disabled
     */
    protected Screen(String owner, ServerPlayer viewer, int rows, String title) {
        this.owner = owner;
        this.viewer = viewer;
        this.rows = Math.max(1, Math.min(6, rows));
        this.title = title;
        this.container = new SimpleContainer(this.rows * 9);
    }

    public String owner() {
        return owner;
    }

    public Screen back(Runnable back) {
        this.back = back;
        return this;
    }

    /** Draw all non-editable slots. Called on open and on every {@link #refresh()}. */
    protected abstract void draw();

    /** Called once before the first draw (put initial items into editable slots here). */
    protected void init() {}

    /** Slots players may put real items into / take real items out of. */
    public boolean isEditable(int slot) {
        return false;
    }

    /** Validates items placed into editable slots. */
    public boolean mayPlace(int slot, ItemStack stack) {
        return !IconTag.is(stack);
    }

    /** Whether the player may use their own inventory while this screen is open. */
    public boolean allowPlayerInventory() {
        return false;
    }

    /** After an editable slot changed. */
    public void onEditableChanged() {}

    /**
     * When the screen closes (ESC, logout, module disabled, opening another screen). Return items held in
     * editable slots here. Not called while the screen is {@link #suspend suspended}.
     */
    protected void onClose() {}

    /** A suspended screen will never be reopened (logout, server stop). Defaults to {@link #onClose}. */
    protected void onAbandon() {
        onClose();
    }

    /**
     * Call right before opening a sub-screen or prompt that will come back here. The screen keeps its
     * editable contents; if the player never returns, {@link #onAbandon} runs.
     */
    protected void suspend() {
        UiManager.suspend(viewer, this);
    }

    boolean isSuspended() {
        return UiManager.isSuspended(viewer, this);
    }

    /** Every second while open. */
    public void onSecond() {}

    public ServerPlayer viewer() {
        return viewer;
    }

    public String title() {
        return title;
    }

    public void open() {
        if (viewer.hasDisconnected()) return;
        if (!initialised) {
            initialised = true;
            init();
        }
        clearButtons();
        draw();
        UiManager.closeCurrentQuietly(viewer);
        UiManager.unsuspend(viewer, this);
        viewer.openMenu(new SimpleMenuProvider((id, inv, pl) -> {
            OdysseyMenu m = new OdysseyMenu(this, id, inv);
            this.menu = m;
            return m;
        }, Msg.text(fitTitle(title, 160))));
        closed = false;
        UiManager.opened(viewer, this);
    }

    /** Redraws in place (the title stays the same; use {@link #reopen} to change it). */
    public void refresh() {
        if (closed || menu == null) return;
        clearButtons();
        draw();
        menu.broadcastChanges();
    }

    public void reopen() {
        open();
    }

    public void close() {
        if (!closed && viewer.containerMenu == menu) viewer.closeContainer();
    }

    public boolean isOpen() {
        return !closed && menu != null && viewer.containerMenu == menu;
    }

    private void clearButtons() {
        actions.clear();
        perms.clear();
        for (int i = 0; i < container.getContainerSize(); i++) if (!isEditable(i)) container.setItem(i, ItemStack.EMPTY);
    }

    // ------------------------------------------------------------------ drawing helpers

    protected void set(int slot, ItemStack icon, Consumer<Click> action) {
        set(slot, icon, null, action);
    }

    /** A button that is only usable with {@code perm} (checked again on click). */
    protected void set(int slot, ItemStack icon, OdysseyPerm perm, Consumer<Click> action) {
        if (slot < 0 || slot >= container.getContainerSize() || isEditable(slot)) return;
        container.setItem(slot, IconTag.mark(icon.copy()));
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
        if (perm != null) perms.put(slot, perm);
        else perms.remove(slot);
    }

    protected void set(int slot, ItemStack icon) {
        set(slot, icon, null, null);
    }

    protected void fill(Item pane) {
        ItemStack s = Icons.filler(pane);
        for (int i = 0; i < container.getContainerSize(); i++) if (!isEditable(i) && container.getItem(i).isEmpty()) set(i, s);
    }

    protected void fillRow(int row, Item pane) {
        ItemStack s = Icons.filler(pane);
        for (int i = row * 9; i < row * 9 + 9; i++) if (!isEditable(i)) set(i, s);
    }

    protected void border(Item pane) {
        ItemStack s = Icons.filler(pane);
        for (int i = 0; i < rows * 9; i++) {
            int r = i / 9, c = i % 9;
            if ((r == 0 || r == rows - 1 || c == 0 || c == 8) && !isEditable(i)) set(i, s);
        }
    }

    public record Btn(ItemStack icon, Consumer<Click> action) {}

    /**
     * Column positions for {@code n} buttons centred in a row. Up to 4 are spaced one column apart; more are
     * packed. {@code inner} keeps them inside columns 1-7 (screens with a border).
     */
    protected static int[] centeredColumns(int n, boolean inner) {
        int max = inner ? 7 : 9;
        n = Math.max(0, Math.min(n, max));
        switch (n) {
            case 0: return new int[0];
            case 1: return new int[]{4};
            case 2: return new int[]{3, 5};
            case 3: return new int[]{2, 4, 6};
            case 4: return new int[]{1, 3, 5, 7};
            default:
        }
        int[] out = new int[n];
        if (n % 2 == 1) {
            int start = 4 - n / 2;
            for (int i = 0; i < n; i++) out[i] = start + i;
        } else {
            int half = n / 2, k = 0;
            for (int i = half; i >= 1; i--) out[k++] = 4 - i;
            for (int i = 1; i <= half; i++) out[k++] = 4 + i;
        }
        return out;
    }

    protected void layoutRow(int row, List<Btn> buttons, boolean inner) {
        int[] cols = centeredColumns(buttons.size(), inner);
        for (int i = 0; i < cols.length; i++) set(row * 9 + cols[i], buttons.get(i).icon(), buttons.get(i).action());
    }

    /** Back arrow when a back target exists, otherwise a close button. */
    protected void backButton(int slot) {
        if (back != null) set(slot, Icons.back(), c -> back.run());
        else set(slot, Icons.close(), c -> close());
    }

    protected void msg(String s) {
        Msg.send(viewer, s);
    }

    // ------------------------------------------------------------------ dispatch

    void dispatch(Click click) {
        if (closed || viewer.containerMenu != menu) return;
        runAction(click);
    }

    private void runAction(Click click) {
        Consumer<Click> a = actions.get(click.slot());
        if (a == null) return;
        OdysseyPerm p = perms.get(click.slot());
        if (p != null && !p.check(click.player())) {
            Msg.send(click.player(), "&cYou don't have permission to do that.");
            return;
        }
        Msg.sound(click.player(), SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.0f);
        a.accept(click);
    }

    void handleClose() {
        if (closed) return;
        closed = true;
        UiManager.closed(viewer, this);
        if (!isSuspended()) onClose();
    }

    void abandon() {
        try {
            onAbandon();
        } catch (Exception e) {
            com.lime.odyssey.lib.OdysseyLib.LOGGER.error("[Odyssey] Screen abandon failed ({})", getClass().getName(), e);
        }
    }

    // ------------------------------------------------------------------ remote rendering (Web Controls)

    /** One slot as seen by a remote renderer. */
    public record SlotView(int slot, String item, String name, boolean clickable, boolean editable) {}

    public record Snapshot(String title, int rows, List<SlotView> slots) {}

    /** Current contents; draws first if the screen was never opened. Server thread only. */
    public Snapshot snapshot() {
        if (!initialised) {
            initialised = true;
            init();
        }
        if (closed) {
            clearButtons();
            draw();
        }
        List<SlotView> out = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (s.isEmpty()) continue;
            Component name = s.getHoverName();
            out.add(new SlotView(i, ItemCodec.encode(s), name.getString(), actions.containsKey(i), isEditable(i)));
        }
        return new Snapshot(Msg.strip(Msg.color(title)), rows, out);
    }

    /** Performs a click coming from a remote renderer, as {@link #viewer}. */
    public void remoteClick(int slot, boolean right, boolean shift) {
        ClickType t = shift ? ClickType.QUICK_MOVE : ClickType.PICKUP;
        runAction(new Click(viewer, slot, right ? 1 : 0, t));
        if (closed) {
            clearButtons();
            draw();
        } else {
            refresh();
        }
    }

    // ------------------------------------------------------------------ title fitting

    private static int charWidth(char c) {
        if ("i!.,:;|".indexOf(c) >= 0) return 2;
        if ("l'`".indexOf(c) >= 0) return 3;
        if ("tI[] ".indexOf(c) >= 0) return 4;
        if ("fk<>(){}*\"".indexOf(c) >= 0) return 5;
        if ("@~".indexOf(c) >= 0) return 7;
        return 6;
    }

    /** Shortens a colour-coded title so it fits a chest title bar, ending with "..." when cut. */
    public static String fitTitle(String s, int maxPx) {
        if (s == null) return "";
        String codes = "0123456789abcdefklmnorABCDEFKLMNOR";
        int width = 0, cut = -1, limit = maxPx - 7;
        boolean bold = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < s.length() && codes.indexOf(s.charAt(i + 1)) >= 0) {
                char code = Character.toLowerCase(s.charAt(i + 1));
                if (code == 'l') bold = true;
                else if (code == 'r' || "0123456789abcdef".indexOf(code) >= 0) bold = false;
                i++;
                continue;
            }
            int w = charWidth(c) + (bold ? 1 : 0);
            if (width + w > limit && cut < 0) cut = i;
            width += w;
        }
        if (width <= maxPx || cut < 0) return s;
        return s.substring(0, cut).stripTrailing() + "...";
    }
}
