package com.lime.odyssey.lib.api.migrate;

import com.lime.odyssey.lib.OdysseyLib;
import com.lime.odyssey.lib.api.audit.Audit;
import com.lime.odyssey.lib.api.data.DataFile;
import com.lime.odyssey.lib.api.data.DataStore;
import com.lime.odyssey.lib.api.module.Modules;
import com.lime.odyssey.lib.api.notify.Notify;
import com.lime.odyssey.lib.api.notify.Severity;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs {@link LegacyImporter}s once. A successful, verified import moves the old files to
 * {@code <world>/odyssey/<module>/legacy-backup/<timestamp>/} (never deleted) and records a marker.
 * A failed or unverified import moves nothing and notifies admins; it is retried at the next start.
 */
public final class Migrations {
    private static final List<LegacyImporter> IMPORTERS = new ArrayList<>();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private Migrations() {}

    public static final class Done {
        public Map<String, String> completed = new LinkedHashMap<>();
    }

    /** Register during mod construction. */
    public static synchronized void register(LegacyImporter importer) {
        IMPORTERS.add(importer);
    }

    /** Called by the Library after modules are enabled. */
    public static void runAll(MinecraftServer server) {
        List<LegacyImporter> list;
        synchronized (Migrations.class) {
            list = new ArrayList<>(IMPORTERS);
        }
        if (list.isEmpty()) return;
        DataFile<Done> done = DataStore.world("library", "migrations", Done.class, Done::new);
        for (LegacyImporter imp : list) {
            if (done.get().completed.containsKey(imp.id()) || !Modules.isEnabled(imp.module())) continue;
            List<Path> files = new ArrayList<>();
            for (Path p : imp.legacyFiles(server)) if (Files.exists(p)) files.add(p);
            if (files.isEmpty()) continue;
            run(server, imp, files, done);
        }
        DataStore.close(done);
    }

    private static void run(MinecraftServer server, LegacyImporter imp, List<Path> files, DataFile<Done> done) {
        LegacyImporter.Report r;
        try {
            r = imp.importData(server);
        } catch (Exception e) {
            OdysseyLib.LOGGER.error("[Odyssey] Legacy import {} failed", imp.id(), e);
            Notify.admins(imp.module(), "Importing old data (" + imp.id() + ") failed: " + e.getMessage()
                    + ". Old files were left untouched; it will retry on the next start.", Severity.ALERT);
            return;
        }
        if (r.imported() != r.found()) {
            Notify.admins(imp.module(), "Old data import " + imp.id() + " is incomplete (" + r.imported() + "/" + r.found()
                    + "). Old files were left untouched. " + r.notes(), Severity.ALERT);
            return;
        }
        DataStore.saveAllNow();
        Path backup = DataStore.worldDir(imp.module()).resolve("legacy-backup").resolve(LocalDateTime.now().format(STAMP));
        List<String> moved = new ArrayList<>();
        try {
            Files.createDirectories(backup);
            for (Path f : files) {
                Path dst = backup.resolve(f.getFileName());
                Files.move(f, dst);
                moved.add(f.getFileName().toString());
            }
        } catch (Exception e) {
            OdysseyLib.LOGGER.error("[Odyssey] Could not move legacy files of {} to {}", imp.id(), backup, e);
            Notify.admins(imp.module(), "Old data of " + imp.id() + " was imported, but moving the old files to the backup folder failed: "
                    + e.getMessage() + ". Move them manually to avoid a second import.", Severity.ALERT);
        }
        done.get().completed.put(imp.id(), LocalDateTime.now().toString());
        done.markDirty();
        Audit.record("library", imp.module(), "migration.complete", imp.id(), r.found() + " records; moved " + moved);
        Notify.admins(imp.module(), "Imported old data (" + imp.id() + "): " + r.found() + " records. " + r.notes()
                + " Old files are in " + backup.getFileName() + ".", Severity.INFO);
    }
}
