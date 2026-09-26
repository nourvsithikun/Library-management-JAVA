package com.istad.library.service;

/** A point-in-time summary used by the dashboard and smoke-test output. */
public record LibraryStatistics(
        int registeredUsers,
        int bookTitles,
        int availableCopies,
        long activeLoans,
        long overdueLoans
) {
}
