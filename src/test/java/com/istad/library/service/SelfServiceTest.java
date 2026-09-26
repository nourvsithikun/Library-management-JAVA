package com.istad.library.service;

import com.istad.library.model.AccountStatus;
import com.istad.library.model.Loan;
import com.istad.library.model.User;

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

public final class SelfServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-21T08:00:00Z"), ZoneOffset.UTC);

    private SelfServiceTest() {
    }

    public static void main(String[] args) throws Exception {
        Path dataDirectory = Files.createTempDirectory("library-self-service-test-");
        LibraryService service = new LibraryService(dataDirectory, CLOCK);

        User member = service.registerMember("  Dara Sok  ", " dara@example.com ", "012-345-678");
        assertEquals("U001", member.getId(), "The first self-registered membership should be U001.");
        assertEquals("Dara Sok", member.getName(), "Registration should trim the member name.");
        assertEquals(AccountStatus.ACTIVE, member.getStatus(), "Self-registration should create an active member.");
        assertEquals(1, service.getUsers().size(), "Registration should persist exactly one member.");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.registerMember("Duplicate Phone", "other@example.com", "(012) 345 678"),
                "Phone formatting variants should still count as duplicates."
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.registerMember("Duplicate Email", "DARA@EXAMPLE.COM", "098765432"),
                "Email matching should be case-insensitive."
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.registerMember("No Email", "", "098765432"),
                "Self-registration should require an email."
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.registerMember("Bad Email", "not-an-email", "098765432"),
                "Self-registration should reject malformed emails."
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.registerMember("Bad Phone", "phone@example.com", "012-ABC-678"),
                "Self-registration should reject letters in phone numbers."
        );
        assertEquals(1, service.getUsers().size(), "Failed registrations must not change stored members.");

        service.addBook("B001", "Effective Java", "Joshua Bloch", "Programming", 2);
        IllegalArgumentException wrongPhone = assertThrows(
                IllegalArgumentException.class,
                () -> service.borrowBookAsMember("U001", "098765432", "B001"),
                "The wrong phone should not authenticate."
        );
        IllegalArgumentException unknownMember = assertThrows(
                IllegalArgumentException.class,
                () -> service.borrowBookAsMember("U999", "098765432", "B001"),
                "An unknown member should not authenticate."
        );
        assertEquals(wrongPhone.getMessage(), unknownMember.getMessage(),
                "Wrong credentials should use one generic message.");
        assertEquals(2, service.findBookById("B001").orElseThrow().getAvailable(),
                "Failed verification must not change inventory.");

        Loan loan = service.borrowBookAsMember("u001", "(012) 345 678", "B001");
        assertEquals(TODAY, loan.getBorrowDate(), "Self-service borrow date should be controlled by the system.");
        assertEquals(TODAY.plusDays(14), loan.getDueDate(), "Self-service loans should use a 14-day term.");
        assertEquals(1, service.findBookById("B001").orElseThrow().getAvailable(),
                "A successful self-service loan should reduce availability.");
        assertThrows(
                IllegalArgumentException.class,
                () -> service.borrowBookAsMember("U001", "012345678", "B001"),
                "Existing duplicate-loan rules should apply to self-service."
        );

        User inactive = service.registerMember("Inactive Member", "inactive@example.com", "099887766");
        service.updateUser(
                inactive.getId(), inactive.getName(), inactive.getEmail(), inactive.getPhone(), AccountStatus.INACTIVE
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.borrowBookAsMember(inactive.getId(), "099-887-766", "B001"),
                "A correctly verified inactive member still must not borrow."
        );

        LibraryService reloaded = new LibraryService(dataDirectory, CLOCK);
        assertEquals(2, reloaded.getUsers().size(), "Self-registered members should survive reload.");
        assertEquals(1, reloaded.getLoans().size(), "Self-service loans should survive reload.");
        String userHeader = Files.readAllLines(dataDirectory.resolve("users.csv"), StandardCharsets.UTF_8).get(0);
        assertEquals("userId,name,email,phone,status", userHeader,
                "Self-service must preserve the existing users.csv schema.");

        Path highIdDirectory = Files.createTempDirectory("library-member-id-test-");
        LibraryService highIdService = new LibraryService(highIdDirectory, CLOCK);
        highIdService.addUser("CUSTOM", "Custom ID", "", "", AccountStatus.ACTIVE);
        highIdService.addUser("U999", "High ID", "", "", AccountStatus.ACTIVE);
        User numberOneThousand = highIdService.registerMember(
                "Next Member", "next@example.com", "010203040"
        );
        assertEquals("U1000", numberOneThousand.getId(), "Generated IDs should cross U999 safely.");
        assertTrue(highIdService.findUserById("CUSTOM").isPresent(), "Custom admin IDs should remain supported.");

        verifyLegacyBlankContactsRemainCompatible();
        verifyTwoOpenApplicationsDoNotOverwriteEachOther();

        System.out.println("SelfServiceTest passed.");
    }

    private static void verifyTwoOpenApplicationsDoNotOverwriteEachOther() throws Exception {
        Path sharedDirectory = Files.createTempDirectory("library-shared-app-test-");
        LibraryService admin = new LibraryService(sharedDirectory, CLOCK);
        LibraryService kiosk = new LibraryService(sharedDirectory, CLOCK);

        admin.addUser("U001", "Admin Added", "admin@example.com", "012345678", AccountStatus.ACTIVE);
        User kioskMember = kiosk.registerMember("Kiosk Added", "kiosk@example.com", "098765432");
        assertEquals("U002", kioskMember.getId(),
                "A stale kiosk instance should reload before generating a member ID.");

        admin.addBook("B001", "Shared Book", "", "", 2);
        Loan kioskLoan = kiosk.borrowBookAsMember("U002", "098-765-432", "B001");
        Loan adminLoan = admin.borrowBook("U001", "B001", TODAY.plusDays(14));
        assertEquals("L0001", kioskLoan.getId(), "The first process should create L0001.");
        assertEquals("L0002", adminLoan.getId(), "The stale admin instance should reload before creating a loan ID.");

        LibraryService reloaded = new LibraryService(sharedDirectory, CLOCK);
        assertEquals(2, reloaded.getUsers().size(), "Neither process may overwrite the other's member.");
        assertEquals(2, reloaded.getLoans().size(), "Neither process may overwrite the other's loan.");
        assertEquals(0, reloaded.findBookById("B001").orElseThrow().getAvailable(),
                "Both cross-application loans must be reflected in inventory.");

        admin.refresh();
        assertEquals(2, admin.getUsers().size(), "An open application should be able to refresh external changes.");
    }

    private static void verifyLegacyBlankContactsRemainCompatible() throws Exception {
        Path legacyDirectory = Files.createTempDirectory("library-legacy-member-test-");
        LibraryService service = new LibraryService(legacyDirectory, CLOCK);
        service.addUser("LEGACY", "Legacy Member", "", "", AccountStatus.ACTIVE);
        User registered = service.registerMember("New Member", "new@example.com", "077665544");
        assertEquals("U001", registered.getId(), "A custom legacy ID should not disrupt generated IDs.");
        service.addBook("B001", "Legacy Test Book", "", "", 1);
        IllegalArgumentException missingCredential = assertThrows(
                IllegalArgumentException.class,
                () -> service.borrowBookAsMember("LEGACY", "077665544", "B001"),
                "A blank legacy phone must never authenticate."
        );
        assertEquals("Membership ID or phone number is incorrect.", missingCredential.getMessage(),
                "Legacy credential failures should use the generic message.");
    }
}
