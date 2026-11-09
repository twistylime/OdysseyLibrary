package com.lime.odyssey.lib.internal;

import com.lime.odyssey.lib.api.config.Comment;
import com.lime.odyssey.lib.api.config.MaxLength;
import com.lime.odyssey.lib.api.config.Range;

/** {@code config/odyssey/library.json}. */
public final class LibraryConfig {
    @Comment("Vanilla OP level that counts as an Odyssey admin")
    @Range(min = 1, max = 4)
    public int adminOpLevel = 2;

    @Comment("Seconds between saves of changed Odyssey data")
    @Range(min = 5, max = 600)
    public int saveIntervalSeconds = 30;

    @Comment("Per-module time budget per tick in milliseconds before a lag warning is logged")
    @Range(min = 1, max = 50)
    public int taskBudgetMs = 5;

    @Comment("Chat prefix for all Odyssey messages ('&' colour codes)")
    @MaxLength(64)
    public String prefix = "&8[&6Odyssey&8] &r";

    @Comment("Prefix used by modules: {name} is replaced with the module name")
    @MaxLength(64)
    public String modulePrefix = "&8[&6Odyssey &8| &e{name}&8] &r";

    @Comment("Seconds to wait for pending file writes at server stop")
    @Range(min = 1, max = 120)
    public int shutdownFlushSeconds = 10;

    @Comment("Log extra diagnostic output")
    public boolean debug = false;
}
