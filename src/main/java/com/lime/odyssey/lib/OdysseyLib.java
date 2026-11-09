package com.lime.odyssey.lib;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Odyssey Library entry point. Server-side only: every service starts on server start and
 * stops on server stop. Feature mods register themselves with the Library module registry.
 */
@Mod(OdysseyLib.MODID)
public final class OdysseyLib {
    public static final String MODID = "odysseylib";
    public static final Logger LOGGER = LogUtils.getLogger();

    public OdysseyLib() {}
}
