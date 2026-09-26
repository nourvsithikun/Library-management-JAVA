package com.istad.library.document;

import com.istad.library.model.AccountStatus;
import com.istad.library.model.Book;
import com.istad.library.model.Loan;
import com.istad.library.model.User;
import com.istad.library.service.LibraryService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static com.istad.library.test.TestSupport.assertEquals;
import static com.istad.library.test.TestSupport.assertThrows;
import static com.istad.library.test.TestSupport.assertTrue;

public final class FineInvoicePdfTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-21T08:00:00Z"),
            ZoneOffset.UTC
    );

    private FineInvoicePdfTest() {
    }

    public static void main(String[] args) throws Exception {
        verifyServiceInvoiceGeneration();
        if (args.length > 0) {
            Path sample = createPresentationSample(Path.of(args[0]));
            System.out.println("Sample invoice created: " + sample);
        }
        System.out.println("FineInvoicePdfTest passed.");
    }

    private static void verifyServiceInvoiceGeneration() throws Exception {
        Path dataDirectory = Files.createTempDirectory("library-invoice-test-");
        LibraryService service = new LibraryService(dataDirectory, FIXED_CLOCK);
        service.addUser("U001", "Sok Dara", "dara@example.com", "012345678", AccountStatus.ACTIVE);
        service.addBook("B001", "Effective Java", "Joshua Bloch", "Programming", 1);

        Loan overdue = service.borrowBook("U001", "B001", TODAY);
        Loan returned = service.returnLoan(overdue.getId(), TODAY.plusDays(4));
        assertEquals(2.00, returned.getFineAmount(), 0.001, "Four overdue days should cost $2.00.");

        Path invoice = service.generateFineInvoice(returned.getId());
        assertEquals(
                dataDirectory.resolve("invoices").resolve("Fine-Invoice-L0001.pdf").toAbsolutePath(),
                invoice,
                "Invoices should use a stable filename inside the library data folder."
        );
        assertTrue(Files.isRegularFile(invoice), "The invoice PDF should be created.");
        assertTrue(Files.size(invoice) > 20_000, "The rendered invoice should contain substantial page data.");

        byte[] bytes = Files.readAllBytes(invoice);
        String structure = new String(bytes, StandardCharsets.ISO_8859_1);
        assertTrue(structure.startsWith("%PDF-1.4"), "The invoice should have a valid PDF header.");
        assertTrue(structure.contains("/Subtype /Image"), "The PDF should contain its rendered invoice page.");
        assertTrue(structure.endsWith("%%EOF\n"), "The invoice should have a valid PDF terminator.");
        assertEquals(invoice, service.generateFineInvoice(returned.getId()),
                "Regenerating an invoice should safely replace the same file.");

        Loan noFine = service.borrowBook("U001", "B001", TODAY.plusDays(10));
        service.returnLoan(noFine.getId(), TODAY.plusDays(5));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.generateFineInvoice(noFine.getId()),
                "A returned loan without a fine should not create an invoice."
        );

        Loan active = service.borrowBook("U001", "B001", TODAY.plusDays(14));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.generateFineInvoice(active.getId()),
                "An active loan should not create a final fine invoice."
        );
    }

    private static Path createPresentationSample(Path outputDirectory) throws Exception {
        Loan loan = new Loan(
                "L-DEMO-001",
                "U-DEMO",
                "B-DEMO",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 9, 23),
                4.00
        );
        User member = new User(
                "U-DEMO",
                "Sok Dara",
                "dara@example.com",
                "012 345 678",
                AccountStatus.ACTIVE
        );
        Book book = new Book(
                "B-DEMO",
                "Effective Java",
                "Joshua Bloch",
                "Programming",
                3,
                3
        );
        return FineInvoicePdfGenerator.generate(outputDirectory, loan, member, book);
    }
}
