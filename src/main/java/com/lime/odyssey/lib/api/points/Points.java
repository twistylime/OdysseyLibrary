package com.lime.odyssey.lib.api.points;

import com.lime.odyssey.lib.api.audit.Audit;
import com.lime.odyssey.lib.api.config.ConfigFile;
import com.lime.odyssey.lib.api.config.Configs;
import com.lime.odyssey.lib.api.data.DataFile;
import com.lime.odyssey.lib.api.data.DataStore;
import com.lime.odyssey.lib.api.event.PointsChangedEvent;
import com.lime.odyssey.lib.api.msg.Msg;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Odyssey Points: the suite currency. Balances and a short transaction history per player
 * ({@code <world>/odyssey/library/points.json}). All methods must be called on the server thread.
 */
public final class Points {
    private static final int HISTORY = 200;
    private static DataFile<Data> data;
    private static ConfigFile<PointsConfig> config;

    private Points() {}

    public record Txn(long time, long delta, String source, String reason) {}

    public static final class Account {
        public String name = "";
        public long balance;
        public long lifetimeEarned;
        public long activeSecondsCarry;
        public String lastLoginDay = "";
        public int streak;
        public List<Txn> history = new ArrayList<>();
    }

    public static final class Data {
        public Map<UUID, Account> accounts = new HashMap<>();
    }

    public static void start() {
        config = Configs.open("library", "points", PointsConfig.class);
        data = DataStore.world("library", "points", Data.class, Data::new);
    }

    public static void stop() {
        data = null;
        config = null;
    }

    public static PointsConfig config() {
        return config == null ? new PointsConfig() : config.get();
    }

    public static boolean running() {
        return data != null;
    }

    public static long balance(UUID player) {
        Account a = data == null ? null : data.get().accounts.get(player);
        return a == null ? 0 : a.balance;
    }

    public static Account account(UUID player) {
        return data.get().accounts.computeIfAbsent(player, k -> new Account());
    }

    /** Every player who ever had an account (all players who joined since the Library was installed). */
    public static Map<UUID, Account> accounts() {
        return data == null ? Map.of() : data.get().accounts;
    }

    /** Adds points (negative amounts are rejected; use {@link #remove}). */
    public static void add(UUID player, long amount, String source, String reason) {
        if (amount <= 0 || data == null) return;
        Account a = account(player);
        a.balance = Math.addExact(a.balance, amount);
        a.lifetimeEarned += amount;
        log(player, a, amount, source, reason);
    }

    /** Removes points if the balance is sufficient. */
    public static boolean remove(UUID player, long amount, String source, String reason) {
        if (amount <= 0 || data == null) return false;
        Account a = account(player);
        if (a.balance < amount) return false;
        a.balance -= amount;
        log(player, a, -amount, source, reason);
        return true;
    }

    /** Admin correction. */
    public static void set(UUID player, long amount, String actor) {
        if (data == null || amount < 0) return;
        Account a = account(player);
        long delta = amount - a.balance;
        a.balance = amount;
        log(player, a, delta, "admin", "set by " + actor);
        Audit.record(actor, "library", "points.set", player.toString(), String.valueOf(amount));
    }

    private static void log(UUID player, Account a, long delta, String source, String reason) {
        a.history.add(0, new Txn(System.currentTimeMillis(), delta, source, reason));
        while (a.history.size() > HISTORY) a.history.remove(a.history.size() - 1);
        data.markDirty();
        MinecraftForge.EVENT_BUS.post(new PointsChangedEvent(player, delta, a.balance, source));
    }

    /** Called once per second for each active (non-AFK) player. */
    static void tickActive(ServerPlayer p) {
        PointsConfig c = config();
        if (!c.earningEnabled || data == null) return;
        Account a = account(p.getUUID());
        a.activeSecondsCarry++;
        long need = c.intervalMinutes * 60L;
        if (a.activeSecondsCarry >= need) {
            a.activeSecondsCarry -= need;
            if (c.pointsPerInterval > 0) {
                add(p.getUUID(), c.pointsPerInterval, "playtime", c.intervalMinutes + " active minutes");
                Msg.actionBar(p, "&6+" + c.pointsPerInterval + " Odyssey Points &7(" + c.intervalMinutes + " min played)");
            }
        }
        data.markDirty();
    }

    /** Daily login bonus with streak. */
    static void onLogin(ServerPlayer p) {
        if (data == null) return;
        PointsConfig c = config();
        Account a = account(p.getUUID());
        a.name = p.getGameProfile().getName();
        String today = LocalDate.now().toString();
        if (today.equals(a.lastLoginDay)) {
            data.markDirty();
            return;
        }
        String yesterday = LocalDate.now().minusDays(1).toString();
        a.streak = yesterday.equals(a.lastLoginDay) ? a.streak + 1 : 1;
        a.lastLoginDay = today;
        long bonus = c.dailyLoginBonus + c.streakBonusPerDay * (Math.min(a.streak, c.streakCap) - 1);
        if (c.earningEnabled && bonus > 0) {
            add(p.getUUID(), bonus, "daily", "day " + a.streak + " login");
            Msg.send(p, "&6+" + bonus + " Odyssey Points &7for logging in today" + (a.streak > 1 ? " &8(streak: " + a.streak + " days)" : ""));
        }
        data.markDirty();
    }
}
