package com.istad.library.storage;

import com.istad.library.model.AccountStatus;
import com.istad.library.model.Book;
import com.istad.library.model.Loan;
import com.istad.library.model.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static com.istad.library.test.TestSupport.assertEquals;
import static com.istad.library.test.TestSupport.assertThrows;
import static com.istad.library.test.TestSupport.assertTrue;

public final class RepositoryTest {
    private RepositoryTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("library-repository-test-");
        testUsers(root.resolve("users"));
        testBooks(root.resolve("books"));
        testLoans(root.resolve("loans"));
        testMalformedCsv(root.resolve("malformed"));
        System.out.println("RepositoryTest passed.");
    }

    private static void testUsers(Path directory) throws Exception {
        UserRepository repository = new UserRepository(directory.resolve("users.csv"));
        assertTrue(repository.loadAll().isEmpty(), "A missing user file should load as an empty collection.");
        repository.initializeIfMissing();
        assertTrue(Files.exists(directory.resolve("users.csv")), "Initialization should create users.csv.");

        String specialName = "Sok, \"Dara\"\nអ្នកអាន";
        repository.saveAll(List.of(new User("U001", specialName, "dara@example.com", "012 345 678", AccountStatus.ACTIVE)));
        List<User> reloaded = repository.loadAll();
        assertEquals(1, reloaded.size(), "One user should round-trip.");
        assertEquals(specialName, reloaded.get(0).getName(), "CSV should preserve commas, quotes, newlines, and Khmer.");
    }

    private static void testBooks(Path directory) throws Exception {
        BookRepository repository = new BookRepository(directory.resolve("books.csv"));
        repository.initializeIfMissing();
        repository.saveAll(List.of(new Book("B001", "Effective Java", "Joshua Bloch", "Programming", 3, 2)));
        Book book = repository.loadAll().get(0);
        assertEquals(3, book.getQuantity(), "Book quantity should round-trip.");
        assertEquals(2, book.getAvailable(), "Book availability should round-trip.");
        assertThrows(
                IOException.class,
                () -> repository.saveAll(List.of(new Book("B002", "Broken", "", "", 1, 2))),
                "Availability greater than quantity must be rejected."
        );
    }

    private static void testLoans(Path directory) throws Exception {
        LoanRepository repository = new LoanRepository(directory.resolve("loans.csv"));
        repository.initializeIfMissing();
        Loan active = new Loan(
                "L0001", "U001", "B001", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15), null, 0
        );
        Loan returned = new Loan(
                "L0002", "U001", "B002", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 8, 17), 1.0
        );
        repository.saveAll(List.of(active, returned));
        List<Loan> reloaded = repository.loadAll();
        assertEquals(2, reloaded.size(), "Both loans should round-trip.");
        assertTrue(reloaded.get(0).isActive(), "Blank return date should load as active.");
        assertEquals(1.0, reloaded.get(1).getFineAmount(), 0.001, "Fine should round-trip with two decimals.");
    }

    private static void testMalformedCsv(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path wrongHeader = directory.resolve("users.csv");
        Files.writeString(wrongHeader, "id,name,email,phone,status\n", StandardCharsets.UTF_8);
        assertThrows(
                IOException.class,
                () -> new UserRepository(wrongHeader).loadAll(),
                "A wrong CSV header should be rejected."
        );

        Path unclosedQuote = directory.resolve("books.csv");
        Files.writeString(
                unclosedQuote,
                "bookId,title,author,category,quantity,available\nB001,\"Broken title,Author,Category,1,1\n",
                StandardCharsets.UTF_8
        );
        assertThrows(
                IOException.class,
                () -> new BookRepository(unclosedQuote).loadAll(),
                "An unterminated quoted field should be rejected."
        );
    }
}
