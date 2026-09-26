package com.istad.library.storage;

import com.istad.library.model.Loan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LoanRepository {
    private static final List<String> HEADER = List.of(
            "loanId", "userId", "bookId", "borrowDate", "dueDate", "returnDate", "fineAmount"
    );
    private final Path file;

    public LoanRepository(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    public List<Loan> loadAll() throws IOException {
        List<Loan> loans = new ArrayList<>();
        List<List<String>> rows = CsvSupport.readRows(file, HEADER);
        for (int index = 0; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            requireColumnCount(row, HEADER.size(), index);
            try {
                String loanId = required(row.get(0), "loan ID");
                String userId = required(row.get(1), "user ID");
                String bookId = required(row.get(2), "book ID");
                LocalDate borrowDate = LocalDate.parse(row.get(3));
                LocalDate dueDate = LocalDate.parse(row.get(4));
                LocalDate returnDate = row.get(5).isBlank() ? null : LocalDate.parse(row.get(5));
                double fine = Double.parseDouble(row.get(6));
                validateDatesAndFine(borrowDate, dueDate, returnDate, fine);
                loans.add(new Loan(loanId, userId, bookId, borrowDate, dueDate, returnDate, fine));
            } catch (RuntimeException exception) {
                throw invalidRow(index, exception);
            }
        }
        return loans;
    }

    public void saveAll(List<Loan> loans) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (Loan loan : loans) {
            try {
                required(loan.getId(), "loan ID");
                required(loan.getUserId(), "user ID");
                required(loan.getBookId(), "book ID");
                validateDatesAndFine(
                        loan.getBorrowDate(), loan.getDueDate(), loan.getReturnDate(), loan.getFineAmount()
                );
            } catch (RuntimeException exception) {
                throw new IOException("Cannot save invalid loan " + loan.getId() + ": " + exception.getMessage(), exception);
            }
            rows.add(List.of(
                    loan.getId(),
                    loan.getUserId(),
                    loan.getBookId(),
                    loan.getBorrowDate().toString(),
                    loan.getDueDate().toString(),
                    loan.getReturnDate() == null ? "" : loan.getReturnDate().toString(),
                    String.format(Locale.US, "%.2f", loan.getFineAmount())
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

    private static void validateDatesAndFine(
            LocalDate borrowDate,
            LocalDate dueDate,
            LocalDate returnDate,
            double fine
    ) {
        if (borrowDate == null || dueDate == null) {
            throw new IllegalArgumentException("borrow and due dates are required");
        }
        if (dueDate.isBefore(borrowDate)) {
            throw new IllegalArgumentException("due date cannot be before borrow date");
        }
        if (returnDate != null && returnDate.isBefore(borrowDate)) {
            throw new IllegalArgumentException("return date cannot be before borrow date");
        }
        if (!Double.isFinite(fine) || fine < 0) {
            throw new IllegalArgumentException("fine amount cannot be negative or non-finite");
        }
    }
}
