package com.lime.odyssey.lib.api.ui;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The {@code /odyssey} hub. Library services put buttons in the header row through {@link #header}
 * (e.g. Points balance and Mailbox). Modules appear automatically.
 */
public final class Hub {
    private static final Map<Integer, Function<ServerPlayer, Screen.Btn>> HEADER = new ConcurrentHashMap<>();

    private Hub() {}

    /** Header slot 0-8 (4 is the player head). The function may return null to hide the button. */
    public static void header(int slot, Function<ServerPlayer, Screen.Btn> button) {
        if (slot < 0 || slot > 8 || slot == 4) throw new IllegalArgumentException("Hub header slot must be 0-8 except 4");
        HEADER.put(slot, button);
    }

    static Map<Integer, Function<ServerPlayer, Screen.Btn>> headers() {
        return HEADER;
    }

    public static void open(ServerPlayer p) {
        new HubScreen(p).open();
    }
}
