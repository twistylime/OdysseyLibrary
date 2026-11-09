package com.lime.odyssey.lib.api.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lime.odyssey.lib.OdysseyLib;
import com.lime.odyssey.lib.api.audit.Audit;
import com.lime.odyssey.lib.api.data.Json;
import com.lime.odyssey.lib.api.data.SafeFiles;
import com.lime.odyssey.lib.api.event.ConfigChangedEvent;
import com.lime.odyssey.lib.api.notify.Notify;
import com.lime.odyssey.lib.api.notify.Severity;
import com.lime.odyssey.lib.api.sched.Scheduler;
import net.minecraftforge.common.MinecraftForge;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A live, typed config: {@code config/odyssey/<name>.json}.
 * <p>The current value is an object that is never mutated: every change builds a new instance, validates it
 * and swaps it in atomically, then posts {@link ConfigChangedEvent}. Invalid changes are rejected and the
 * previous value stays active. Manual edits of the file are picked up by {@link ConfigWatcher}.</p>
 *
 * @param <T> config class: public non-final fields with defaults, optional {@link Comment}/{@link Range}/{@link MaxLength}
 */
public final class ConfigFile<T> {
    private final String module;
    private final Path path;
    private final Class<T> type;
    private final AtomicReference<T> current = new AtomicReference<>();
    private volatile String lastWrittenJson = "";

    ConfigFile(String module, Path path, Class<T> type) {
        this.module = module;
        this.path = path;
        this.type = type;
        T loaded = null;
        JsonObject raw = null;
        if (Files.exists(path)) {
            try {
                raw = JsonParser.parseString(SafeFiles.read(path)).getAsJsonObject();
                loaded = Json.GSON.fromJson(raw, type);
                List<String> errors = validate(loaded);
                if (!errors.isEmpty()) {
                    OdysseyLib.LOGGER.error("[Odyssey] {} is invalid ({}); using defaults for this run", path, errors);
                    Notify.admins(module, "Config " + path.getFileName() + " is invalid: " + String.join("; ", errors) + ". Defaults are active until it is fixed.", Severity.ALERT);
                    loaded = null;
                }
            } catch (Exception e) {
                OdysseyLib.LOGGER.error("[Odyssey] Could not read {}; using defaults for this run", path, e);
                Notify.admins(module, "Config " + path.getFileName() + " could not be read: " + e.getMessage() + ". Defaults are active until it is fixed.", Severity.ALERT);
            }
        }
        boolean usable = loaded != null;
        current.set(usable ? loaded : newDefault());
        // Rewrite only when the file was missing or was valid (fills new keys); never overwrite a broken file.
        if (!Files.exists(path) || usable) writeAsync(current.get(), raw);
    }

    public String module() {
        return module;
    }

    public Path path() {
        return path;
    }

    /** File name without {@code .json}. */
    public String name() {
        String n = path.getFileName().toString();
        return n.endsWith(".json") ? n.substring(0, n.length() - 5) : n;
    }

    public Class<T> type() {
        return type;
    }

    /** Current value. Never mutate it: use {@link #edit}. */
    public T get() {
        return current.get();
    }

    /**
     * Applies an edit to a copy, validates it, and swaps it in. Must be called on the server thread.
     *
     * @return validation errors; empty when the change was applied
     */
    public List<String> edit(Consumer<T> editor, String actor) {
        T copy = copy(current.get());
        editor.accept(copy);
        return apply(copy, actor, true);
    }

    /** Sets one top-level field from text (used by commands, the UI and Web Controls). */
    public List<String> setFromString(String key, String value, String actor) {
        Field f = field(key);
        if (f == null) return List.of("Unknown setting '" + key + "'");
        Object parsed;
        try {
            parsed = parse(f.getType(), value);
        } catch (IllegalArgumentException e) {
            return List.of(e.getMessage());
        }
        T copy = copy(current.get());
        try {
            f.set(copy, parsed);
        } catch (IllegalAccessException e) {
            return List.of("Setting '" + key + "' cannot be changed");
        }
        return apply(copy, actor, true);
    }

    /** Re-reads the file after a manual edit (called by {@link ConfigWatcher}). */
    void reloadFromDisk(String json) {
        if (json.equals(lastWrittenJson)) return;
        T parsed;
        try {
            parsed = Json.GSON.fromJson(JsonParser.parseString(json), type);
        } catch (Exception e) {
            Notify.admins(module, "Manual edit of " + path.getFileName() + " ignored: " + e.getMessage(), Severity.WARN);
            return;
        }
        List<String> errors = apply(parsed, "file", false);
        if (!errors.isEmpty()) {
            Notify.admins(module, "Manual edit of " + path.getFileName() + " rejected: " + String.join("; ", errors), Severity.WARN);
        }
    }

    private List<String> apply(T next, String actor, boolean write) {
        List<String> errors = validate(next);
        if (!errors.isEmpty()) return errors;
        T previous = current.getAndSet(next);
        Set<String> changed = diff(previous, next);
        if (changed.isEmpty()) return List.of();
        if (write) writeAsync(next, null);
        Audit.record(actor, module, "config.change", path.getFileName().toString(), String.join(",", changed));
        MinecraftForge.EVENT_BUS.post(new ConfigChangedEvent(module, name(), changed));
        return List.of();
    }

    /** All editable top-level fields, in declaration order. */
    public List<Field> fields() {
        List<Field> out = new ArrayList<>();
        for (Field f : type.getFields()) {
            int m = f.getModifiers();
            if (Modifier.isStatic(m) || Modifier.isFinal(m) || Modifier.isTransient(m)) continue;
            out.add(f);
        }
        return out;
    }

    public Field field(String key) {
        for (Field f : fields()) if (f.getName().equals(key)) return f;
        return null;
    }

    public Object valueOf(String key) {
        Field f = field(key);
        if (f == null) return null;
        try {
            return f.get(current.get());
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private List<String> validate(T v) {
        List<String> errors = new ArrayList<>();
        if (v == null) {
            errors.add("empty config");
            return errors;
        }
        for (Field f : fields()) {
            try {
                Object o = f.get(v);
                Range r = f.getAnnotation(Range.class);
                if (r != null && o instanceof Number n) {
                    double d = n.doubleValue();
                    if (d < r.min() || d > r.max()) errors.add(f.getName() + " must be between " + fmt(r.min()) + " and " + fmt(r.max()));
                }
                MaxLength ml = f.getAnnotation(MaxLength.class);
                if (ml != null && o instanceof String s && s.length() > ml.value()) errors.add(f.getName() + " is longer than " + ml.value() + " characters");
                if (o == null && !f.getType().isPrimitive()) errors.add(f.getName() + " is missing");
            } catch (IllegalAccessException ignored) {
            }
        }
        if (errors.isEmpty() && v instanceof Validatable val) errors.addAll(val.validate());
        return errors;
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private Set<String> diff(T a, T b) {
        Set<String> out = new LinkedHashSet<>();
        JsonObject ja = Json.GSON.toJsonTree(a).getAsJsonObject();
        JsonObject jb = Json.GSON.toJsonTree(b).getAsJsonObject();
        for (String k : jb.keySet()) if (!jb.get(k).equals(ja.get(k))) out.add(k);
        for (String k : ja.keySet()) if (!jb.has(k)) out.add(k);
        return out;
    }

    private T copy(T v) {
        return Json.GSON.fromJson(Json.GSON.toJsonTree(v), type);
    }

    private T newDefault() {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Config class " + type.getName() + " needs a public no-arg constructor", e);
        }
    }

    private void writeAsync(T value, JsonObject previousRaw) {
        JsonObject out = Json.GSON.toJsonTree(value).getAsJsonObject();
        if (previousRaw != null) {
            // Keep keys that this version does not know (written by a newer/older module version).
            for (Map.Entry<String, JsonElement> e : previousRaw.entrySet()) {
                if (!out.has(e.getKey()) && !e.getKey().equals("_comments")) out.add(e.getKey(), e.getValue());
            }
        }
        Map<String, String> comments = new LinkedHashMap<>();
        for (Field f : fields()) {
            Comment c = f.getAnnotation(Comment.class);
            Range r = f.getAnnotation(Range.class);
            String text = c == null ? "" : c.value();
            if (r != null) text = (text.isEmpty() ? "" : text + " ") + "[" + fmt(r.min()) + " - " + fmt(r.max()) + "]";
            if (!text.isEmpty()) comments.put(f.getName(), text);
        }
        if (!comments.isEmpty()) out.add("_comments", Json.GSON.toJsonTree(comments));
        String json = Json.GSON.toJson(out);
        lastWrittenJson = json;
        Scheduler.async(() -> {
            try {
                SafeFiles.writeAtomic(path, json, 1);
            } catch (Exception e) {
                OdysseyLib.LOGGER.error("[Odyssey] Could not write {}", path, e);
            }
        });
    }

    static Object parse(Class<?> t, String s) {
        String v = s.trim();
        try {
            if (t == int.class || t == Integer.class) return Integer.parseInt(v);
            if (t == long.class || t == Long.class) return Long.parseLong(v);
            if (t == double.class || t == Double.class) return Double.parseDouble(v);
            if (t == float.class || t == Float.class) return Float.parseFloat(v);
            if (t == boolean.class || t == Boolean.class) {
                if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("on") || v.equalsIgnoreCase("yes")) return true;
                if (v.equalsIgnoreCase("false") || v.equalsIgnoreCase("off") || v.equalsIgnoreCase("no")) return false;
                throw new IllegalArgumentException("'" + v + "' is not true/false");
            }
            if (t == String.class) return s;
            if (t.isEnum()) {
                for (Object c : t.getEnumConstants()) if (((Enum<?>) c).name().equalsIgnoreCase(v)) return c;
                throw new IllegalArgumentException("'" + v + "' is not one of " + java.util.Arrays.toString(t.getEnumConstants()));
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + v + "' is not a number");
        }
        throw new IllegalArgumentException("This setting can only be edited in its own menu");
    }

    /** Whether {@link #setFromString} can edit a field of this type. */
    public static boolean isSimple(Class<?> t) {
        return t.isPrimitive() || t == Integer.class || t == Long.class || t == Double.class || t == Float.class
                || t == Boolean.class || t == String.class || t.isEnum();
    }
}
