package com.lime.odyssey.lib.api.command;

import com.lime.odyssey.lib.OdysseyLib;
import com.lime.odyssey.lib.api.audit.Audit;
import com.lime.odyssey.lib.api.config.ConfigFile;
import com.lime.odyssey.lib.api.config.Configs;
import com.lime.odyssey.lib.api.event.ConfigChangedEvent;
import com.lime.odyssey.lib.api.module.Modules;
import com.lime.odyssey.lib.api.module.OdysseyModule;
import com.lime.odyssey.lib.api.msg.Lang;
import com.lime.odyssey.lib.api.msg.Msg;
import com.lime.odyssey.lib.api.perf.Perf;
import com.lime.odyssey.lib.api.perm.Perms;
import com.lime.odyssey.lib.api.sched.Scheduler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Owner of the {@code /odyssey} command root. Modules contribute subtrees through
 * {@link OdysseyModule#commands}; other Library services contribute through {@link #addLibraryCommand}.
 */
public final class OdysseyCommands {
    public static final String ROOT = "odyssey";
    private static final List<LiteralArgumentBuilder<CommandSourceStack>> LIBRARY = new ArrayList<>();
    private static final Map<String, String> REGISTERED_ALIASES = new java.util.HashMap<>();
    private static ConfigFile<AliasConfig> aliases;
    private static Consumer<ServerPlayer> hubOpener;
    private static BiConsumer<ServerPlayer, ConfigFile<?>> configOpener;

    private OdysseyCommands() {}

    /** Library services add root subcommands here (before the command tree is built). */
    public static synchronized void addLibraryCommand(LiteralArgumentBuilder<CommandSourceStack> sub) {
        LIBRARY.add(sub);
    }

    public static void setHubOpener(Consumer<ServerPlayer> opener) {
        hubOpener = opener;
    }

    public static void setConfigOpener(BiConsumer<ServerPlayer, ConfigFile<?>> opener) {
        configOpener = opener;
    }

    /** RegisterCommandsEvent (server start and every /reload). */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(ROOT)
                .requires(Perms.HUB.requirement())
                .executes(OdysseyCommands::openHub);
        root.then(libraryCommands());
        root.then(Commands.literal("module").requires(Perms.ADMIN.requirement())
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Modules.all().stream().map(m -> m.info().id()), b))
                        .then(Commands.literal("enable").executes(c -> toggle(c, true)))
                        .then(Commands.literal("disable").executes(c -> toggle(c, false)))));
        root.then(Commands.literal("modules").requires(Perms.ADMIN.requirement()).executes(OdysseyCommands::listModules));
        root.then(configCommand());
        root.then(Commands.literal("perf").requires(Perms.ADMIN.requirement()).executes(OdysseyCommands::perf));
        root.then(Commands.literal("reload-messages").requires(Perms.ADMIN.requirement()).executes(c -> {
            Lang.reload();
            Msg.success(c.getSource(), null, "&aMessage files reloaded.");
            return 1;
        }));
        synchronized (OdysseyCommands.class) {
            for (LiteralArgumentBuilder<CommandSourceStack> sub : LIBRARY) root.then(sub);
        }
        for (OdysseyModule m : Modules.all()) {
            String id = m.info().id();
            try {
                m.commands(new ModuleCommands() {
                    @Override
                    public void add(LiteralArgumentBuilder<CommandSourceStack> sub) {
                        var prev = sub.getRequirement();
                        sub.requires(src -> Modules.isEnabled(id) && prev.test(src));
                        root.then(sub);
                    }

                    @Override
                    public CommandBuildContext buildContext() {
                        return buildContext;
                    }
                });
            } catch (Throwable e) {
                OdysseyLib.LOGGER.error("[Odyssey] Module '{}' failed to register commands", id, e);
            }
        }
        dispatcher.register(root);
        REGISTERED_ALIASES.clear();
        if (Scheduler.server() != null) registerAliases(dispatcher);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> libraryCommands() {
        return Commands.literal("audit").requires(Perms.ADMIN.requirement()).executes(c -> {
            var list = Audit.recent();
            Msg.send(c.getSource(), null, "&eLast " + Math.min(10, list.size()) + " admin actions:");
            for (int i = 0; i < Math.min(10, list.size()); i++) {
                var e = list.get(i);
                c.getSource().sendSystemMessage(Msg.text("&7" + new java.text.SimpleDateFormat("MM-dd HH:mm").format(new java.util.Date(e.time()))
                        + " &f" + e.actor().replaceAll("\\(.*\\)", "") + " &8" + e.module() + " &e" + e.action() + " &7" + e.target()));
            }
            return 1;
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> configCommand() {
        return Commands.literal("config").requires(Perms.ADMIN.requirement())
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Configs.all().stream().map(f -> stem(f)), b))
                        .executes(c -> {
                            ConfigFile<?> f = Configs.get(StringArgumentType.getString(c, "name"));
                            if (f == null) {
                                Msg.error(c.getSource(), null, "Unknown config.");
                                return 0;
                            }
                            if (configOpener != null && c.getSource().getEntity() instanceof ServerPlayer p) {
                                configOpener.accept(p, f);
                                return 1;
                            }
                            Msg.send(c.getSource(), null, "&e" + stem(f) + ":");
                            for (Field fld : f.fields()) {
                                c.getSource().sendSystemMessage(Msg.text("&7 " + fld.getName() + " = &f" + f.valueOf(fld.getName())));
                            }
                            return 1;
                        })
                        .then(Commands.argument("key", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    ConfigFile<?> f = Configs.get(StringArgumentType.getString(c, "name"));
                                    return f == null ? b.buildFuture() : SharedSuggestionProvider.suggest(
                                            f.fields().stream().filter(fl -> ConfigFile.isSimple(fl.getType())).map(Field::getName), b);
                                })
                                .then(Commands.argument("value", StringArgumentType.greedyString()).executes(c -> {
                                    ConfigFile<?> f = Configs.get(StringArgumentType.getString(c, "name"));
                                    if (f == null) {
                                        Msg.error(c.getSource(), null, "Unknown config.");
                                        return 0;
                                    }
                                    String key = StringArgumentType.getString(c, "key");
                                    List<String> errors = f.setFromString(key, StringArgumentType.getString(c, "value"), Audit.actor(c.getSource()));
                                    if (!errors.isEmpty()) {
                                        Msg.error(c.getSource(), null, String.join("; ", errors));
                                        return 0;
                                    }
                                    Msg.success(c.getSource(), null, "&a" + key + " = &f" + f.valueOf(key));
                                    return 1;
                                }))));
    }

    private static String stem(ConfigFile<?> f) {
        String n = f.path().getFileName().toString();
        return n.substring(0, n.length() - 5);
    }

    private static int openHub(CommandContext<CommandSourceStack> c) {
        if (c.getSource().getEntity() instanceof ServerPlayer p && hubOpener != null) {
            hubOpener.accept(p);
            return 1;
        }
        return listModules(c);
    }

    private static int listModules(CommandContext<CommandSourceStack> c) {
        Msg.send(c.getSource(), null, "&eInstalled Odyssey modules:");
        for (OdysseyModule m : Modules.all()) {
            boolean on = Modules.isEnabled(m.info().id());
            c.getSource().sendSystemMessage(Msg.text((on ? " &a● " : " &c● ") + "&f" + m.info().name()
                    + " &7(" + m.info().id() + ", " + m.info().category().label() + ", v" + m.info().version() + ")"));
        }
        return 1;
    }

    private static int toggle(CommandContext<CommandSourceStack> c, boolean on) {
        String id = StringArgumentType.getString(c, "id");
        String actor = Audit.actor(c.getSource());
        String err = on ? Modules.enable(id, actor) : Modules.disable(id, actor);
        if (err != null) {
            Msg.error(c.getSource(), null, err);
            return 0;
        }
        Msg.success(c.getSource(), null, on ? "&aEnabled &f" + id : "&cDisabled &f" + id);
        return 1;
    }

    private static int perf(CommandContext<CommandSourceStack> c) {
        Perf.Snapshot s = Perf.snapshot();
        Msg.send(c.getSource(), null, String.format("&eTPS &f%.1f &7| MSPT avg &f%.1f &7max &f%.1f", s.tps(), s.msptAvg(), s.msptMax()));
        int n = 0;
        for (Map.Entry<String, Perf.Stat> e : Perf.ranking()) {
            if (n++ >= 10) break;
            c.getSource().sendSystemMessage(Msg.text(String.format(" &f%s &7avg &f%.3f ms &7max &f%.2f ms &7over budget &f%d",
                    e.getKey(), e.getValue().avgMs(), e.getValue().maxMs(), e.getValue().overBudget())));
        }
        return 1;
    }

    // ------------------------------------------------------------------ aliases

    public static void startAliases(MinecraftServer server) {
        aliases = Configs.open("library", "aliases", AliasConfig.class);
        registerAliases(server.getCommands().getDispatcher());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) server.getCommands().sendCommands(p);
    }

    public static void stopAliases() {
        aliases = null;
        REGISTERED_ALIASES.clear();
    }

    /** Brings alias nodes in line with aliases.json: adds new ones, re-points or removes changed ones. */
    private static void registerAliases(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (aliases == null) return;
        Map<String, String> wanted = aliases.get().aliases;
        for (String alias : new ArrayList<>(REGISTERED_ALIASES.keySet())) {
            String target = wanted.get(alias);
            if (target == null || !target.equals(REGISTERED_ALIASES.get(alias))) {
                removeChild(dispatcher.getRoot(), alias);
                REGISTERED_ALIASES.remove(alias);
            }
        }
        for (Map.Entry<String, String> e : wanted.entrySet()) {
            String alias = e.getKey();
            if (REGISTERED_ALIASES.containsKey(alias)) continue;
            if (!alias.matches("[a-z0-9_-]{1,32}")) {
                OdysseyLib.LOGGER.warn("[Odyssey] Alias '{}' ignored: use lowercase letters, digits, - or _", alias);
                continue;
            }
            if (dispatcher.getRoot().getChild(alias) != null) {
                OdysseyLib.LOGGER.warn("[Odyssey] Alias /{} not registered: another command already uses that name", alias);
                continue;
            }
            CommandNode<CommandSourceStack> target = find(dispatcher, e.getValue());
            if (target == null) {
                OdysseyLib.LOGGER.warn("[Odyssey] Alias /{} -> /{}: target command not found", alias, e.getValue());
                continue;
            }
            LiteralCommandNode<CommandSourceStack> node = Commands.literal(alias)
                    .requires(src -> aliases != null && aliases.get().enabled && target.canUse(src))
                    .executes(target.getCommand())
                    .redirect(target)
                    .build();
            dispatcher.getRoot().addChild(node);
            REGISTERED_ALIASES.put(alias, e.getValue());
        }
    }

    /** Brigadier has no removal API; its maps are plain fields (Brigadier is not obfuscated). */
    @SuppressWarnings("unchecked")
    private static void removeChild(CommandNode<CommandSourceStack> parent, String name) {
        try {
            for (String field : new String[]{"children", "literals", "arguments"}) {
                Field f = CommandNode.class.getDeclaredField(field);
                f.setAccessible(true);
                ((Map<String, ?>) f.get(parent)).remove(name);
            }
        } catch (ReflectiveOperationException e) {
            OdysseyLib.LOGGER.warn("[Odyssey] Could not remove alias /{}; it stays until restart", name, e);
        }
    }

    private static CommandNode<CommandSourceStack> find(CommandDispatcher<CommandSourceStack> d, String path) {
        CommandNode<CommandSourceStack> node = d.getRoot();
        for (String part : path.trim().split("\\s+")) {
            node = node.getChild(part);
            if (node == null) return null;
        }
        return node == d.getRoot() ? null : node;
    }

    /** Re-registers aliases after aliases.json changed and resends the command tree. */
    public static void onConfigChanged(ConfigChangedEvent e) {
        MinecraftServer server = Scheduler.server();
        if (server == null || aliases == null || !e.config().equals("aliases")) return;
        registerAliases(server.getCommands().getDispatcher());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) server.getCommands().sendCommands(p);
    }
}
