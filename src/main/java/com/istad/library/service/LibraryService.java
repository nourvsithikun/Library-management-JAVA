package com.istad.library.service;

import com.istad.library.document.FineInvoicePdfGenerator;
import com.istad.library.model.AccountStatus;
import com.istad.library.model.Book;
import com.istad.library.model.Loan;
import com.istad.library.model.User;
import com.istad.library.storage.BookRepository;
import com.istad.library.storage.LoanRepository;
import com.istad.library.storage.UserRepository;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Application service and business-rule boundary.
 *
 * <p>Every public operation is synchronized because the Swing event thread is not the only possible
 * caller. Mutations also take an interprocess file lock, reload the current CSV snapshot, prepare changes
 * on copies, persist them, and only then install them in memory.</p>
 */
public final class LibraryService {
    public static final int DEFAULT_LOAN_DAYS = 14;
    private static final Comparator<String> ID_ORDER = String.CASE_INSENSITIVE_ORDER;
    private static final Map<Path, Object> JVM_DATA_LOCKS = new ConcurrentHashMap<>();

    private final Path dataDirectory;
    private final Clock clock;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;
    private final Path lockFile;

    private List<User> users = List.of();
    private List<Book> books = List.of();
    private List<Loan> loans = List.of();
    private boolean mutationContext;

    public LibraryService(Path dataDirectory) throws IOException {
        this(dataDirectory, Clock.systemDefaultZone());
    }

    public LibraryService(Path dataDirectory, Clock clock) throws IOException {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "Data directory is required.")
                .toAbsolutePath()
                .normalize();
        this.clock = Objects.requireNonNull(clock, "Clock is required.");
        userRepository = new UserRepository(this.dataDirectory.resolve("users.csv"));
        bookRepository = new BookRepository(this.dataDirectory.resolve("books.csv"));
        loanRepository = new LoanRepository(this.dataDirectory.resolve("loans.csv"));
        lockFile = this.dataDirectory.resolve(".library.lock");

        withDataLock(() -> {
            reloadStateFromDisk();
            // Create only missing files. Existing files are never rewritten merely by starting the app.
            userRepository.initializeIfMissing();
            bookRepository.initializeIfMissing();
            loanRepository.initializeIfMissing();
            return null;
        });
    }

    public synchronized Path getDataDirectory() {
        return dataDirectory;
    }

    public synchronized LocalDate getToday() {
        return LocalDate.now(clock);
    }

    /** Reloads the latest valid CSV snapshot while coordinating with other app processes. */
    public synchronized void refresh() throws IOException {
        if (mutationContext) {
            return;
        }
        withDataLock(() -> {
            reloadStateFromDisk();
            return null;
        });
    }

    public synchronized List<User> getUsers() {
        return users.stream()
                .map(User::new)
                .sorted(Comparator.comparing(User::getId, ID_ORDER))
                .toList();
    }

    public synchronized List<Book> getBooks() {
        return books.stream()
                .map(Book::new)
                .sorted(Comparator.comparing(Book::getId, ID_ORDER))
                .toList();
    }

    public synchronized List<Loan> getLoans() {
        return loans.stream()
                .map(Loan::new)
                .sorted(Comparator.comparingLong(LibraryService::loanSequence)
                        .reversed()
                        .thenComparing(Loan::getId, ID_ORDER.reversed()))
                .toList();
    }

    public synchronized Optional<User> findUserById(String id) {
        return findUserInternal(id).map(User::new);
    }

    public synchronized List<User> searchUsers(String query) {
        String normalized = normalized(query);
        if (normalized.isEmpty()) {
            return getUsers();
        }
        return users.stream()
                .filter(user -> contains(user.getId(), normalized)
                        || contains(user.getName(), normalized)
                        || contains(user.getEmail(), normalized)
                        || contains(user.getPhone(), normalized))
                .map(User::new)
                .sorted(Comparator.comparing(User::getId, ID_ORDER))
                .toList();
    }

    public synchronized User addUser(
            String id,
            String name,
            String email,
            String phone,
            AccountStatus status
    ) throws IOException {
        if (!mutationContext) {
            return withMutationLock(() -> addUser(id, name, email, phone, status));
        }
        String cleanId = required(id, "User ID");
        String cleanName = required(name, "Name");
        if (findUserInternal(cleanId).isPresent()) {
            throw new IllegalArgumentException("A user with ID " + cleanId + " already exists.");
        }

        User user = new User(cleanId, cleanName, email, phone, status);
        List<User> updated = copyUsers(users);
        updated.add(user);
        userRepository.saveAll(updated);
        users = updated;
        return new User(user);
    }

    /** Registers a new active member and assigns the next available U-number. */
    public synchronized User registerMember(String name, String email, String phone) throws IOException {
        if (!mutationContext) {
            return withMutationLock(() -> registerMember(name, email, phone));
        }
        String cleanName = required(name, "Full name");
        String cleanEmail = required(email, "Email address");
        String cleanPhone = clean(phone);
        requireMaximumLength(cleanName, "Full name", 120);
        requireMaximumLength(cleanEmail, "Email address", 254);
        requireMaximumLength(cleanPhone, "Phone number", 32);
        String phoneKey = validatedPhoneKey(cleanPhone);
        validateEmail(cleanEmail);

        boolean phoneAlreadyRegistered = users.stream()
                .map(User::getPhone)
                .map(LibraryService::phoneKey)
                .anyMatch(phoneKey::equals);
        boolean emailAlreadyRegistered = users.stream()
                .map(User::getEmail)
                .map(LibraryService::normalized)
                .filter(existingEmail -> !existingEmail.isEmpty())
                .anyMatch(normalized(cleanEmail)::equals);
        if (phoneAlreadyRegistered || emailAlreadyRegistered) {
            throw new IllegalArgumentException(
                    "A membership already exists with this phone number or email address."
            );
        }
        return addUser(nextMemberId(), cleanName, cleanEmail, cleanPhone, AccountStatus.ACTIVE);
    }

    public synchronized User updateUser(
            String id,
            String name,
            String email,
            String phone,
            AccountStatus status
    ) throws IOException {
        if (!mutationContext) {
            return withMutationLock(() -> updateUser(id, name, email, phone, status));
        }
        String cleanId = required(id, "User ID");
        String cleanName = required(name, "Name");
        int index = indexOf(users, User::getId, cleanId);
        if (index < 0) {
            throw new IllegalArgumentException("User ID " + cleanId + " was not found.");
        }

        User replacement = new User(users.get(index).getId(), cleanName, email, phone, status);
        List<User> updated = copyUsers(users);
        updated.set(index, replacement);
        userRepository.saveAll(updated);
        users = updated;
        return new User(replacement);
    }

    public synchronized Optional<Book> findBookById(String id) {
        return findBookInternal(id).map(Book::new);
    }

    public synchronized List<Book> searchBooks(String query) {
        String normalized = normalized(query);
        if (normalized.isEmpty()) {
            return getBooks();
        }
        return books.stream()
                .filter(book -> contains(book.getId(), normalized)
                        || contains(book.getTitle(), normalized)
                        || contains(book.getAuthor(), normalized)
                        || contains(book.getCategory(), normalized))
                .map(Book::new)
                .sorted(Comparator.comparing(Book::getId, ID_ORDER))
                .toList();
    }

    public synchronized List<Book> getAvailableBooks() {
        return getBooks().stream().filter(book -> book.getAvailable() > 0).toList();
    }

    public synchronized Book addBook(
            String id,
            String title,
            String author,
            String category,
            int quantity
    ) throws IOException {
        if (!mutationContext) {
            return withMutationLock(() -> addBook(id, title, author, category, quantity));
        }
        String cleanId = required(id, "Book ID");
        String cleanTitle = required(title, "Title");
        requirePositive(quantity, "Quantity");
        if (findBookInternal(cleanId).isPresent()) {
            throw new IllegalArgumentException("A book with ID " + cleanId + " already exists.");
        }

        Book book = new Book(cleanId, cleanTitle, author, category, quantity, quantity);
        List<Book> updated = copyBooks(books);
        updated.add(book);
        bookRepository.saveAll(updated);
        books = updated;
        return new Book(book);
    }

    public synchronized Book updateBook(
            String id,
            String title,
            String author,
            String category,
            int quantity
    ) throws IOException {
        if (!mutationContext) {
            return withMutationLock(() -> updateBook(id, title, author, category, quantity));
        }
        String cleanId = required(id, "Book ID");
        String cleanTitle = required(title, "Title");
        requirePositive(quantity, "Quantity");
        int index = indexOf(books, Book::getId, cleanId);
        if (index < 0) {
            throw new IllegalArgumentException("Book ID " + cleanId + " was not found.");
        }

        Book existing = books.get(index);
        int borrowedCopies = existing.getQuantity() - existing.getAvailable();
        if (quantity < borrowedCopies) {
            throw new IllegalArgumentException(
                    "Quantity cannot be lower than the " + borrowedCopies + " currently borrowed copies."
            );
        }

        Book replacement = new Book(
                existing.getId(),
                cleanTitle,
                author,
                category,
                quantity,
                quantity - borrowedCopies
        );
        List<Book> updated = copyBooks(books);
        updated.set(index, replacement);
        bookRepository.saveAll(updated);
        books = updated;
        return new Book(replacement);
    }

    public synchronized Optional<Loan> findLoanById(String id) {
        return findLoanInternal(id).map(Loan::new);
    }

    public synchronized Loan borrowBook(String userId, String bookId, LocalDate dueDate) throws IOException {
        if (!mutationContext) {
            return withMutationLock(() -> borrowBook(userId, bookId, dueDate));
        }
        User user = findUserInternal(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Registered user ID " + clean(userId) + " was not found."
                ));
        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("This user account is inactive.");
        }

        int bookIndex = indexOf(books, Book::getId, bookId);
        if (bookIndex < 0) {
            throw new IllegalArgumentException("Book ID " + clean(bookId) + " was not found.");
        }
        Book book = books.get(bookIndex);
        if (book.getAvailable() <= 0) {
            throw new IllegalArgumentException("No available copy of this book remains.");
        }

        LocalDate today = getToday();
        if (dueDate == null || dueDate.isBefore(today)) {
            throw new IllegalArgumentException("Due date must be today or later.");
        }
        boolean duplicateActiveLoan = loans.stream().anyMatch(loan -> loan.isActive()
                && sameId(loan.getUserId(), user.getId())
                && sameId(loan.getBookId(), book.getId()));
        if (duplicateActiveLoan) {
            throw new IllegalArgumentException("This user already has an active loan for this book.");
        }

        Loan loan = new Loan(nextLoanId(), user.getId(), book.getId(), today, dueDate, null, 0.0);
        List<Book> updatedBooks = copyBooks(books);
        Book updatedBook = updatedBooks.get(bookIndex);
        updatedBook.setAvailable(updatedBook.getAvailable() - 1);
        List<Loan> updatedLoans = copyLoans(loans);
        updatedLoans.add(loan);

        saveBooksAndLoans(updatedBooks, updatedLoans);
        books = updatedBooks;
        loans = updatedLoans;
        return new Loan(loan);
    }

    /**
     * Creates a standard self-service loan after matching the member's ID and registered phone number.
     * Borrow and due dates are controlled by the system.
     */
    public synchronized Loan borrowBookAsMember(String userId, String phone, String bookId) throws IOException {
        if (!mutationContext) {
            return withMutationLock(() -> borrowBookAsMember(userId, phone, bookId));
        }
        String cleanId = clean(userId);
        if (cleanId.isEmpty()) {
            throw new IllegalArgumentException("Membership ID or phone number is incorrect.");
        }
        String submittedPhone;
        try {
            submittedPhone = validatedPhoneKey(phone);
        } catch (IllegalArgumentException invalidPhone) {
            throw new IllegalArgumentException("Membership ID or phone number is incorrect.");
        }
        User user = findUserInternal(cleanId).orElseThrow(() -> new IllegalArgumentException(
                "Membership ID or phone number is incorrect."
        ));
        String storedPhone;
        try {
            storedPhone = validatedPhoneKey(user.getPhone());
        } catch (IllegalArgumentException invalidStoredPhone) {
            throw new IllegalArgumentException("Membership ID or phone number is incorrect.");
        }
        if (!storedPhone.equals(submittedPhone)) {
            throw new IllegalArgumentException("Membership ID or phone number is incorrect.");
        }
        return borrowBook(user.getId(), bookId, getToday().plusDays(DEFAULT_LOAN_DAYS));
    }

    public synchronized Loan returnLoan(String loanId, LocalDate returnDate) throws IOException {
        if (!mutationContext) {
            return withMutationLock(() -> returnLoan(loanId, returnDate));
        }
        int loanIndex = indexOf(loans, Loan::getId, loanId);
        if (loanIndex < 0) {
            throw new IllegalArgumentException("Loan ID " + clean(loanId) + " was not found.");
        }
        Loan existingLoan = loans.get(loanIndex);
        if (!existingLoan.isActive()) {
            throw new IllegalArgumentException("This loan was already returned.");
        }
        if (returnDate == null || returnDate.isBefore(existingLoan.getBorrowDate())) {
            throw new IllegalArgumentException("Return date cannot be before the borrowing date.");
        }

        int bookIndex = indexOf(books, Book::getId, existingLoan.getBookId());
        if (bookIndex < 0) {
            throw new IllegalStateException("The loan's book record is missing.");
        }

        List<Loan> updatedLoans = copyLoans(loans);
        Loan returnedLoan = updatedLoans.get(loanIndex);
        returnedLoan.markReturned(returnDate);
        List<Book> updatedBooks = copyBooks(books);
        Book updatedBook = updatedBooks.get(bookIndex);
        updatedBook.setAvailable(Math.min(updatedBook.getQuantity(), updatedBook.getAvailable() + 1));

        saveBooksAndLoans(updatedBooks, updatedLoans);
        books = updatedBooks;
        loans = updatedLoans;
        return new Loan(returnedLoan);
    }

    /** Creates or replaces the PDF fine invoice for a returned overdue loan. */
    public synchronized Path generateFineInvoice(String loanId) throws IOException {
        return withDataLock(() -> {
            reloadStateFromDisk();
            Loan loan = findLoanInternal(loanId).orElseThrow(() -> new IllegalArgumentException(
                    "Loan ID " + clean(loanId) + " was not found."
            ));
            if (loan.isActive()) {
                throw new IllegalArgumentException("Return the book before generating its fine invoice.");
            }
            if (loan.getFineAmount() <= 0) {
                throw new IllegalArgumentException("This loan has no fine to invoice.");
            }
            User member = findUserInternal(loan.getUserId()).orElseThrow(() -> new IllegalStateException(
                    "The loan's member record is missing."
            ));
            Book book = findBookInternal(loan.getBookId()).orElseThrow(() -> new IllegalStateException(
                    "The loan's book record is missing."
            ));
            return FineInvoicePdfGenerator.generate(
                    dataDirectory.resolve("invoices"),
                    new Loan(loan),
                    new User(member),
                    new Book(book)
            );
        });
    }

    public synchronized long getActiveLoanCount() {
        return loans.stream().filter(Loan::isActive).count();
    }

    public synchronized long getOverdueLoanCount() {
        return getOverdueLoanCount(getToday());
    }

    public synchronized long getOverdueLoanCount(LocalDate asOfDate) {
        Objects.requireNonNull(asOfDate, "As-of date is required.");
        return loans.stream().filter(Loan::isActive).filter(loan -> loan.isOverdue(asOfDate)).count();
    }

    public synchronized int getAvailableBookCount() {
        return books.stream().mapToInt(Book::getAvailable).sum();
    }

    public synchronized LibraryStatistics getStatistics() {
        return new LibraryStatistics(
                users.size(),
                books.size(),
                getAvailableBookCount(),
                getActiveLoanCount(),
                getOverdueLoanCount()
        );
    }

    private Optional<User> findUserInternal(String id) {
        return users.stream().filter(user -> sameId(user.getId(), id)).findFirst();
    }

    private Optional<Book> findBookInternal(String id) {
        return books.stream().filter(book -> sameId(book.getId(), id)).findFirst();
    }

    private Optional<Loan> findLoanInternal(String id) {
        return loans.stream().filter(loan -> sameId(loan.getId(), id)).findFirst();
    }

    private void reloadStateFromDisk() throws IOException {
        List<User> loadedUsers = userRepository.loadAll();
        List<Book> loadedBooks = bookRepository.loadAll();
        List<Loan> loadedLoans = loanRepository.loadAll();
        validateLoadedState(loadedUsers, loadedBooks, loadedLoans);
        users = copyUsers(loadedUsers);
        books = copyBooks(loadedBooks);
        loans = copyLoans(loadedLoans);
    }

    private <T> T withMutationLock(IoSupplier<T> operation) throws IOException {
        if (mutationContext) {
            return operation.get();
        }
        return withDataLock(() -> {
            reloadStateFromDisk();
            mutationContext = true;
            try {
                return operation.get();
            } finally {
                mutationContext = false;
            }
        });
    }

    private <T> T withDataLock(IoSupplier<T> operation) throws IOException {
        Object jvmLock = JVM_DATA_LOCKS.computeIfAbsent(dataDirectory, ignored -> new Object());
        synchronized (jvmLock) {
            Files.createDirectories(dataDirectory);
            try (FileChannel channel = FileChannel.open(
                    lockFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE
            ); FileLock dataLock = channel.lock()) {
                if (!dataLock.isValid()) {
                    throw new IOException("Could not lock the library data folder.");
                }
                return operation.get();
            }
        }
    }

    private void saveBooksAndLoans(List<Book> updatedBooks, List<Loan> updatedLoans) throws IOException {
        bookRepository.saveAll(updatedBooks);
        try {
            loanRepository.saveAll(updatedLoans);
        } catch (IOException failure) {
            // The individual CSV replacement is atomic. Restore the first file if the second save fails.
            try {
                bookRepository.saveAll(books);
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    private String nextLoanId() {
        long maximum = loans.stream()
                .mapToLong(LibraryService::loanSequence)
                .filter(sequence -> sequence >= 0)
                .max()
                .orElse(0L);
        if (maximum == Long.MAX_VALUE) {
            throw new IllegalStateException("No more loan IDs can be generated.");
        }
        return String.format(Locale.ROOT, "L%04d", maximum + 1);
    }

    private String nextMemberId() {
        long maximum = users.stream()
                .mapToLong(LibraryService::memberSequence)
                .filter(sequence -> sequence >= 0)
                .max()
                .orElse(0L);
        if (maximum == Long.MAX_VALUE) {
            throw new IllegalStateException("No more membership IDs can be generated.");
        }

        long candidate = maximum + 1;
        String id = String.format(Locale.ROOT, "U%03d", candidate);
        while (findUserInternal(id).isPresent()) {
            if (candidate == Long.MAX_VALUE) {
                throw new IllegalStateException("No more membership IDs can be generated.");
            }
            id = String.format(Locale.ROOT, "U%03d", ++candidate);
        }
        return id;
    }

    private static long loanSequence(Loan loan) {
        String id = loan.getId();
        if (!id.matches("(?i)L\\d+")) {
            return -1L;
        }
        try {
            return Long.parseLong(id.substring(1));
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private static long memberSequence(User user) {
        String id = user.getId();
        if (!id.matches("(?i)U\\d+")) {
            return -1L;
        }
        try {
            return Long.parseLong(id.substring(1));
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private static void validateLoadedState(
            List<User> loadedUsers,
            List<Book> loadedBooks,
            List<Loan> loadedLoans
    ) throws IOException {
        ensureUniqueIds(loadedUsers, User::getId, "user");
        ensureUniqueIds(loadedBooks, Book::getId, "book");
        ensureUniqueIds(loadedLoans, Loan::getId, "loan");

        Map<String, User> userById = new HashMap<>();
        for (User user : loadedUsers) {
            userById.put(normalized(user.getId()), user);
        }
        Map<String, Book> bookById = new HashMap<>();
        for (Book book : loadedBooks) {
            bookById.put(normalized(book.getId()), book);
        }

        Map<String, Integer> activeLoansByBook = new HashMap<>();
        Set<String> activePairs = new HashSet<>();
        for (Loan loan : loadedLoans) {
            String userKey = normalized(loan.getUserId());
            String bookKey = normalized(loan.getBookId());
            if (!userById.containsKey(userKey)) {
                throw new IOException("Loan " + loan.getId() + " references missing user " + loan.getUserId() + ".");
            }
            if (!bookById.containsKey(bookKey)) {
                throw new IOException("Loan " + loan.getId() + " references missing book " + loan.getBookId() + ".");
            }
            if (loan.isActive()) {
                String pair = userKey + "\u0000" + bookKey;
                if (!activePairs.add(pair)) {
                    throw new IOException("More than one active loan exists for user "
                            + loan.getUserId() + " and book " + loan.getBookId() + ".");
                }
                activeLoansByBook.merge(bookKey, 1, Integer::sum);
                if (Math.abs(loan.getFineAmount()) > 0.0001) {
                    throw new IOException("Active loan " + loan.getId() + " must have a stored fine of 0.00.");
                }
            } else if (Math.abs(loan.getFineAmount() - loan.calculateFine(loan.getReturnDate())) > 0.005) {
                throw new IOException("Stored fine for returned loan " + loan.getId() + " is inconsistent.");
            }
        }

        for (Book book : loadedBooks) {
            int activeCount = activeLoansByBook.getOrDefault(normalized(book.getId()), 0);
            int expectedAvailable = book.getQuantity() - activeCount;
            if (book.getAvailable() != expectedAvailable) {
                throw new IOException(
                        "Book " + book.getId() + " has " + book.getAvailable() + " available copies; expected "
                                + expectedAvailable + " from its active loans."
                );
            }
        }
    }

    private static <T> void ensureUniqueIds(
            List<T> values,
            Function<T, String> idExtractor,
            String label
    ) throws IOException {
        Set<String> seen = new HashSet<>();
        for (T value : values) {
            String id = idExtractor.apply(value);
            if (!seen.add(normalized(id))) {
                throw new IOException("Duplicate " + label + " ID in CSV data: " + id + ".");
            }
        }
    }

    private static <T> int indexOf(List<T> values, Function<T, String> idExtractor, String id) {
        for (int index = 0; index < values.size(); index++) {
            if (sameId(idExtractor.apply(values.get(index)), id)) {
                return index;
            }
        }
        return -1;
    }

    private static List<User> copyUsers(List<User> source) {
        return source.stream().map(User::new).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private static List<Book> copyBooks(List<Book> source) {
        return source.stream().map(Book::new).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private static List<Loan> copyLoans(List<Loan> source) {
        return source.stream().map(Loan::new).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private static boolean contains(String text, String normalizedQuery) {
        return normalized(text).contains(normalizedQuery);
    }

    private static boolean sameId(String left, String right) {
        return clean(left).equalsIgnoreCase(clean(right));
    }

    private static String normalized(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String validatedPhoneKey(String phone) {
        String cleaned = required(phone, "Phone number");
        if (!cleaned.matches("[+0-9() .-]+")) {
            throw new IllegalArgumentException("Phone number contains unsupported characters.");
        }
        String key = phoneKey(cleaned);
        if (key.length() < 8 || key.length() > 15) {
            throw new IllegalArgumentException("Phone number must contain 8 to 15 digits.");
        }
        return key;
    }

    private static String phoneKey(String phone) {
        String cleaned = clean(phone);
        StringBuilder digits = new StringBuilder();
        for (int index = 0; index < cleaned.length(); index++) {
            char character = cleaned.charAt(index);
            if (Character.isDigit(character)) {
                digits.append(character);
            }
        }
        return digits.toString();
    }

    private static void validateEmail(String email) {
        if (!email.isEmpty() && !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new IllegalArgumentException("Email address is not valid.");
        }
    }

    private static String required(String value, String label) {
        String cleaned = clean(value);
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return cleaned;
    }

    private static void requirePositive(int value, String label) {
        if (value <= 0) {
            throw new IllegalArgumentException(label + " must be greater than zero.");
        }
    }

    private static void requireMaximumLength(String value, String label, int maximum) {
        if (value.length() > maximum) {
            throw new IllegalArgumentException(label + " must be " + maximum + " characters or fewer.");
        }
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }
}
