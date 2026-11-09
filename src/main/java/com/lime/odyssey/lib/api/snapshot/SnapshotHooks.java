package com.lime.odyssey.lib.api.snapshot;

import com.lime.odyssey.lib.api.audit.Audit;
import com.lime.odyssey.lib.api.command.OdysseyCommands;
import com.lime.odyssey.lib.api.msg.Msg;
import com.lime.odyssey.lib.api.perm.Perms;
import com.lime.odyssey.lib.api.sched.Scheduler;
import com.lime.odyssey.lib.api.ui.Click;
import com.lime.odyssey.lib.api.ui.ConfirmScreen;
import com.lime.odyssey.lib.api.ui.ItemBuilder;
import com.lime.odyssey.lib.api.ui.PagedScreen;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** Wires player snapshots into the server; adds {@code /odyssey snapshots <player>} for admins. */
public final class SnapshotHooks {
    public SnapshotHooks() {
        OdysseyCommands.addLibraryCommand(Commands.literal("snapshots").requires(Perms.SNAPSHOT_ADMIN.requirement())
                .then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    if (!(c.getSource().getEntity() instanceof ServerPlayer admin)) return 0;
                    ServerPlayer target = EntityArgument.getPlayer(c, "player");
                    open(admin, target);
                    return 1;
                })));
    }

    private static void open(ServerPlayer admin, ServerPlayer target) {
        PlayerSnapshots.history(target.getUUID()).thenAccept(list -> Scheduler.sync(() -> {
            if (admin.hasDisconnected()) return;
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            PagedScreen<CompoundTag> s = new PagedScreen<>("library", admin, "&8Snapshots: " + target.getGameProfile().getName()) {
                @Override
                protected List<CompoundTag> entries() {
                    return list;
                }

                @Override
                protected ItemStack icon(CompoundTag t) {
                    boolean active = !target.hasDisconnected() && PlayerSnapshots.has(target, t.getString("id"));
                    int items = t.getList("inventory", Tag.TAG_COMPOUND).size();
                    return ItemBuilder.of(active ? Items.ENDER_CHEST : Items.CHEST).name("&e" + t.getString("reason"))
                            .lore("&7Taken: &f" + fmt.format(new Date(t.getLong("time"))), "&7By: &f" + t.getString("owner"),
                                    "&7Inventory stacks: &f" + items, t.contains("curios") ? "&7Includes Curios" : null,
                                    t.contains("origins") ? "&7Includes Origins" : null, active ? "&aActive (not restored yet)" : null,
                                    "", "&eClick to restore (current state is saved first)").build();
                }

                @Override
                protected void onClick(CompoundTag t, Click click) {
                    if (target.hasDisconnected()) {
                        Msg.send(admin, "&c" + target.getGameProfile().getName() + " is offline.");
                        return;
                    }
                    new ConfirmScreen("library", admin, "Restore this snapshot?", icon(t), () -> {
                        PlayerSnapshots.restoreFromHistory(target, t, Audit.actor(admin));
                        if (PlayerSnapshots.has(target, t.getString("id"))) PlayerSnapshots.discard(target, t.getString("id"));
                        Msg.send(admin, "&aRestored " + target.getGameProfile().getName() + ".");
                        Msg.send(target, "&eAn admin restored your inventory from a backup.");
                        admin.closeContainer();
                    }, this::open).open();
                }
            };
            s.open();
        }));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onStarted(ServerStartedEvent e) {
        PlayerSnapshots.start();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onStopping(ServerStoppingEvent e) {
        PlayerSnapshots.stop();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) PlayerSnapshots.onLogin(p);
    }
}
