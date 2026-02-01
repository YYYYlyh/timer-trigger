package com.example.timertrigger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogWriter {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path logDir;
    private LocalDate currentDate;
    private Path currentFile;

    public LogWriter(String logDir) {
        this.logDir = Path.of(logDir);
    }

    public synchronized void info(String message) {
        write("INFO", message);
    }

    public synchronized void error(String message) {
        write("ERROR", message);
    }

    private void write(String level, String message) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String line = String.format("%s [%s] %s", timestamp, level, message);
        System.out.println(line);
        try {
            ensureFile();
            Files.writeString(currentFile, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println(timestamp + " [ERROR] Failed to write log file: " + e.getMessage());
        }
    }

    private void ensureFile() throws IOException {
        LocalDate now = LocalDate.now();
        if (!now.equals(currentDate)) {
            Files.createDirectories(logDir);
            currentDate = now;
            currentFile = logDir.resolve(DATE_FORMAT.format(now) + ".txt");
        }
    }
}
