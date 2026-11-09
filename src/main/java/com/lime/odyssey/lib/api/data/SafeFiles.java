package com.lime.odyssey.lib.api.data;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Crash-safe file primitives: write to a temp file, fsync, rotate backups, atomic move.
 * A half-written file can never replace a good one.
 */
public final class SafeFiles {
    public static final int BACKUPS = 3;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private SafeFiles() {}

    public static void writeAtomic(Path file, String content, int backups) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(bytes);
            while (buf.hasRemaining()) ch.write(buf);
            ch.force(true);
        }
        if (backups > 0 && Files.exists(file)) rotate(file, backups);
        move(tmp, file);
    }

    private static void rotate(Path file, int backups) throws IOException {
        for (int i = backups; i >= 1; i--) {
            Path src = i == 1 ? file : bak(file, i - 1);
            Path dst = bak(file, i);
            if (Files.exists(src)) {
                if (i == 1) Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                else Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public static Path bak(Path file, int i) {
        return file.resolveSibling(file.getFileName() + ".bak" + i);
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /** Moves an unreadable file aside so it is never overwritten. Returns the new location. */
    public static Path quarantine(Path file) throws IOException {
        Path dir = file.getParent().resolve("quarantine");
        Files.createDirectories(dir);
        Path dst = dir.resolve(file.getFileName() + "-" + LocalDateTime.now().format(STAMP));
        Files.move(file, dst, StandardCopyOption.REPLACE_EXISTING);
        return dst;
    }
}
