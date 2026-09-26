package com.istad.library.model;

import java.time.LocalDate;

import static com.istad.library.test.TestSupport.assertEquals;
import static com.istad.library.test.TestSupport.assertFalse;
import static com.istad.library.test.TestSupport.assertThrows;
import static com.istad.library.test.TestSupport.assertTrue;

public final class LoanTest {
    private LoanTest() {
    }

    public static void main(String[] args) throws Exception {
        LocalDate borrowed = LocalDate.of(2026, 9, 1);
        LocalDate due = LocalDate.of(2026, 9, 15);
        Loan loan = new Loan(" L0001 ", " U001 ", " B001 ", borrowed, due, null, 0);

        assertEquals("L0001", loan.getId(), "Loan IDs should be trimmed.");
        assertTrue(loan.isActive(), "A loan without a return date should be active.");
        assertFalse(loan.isOverdue(due), "The due date itself should not be overdue.");
        assertEquals(0.0, loan.calculateFine(due), 0.001, "No fine is due on the due date.");
        assertEquals(0.50, loan.calculateFine(due.plusDays(1)), 0.001, "One late day should cost $0.50.");
        assertEquals(5.00, loan.calculateFine(due.plusDays(10)), 0.001, "Ten late days should cost $5.00.");

        loan.markReturned(due.plusDays(3));
        assertFalse(loan.isActive(), "A returned loan should not remain active.");
        assertEquals(1.50, loan.getFineAmount(), 0.001, "Returned fine should be stored exactly.");
        assertEquals(1.50, loan.calculateFine(due.plusYears(1)), 0.001, "Returned fine should stop changing.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new Loan("L2", "U1", "B1", borrowed, due, null, 0).markReturned(borrowed.minusDays(1)),
                "A book cannot be returned before it was borrowed."
        );
        System.out.println("LoanTest passed.");
    }
}
