package com.istad.library.service;

import com.istad.library.model.AccountStatus;
import com.istad.library.model.Book;
import com.istad.library.model.Loan;
import com.istad.library.model.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static com.istad.library.test.TestSupport.assertEquals;
import static com.istad.library.test.TestSupport.assertThrows;
import static com.istad.library.test.TestSupport.assertTrue;

public final class LibraryServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-09-21T08:00:00Z"),
            ZoneOffset.UTC
    );

    private LibraryServiceTest() {
    }

    public static void main(String[] args) throws Exception {
        Path dataDirectory = Files.createTempDirectory("library-service-test-");
        LibraryService service = new LibraryService(dataDirectory, FIXED_CLOCK);
        assertCsvFilesCreated(dataDirectory);

        exerciseMembers(service);
        exerciseBooksAndLoans(service);
        verifyPersistenceWithoutStartupRewrite(dataDirectory);
        verifyCorruptAggregateIsRejected();
        System.out.println("LibraryServiceTest passed.");
    }

    private static void exerciseMembers(LibraryService service) throws Exception {
        service.addUser(" U100 ", "Sok, Dara", "dara@example.com", "012345678", AccountStatus.ACTIVE);
        service.addUser("U200", "Inactive User", "", "", AccountStatus.INACTIVE);
        service.addUser("U300", "Sophy Lim", "sophy@example.com", "098765432", AccountStatus.ACTIVE);
        service.addUser("U400", "Vanna Chea", "", "", AccountStatus.ACTIVE);

        assertTrue(service.findUserById("u100").isPresent(), "User lookup should ignore ID case.");
        assertEquals("U100", service.findUserById("U100").orElseThrow().getId(), "IDs should be trimmed.");
        assertEquals(1, service.searchUsers("DARA@EXAMPLE").size(), "Search should include email and ignore case.");
        assertThrows(
                IllegalArgumentException.class,
                () -> service.addUser("u100", "Duplicate", "", "", AccountStatus.ACTIVE),
                "Duplicate user IDs should be rejected case-insensitively."
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.addUser("", "No ID", "", "", AccountStatus.ACTIVE),
                "Blank user IDs should be rejected."
        );

        User detached = service.findUserById("U100").orElseThrow();
        detached.setName("Changed outside service");
        assertEquals(
                "Sok, Dara",
                service.findUserById("U100").orElseThrow().getName(),
                "Returned models should be defensive copies."
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> service.getUsers().add(new User("U999", "No", "", "", AccountStatus.ACTIVE)),
                "Returned lists should be unmodifiable."
        );

        User updated = service.updateUser(
                "U400", "Vanna C.", "vanna@example.com", "011223344", AccountStatus.ACTIVE
        );
        assertEquals("Vanna C.", updated.getName(), "Updating a member should return the new data.");
        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateUser("missing", "Name", "", "", AccountStatus.ACTIVE),
                "Updating an unknown member should fail."
        );
    }

    private static void exerciseBooksAndLoans(LibraryService service) throws Exception {
        service.addBook(" B100 ", "Java Fundamentals", "School Author", "Programming", 3);
        service.addBook("B200", "Khmer Literature", "Local Author", "Education", 1);
        assertEquals(2, service.searchBooks("author").size(), "Book search should include author.");
        assertThrows(
                IllegalArgumentException.class,
                () -> service.addBook("b100", "Duplicate", "", "", 1),
                "Duplicate book IDs should be rejected case-insensitively."
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.addBook("B300", "Invalid", "", "", 0),
                "Book quantity must be positive."
        );

        Loan first = service.borrowBook("U100", "B100", TODAY);
        assertEquals("L0001", first.getId(), "The first generated loan ID should be L0001.");
        assertEquals(2, service.findBookById("B100").orElseThrow().getAvailable(),
                "Borrowing should reduce availability once.");
        assertThrows(
                IllegalArgumentException.class,
                () -> service.borrowBook("U100", "B100", TODAY.plusDays(7)),
                "A duplicate active user/book loan should be rejected."
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.borrowBook("U200", "B100", TODAY.plusDays(7)),
                "Inactive members should not borrow."
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.borrowBook("U400", "B100", TODAY.minusDays(1)),
                "A past due date should be rejected."
        );

        Loan second = service.borrowBook("U300", "B100", TODAY.plusDays(7));
        service.borrowBook("U300", "B200", TODAY.plusDays(7));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.borrowBook("U400", "B200", TODAY.plusDays(7)),
                "An unavailable book should not be borrowed."
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.updateBook("B100", "Java Fundamentals", "School Author", "Programming", 1),
                "Quantity cannot drop below the two borrowed copies."
        );

        Book resized = service.updateBook(
                "B100", "Java Fundamentals, 2nd Edition", "School Author", "Programming", 2
        );
        assertEquals(0, resized.getAvailable(), "Updating quantity should preserve the borrowed copy count.");

        Loan returned = service.returnLoan(first.getId(), TODAY.plusDays(3));
        assertEquals(1.50, returned.getFineAmount(), 0.001, "Three overdue days should cost $1.50.");
        assertEquals(1, service.findBookById("B100").orElseThrow().getAvailable(),
                "Returning should restore one available copy.");
        assertThrows(
                IllegalArgumentException.class,
                () -> service.returnLoan(first.getId(), TODAY.plusDays(4)),
                "Returning the same loan twice should fail."
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.returnLoan(second.getId(), TODAY.minusDays(1)),
                "A return cannot predate borrowing."
        );

        Loan reborrowed = service.borrowBook("U100", "B100", TODAY.plusDays(14));
        assertEquals("L0004", reborrowed.getId(), "Loan IDs should continue increasing after returns.");
        LibraryStatistics statistics = service.getStatistics();
        assertEquals(4, statistics.registeredUsers(), "Dashboard member count should be correct.");
        assertEquals(2, statistics.bookTitles(), "Dashboard title count should be correct.");
        assertEquals(0, statistics.availableCopies(), "Dashboard should count available copies, not titles.");
        assertEquals(3L, statistics.activeLoans(), "Three loans should remain active.");
        assertEquals(0L, statistics.overdueLoans(), "No active loan should be overdue on the fixed date.");
    }

    private static void verifyPersistenceWithoutStartupRewrite(Path dataDirectory) throws Exception {
        Path usersFile = dataDirectory.resolve("users.csv");
        FileTime marker = FileTime.fromMillis(1_234_000L);
        Files.setLastModifiedTime(usersFile, marker);

        LibraryService reloaded = new LibraryService(dataDirectory, FIXED_CLOCK);
        assertEquals(marker, Files.getLastModifiedTime(usersFile), "Startup should not rewrite an existing valid CSV file.");
        assertEquals("Sok, Dara", reloaded.findUserById("U100").orElseThrow().getName(),
                "A comma-containing name should survive an actual reload.");
        assertEquals(4, reloaded.getLoans().size(), "All loans should persist after reloading.");
        Loan returned = reloaded.findLoanById("L0001").orElseThrow();
        assertEquals(TODAY.plusDays(3), returned.getReturnDate(), "Return date should persist.");
        assertEquals(1.50, returned.getFineAmount(), 0.001, "Returned fine should persist.");
        assertEquals(3L, reloaded.getActiveLoanCount(), "Active loan state should persist.");
    }

    private static void verifyCorruptAggregateIsRejected() throws Exception {
        Path dataDirectory = Files.createTempDirectory("library-corrupt-state-");
        Files.writeString(
                dataDirectory.resolve("users.csv"),
                "userId,name,email,phone,status\nU001,Test User,,,ACTIVE\n",
                StandardCharsets.UTF_8
        );
        Files.writeString(
                dataDirectory.resolve("books.csv"),
                "bookId,title,author,category,quantity,available\nB001,Test Book,,,1,1\n",
                StandardCharsets.UTF_8
        );
        Files.writeString(
                dataDirectory.resolve("loans.csv"),
                "loanId,userId,bookId,borrowDate,dueDate,returnDate,fineAmount\n"
                        + "L0001,U001,B001,2026-09-20,2026-09-30,,0.00\n",
                StandardCharsets.UTF_8
        );
        assertThrows(
                IOException.class,
                () -> new LibraryService(dataDirectory, FIXED_CLOCK),
                "Inventory that disagrees with active loans should be rejected."
        );
    }

    private static void assertCsvFilesCreated(Path dataDirectory) throws IOException {
        List<String> expectedFiles = List.of("users.csv", "books.csv", "loans.csv");
        for (String filename : expectedFiles) {
            Path file = dataDirectory.resolve(filename);
            assertTrue(Files.exists(file), filename + " should be created on first startup.");
            assertTrue(Files.size(file) > 0, filename + " should contain a header.");
        }
    }
}
