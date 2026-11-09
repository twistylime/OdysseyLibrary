package com.lime.odyssey.lib.internal;

import com.lime.odyssey.lib.api.config.ConfigFile;

/** Library-internal state shared between services. */
public final class Lib {
    public static final String MODULE = "library";
    private static ConfigFile<LibraryConfig> config;

    private Lib() {}

    static void setConfig(ConfigFile<LibraryConfig> c) {
        config = c;
    }

    public static LibraryConfig config() {
        return config == null ? new LibraryConfig() : config.get();
    }

    public static ConfigFile<LibraryConfig> configFile() {
        return config;
    }
}
