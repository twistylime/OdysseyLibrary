package com.lime.odyssey.lib.api.mailbox;

import com.lime.odyssey.lib.api.audit.Audit;
import com.lime.odyssey.lib.api.command.OdysseyCommands;
import com.lime.odyssey.lib.api.msg.Msg;
import com.lime.odyssey.lib.api.perm.Perms;
import com.lime.odyssey.lib.api.sched.Scheduler;
import com.lime.odyssey.lib.api.soulbound.Soulbound;
import com.lime.odyssey.lib.api.ui.Hub;
import com.lime.odyssey.lib.api.ui.ItemBuilder;
import com.lime.odyssey.lib.api.ui.Screen;
import com.lime.odyssey.lib.api.ui.UiManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Wires the Mailbox, Rewards and soulbound items into the server; adds {@code /odyssey mailbox}. */
public final class MailboxHooks {
    private int ticker;

    public MailboxHooks() {
        OdysseyCommands.addLibraryCommand(Commands.literal("mailbox").requires(Perms.MAILBOX.requirement())
                .executes(c -> {
                    if (!(c.getSource().getEntity() instanceof ServerPlayer p)) return 0;
                    MailboxScreen.open(p);
                    return 1;
                })
                .then(Commands.literal("send").requires(Perms.MAILBOX_ADMIN.requirement())
                        .executes(c -> {
                            if (!(c.getSource().getEntity() instanceof ServerPlayer p)) return 0;
                            new MailComposeScreen(p, null, null).open();
                            return 1;
                        })
                        .then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(c -> {
                            if (!(c.getSource().getEntity() instanceof ServerPlayer p)) return 0;
                            GameProfile gp = GameProfileArgument.getGameProfiles(c, "player").iterator().next();
                            new MailComposeScreen(p, gp.getId(), gp.getName()).open();
                            return 1;
                        })))
                .then(Commands.literal("view").requires(Perms.MAILBOX_ADMIN.requirement())
                        .then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(c -> {
                            if (!(c.getSource().getEntity() instanceof ServerPlayer p)) return 0;
                            GameProfile gp = GameProfileArgument.getGameProfiles(c, "player").iterator().next();
                            new MailboxScreen(p, gp.getId(), gp.getName()).open();
                            return 1;
                        })))
                .then(Commands.literal("purge").requires(Perms.MAILBOX_ADMIN.requirement())
                        .then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(c -> {
                            int total = 0;
                            for (GameProfile gp : GameProfileArgument.getGameProfiles(c, "player")) {
                                int n = Mailbox.purge(gp.getId(), Audit.actor(c.getSource()));
                                total += n;
                                Msg.success(c.getSource(), null, "&7Removed &f" + n + " &7mail(s) of &f" + gp.getName());
                            }
                            return total;
                        }))));
        Hub.header(6, p -> {
            if (!Mailbox.running()) return null;
            int n = Mailbox.count(p.getUUID());
            int unseen = Mailbox.unseen(p.getUUID());
            return new Screen.Btn(ItemBuilder.of(n > 0 ? Items.CHEST_MINECART : Items.MINECART)
                    .name("&eMailbox" + (n > 0 ? " &7(" + n + ")" : ""))
                    .lore(n == 0 ? "&7No mail" : "&7" + n + " to claim" + (unseen > 0 ? ", &e" + unseen + " new" : ""),
                            "&7Rewards from events and staff", "", "&eClick to open")
                    .glow(unseen > 0).build(), c -> MailboxScreen.open(c.player()));
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onStarted(ServerStartedEvent e) {
        Mailbox.start();
        UiManager.setItemReturner((p, stack) -> Mailbox.returnItem(p.getUUID(), stack, "menu"));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onStopping(ServerStoppingEvent e) {
        UiManager.setItemReturner(null);
        Mailbox.stop();
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Scheduler.later("library", 40, () -> {
            if (!p.hasDisconnected()) Mailbox.onLogin(p);
        });
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || Scheduler.server() == null) return;
        if (++ticker % 40 != 0) return;
        for (ServerPlayer p : Scheduler.server().getPlayerList().getPlayers()) Soulbound.scan(p);
    }
}
