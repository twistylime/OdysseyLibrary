package com.lime.odyssey.lib.api.msg;

import com.lime.odyssey.lib.api.sched.Scheduler;
import com.lime.odyssey.lib.internal.Lib;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.Collection;

/**
 * Chat, title, action bar and sound helpers. Text uses {@code &} colour codes; every player-facing
 * message gets the Odyssey prefix (or a module prefix).
 */
public final class Msg {
    private static final String CODES = "0123456789abcdefklmnorABCDEFKLMNOR";

    private Msg() {}

    public static String color(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length() && CODES.indexOf(s.charAt(i + 1)) >= 0) sb.append('§');
            else sb.append(c);
        }
        return sb.toString();
    }

    public static String strip(String s) {
        return s == null ? "" : s.replaceAll("(?i)[&§][0-9a-fk-or]", "");
    }

    public static MutableComponent text(String s) {
        return Component.literal(color(s));
    }

    /** Non-italic text for item names and lore. */
    public static MutableComponent item(String s) {
        return Component.literal(color(s)).withStyle(st -> st.withItalic(false));
    }

    public static String prefix(String moduleName) {
        if (moduleName == null || moduleName.isEmpty()) return Lib.config().prefix;
        return Lib.config().modulePrefix.replace("{name}", moduleName);
    }

    public static MutableComponent prefixed(String moduleName, String s) {
        return text(prefix(moduleName) + s);
    }

    public static void send(ServerPlayer p, String moduleName, String s) {
        if (p != null) p.sendSystemMessage(prefixed(moduleName, s));
    }

    public static void send(ServerPlayer p, String s) {
        send(p, null, s);
    }

    public static void send(CommandSourceStack src, String moduleName, String s) {
        src.sendSystemMessage(prefixed(moduleName, s));
    }

    public static void send(ServerPlayer p, Component c) {
        if (p != null) p.sendSystemMessage(c);
    }

    public static void success(CommandSourceStack src, String moduleName, String s) {
        src.sendSuccess(() -> prefixed(moduleName, s), false);
    }

    public static void error(CommandSourceStack src, String moduleName, String s) {
        src.sendFailure(prefixed(moduleName, "&c" + s));
    }

    public static void broadcast(String moduleName, String s) {
        MinecraftServer server = Scheduler.server();
        if (server != null) server.getPlayerList().broadcastSystemMessage(prefixed(moduleName, s), false);
    }

    public static void broadcast(Component c) {
        MinecraftServer server = Scheduler.server();
        if (server != null) server.getPlayerList().broadcastSystemMessage(c, false);
    }

    public static void actionBar(ServerPlayer p, String s) {
        if (p != null) p.displayClientMessage(text(s), true);
    }

    public static void title(ServerPlayer p, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (p == null) return;
        p.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        p.connection.send(new ClientboundSetSubtitleTextPacket(text(subtitle == null ? "" : subtitle)));
        p.connection.send(new ClientboundSetTitleTextPacket(text(title == null ? "" : title)));
    }

    public static void title(Collection<ServerPlayer> ps, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        for (ServerPlayer p : ps) title(p, title, subtitle, fadeIn, stay, fadeOut);
    }

    public static void sound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        if (p != null) p.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
    }

    /** Clickable chat text that runs {@code command} (include the leading slash). */
    public static MutableComponent button(String label, String command, String hover) {
        return text(label).withStyle(st -> st.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, text(hover == null ? command : hover))));
    }

    /** Clickable chat text that fills the chat box with {@code command}. */
    public static MutableComponent suggest(String label, String command, String hover) {
        return text(label).withStyle(st -> st.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, text(hover == null ? command : hover))));
    }

    public static MutableComponent gray(String s) {
        return Component.literal(s).withStyle(ChatFormatting.GRAY);
    }

    /** Formats seconds as {@code h:mm:ss} or {@code mm:ss}. */
    public static String time(long totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        long h = totalSeconds / 3600, m = (totalSeconds % 3600) / 60, s = totalSeconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }

    public static String number(long n) {
        return String.format("%,d", n);
    }
}
