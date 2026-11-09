package com.lime.odyssey.lib.api.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lime.odyssey.lib.OdysseyLib;
import com.lime.odyssey.lib.api.notify.Notify;
import com.lime.odyssey.lib.api.notify.Severity;
import com.lime.odyssey.lib.api.sched.Scheduler;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * One JSON document on disk ({@code {"schema": n, "data": ...}}), held in memory and saved
 * asynchronously when dirty.
 * <p>Loading happens once when the file is opened (server start / module enable) and falls back to the
 * newest readable backup. An unreadable file is moved to {@code quarantine/} and admins are notified;
 * it is never overwritten. Saving serialises on the server thread and writes on the IO thread.</p>
 *
 * @param <T> document type (a plain Java class serialised with {@link Json#GSON})
 */
public final class DataFile<T> {
    private final String owner;
    private final Path path;
    private final Type type;
    private final int schema;
    private final Supplier<T> defaults;
    private final Function<JsonElement, JsonElement> migrator;
    private T value;
    private volatile boolean dirty;
    private long writeSeq;
    private long lastWritten;

    DataFile(String owner, Path path, Type type, int schema, Supplier<T> defaults, Function<JsonElement, JsonElement> migrator) {
        this.owner = owner;
        this.path = path;
        this.type = type;
        this.schema = schema;
        this.defaults = defaults;
        this.migrator = migrator;
        load();
    }

    public Path path() {
        return path;
    }

    public T get() {
        return value;
    }

    /** Replaces the value (server thread) and marks the file dirty. */
    public void set(T v) {
        value = v;
        dirty = true;
    }

    public void update(UnaryOperator<T> op) {
        set(op.apply(value));
    }

    /** Marks the in-memory value as changed (after mutating it in place). */
    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    private void load() {
        T loaded = tryLoad(path);
        if (loaded == null && Files.exists(path)) {
            try {
                Path q = SafeFiles.quarantine(path);
                Notify.admins(owner, "Data file " + path.getFileName() + " was unreadable and was moved to " + q.getFileName()
                        + ". The newest backup was loaded instead.", Severity.ALERT);
            } catch (Exception e) {
                OdysseyLib.LOGGER.error("[Odyssey] Could not quarantine {}", path, e);
            }
        }
        for (int i = 1; loaded == null && i <= SafeFiles.BACKUPS; i++) loaded = tryLoad(SafeFiles.bak(path, i));
        value = loaded != null ? loaded : defaults.get();
    }

    private T tryLoad(Path p) {
        if (!Files.exists(p)) return null;
        try {
            JsonElement root = JsonParser.parseString(SafeFiles.read(p));
            JsonElement data = root;
            int found = 0;
            if (root.isJsonObject() && root.getAsJsonObject().has("schema") && root.getAsJsonObject().has("data")) {
                found = root.getAsJsonObject().get("schema").getAsInt();
                data = root.getAsJsonObject().get("data");
            }
            if (found != schema && migrator != null) data = migrator.apply(data);
            T v = Json.GSON.fromJson(data, type);
            return v;
        } catch (Exception e) {
            OdysseyLib.LOGGER.error("[Odyssey] Could not read {}", p, e);
            return null;
        }
    }

    /**
     * Saves at the end of the current tick (off-thread) instead of waiting for the next periodic save.
     * Many calls in one tick result in a single write.
     */
    public void flush() {
        dirty = true;
        DataStore.requestFlush(this);
    }

    /** Serialises now (server thread) and writes on the IO thread. */
    void saveAsync() {
        if (!dirty) return;
        dirty = false;
        String json = serialise();
        long seq = ++writeSeq;
        Scheduler.async(() -> write(json, seq));
    }

    /** Serialises and writes on the calling thread (shutdown). */
    void saveNow() {
        if (!dirty) return;
        dirty = false;
        write(serialise(), ++writeSeq);
    }

    private String serialise() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", schema);
        root.add("data", Json.GSON.toJsonTree(value, type));
        return Json.GSON.toJson(root);
    }

    private synchronized void write(String json, long seq) {
        if (seq < lastWritten) return;
        try {
            SafeFiles.writeAtomic(path, json, SafeFiles.BACKUPS);
            lastWritten = seq;
        } catch (Exception e) {
            dirty = true;
            OdysseyLib.LOGGER.error("[Odyssey] Could not save {}", path, e);
            Scheduler.sync(() -> Notify.admins(owner, "Saving " + path.getFileName() + " failed: " + e.getMessage()
                    + ". The previous file is kept; retrying on the next save.", Severity.ALERT));
        }
    }
}
