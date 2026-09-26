package com.istad.library.storage;

import com.istad.library.model.Book;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class BookRepository {
    private static final List<String> HEADER = List.of(
            "bookId", "title", "author", "category", "quantity", "available"
    );
    private final Path file;

    public BookRepository(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    public List<Book> loadAll() throws IOException {
        List<Book> books = new ArrayList<>();
        List<List<String>> rows = CsvSupport.readRows(file, HEADER);
        for (int index = 0; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            requireColumnCount(row, HEADER.size(), index);
            try {
                String id = required(row.get(0), "book ID");
                String title = required(row.get(1), "title");
                int quantity = Integer.parseInt(row.get(4));
                int available = Integer.parseInt(row.get(5));
                validateCounts(quantity, available);
                books.add(new Book(id, title, row.get(2), row.get(3), quantity, available));
            } catch (RuntimeException exception) {
                throw invalidRow(index, exception);
            }
        }
        return books;
    }

    public void saveAll(List<Book> books) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (Book book : books) {
            try {
                required(book.getId(), "book ID");
                required(book.getTitle(), "title");
                validateCounts(book.getQuantity(), book.getAvailable());
            } catch (RuntimeException exception) {
                throw new IOException("Cannot save invalid book " + book.getId() + ": " + exception.getMessage(), exception);
            }
            rows.add(List.of(
                    book.getId(),
                    book.getTitle(),
                    book.getAuthor(),
                    book.getCategory(),
                    Integer.toString(book.getQuantity()),
                    Integer.toString(book.getAvailable())
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

    private static String required(String value, String label) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return cleaned;
    }

    private static void validateCounts(int quantity, int available) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
        if (available < 0 || available > quantity) {
            throw new IllegalArgumentException("available copies must be between zero and quantity");
        }
    }
}
