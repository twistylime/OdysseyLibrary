package com.lime.odyssey.lib.api.points;

import com.lime.odyssey.lib.api.event.PlayerAfkChangedEvent;
import com.lime.odyssey.lib.api.msg.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AFK detection. A player is AFK after {@code afkMinutes} without moving (0.5 blocks), turning (3 degrees),
 * chatting, running commands or interacting, or after toggling {@code /odyssey afk}. AFK players earn no
 * points and get a tab-list suffix. Any activity (including moving or turning) ends AFK. Riding counts as
 * movement only when the player turns.
 */
public final class Afk {
    private static final Map<UUID, State> STATES = new HashMap<>();

    private Afk() {}

    private static final class State {
        long lastActive = System.currentTimeMillis();
        double x, y, z;
        float yaw, pitch;
        boolean afk;
        boolean manual;
    }

    public static boolean isAfk(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.afk;
    }

    /** Records real activity (chat, command, interaction...). */
    public static void activity(ServerPlayer p) {
        State s = STATES.computeIfAbsent(p.getUUID(), k -> init(p));
        s.lastActive = System.currentTimeMillis();
        if (s.afk) set(p, s, false, false);
    }

    /** Toggles manual AFK. Returns the new state. */
    public static boolean toggle(ServerPlayer p) {
        State s = STATES.computeIfAbsent(p.getUUID(), k -> init(p));
        if (s.afk) {
            s.lastActive = System.currentTimeMillis();
            set(p, s, false, false);
        } else {
            sample(p, s);
            set(p, s, true, true);
        }
        return s.afk;
    }

    static void onLogin(ServerPlayer p) {
        STATES.put(p.getUUID(), init(p));
    }

    static void onLogout(ServerPlayer p) {
        STATES.remove(p.getUUID());
    }

    static void clear() {
        STATES.clear();
    }

    /** Once per second: movement/rotation sampling, AFK timeout, point accrual. */
    static void tickSecond(MinecraftServer server) {
        long now = System.currentTimeMillis();
        long timeout = Points.config().afkMinutes * 60_000L;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            State s = STATES.computeIfAbsent(p.getUUID(), k -> init(p));
            double dx = p.getX() - s.x, dy = p.getY() - s.y, dz = p.getZ() - s.z;
            boolean moved = dx * dx + dy * dy + dz * dz > 0.25;
            boolean turned = Math.abs(wrap(p.getYRot() - s.yaw)) > 3 || Math.abs(p.getXRot() - s.pitch) > 3;
            if (turned || moved && !p.isPassenger()) {
                sample(p, s);
                s.lastActive = now;
                if (s.afk) set(p, s, false, false);
            }
            if (!s.afk && now - s.lastActive >= timeout) set(p, s, true, false);
            if (!s.afk && !p.isSpectator()) Points.tickActive(p);
        }
    }

    private static State init(ServerPlayer p) {
        State s = new State();
        sample(p, s);
        return s;
    }

    private static void sample(ServerPlayer p, State s) {
        s.x = p.getX();
        s.y = p.getY();
        s.z = p.getZ();
        s.yaw = p.getYRot();
        s.pitch = p.getXRot();
    }

    private static float wrap(float deg) {
        deg %= 360;
        if (deg >= 180) deg -= 360;
        if (deg < -180) deg += 360;
        return deg;
    }

    private static void set(ServerPlayer p, State s, boolean afk, boolean manual) {
        s.afk = afk;
        s.manual = afk && manual;
        p.refreshTabListName();
        if (Points.config().afkMessages) Msg.send(p, afk ? "&7You are now AFK." : "&7You are no longer AFK.");
        MinecraftForge.EVENT_BUS.post(new PlayerAfkChangedEvent(p, afk, manual));
    }

    /** Tab-list name with the AFK suffix. */
    static Component tabName(ServerPlayer p, Component current) {
        if (!isAfk(p)) return current;
        Component base = current != null ? current : p.getDisplayName();
        return base.copy().append(Msg.text(Points.config().afkTabSuffix));
    }
}
