package com.istad.library.storage;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/** Small RFC-4180-style CSV reader/writer used by the three repositories. */
final class CsvSupport {
    private static final char UTF8_BOM = '\uFEFF';

    private CsvSupport() {
    }

    static List<List<String>> readRows(Path file, List<String> expectedHeader) throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }

        String content = Files.readString(file, StandardCharsets.UTF_8);
        if (!content.isEmpty() && content.charAt(0) == UTF8_BOM) {
            content = content.substring(1);
        }

        List<List<String>> records = parse(content, file);
        if (records.isEmpty()) {
            throw new IOException("CSV file is empty and has no header: " + file);
        }
        if (!records.get(0).equals(expectedHeader)) {
            throw new IOException(
                    "Unexpected CSV header in " + file.getFileName()
                            + ". Expected " + expectedHeader + " but found " + records.get(0) + "."
            );
        }

        List<List<String>> rows = new ArrayList<>();
        for (int index = 1; index < records.size(); index++) {
            List<String> row = records.get(index);
            if (!isBlankRecord(row)) {
                rows.add(List.copyOf(row));
            }
        }
        return List.copyOf(rows);
    }

    static void writeRows(Path file, List<String> header, List<List<String>> rows) throws IOException {
        Path absoluteFile = file.toAbsolutePath().normalize();
        Path parent = absoluteFile.getParent();
        if (parent == null) {
            throw new IOException("CSV file must have a parent directory: " + file);
        }
        Files.createDirectories(parent);

        Path temporaryFile = Files.createTempFile(parent, absoluteFile.getFileName() + ".", ".tmp");
        boolean moved = false;
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                writer.write(formatRow(header));
                writer.newLine();
                for (List<String> row : rows) {
                    writer.write(formatRow(row));
                    writer.newLine();
                }
            }

            try {
                Files.move(
                        temporaryFile,
                        absoluteFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, absoluteFile, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporaryFile);
            }
        }
    }

    private static List<List<String>> parse(String content, Path file) throws IOException {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean afterClosingQuote = false;
        boolean recordStarted = false;

        for (int index = 0; index < content.length(); index++) {
            char character = content.charAt(index);

            if (quoted) {
                if (character == '"') {
                    if (index + 1 < content.length() && content.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                        afterClosingQuote = true;
                    }
                } else {
                    field.append(character);
                }
                continue;
            }

            if (afterClosingQuote) {
                if (character == ',') {
                    record.add(field.toString());
                    field.setLength(0);
                    afterClosingQuote = false;
                    recordStarted = true;
                } else if (character == '\n' || character == '\r') {
                    record.add(field.toString());
                    records.add(List.copyOf(record));
                    record = new ArrayList<>();
                    field.setLength(0);
                    afterClosingQuote = false;
                    recordStarted = false;
                    if (character == '\r' && index + 1 < content.length() && content.charAt(index + 1) == '\n') {
                        index++;
                    }
                } else {
                    throw new IOException("Unexpected character after a closing quote in " + file + ".");
                }
                continue;
            }

            switch (character) {
                case '"' -> {
                    if (field.length() != 0) {
                        throw new IOException("Unexpected quote inside an unquoted field in " + file + ".");
                    }
                    quoted = true;
                    recordStarted = true;
                }
                case ',' -> {
                    record.add(field.toString());
                    field.setLength(0);
                    recordStarted = true;
                }
                case '\n', '\r' -> {
                    record.add(field.toString());
                    records.add(List.copyOf(record));
                    record = new ArrayList<>();
                    field.setLength(0);
                    recordStarted = false;
                    if (character == '\r' && index + 1 < content.length() && content.charAt(index + 1) == '\n') {
                        index++;
                    }
                }
                default -> {
                    field.append(character);
                    recordStarted = true;
                }
            }
        }

        if (quoted) {
            throw new IOException("Unterminated quoted field in " + file + ".");
        }
        if (afterClosingQuote || recordStarted || !record.isEmpty()) {
            record.add(field.toString());
            records.add(List.copyOf(record));
        }
        return List.copyOf(records);
    }

    private static boolean isBlankRecord(List<String> row) {
        return row.size() == 1 && row.get(0).isBlank();
    }

    private static String formatRow(List<String> row) {
        StringJoiner joiner = new StringJoiner(",");
        for (String value : row) {
            joiner.add(escape(value));
        }
        return joiner.toString();
    }

    private static String escape(String value) {
        String text = value == null ? "" : value;
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
