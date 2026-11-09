package com.lime.odyssey.lib.api.region;

import com.lime.odyssey.lib.api.event.OdysseyEventEndedEvent;
import com.lime.odyssey.lib.api.event.OdysseyEventStartedEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Areas of currently running Odyssey events (boss fights, arenas...). Event mods call
 * {@link #start}/{@link #end}; other mods query {@link #isInsideEvent} (e.g. Sweeper skips items there).
 */
public final class EventRegions {
    private static final Map<String, Active> ACTIVE = new ConcurrentHashMap<>();

    private EventRegions() {}

    public record Active(String module, String eventId, String displayName, Region region) {}

    public static void start(String module, String eventId, String displayName, Region region) {
        Active a = new Active(module, eventId, displayName, region);
        ACTIVE.put(module + ":" + eventId, a);
        MinecraftForge.EVENT_BUS.post(new OdysseyEventStartedEvent(module, eventId, displayName, region));
    }

    public static void end(String module, String eventId) {
        Active a = ACTIVE.remove(module + ":" + eventId);
        if (a != null) MinecraftForge.EVENT_BUS.post(new OdysseyEventEndedEvent(module, eventId, a.displayName()));
    }

    public static List<Active> active() {
        return List.copyOf(ACTIVE.values());
    }

    public static boolean isInsideEvent(ResourceKey<Level> dim, Vec3 pos) {
        for (Active a : ACTIVE.values()) if (a.region() != null && a.region().contains(dim, pos)) return true;
        return false;
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
