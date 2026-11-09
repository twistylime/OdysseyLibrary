package com.lime.odyssey.lib.api.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Weighted random rewards: {@link #picks} distinct entries are drawn when granted. */
public final class RewardPool {
    public List<Weighted> entries = new ArrayList<>();
    public int picks = 1;

    public static final class Weighted {
        public int weight = 1;
        public RewardSpec reward = new RewardSpec();
    }

    /** Draws and merges the picked rewards. */
    public RewardSpec roll(Random random) {
        List<Weighted> left = new ArrayList<>(entries);
        left.removeIf(w -> w.weight <= 0 || w.reward == null);
        RewardSpec out = new RewardSpec();
        for (int i = 0; i < Math.max(0, picks) && !left.isEmpty(); i++) {
            int total = 0;
            for (Weighted w : left) total += w.weight;
            int r = random.nextInt(total);
            Weighted chosen = left.get(left.size() - 1);
            for (Weighted w : left) {
                r -= w.weight;
                if (r < 0) {
                    chosen = w;
                    break;
                }
            }
            left.remove(chosen);
            RewardSpec c = chosen.reward.copy();
            out.items.addAll(c.items);
            out.points += c.points;
            out.xp += c.xp;
            out.commands.addAll(c.commands);
            out.custom.addAll(c.custom);
        }
        return out;
    }
}
