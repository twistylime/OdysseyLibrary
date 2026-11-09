package com.lime.odyssey.lib;

import com.lime.odyssey.lib.internal.Lifecycle;
import com.lime.odyssey.lib.internal.UiHooks;
import com.lime.odyssey.lib.api.mailbox.MailboxHooks;
import com.lime.odyssey.lib.api.points.PointsHooks;
import com.lime.odyssey.lib.api.snapshot.SnapshotHooks;
import com.lime.odyssey.lib.api.soulbound.Soulbound;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Odyssey Library entry point. Server-side only: every service starts on server start and
 * stops on server stop. Feature mods register themselves through {@link com.lime.odyssey.lib.api.module.Modules}.
 */
@Mod(OdysseyLib.MODID)
public final class OdysseyLib {
    public static final String MODID = "odysseylib";
    public static final Logger LOGGER = LogUtils.getLogger();

    public OdysseyLib() {
        MinecraftForge.EVENT_BUS.register(new Lifecycle());
        MinecraftForge.EVENT_BUS.register(new UiHooks());
        MinecraftForge.EVENT_BUS.register(new PointsHooks());
        MinecraftForge.EVENT_BUS.register(new MailboxHooks());
        MinecraftForge.EVENT_BUS.register(new Soulbound());
        MinecraftForge.EVENT_BUS.register(new SnapshotHooks());
    }
}
