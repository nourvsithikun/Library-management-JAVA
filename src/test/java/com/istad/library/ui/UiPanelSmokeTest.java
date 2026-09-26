package com.istad.library.ui;

import com.istad.library.model.AccountStatus;
import com.istad.library.service.LibraryService;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static com.istad.library.test.TestSupport.assertEquals;
import static com.istad.library.test.TestSupport.assertTrue;

public final class UiPanelSmokeTest {
    private UiPanelSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Path dataDirectory = Files.createTempDirectory("library-ui-test-");
        Clock clock = Clock.fixed(Instant.parse("2026-09-21T08:00:00Z"), ZoneOffset.UTC);
        LibraryService service = new LibraryService(dataDirectory, clock);

        SwingUtilities.invokeAndWait(() -> runWorkflow(service, dataDirectory));
        System.out.println("UiPanelSmokeTest passed.");
    }

    private static void runWorkflow(LibraryService service, Path dataDirectory) {
        AppTheme.apply();

        // The single-window portal keeps member access separate from the protected admin dashboard.
        LibraryPortalPanel portal = new LibraryPortalPanel(service, dataDirectory);
        portal.setSize(1280, 790);
        assertTrue(panel(portal, "portal.access").isVisible(), "Role selection should be the initial page.");
        assertTrue(!panel(portal, "portal.admin").isVisible(), "Admin tools must be hidden before login.");
        button(portal, "access.member").doClick();
        assertTrue(panel(portal, "page.selfService").isVisible(),
                "Members should enter self-service without seeing admin tools.");
        button(portal, "self.finish").doClick();
        assertTrue(panel(portal, "portal.access").isVisible(),
                "Finishing a member session should return to role selection.");
        field(portal, "access.admin.username").setText("admin");
        field(portal, "access.admin.password").setText("wrong-password");
        button(portal, "access.admin.login").doClick();
        assertTrue(!panel(portal, "portal.admin").isVisible(), "Incorrect credentials must not reveal admin tools.");
        assertTrue(label(portal, "access.admin.feedback").getText().contains("Incorrect"),
                "A rejected login should show a useful error.");
        field(portal, "access.admin.username").setText("admin");
        field(portal, "access.admin.password").setText("admin123");
        button(portal, "access.admin.login").doClick();
        assertTrue(panel(portal, "portal.admin").isVisible(), "Valid librarian credentials should open admin tools.");
        button(portal, "nav.logout").doClick();
        assertTrue(panel(portal, "portal.access").isVisible(), "Librarian logout should return to role selection.");

        LibraryApplicationPanel application = new LibraryApplicationPanel(service, dataDirectory);
        application.setSize(1280, 790);
        assertTrue(panel(application, "page.overview").isVisible(), "Overview should be the initial page.");
        assertEquals("Member Self-Service", button(application, "dashboard.selfService").getText(),
                "The admin dashboard should expose the member-facing application.");

        // Register a member through the actual form and button.
        button(application, "nav.members").doClick();
        assertTrue(panel(application, "page.members").isVisible(), "Members navigation should show the member page.");
        field(application, "members.id").setText("U001");
        field(application, "members.name").setText("Dara Sok");
        field(application, "members.email").setText("dara@example.com");
        field(application, "members.phone").setText("012345678");
        button(application, "members.add").doClick();
        assertTrue(service.findUserById("U001").isPresent(), "Member button should create a member.");

        // Add a book through the book form.
        button(application, "nav.books").doClick();
        assertTrue(panel(application, "page.books").isVisible(), "Books navigation should show the book page.");
        field(application, "books.id").setText("B001");
        field(application, "books.title").setText("Effective Java");
        field(application, "books.author").setText("Joshua Bloch");
        field(application, "books.category").setText("Programming");
        component(application, "books.quantity", JSpinner.class).setValue(2);
        button(application, "books.add").doClick();
        assertEquals(1, service.getBooks().size(), "Book button should create a book.");

        // Search-as-you-type updates the member table.
        field(application, "members.search").setText("Dara");
        assertEquals(1, table(application, "members.table").getRowCount(), "Member search should filter rows.");
        field(application, "members.search").setText("Nobody");
        assertEquals(0, table(application, "members.table").getRowCount(), "Unknown search should show no rows.");
        field(application, "members.search").setText("");

        // Borrow through the circulation controls.
        button(application, "nav.circulation").doClick();
        assertTrue(panel(application, "page.circulation").isVisible(),
                "Circulation navigation should show the loan page.");
        JComboBox<?> userBox = component(application, "loans.user", JComboBox.class);
        JComboBox<?> bookBox = component(application, "loans.book", JComboBox.class);
        assertEquals(1, userBox.getItemCount(), "Borrow form should list registered members.");
        assertEquals(1, bookBox.getItemCount(), "Borrow form should list books.");
        field(application, "loans.dueDate").setText("not-a-date");
        button(application, "loans.borrow").doClick();
        assertEquals(0L, service.getActiveLoanCount(), "Malformed dates should not create loans.");
        field(application, "loans.dueDate").setText("2026-10-05");
        button(application, "loans.borrow").doClick();
        assertEquals(1L, service.getActiveLoanCount(), "Borrow button should create an active loan.");
        assertEquals("1", label(application, "metric.activeLoans").getText(), "Dashboard should refresh after borrowing.");

        JTable loansTable = table(application, "loans.table");
        assertEquals(1, loansTable.getRowCount(), "Active-loan table should show the new loan.");
        loansTable.setRowSelectionInterval(0, 0);
        JButton returnButton = button(application, "loans.return");
        assertTrue(returnButton.isEnabled(), "Return should enable for an active selection.");
        assertTrue(!button(application, "loans.invoice").isEnabled(),
                "A final invoice should not be available before an overdue loan is returned.");
        returnButton.doClick();
        assertEquals(0L, service.getActiveLoanCount(), "Return button should close the loan.");
        assertTrue(!button(application, "loans.invoice").isEnabled(),
                "A returned loan with no fine should not offer an invoice.");
        assertEquals("0", label(application, "metric.activeLoans").getText(), "Dashboard should refresh after return.");

        JCheckBox activeOnly = component(application, "loans.activeOnly", JCheckBox.class);
        assertEquals(0, table(application, "loans.table").getRowCount(), "Returned loans should be hidden initially.");
        activeOnly.doClick();
        assertEquals(1, table(application, "loans.table").getRowCount(), "History view should include returned loans.");

        // Existing members can verify their phone and borrow with system-controlled dates.
        SelfServicePanel selfService = new SelfServicePanel(service, application::refreshAll);
        selfService.setSize(960, 740);
        assertEquals("2026-09-21", field(selfService, "self.borrowDate").getText(),
                "Self-service should show today's fixed date.");
        assertEquals("2026-10-05", field(selfService, "self.dueDate").getText(),
                "Self-service should calculate the 14-day due date.");
        assertTrue(!field(selfService, "self.borrowDate").isEditable(), "Borrow date should be read-only.");
        assertEquals(1, component(selfService, "self.book", JComboBox.class).getItemCount(),
                "Self-service should list the available book.");
        field(selfService, "self.memberId").setText("U001");
        field(selfService, "self.memberPhone").setText("wrong-phone");
        assertTrue(!button(selfService, "self.borrow").isEnabled(),
                "Borrowing must remain disabled until the member confirms.");
        button(selfService, "self.borrow").doClick();
        assertEquals(0L, service.getActiveLoanCount(), "An unconfirmed click must not create a loan.");
        component(selfService, "self.confirm", JCheckBox.class).doClick();
        button(selfService, "self.borrow").doClick();
        assertEquals(0L, service.getActiveLoanCount(), "Wrong phone verification must not create a loan.");
        assertEquals(0, component(selfService, "self.memberPhone", JPasswordField.class).getPassword().length,
                "A rejected credential should be cleared from the kiosk.");
        field(selfService, "self.memberPhone").setText("(012) 345 678");
        component(selfService, "self.confirm", JCheckBox.class).doClick();
        button(selfService, "self.borrow").doClick();
        assertEquals(1L, service.getActiveLoanCount(), "A verified member should borrow successfully.");
        assertEquals(service.getToday().plusDays(14), service.getLoans().get(0).getDueDate(),
                "Members must not control their own due date.");

        // A visitor can register, receives an ID, and then explicitly confirms a loan.
        JTabbedPane selfTabs = component(selfService, "self.tabs", JTabbedPane.class);
        selfTabs.setSelectedIndex(1);
        field(selfService, "self.register.name").setText("Sophy Lim");
        field(selfService, "self.register.email").setText("sophy@example.com");
        field(selfService, "self.register.phone").setText("098-765-432");
        button(selfService, "self.register.submit").doClick();
        assertEquals(0, selfTabs.getSelectedIndex(), "Successful registration should return to the borrowing tab.");
        assertEquals("U002", field(selfService, "self.memberId").getText(),
                "The new membership ID should fill the borrowing form.");
        assertEquals(0, component(selfService, "self.memberPhone", JPasswordField.class).getPassword().length,
                "Registration must not leave the phone credential visible or prefilled.");
        assertTrue(service.findUserById("U002").isPresent(), "Self-registration should persist the new member.");
        assertEquals(AccountStatus.ACTIVE, service.findUserById("U002").orElseThrow().getStatus(),
                "Self-registered memberships should be active.");
        assertTrue(label(selfService, "self.register.result").getText().contains("U002"),
                "The registration result should display the new membership ID.");
        field(selfService, "self.memberPhone").setText("098765432");
        component(selfService, "self.confirm", JCheckBox.class).doClick();
        button(selfService, "self.borrow").doClick();
        assertEquals(2L, service.getActiveLoanCount(),
                "The newly registered member should be able to confirm a separate loan.");
        button(selfService, "self.finish").doClick();
        assertTrue(field(selfService, "self.memberId").getText().isBlank(),
                "Finishing a kiosk session should clear the membership ID.");
        assertTrue(label(selfService, "self.register.result").getText().isBlank(),
                "Finishing a kiosk session should clear the generated ID message.");

        // Exact member IDs take precedence over broad text matches, and invalid borrow choices stay hidden.
        try {
            service.addUser("U003", "Mentions U001", "", "", AccountStatus.INACTIVE);
            service.addBook("B002", "Unavailable Book", "", "", 1);
            service.addBook("B003", "Available Book", "", "", 1);
            service.borrowBook("U001", "B002", service.getToday().plusDays(7));
        } catch (Exception exception) {
            throw new AssertionError("Could not prepare selector-filter fixture.", exception);
        }
        application.refreshAll();
        field(application, "members.search").setText("U001");
        assertEquals(1, table(application, "members.table").getRowCount(),
                "An exact member ID should take precedence over other substring matches.");
        assertEquals(2, userBox.getItemCount(), "Inactive members should not appear in the borrow selector.");
        assertEquals(1, bookBox.getItemCount(), "Unavailable books should not appear in the borrow selector.");

        // Navigate, lay out, and paint every page at minimum and target sizes without creating a JFrame.
        String[] pages = {"overview", "members", "books", "circulation"};
        for (String page : pages) {
            button(application, "nav." + page).doClick();
            assertTrue(panel(application, "page." + page).isVisible(), "Navigation should reveal page " + page + ".");
            assertEquals(AppTheme.BLUE, button(application, "nav." + page).getBackground(),
                    "The selected navigation button should be highlighted.");
            paint(application, 1080, 700);
            paint(application, 1280, 790);
        }
        paint(selfService, 820, 680);
        paint(selfService, 960, 740);
        paint(portal, 1080, 700);
        paint(portal, 1280, 790);
        selfService.resetSession();
    }

    private static JTextField field(Container root, String name) {
        return component(root, name, JTextField.class);
    }

    private static JButton button(Container root, String name) {
        return component(root, name, JButton.class);
    }

    private static JTable table(Container root, String name) {
        return component(root, name, JTable.class);
    }

    private static JLabel label(Container root, String name) {
        return component(root, name, JLabel.class);
    }

    private static JPanel panel(Container root, String name) {
        return component(root, name, JPanel.class);
    }

    private static void paint(JPanel application, int width, int height) {
        application.setSize(width, height);
        layoutTree(application);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            application.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        assertEquals(width, image.getWidth(), "Painted image should use the requested width.");
    }

    private static void layoutTree(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) {
                layoutTree(nested);
            }
        }
    }

    private static <T extends Component> T component(Container root, String name, Class<T> type) {
        Component found = find(root, name);
        if (found == null) {
            throw new AssertionError("Component was not found: " + name);
        }
        if (!type.isInstance(found)) {
            throw new AssertionError("Component " + name + " is not a " + type.getSimpleName() + ".");
        }
        return type.cast(found);
    }

    private static Component find(Component component, String name) {
        if (name.equals(component.getName())) {
            return component;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component found = find(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
