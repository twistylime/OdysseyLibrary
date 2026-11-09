package com.lime.odyssey.lib.api.mailbox;

import com.lime.odyssey.lib.api.audit.Audit;
import com.lime.odyssey.lib.api.msg.Msg;
import com.lime.odyssey.lib.api.perm.Perms;
import com.lime.odyssey.lib.api.ui.Click;
import com.lime.odyssey.lib.api.ui.ConfirmScreen;
import com.lime.odyssey.lib.api.ui.ItemBuilder;
import com.lime.odyssey.lib.api.ui.PagedScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * A mailbox. Owners click to claim; admins viewing someone else's mailbox can only inspect and delete
 * (shift-right-click), never claim for themselves.
 */
public class MailboxScreen extends PagedScreen<MailEntry> {
    private final UUID owner;
    private final String ownerName;
    private final boolean own;

    public MailboxScreen(ServerPlayer viewer, UUID owner, String ownerName) {
        super("library", viewer, viewer.getUUID().equals(owner) ? "&8✉ Mailbox" : "&8✉ Mailbox of " + ownerName);
        this.owner = owner;
        this.ownerName = ownerName;
        this.own = viewer.getUUID().equals(owner);
    }

    public static void open(ServerPlayer p) {
        new MailboxScreen(p, p.getUUID(), p.getGameProfile().getName()).open();
    }

    @Override
    protected void init() {
        if (own) Mailbox.markSeen(owner);
    }

    @Override
    protected List<MailEntry> entries() {
        List<MailEntry> l = new ArrayList<>(Mailbox.entries(owner));
        l.sort((a, b) -> Long.compare(b.created, a.created));
        return l;
    }

    @Override
    protected ItemStack icon(MailEntry e) {
        ItemStack base = e.items.isEmpty() ? new ItemStack(e.points > 0 ? Items.SUNFLOWER : Items.PAPER) : e.items.get(0).copy();
        ItemBuilder b = ItemBuilder.of(base).count(1).name("&e" + (e.title.isEmpty() ? "Mail" : e.title));
        b.lore("&7From: &f" + e.source, "&7Received: &f" + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(e.created)));
        if (!e.items.isEmpty()) {
            b.lore("", "&7Items:");
            int shown = 0;
            for (ItemStack s : e.items) {
                if (shown++ >= 6) {
                    b.lore("&8 ...and " + (e.items.size() - 6) + " more");
                    break;
                }
                b.lore("&8 • &f" + s.getCount() + "x " + s.getHoverName().getString());
            }
        }
        if (e.points > 0) b.lore("&6 + " + Msg.number(e.points) + " Odyssey Points");
        if (e.xp > 0) b.lore("&a + " + e.xp + " XP");
        if (!e.commands.isEmpty()) b.lore("&b + " + e.commands.size() + " special reward" + (e.commands.size() == 1 ? "" : "s"));
        if (own) b.lore("", "&eClick to claim");
        if (Perms.MAILBOX_ADMIN.check(viewer)) b.lore("&cShift-right-click to delete");
        return b.build();
    }

    @Override
    protected void onClick(MailEntry e, Click click) {
        if (click.shiftRight() && Perms.MAILBOX_ADMIN.check(viewer)) {
            new ConfirmScreen("library", viewer, "Delete this mail?", icon(e), () -> {
                Mailbox.delete(owner, e.id, Audit.actor(viewer));
                open();
            }, this::open).open();
            return;
        }
        if (!own) return;
        switch (Mailbox.claim(viewer, e.id)) {
            case CLAIMED -> Msg.send(viewer, "&aClaimed: &f" + e.title);
            case PARTIAL -> Msg.send(viewer, "&eYour inventory is full; the rest stays in your mailbox.");
            case NOTHING_FIT -> Msg.send(viewer, "&cYour inventory is full.");
            case GONE -> Msg.send(viewer, "&7That mail was already claimed.");
        }
        refresh();
    }

    @Override
    protected void drawFooter() {
        if (own && !entries().isEmpty()) {
            set(47, ItemBuilder.of(Items.HOPPER).name("&aClaim all").lore("&7Claims everything that fits").build(), c -> {
                int n = Mailbox.claimAll(viewer);
                Msg.send(viewer, n > 0 ? "&aClaimed " + n + " mail" + (n == 1 ? "" : "s") + "." : "&7Nothing could be claimed.");
                refresh();
            });
        }
        if (Perms.MAILBOX_ADMIN.check(viewer)) {
            set(51, ItemBuilder.of(Items.WRITABLE_BOOK).name("&cSend mail").lore("&7Send items or points to " + (own ? "players" : ownerName)).build(),
                    Perms.MAILBOX_ADMIN, c -> {
                        MailComposeScreen s = new MailComposeScreen(viewer, own ? null : owner, own ? null : ownerName);
                        s.back(this::open);
                        s.open();
                    });
        }
    }
}
