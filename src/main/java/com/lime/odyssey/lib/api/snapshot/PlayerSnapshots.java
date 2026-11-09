package com.lime.odyssey.lib.api.snapshot;

import com.lime.odyssey.lib.OdysseyLib;
import com.lime.odyssey.lib.api.audit.Audit;
import com.lime.odyssey.lib.api.data.DataFile;
import com.lime.odyssey.lib.api.data.DataStore;
import com.lime.odyssey.lib.api.mailbox.Mailbox;
import com.lime.odyssey.lib.api.sched.Scheduler;
import com.lime.odyssey.lib.api.ui.IconTag;
import com.lime.odyssey.lib.compat.CuriosBridge;
import com.lime.odyssey.lib.compat.OriginsBridge;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Saves and restores a player's complete state: inventory, armour, offhand, cursor, crafting grid, XP,
 * health, food, effects, game mode, position, every Curios slot (incl. cosmetics and backpacks worn there)
 * and Origins layers with power data.
 * <p><b>Crash safety:</b> the active snapshot is stored inside the player's own save data
 * ({@code ForgeData.PlayerPersisted.odyssey_snapshots}), so it is always written together with the inventory it
 * replaced; after any crash both are consistent. A copy is also kept on disk for admins (30 days).</p>
 * <p>Restoring always saves the current state first, so a restore can never delete or duplicate items.
 * Anything that cannot be put back goes to the Mailbox. Nothing is ever dropped.</p>
 */
public final class PlayerSnapshots {
    private static final String KEY = "odyssey_snapshots";
    private static final int RETENTION_DAYS = 30;
    private static DataFile<Pending> pending;

    private PlayerSnapshots() {}

    /** What to clear after capturing. */
    public enum Clear { NOTHING, INVENTORY, EVERYTHING }

    public record Info(String id, String reason, String owner, long time) {}

    /** Snapshots to restore at the player's next login (player was offline when the restore was requested). */
    public static final class Pending {
        public Map<UUID, List<String>> restoreOnLogin = new HashMap<>();
    }

    public static void start() {
        pending = DataStore.world("library", "snapshot-pending", Pending.class, Pending::new);
        pruneHistory();
    }

    public static void stop() {
        pending = null;
    }

    // ------------------------------------------------------------------ capture

    /**
     * Captures the player's state and (optionally) clears it.
     *
     * @param owner  module id that owns the snapshot (shown to admins)
     * @param reason short description, e.g. "spleef #12"
     * @return snapshot id
     */
    public static String capture(ServerPlayer p, String owner, String reason, Clear clear) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        CompoundTag snap = build(p);
        snap.putString("id", id);
        snap.putString("owner", owner);
        snap.putString("reason", reason);
        snap.putLong("time", System.currentTimeMillis());
        store(p).put(id, snap);
        writeHistory(p.getUUID(), snap.copy());
        if (clear != Clear.NOTHING) clear(p, clear == Clear.EVERYTHING, snap.contains("origins"));
        Audit.record("library", owner, "snapshot.capture", p.getGameProfile().getName(), id + " " + reason);
        return id;
    }

    private static CompoundTag build(ServerPlayer p) {
        CompoundTag t = new CompoundTag();
        t.put("inventory", p.getInventory().save(new ListTag()));
        ItemStack carried = p.containerMenu.getCarried();
        if (!carried.isEmpty() && !IconTag.is(carried)) t.put("carried", carried.save(new CompoundTag()));
        ListTag craft = new ListTag();
        CraftingContainer grid = p.inventoryMenu.getCraftSlots();
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack s = grid.getItem(i);
            if (!s.isEmpty()) craft.add(s.save(new CompoundTag()));
        }
        t.put("craft", craft);
        t.putInt("xpLevel", p.experienceLevel);
        t.putFloat("xpProgress", p.experienceProgress);
        t.putInt("xpTotal", p.totalExperience);
        t.putFloat("health", p.getHealth());
        t.putInt("food", p.getFoodData().getFoodLevel());
        t.putFloat("saturation", p.getFoodData().getSaturationLevel());
        ListTag effects = new ListTag();
        for (MobEffectInstance e : p.getActiveEffects()) effects.add(e.save(new CompoundTag()));
        t.put("effects", effects);
        t.putInt("gamemode", p.gameMode.getGameModeForPlayer().getId());
        t.putString("dim", p.level().dimension().location().toString());
        t.putDouble("x", p.getX());
        t.putDouble("y", p.getY());
        t.putDouble("z", p.getZ());
        t.putFloat("yaw", p.getYRot());
        t.putFloat("pitch", p.getXRot());
        CompoundTag curios = CuriosBridge.save(p);
        if (curios != null) t.put("curios", curios);
        CompoundTag origins = OriginsBridge.snapshot(p);
        if (origins != null) t.put("origins", origins);
        return t;
    }

    private static void clear(ServerPlayer p, boolean everything, boolean hadOrigins) {
        p.getInventory().clearContent();
        p.containerMenu.setCarried(ItemStack.EMPTY);
        CraftingContainer grid = p.inventoryMenu.getCraftSlots();
        for (int i = 0; i < grid.getContainerSize(); i++) grid.setItem(i, ItemStack.EMPTY);
        CuriosBridge.clear(p);
        if (everything) {
            p.removeAllEffects();
            if (hadOrigins) OriginsBridge.strip(p);
        }
        p.inventoryMenu.broadcastChanges();
    }

    // ------------------------------------------------------------------ restore

    public static boolean has(ServerPlayer p, String id) {
        return store(p).contains(id, Tag.TAG_COMPOUND);
    }

    public static List<Info> active(ServerPlayer p) {
        List<Info> out = new ArrayList<>();
        CompoundTag s = store(p);
        for (String id : s.getAllKeys()) {
            CompoundTag t = s.getCompound(id);
            out.add(new Info(id, t.getString("reason"), t.getString("owner"), t.getLong("time")));
        }
        return out;
    }

    /**
     * Restores an active snapshot (replacing the current state, which is saved to history first).
     *
     * @param teleport also return the player to where the snapshot was taken
     */
    public static boolean restore(ServerPlayer p, String id, boolean teleport) {
        CompoundTag snap = store(p).getCompound(id);
        if (snap.isEmpty()) return false;
        apply(p, snap, teleport, "restore " + id);
        store(p).remove(id);
        return true;
    }

    /** Restores a snapshot from the on-disk history (admin tool). The current state is saved first. */
    public static void restoreFromHistory(ServerPlayer p, CompoundTag snap, String actor) {
        apply(p, snap, false, "history restore by " + actor);
        Audit.record(actor, "library", "snapshot.restore", p.getGameProfile().getName(), snap.getString("id"));
    }

    /** Discards an active snapshot without restoring (e.g. the event ended normally and kept the state). */
    public static void discard(ServerPlayer p, String id) {
        store(p).remove(id);
    }

    /** Queues a restore for a player who is offline; it runs at their next login. */
    public static void restoreOnLogin(UUID player, String id) {
        if (pending == null) return;
        pending.get().restoreOnLogin.computeIfAbsent(player, k -> new ArrayList<>()).add(id);
        pending.markDirty();
    }

    private static void apply(ServerPlayer p, CompoundTag snap, boolean teleport, String why) {
        CompoundTag before = build(p);
        before.putString("id", "pre-" + snap.getString("id"));
        before.putString("owner", "library");
        before.putString("reason", "state before " + why);
        before.putLong("time", System.currentTimeMillis());
        writeHistory(p.getUUID(), before);

        clear(p, true, true);
        p.getInventory().load(snap.getList("inventory", Tag.TAG_COMPOUND));
        List<ItemStack> extra = new ArrayList<>();
        if (snap.contains("carried")) extra.add(ItemStack.of(snap.getCompound("carried")));
        for (Tag t : snap.getList("craft", Tag.TAG_COMPOUND)) extra.add(ItemStack.of((CompoundTag) t));

        p.setExperienceLevels(snap.getInt("xpLevel"));
        p.setExperiencePoints(Math.round(snap.getFloat("xpProgress") * p.getXpNeededForNextLevel()));
        p.totalExperience = snap.getInt("xpTotal");
        p.getFoodData().setFoodLevel(snap.getInt("food"));
        p.getFoodData().setSaturation(snap.getFloat("saturation"));
        for (Tag t : snap.getList("effects", Tag.TAG_COMPOUND)) {
            MobEffectInstance e = MobEffectInstance.load((CompoundTag) t);
            if (e != null) p.addEffect(e);
        }
        GameType mode = GameType.byId(snap.getInt("gamemode"));
        if (p.gameMode.getGameModeForPlayer() != mode) p.setGameMode(mode);
        if (snap.contains("origins")) OriginsBridge.restore(p, snap.getCompound("origins"));
        p.setHealth(Math.max(1f, Math.min(p.getMaxHealth(), snap.getFloat("health"))));
        if (teleport) teleport(p, snap);

        List<CuriosBridge.Leftover> curioLeft = CuriosBridge.restore(p, snap.getCompound("curios"));
        p.inventoryMenu.broadcastChanges();
        UUID uuid = p.getUUID();
        // Curio slots granted by other curios (and backpacks in them) only exist after Curios rebuilds slots.
        Scheduler.later("library", 20, () -> finishRestore(uuid, curioLeft, extra));
    }

    private static void finishRestore(UUID uuid, List<CuriosBridge.Leftover> curioLeft, List<ItemStack> extra) {
        ServerPlayer p = Scheduler.server() == null ? null : Scheduler.server().getPlayerList().getPlayer(uuid);
        List<ItemStack> rest = new ArrayList<>(extra);
        for (CuriosBridge.Leftover l : curioLeft) {
            if (p == null || !CuriosBridge.place(p, l, false) && !CuriosBridge.place(p, l, true)) rest.add(l.stack());
        }
        for (ItemStack s : rest) {
            if (s.isEmpty()) continue;
            if (p != null && p.getInventory().add(s) && s.isEmpty()) continue;
            Mailbox.returnItem(uuid, s, "restore");
        }
        if (p != null) p.inventoryMenu.broadcastChanges();
    }

    private static void teleport(ServerPlayer p, CompoundTag snap) {
        ResourceLocation dim = ResourceLocation.tryParse(snap.getString("dim"));
        if (dim == null) return;
        ServerLevel level = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        if (level == null) return;
        p.teleportTo(level, snap.getDouble("x"), snap.getDouble("y"), snap.getDouble("z"), snap.getFloat("yaw"), snap.getFloat("pitch"));
    }

    /** Applies queued restores at login. */
    public static void onLogin(ServerPlayer p) {
        if (pending == null) return;
        List<String> ids = pending.get().restoreOnLogin.remove(p.getUUID());
        if (ids == null) return;
        pending.markDirty();
        for (String id : ids) {
            if (!restore(p, id, true)) OdysseyLib.LOGGER.warn("[Odyssey] Queued snapshot {} for {} no longer exists", id, p.getGameProfile().getName());
        }
    }

    private static CompoundTag store(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(KEY, Tag.TAG_COMPOUND)) persisted.put(KEY, new CompoundTag());
        return persisted.getCompound(KEY);
    }

    // ------------------------------------------------------------------ history on disk

    public static Path historyDir(UUID player) {
        return DataStore.worldDir("library").resolve("snapshots").resolve(player.toString());
    }

    private static void writeHistory(UUID player, CompoundTag snap) {
        Path file = historyDir(player).resolve(snap.getLong("time") + "-" + snap.getString("id") + ".dat");
        Scheduler.async(() -> {
            try {
                Files.createDirectories(file.getParent());
                NbtIo.writeCompressed(snap, file.toFile());
            } catch (IOException e) {
                OdysseyLib.LOGGER.error("[Odyssey] Could not write snapshot history {}", file, e);
            }
        });
    }

    /** Reads the history of one player off-thread. */
    public static java.util.concurrent.CompletableFuture<List<CompoundTag>> history(UUID player) {
        Path dir = historyDir(player);
        return Scheduler.async(() -> {
            List<CompoundTag> out = new ArrayList<>();
            if (!Files.isDirectory(dir)) return out;
            try (Stream<Path> s = Files.list(dir)) {
                for (Path f : s.filter(x -> x.toString().endsWith(".dat")).sorted((a, b) -> b.getFileName().compareTo(a.getFileName())).toList()) {
                    try {
                        out.add(NbtIo.readCompressed(f.toFile()));
                    } catch (IOException e) {
                        OdysseyLib.LOGGER.warn("[Odyssey] Unreadable snapshot {}", f, e);
                    }
                }
            }
            return out;
        });
    }

    private static void pruneHistory() {
        Path root = DataStore.worldDir("library").resolve("snapshots");
        long limit = Instant.now().minusSeconds(RETENTION_DAYS * 86400L).toEpochMilli();
        Scheduler.async(() -> {
            if (!Files.isDirectory(root)) return;
            try (Stream<Path> s = Files.walk(root, 2)) {
                s.filter(f -> f.toString().endsWith(".dat")).forEach(f -> {
                    try {
                        String n = f.getFileName().toString();
                        long t = Long.parseLong(n.substring(0, n.indexOf('-')));
                        if (t < limit) Files.deleteIfExists(f);
                    } catch (Exception ignored) {
                    }
                });
            } catch (IOException e) {
                OdysseyLib.LOGGER.warn("[Odyssey] Could not prune snapshot history", e);
            }
        });
    }
}
