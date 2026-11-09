package com.lime.odyssey.lib.api.perm;

import com.lime.odyssey.lib.OdysseyLib;
import com.lime.odyssey.lib.internal.Lib;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Permission registry for all Odyssey modules. */
public final class Perms {
    private static final Map<String, OdysseyPerm> NODES = new ConcurrentHashMap<>();
    private static volatile boolean gathered;

    /** Library's own nodes. */
    public static final OdysseyPerm ADMIN = declare("library", "admin", PermDefault.ADMIN);
    public static final OdysseyPerm HUB = declare("library", "hub", PermDefault.ALL);
    public static final OdysseyPerm MAILBOX = declare("library", "mailbox", PermDefault.ALL);
    public static final OdysseyPerm MAILBOX_ADMIN = declare("library", "mailbox.admin", PermDefault.ADMIN);
    public static final OdysseyPerm POINTS = declare("library", "points", PermDefault.ALL);
    public static final OdysseyPerm POINTS_ADMIN = declare("library", "points.admin", PermDefault.ADMIN);
    public static final OdysseyPerm AFK = declare("library", "afk", PermDefault.ALL);
    public static final OdysseyPerm SNAPSHOT_ADMIN = declare("library", "snapshot.admin", PermDefault.ADMIN);

    private Perms() {}

    /**
     * Declares {@code odyssey.<module>.<action>}. Call during mod construction. Declaring the same node
     * twice returns the existing one.
     */
    public static OdysseyPerm declare(String module, String action, PermDefault def) {
        String node = "odyssey." + module + "." + action;
        OdysseyPerm existing = NODES.get(node);
        if (existing != null) return existing;
        if (gathered) OdysseyLib.LOGGER.warn("[Odyssey] Permission {} declared after server start; it uses the OP default only", node);
        OdysseyPerm p = new OdysseyPerm(node, def);
        NODES.put(node, p);
        return p;
    }

    public static Collection<OdysseyPerm> all() {
        return List.copyOf(NODES.values());
    }

    public static int levelFor(PermDefault def) {
        return switch (def) {
            case ALL -> 0;
            case ADMIN -> Lib.config().adminOpLevel;
            case OWNER -> 4;
        };
    }

    public static boolean isAdmin(ServerPlayer p) {
        return p != null && ADMIN.check(p);
    }

    static boolean check(OdysseyPerm perm, ServerPlayer p) {
        if (p == null) return false;
        PermissionNode<Boolean> n = perm.forgeNode;
        if (n != null) {
            try {
                return PermissionAPI.getPermission(p, n);
            } catch (Exception e) {
                // Node not known to the active handler: fall back to the OP default.
            }
        }
        return p.hasPermissions(levelFor(perm.defaultLevel()));
    }

    /** Forge permission gathering (fired once at server start). */
    public static void onGather(PermissionGatherEvent.Nodes event) {
        for (OdysseyPerm p : NODES.values()) {
            String rest = p.node().substring("odyssey.".length());
            PermDefault def = p.defaultLevel();
            PermissionNode<Boolean> node = new PermissionNode<>("odyssey", rest, PermissionTypes.BOOLEAN,
                    (player, uuid, ctx) -> player != null && player.hasPermissions(levelFor(def)));
            p.forgeNode = node;
            event.addNodes(node);
        }
        gathered = true;
    }
}
