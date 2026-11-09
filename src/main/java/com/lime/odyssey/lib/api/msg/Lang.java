package com.lime.odyssey.lib.api.msg;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lime.odyssey.lib.OdysseyLib;
import com.lime.odyssey.lib.api.data.DataStore;
import com.lime.odyssey.lib.api.data.Json;
import com.lime.odyssey.lib.api.data.SafeFiles;
import com.lime.odyssey.lib.api.sched.Scheduler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side message files {@code config/odyssey/lang/<module>.json}. Modules supply defaults in code;
 * missing keys are added to the file, admin edits override the defaults. {@code /odyssey reload-messages}
 * re-reads the files. Placeholders: {@code {0}, {1}...}.
 */
public final class Lang {
    private static final Map<String, Map<String, String>> MESSAGES = new ConcurrentHashMap<>();

    private Lang() {}

    public static void register(String module, Map<String, String> defaults) {
        Map<String, String> merged = new LinkedHashMap<>(defaults);
        Path file = file(module);
        boolean missing = true;
        if (Files.exists(file)) {
            try {
                JsonObject o = JsonParser.parseString(SafeFiles.read(file)).getAsJsonObject();
                for (String k : o.keySet()) if (o.get(k).isJsonPrimitive()) merged.put(k, o.get(k).getAsString());
                missing = !o.keySet().containsAll(defaults.keySet());
            } catch (Exception e) {
                OdysseyLib.LOGGER.error("[Odyssey] Could not read {}; using built-in messages", file, e);
                missing = false;
            }
        }
        MESSAGES.put(module, merged);
        if (missing) {
            String json = Json.GSON.toJson(merged);
            Scheduler.async(() -> {
                try {
                    SafeFiles.writeAtomic(file, json, 0);
                } catch (Exception e) {
                    OdysseyLib.LOGGER.error("[Odyssey] Could not write {}", file, e);
                }
            });
        }
    }

    /** Re-reads all message files (keeps defaults for missing keys). */
    public static void reload() {
        for (String module : MESSAGES.keySet()) {
            Map<String, String> current = new LinkedHashMap<>(MESSAGES.get(module));
            register(module, current);
        }
    }

    public static String get(String module, String key, Object... args) {
        Map<String, String> m = MESSAGES.get(module);
        String s = m == null ? null : m.get(key);
        if (s == null) return key;
        for (int i = 0; i < args.length; i++) s = s.replace("{" + i + "}", String.valueOf(args[i]));
        return s;
    }

    private static Path file(String module) {
        return DataStore.configDir().resolve("lang").resolve(module + ".json");
    }
}
