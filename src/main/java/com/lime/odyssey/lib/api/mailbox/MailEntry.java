package com.lime.odyssey.lib.api.mailbox;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One mailbox entry: items, Odyssey Points, XP and console commands, delivered together.
 * Commands run when the entry is claimed ({@code {player}} and {@code {uuid}} are replaced).
 */
public final class MailEntry {
    public UUID id = UUID.randomUUID();
    public long created = System.currentTimeMillis();
    /** Sending module id (or "admin"). */
    public String source = "";
    public String title = "";
    public List<ItemStack> items = new ArrayList<>();
    public long points;
    public int xp;
    public List<String> commands = new ArrayList<>();
    public boolean seen;

    public static MailEntry of(String source, String title) {
        MailEntry e = new MailEntry();
        e.source = source;
        e.title = title;
        return e;
    }

    public MailEntry item(ItemStack s) {
        if (s != null && !s.isEmpty()) items.add(s.copy());
        return this;
    }

    public MailEntry items(List<ItemStack> list) {
        for (ItemStack s : list) item(s);
        return this;
    }

    public MailEntry points(long p) {
        this.points += Math.max(0, p);
        return this;
    }

    public MailEntry xp(int x) {
        this.xp += Math.max(0, x);
        return this;
    }

    public MailEntry command(String c) {
        if (c != null && !c.isBlank()) commands.add(c);
        return this;
    }

    public boolean isEmpty() {
        items.removeIf(ItemStack::isEmpty);
        return items.isEmpty() && points <= 0 && xp <= 0 && commands.isEmpty();
    }

    public int itemCount() {
        int n = 0;
        for (ItemStack s : items) n += s.getCount();
        return n;
    }

    /** Deep copy with a new id (for sending the same content to several players). */
    public MailEntry copyForRecipient() {
        MailEntry e = new MailEntry();
        e.source = source;
        e.title = title;
        for (ItemStack s : items) e.items.add(s.copy());
        e.points = points;
        e.xp = xp;
        e.commands = new ArrayList<>(commands);
        return e;
    }
}
