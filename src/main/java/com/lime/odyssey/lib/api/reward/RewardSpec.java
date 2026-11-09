package com.lime.odyssey.lib.api.reward;

import com.google.gson.JsonElement;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A reward definition stored by a module (e.g. "Top #1 damager"). Granting it always produces a
 * Mailbox entry. Custom parts are resolved by types registered with {@link Rewards#registerType}.
 */
public final class RewardSpec {
    public List<ItemStack> items = new ArrayList<>();
    public long points;
    public int xp;
    /** Console commands run on claim; {@code {player}} / {@code {uuid}} are replaced. */
    public List<String> commands = new ArrayList<>();
    public List<Custom> custom = new ArrayList<>();

    public static final class Custom {
        public String type = "";
        public JsonElement data;

        public Custom() {}

        public Custom(String type, JsonElement data) {
            this.type = type;
            this.data = data;
        }
    }

    public boolean isEmpty() {
        items.removeIf(ItemStack::isEmpty);
        return items.isEmpty() && points <= 0 && xp <= 0 && commands.isEmpty() && custom.isEmpty();
    }

    public RewardSpec copy() {
        RewardSpec r = new RewardSpec();
        for (ItemStack s : items) r.items.add(s.copy());
        r.points = points;
        r.xp = xp;
        r.commands = new ArrayList<>(commands);
        for (Custom c : custom) r.custom.add(new Custom(c.type, c.data == null ? null : c.data.deepCopy()));
        return r;
    }

    /** Short human summary, e.g. "3 items, 50 points". */
    public String summary() {
        List<String> parts = new ArrayList<>();
        int n = 0;
        for (ItemStack s : items) n += s.getCount();
        if (n > 0) parts.add(n + " item" + (n == 1 ? "" : "s"));
        if (points > 0) parts.add(points + " point" + (points == 1 ? "" : "s"));
        if (xp > 0) parts.add(xp + " XP");
        if (!commands.isEmpty()) parts.add(commands.size() + " command" + (commands.size() == 1 ? "" : "s"));
        if (!custom.isEmpty()) parts.add(custom.size() + " special");
        return parts.isEmpty() ? "nothing" : String.join(", ", parts);
    }
}
