package com.istad.library.storage;

import com.istad.library.model.AccountStatus;
import com.istad.library.model.User;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class UserRepository {
    private static final List<String> HEADER = List.of("userId", "name", "email", "phone", "status");
    private final Path file;

    public UserRepository(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    public List<User> loadAll() throws IOException {
        List<User> users = new ArrayList<>();
        List<List<String>> rows = CsvSupport.readRows(file, HEADER);
        for (int index = 0; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            requireColumnCount(row, HEADER.size(), index);
            try {
                requireText(row.get(0), "user ID");
                requireText(row.get(1), "name");
                users.add(new User(
                        row.get(0), row.get(1), row.get(2), row.get(3), AccountStatus.fromText(row.get(4))
                ));
            } catch (RuntimeException exception) {
                throw invalidRow(index, exception);
            }
        }
        return users;
    }

    public void saveAll(List<User> users) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (User user : users) {
            requireTextForSave(user.getId(), "user ID");
            requireTextForSave(user.getName(), "name");
            rows.add(List.of(
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getPhone(),
                    user.getStatus().name()
            ));
        }
        CsvSupport.writeRows(file, HEADER, rows);
    }

    public void initializeIfMissing() throws IOException {
        if (!Files.exists(file)) {
            saveAll(List.of());
        }
    }

    private void requireColumnCount(List<String> row, int expected, int index) throws IOException {
        if (row.size() != expected) {
            throw new IOException(
                    "Invalid row " + (index + 2) + " in " + file.getFileName()
                            + ": expected " + expected + " columns but found " + row.size() + "."
            );
        }
    }

    private IOException invalidRow(int index, RuntimeException cause) {
        return new IOException(
                "Invalid row " + (index + 2) + " in " + file.getFileName() + ": " + cause.getMessage(),
                cause
        );
    }

    private static void requireText(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
    }

    private static void requireTextForSave(String value, String label) throws IOException {
        if (value == null || value.trim().isEmpty()) {
            throw new IOException("Cannot save a user whose " + label + " is blank.");
        }
    }
}
