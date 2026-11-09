package com.lime.odyssey.lib.api.mailbox;

import com.lime.odyssey.lib.api.audit.Audit;
import com.lime.odyssey.lib.api.msg.Msg;
import com.lime.odyssey.lib.api.perm.Perms;
import com.lime.odyssey.lib.api.points.Points;
import com.lime.odyssey.lib.api.ui.AnvilInput;
import com.lime.odyssey.lib.api.ui.ConfirmScreen;
import com.lime.odyssey.lib.api.ui.IconTag;
import com.lime.odyssey.lib.api.ui.Icons;
import com.lime.odyssey.lib.api.ui.ItemBuilder;
import com.lime.odyssey.lib.api.ui.PagedScreen;
import com.lime.odyssey.lib.api.ui.Screen;
import com.lime.odyssey.lib.api.ui.UiManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Admin compose screen: put items in the top four rows, set points and a title, pick the recipient
 * (one player or everyone who ever joined), send. Items are taken from the admin; each recipient gets a copy.
 * Leaving without sending returns the items.
 */
public class MailComposeScreen extends Screen {
    private static final int ITEM_SLOTS = 36;
    private UUID target;
    private String targetName;
    private long points;
    private String mailTitle = "A gift from the staff";
    private boolean sent;

    public MailComposeScreen(ServerPlayer viewer, UUID target, String targetName) {
        super("library", viewer, 6, "&8Send mail");
        this.target = target;
        this.targetName = targetName;
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
    protected void draw() {
        fillRow(4, Items.BLACK_STAINED_GLASS_PANE);
        fillRow(5, Items.BLACK_STAINED_GLASS_PANE);
        set(46, ItemBuilder.of(Items.PLAYER_HEAD).skull(targetName).name("&eRecipient: &f" + (target == null ? "everyone" : targetName))
                .lore(target == null ? "&7Every player who has joined (" + Points.accounts().size() + ")" : "&7One player",
                        "", "&eLeft-click: pick an online player", "&eRight-click: everyone").build(), c -> {
            if (c.right()) {
                target = null;
                targetName = null;
                refresh();
                return;
            }
            suspend();
            PagedScreen.pickPlayer("library", viewer, "&8Send to...", p -> {
                target = p.getUUID();
                targetName = p.getGameProfile().getName();
                open();
            }, this::open);
        });
        set(47, ItemBuilder.of(Items.SUNFLOWER).name("&6Points: &f" + Msg.number(points)).lore("&eClick to change").build(), c -> {
            suspend();
            AnvilInput.number(viewer, "&8Points", points, 0, 1_000_000_000L, (p, v) -> {
                points = v;
                open();
            }, p -> open());
        });
        set(48, ItemBuilder.of(Items.NAME_TAG).name("&eTitle: &f" + mailTitle).lore("&eClick to change").build(), c -> {
            suspend();
            AnvilInput.text(viewer, "&8Mail title", mailTitle, (p, s) -> {
                if (!s.isBlank()) mailTitle = s.length() > 48 ? s.substring(0, 48) : s;
                open();
            }, p -> open());
        });
        backButton(49);
        set(51, Icons.confirm("Send"), Perms.MAILBOX_ADMIN, c -> confirmSend());
    }

    private void confirmSend() {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < ITEM_SLOTS; i++) {
            ItemStack s = container.getItem(i);
            if (!s.isEmpty() && !IconTag.is(s)) items.add(s.copy());
        }
        if (items.isEmpty() && points <= 0) {
            msg("&cAdd items or points first.");
            return;
        }
        Set<UUID> recipients = new HashSet<>();
        if (target != null) recipients.add(target);
        else recipients.addAll(Points.accounts().keySet());
        if (recipients.isEmpty()) {
            msg("&cNo recipients.");
            return;
        }
        String who = target == null ? recipients.size() + " players" : targetName;
        suspend();
        new ConfirmScreen("library", viewer, "Send to " + who + "?", null, () -> {
            MailEntry template = MailEntry.of("staff", mailTitle).items(items).points(points);
            for (UUID r : recipients) Mailbox.send(r, template.copyForRecipient());
            for (int i = 0; i < ITEM_SLOTS; i++) container.setItem(i, ItemStack.EMPTY);
            sent = true;
            UiManager.forget(viewer, this);
            Audit.record(Audit.actor(viewer), "library", "mailbox.send", who,
                    items.size() + " stack(s), " + points + " points, \"" + mailTitle + "\"");
            Msg.send(viewer, "&aMail sent to " + who + ".");
            viewer.closeContainer();
        }, this::open).open();
    }

    @Override
    protected void onClose() {
        if (sent) return;
        for (int i = 0; i < ITEM_SLOTS; i++) {
            ItemStack s = container.getItem(i);
            if (!s.isEmpty()) {
                container.setItem(i, ItemStack.EMPTY);
                UiManager.returnItem(viewer, s);
            }
        }
    }
}
