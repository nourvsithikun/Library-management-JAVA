package com.istad.library.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** A borrowing transaction. A null return date means that the loan is active. */
public final class Loan {
    public static final double DAILY_FINE = 0.50;
    private static final BigDecimal DAILY_FINE_AMOUNT = new BigDecimal("0.50");

    private final String id;
    private final String userId;
    private final String bookId;
    private final LocalDate borrowDate;
    private final LocalDate dueDate;
    private LocalDate returnDate;
    private double fineAmount;

    public Loan(
            String id,
            String userId,
            String bookId,
            LocalDate borrowDate,
            LocalDate dueDate,
            LocalDate returnDate,
            double fineAmount
    ) {
        this.id = clean(id);
        this.userId = clean(userId);
        this.bookId = clean(bookId);
        this.borrowDate = borrowDate;
        this.dueDate = dueDate;
        this.returnDate = returnDate;
        this.fineAmount = money(fineAmount);
    }

    public Loan(Loan source) {
        this(
                source.id,
                source.userId,
                source.bookId,
                source.borrowDate,
                source.dueDate,
                source.returnDate,
                source.fineAmount
        );
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getBookId() {
        return bookId;
    }

    public LocalDate getBorrowDate() {
        return borrowDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public double getFineAmount() {
        return fineAmount;
    }

    public boolean isActive() {
        return returnDate == null;
    }

    public boolean isOverdue(LocalDate asOfDate) {
        LocalDate effectiveDate = effectiveDate(asOfDate);
        return effectiveDate.isAfter(dueDate);
    }

    public double calculateFine(LocalDate asOfDate) {
        LocalDate effectiveDate = effectiveDate(asOfDate);
        long overdueDays = Math.max(0, ChronoUnit.DAYS.between(dueDate, effectiveDate));
        return DAILY_FINE_AMOUNT.multiply(BigDecimal.valueOf(overdueDays)).doubleValue();
    }

    public void markReturned(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Return date is required.");
        }
        if (borrowDate != null && date.isBefore(borrowDate)) {
            throw new IllegalArgumentException("Return date cannot be before the borrowing date.");
        }
        returnDate = date;
        fineAmount = money(calculateFine(date));
    }

    private LocalDate effectiveDate(LocalDate asOfDate) {
        if (dueDate == null) {
            throw new IllegalStateException("A loan must have a due date.");
        }
        if (returnDate != null) {
            return returnDate;
        }
        if (asOfDate == null) {
            throw new IllegalArgumentException("The as-of date is required for an active loan.");
        }
        return asOfDate;
    }

    private static double money(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Fine amount must be a finite number.");
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
