package com.lime.odyssey.lib.api.command;

import com.lime.odyssey.lib.api.config.Comment;

import java.util.LinkedHashMap;
import java.util.Map;

/** {@code config/odyssey/aliases.json}: short player commands that redirect to {@code /odyssey ...}. */
public final class AliasConfig {
    @Comment("Turn every alias on or off")
    public boolean enabled = true;

    @Comment("alias -> target command (without the leading slash)")
    public Map<String, String> aliases = defaults();

    private static Map<String, String> defaults() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("afk", "odyssey afk");
        m.put("mailbox", "odyssey mailbox");
        m.put("points", "odyssey points");
        m.put("mev", "odyssey minievents");
        return m;
    }
}
