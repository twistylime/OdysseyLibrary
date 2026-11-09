package com.lime.odyssey.lib.api.data;

import com.lime.odyssey.lib.OdysseyLib;
import com.lime.odyssey.lib.api.sched.Scheduler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

/**
 * Append-only daily log ({@code logs/odyssey/<module>/<name>-YYYY-MM-DD.log}), written off-thread,
 * with automatic retention. Used for audit trails and tracking logs (e.g. sweeper bin takes).
 */
public final class LogFile {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Path dir;
    private final String name;
    private final int retentionDays;

    public LogFile(String module, String name, int retentionDays) {
        this.dir = DataStore.logsDir(module);
        this.name = name;
        this.retentionDays = retentionDays;
    }

    public void write(String line) {
        String stamped = "[" + LocalDateTime.now().format(TIME) + "] " + line + System.lineSeparator();
        Path file = dir.resolve(name + "-" + LocalDate.now() + ".log");
        Scheduler.async(() -> append(file, stamped));
    }

    private synchronized void append(Path file, String line) {
        try {
            Files.createDirectories(dir);
            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            OdysseyLib.LOGGER.error("[Odyssey] Could not write log {}", file, e);
        }
    }

    /** Deletes files older than the retention period (run off-thread at startup). */
    public void prune() {
        Scheduler.async(() -> {
            if (!Files.isDirectory(dir)) return;
            LocalDate limit = LocalDate.now().minusDays(retentionDays);
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(p -> p.getFileName().toString().startsWith(name + "-")).forEach(p -> {
                    String f = p.getFileName().toString();
                    String date = f.substring(name.length() + 1, f.length() - 4);
                    try {
                        if (LocalDate.parse(date).isBefore(limit)) Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            } catch (IOException e) {
                OdysseyLib.LOGGER.warn("[Odyssey] Could not prune logs in {}", dir, e);
            }
        });
    }
}
