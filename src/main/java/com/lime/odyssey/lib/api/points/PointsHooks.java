package com.lime.odyssey.lib.api.points;

import com.lime.odyssey.lib.api.audit.Audit;
import com.lime.odyssey.lib.api.command.OdysseyCommands;
import com.lime.odyssey.lib.api.msg.Msg;
import com.lime.odyssey.lib.api.perm.Perms;
import com.lime.odyssey.lib.api.sched.Scheduler;
import com.lime.odyssey.lib.api.ui.Hub;
import com.lime.odyssey.lib.api.ui.ItemBuilder;
import com.lime.odyssey.lib.api.ui.Screen;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Collection;

/** Wires Points and AFK into the server and adds {@code /odyssey points} and {@code /odyssey afk}. */
public final class PointsHooks {
    private int ticker;

    public PointsHooks() {
        OdysseyCommands.addLibraryCommand(Commands.literal("afk").requires(Perms.AFK.requirement()).executes(c -> {
            if (!(c.getSource().getEntity() instanceof ServerPlayer p)) return 0;
            Afk.toggle(p);
            return 1;
        }));
        OdysseyCommands.addLibraryCommand(Commands.literal("points").requires(Perms.POINTS.requirement())
                .executes(c -> {
                    if (!(c.getSource().getEntity() instanceof ServerPlayer p)) return 0;
                    Points.Account a = Points.account(p.getUUID());
                    Msg.send(p, "&6Odyssey Points: &f" + Msg.number(a.balance) + " &7(lifetime " + Msg.number(a.lifetimeEarned)
                            + ", streak " + a.streak + " day" + (a.streak == 1 ? "" : "s") + ")");
                    return 1;
                })
                .then(Commands.argument("player", GameProfileArgument.gameProfile()).requires(Perms.POINTS_ADMIN.requirement())
                        .executes(c -> {
                            for (GameProfile gp : profiles(c)) {
                                Msg.send(c.getSource(), null, "&f" + gp.getName() + "&7: &6" + Msg.number(Points.balance(gp.getId())) + " points");
                            }
                            return 1;
                        })
                        .then(Commands.literal("add").then(Commands.argument("amount", LongArgumentType.longArg(1)).executes(c -> change(c, 1))))
                        .then(Commands.literal("remove").then(Commands.argument("amount", LongArgumentType.longArg(1)).executes(c -> change(c, -1))))
                        .then(Commands.literal("set").then(Commands.argument("amount", LongArgumentType.longArg(0)).executes(c -> change(c, 0))))));
        Hub.header(2, p -> Points.running() ? new Screen.Btn(ItemBuilder.of(Items.SUNFLOWER)
                .name("&6Odyssey Points: &f" + Msg.number(Points.balance(p.getUUID())))
                .lore("&7Earned by playing (AFK time doesn't count)", "&7and from event rewards.").build(), null) : null);
    }

    private static Collection<GameProfile> profiles(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return GameProfileArgument.getGameProfiles(c, "player");
    }

    private static int change(CommandContext<CommandSourceStack> c, int mode) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        long amount = LongArgumentType.getLong(c, "amount");
        String actor = Audit.actor(c.getSource());
        int n = 0;
        for (GameProfile gp : profiles(c)) {
            if (mode > 0) {
                Points.add(gp.getId(), amount, "admin", "added by " + actor);
                Audit.record(actor, "library", "points.add", gp.getName(), String.valueOf(amount));
            } else if (mode < 0) {
                if (!Points.remove(gp.getId(), amount, "admin", "removed by " + actor)) {
                    Msg.error(c.getSource(), null, gp.getName() + " has only " + Points.balance(gp.getId()) + " points");
                    continue;
                }
                Audit.record(actor, "library", "points.remove", gp.getName(), String.valueOf(amount));
            } else {
                Points.set(gp.getId(), amount, actor);
            }
            Points.account(gp.getId()).name = gp.getName();
            Msg.success(c.getSource(), null, "&f" + gp.getName() + "&7 now has &6" + Msg.number(Points.balance(gp.getId())) + " points");
            n++;
        }
        return n;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onStarted(ServerStartedEvent e) {
        Points.start();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onStopping(ServerStoppingEvent e) {
        Points.stop();
        Afk.clear();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || Scheduler.server() == null || !Points.running()) return;
        if (++ticker % 20 == 0) Afk.tickSecond(Scheduler.server());
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Afk.onLogin(p);
        Points.onLogin(p);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Afk.onLogout(p);
    }

    @SubscribeEvent
    public void onTabName(PlayerEvent.TabListNameFormat e) {
        if (e.getEntity() instanceof ServerPlayer p) e.setDisplayName(Afk.tabName(p, e.getDisplayName()));
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent e) {
        ServerPlayer p = e.getPlayer();
        Scheduler.sync(() -> Afk.activity(p));
    }

    @SubscribeEvent
    public void onCommand(CommandEvent e) {
        if (e.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer p) {
            String input = e.getParseResults().getReader().getString();
            if (input.startsWith("odyssey afk") || input.equals("afk")) return;
            Afk.activity(p);
        }
    }

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !e.getLevel().isClientSide()) Afk.activity(p);
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Afk.activity(p);
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent e) {
        if (e.getPlayer() instanceof ServerPlayer p) Afk.activity(p);
    }
}
