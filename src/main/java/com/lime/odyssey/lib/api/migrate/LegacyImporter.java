package com.lime.odyssey.lib.api.migrate;

import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.List;

/**
 * Imports data left by an older mod (e.g. the old MiniEvents or ChatGames) into the new format.
 * Runs once, after the owning module was enabled.
 */
public interface LegacyImporter {
    /** Unique id, e.g. {@code minievents-v1}. Used for the "done" marker. */
    String id();

    /** Owning module id (the importer only runs while that module is enabled). */
    String module();

    /** Files or folders that hold the old data (moved to the backup folder after a successful import). */
    List<Path> legacyFiles(MinecraftServer server);

    /**
     * Imports the data into the module's live stores (server thread). Must not delete anything.
     * Throw to abort: nothing is moved and admins are told.
     */
    Report importData(MinecraftServer server) throws Exception;

    /**
     * Result of an import.
     *
     * @param found    records found in the old data
     * @param imported records written to the new data; the import only counts as verified when equal to found
     * @param notes    human-readable summary
     */
    record Report(int found, int imported, String notes) {}
}
