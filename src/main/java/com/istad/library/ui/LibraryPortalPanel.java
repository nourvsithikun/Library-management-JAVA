package com.istad.library.ui;

import com.istad.library.service.LibraryService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.Arrays;

/** One-window entry flow for member access and password-protected librarian access. */
@SuppressWarnings("serial")
public final class LibraryPortalPanel extends JPanel {
    private static final String ACCESS = "access";
    private static final String MEMBER = "member";
    private static final String ADMIN = "admin";
    private static final String ADMIN_USERNAME = "admin";
    private static final char[] ADMIN_PASSWORD = "admin123".toCharArray();

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final JTextField usernameField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final JLabel loginFeedback = new JLabel(" ");
    private final LibraryApplicationPanel adminPanel;
    private final SelfServicePanel memberPanel;
    private String visibleCard = ACCESS;

    public LibraryPortalPanel(LibraryService service, Path dataDirectory) {
        setName("application.portal");
        setLayout(new BorderLayout());
        setBackground(AppTheme.BACKGROUND);

        adminPanel = new LibraryApplicationPanel(service, dataDirectory, this::showMemberPortal, this::logout);
        memberPanel = new SelfServicePanel(service, adminPanel::refreshAll, this::logout);
        JPanel accessPanel = buildAccessPanel();

        accessPanel.setName("portal.access");
        adminPanel.setName("portal.admin");
        cards.add(accessPanel, ACCESS);
        cards.add(memberPanel, MEMBER);
        cards.add(adminPanel, ADMIN);
        add(cards, BorderLayout.CENTER);
        showAccessPage();
    }

    void refreshVisiblePage() {
        if (ADMIN.equals(visibleCard)) {
            adminPanel.refreshAll();
        } else if (MEMBER.equals(visibleCard)) {
            memberPanel.refreshData();
        }
    }

    private JPanel buildAccessPanel() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(AppTheme.BACKGROUND);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppTheme.NAVY);
        header.setBorder(BorderFactory.createEmptyBorder(22, 30, 22, 30));
        JLabel brand = new JLabel("ISTAD LIBRARY");
        brand.setForeground(Color.WHITE);
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 24f));
        header.add(brand, BorderLayout.WEST);
        JLabel subtitle = new JLabel("LIBRARY MANAGEMENT SYSTEM");
        subtitle.setForeground(new Color(190, 201, 228));
        subtitle.setFont(subtitle.getFont().deriveFont(Font.BOLD, 12f));
        header.add(subtitle, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 32));
        JPanel choices = new JPanel(new GridLayout(1, 2, 24, 0));
        choices.setOpaque(false);
        choices.setPreferredSize(new Dimension(850, 410));
        choices.add(buildMemberCard());
        choices.add(buildAdminCard());
        center.add(choices);
        root.add(center, BorderLayout.CENTER);

        JLabel footer = AppTheme.mutedLabel(
                "Members can register and borrow. Librarian tools require administrator credentials."
        );
        footer.setBorder(BorderFactory.createEmptyBorder(10, 24, 16, 24));
        root.add(footer, BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildMemberCard() {
        JPanel card = accessCard();
        card.setLayout(new BorderLayout(0, 18));

        JPanel text = new JPanel(new GridLayout(4, 1, 0, 8));
        text.setOpaque(false);
        JLabel role = new JLabel("MEMBER ACCESS");
        role.setForeground(AppTheme.BLUE);
        role.setFont(role.getFont().deriveFont(Font.BOLD, 12f));
        text.add(role);
        text.add(AppTheme.pageTitle("Library Member"));
        text.add(new JLabel("Register a membership or borrow an available book."));
        text.add(AppTheme.mutedLabel("No librarian login is required."));
        card.add(text, BorderLayout.NORTH);

        JPanel information = new JPanel(new GridLayout(3, 1, 0, 10));
        information.setOpaque(false);
        information.add(new JLabel("• Create your own membership"));
        information.add(new JLabel("• Verify with your ID and phone"));
        information.add(new JLabel("• Receive an automatic 14-day due date"));
        card.add(information, BorderLayout.CENTER);

        JButton memberButton = AppTheme.primaryButton("Open Member Portal");
        memberButton.setName("access.member");
        memberButton.addActionListener(event -> showMemberPortal());
        card.add(memberButton, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildAdminCard() {
        JPanel card = accessCard();
        card.setLayout(new BorderLayout(0, 18));

        JPanel text = new JPanel(new GridLayout(3, 1, 0, 8));
        text.setOpaque(false);
        JLabel role = new JLabel("LIBRARIAN ACCESS");
        role.setForeground(AppTheme.BLUE);
        role.setFont(role.getFont().deriveFont(Font.BOLD, 12f));
        text.add(role);
        text.add(AppTheme.pageTitle("Administrator Login"));
        text.add(AppTheme.mutedLabel("Sign in to manage books, members, loans, and returns."));
        card.add(text, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(7, 0, 7, 0);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0;
        constraints.weightx = 1;

        usernameField.setName("access.admin.username");
        passwordField.setName("access.admin.password");
        AppTheme.applyInputStyle(usernameField);
        AppTheme.applyInputStyle(passwordField);
        addLoginField(form, constraints, 0, "Username", usernameField);
        addLoginField(form, constraints, 2, "Password", passwordField);
        passwordField.addActionListener(event -> attemptAdminLogin());

        loginFeedback.setName("access.admin.feedback");
        loginFeedback.setForeground(AppTheme.RED);
        constraints.gridy = 4;
        form.add(loginFeedback, constraints);

        JButton loginButton = AppTheme.primaryButton("Sign In as Librarian");
        loginButton.setName("access.admin.login");
        loginButton.addActionListener(event -> attemptAdminLogin());
        constraints.gridy = 5;
        form.add(loginButton, constraints);
        card.add(form, BorderLayout.CENTER);
        return card;
    }

    private void attemptAdminLogin() {
        char[] suppliedPassword = passwordField.getPassword();
        boolean authenticated = ADMIN_USERNAME.equals(usernameField.getText().trim())
                && Arrays.equals(ADMIN_PASSWORD, suppliedPassword);
        Arrays.fill(suppliedPassword, '\0');
        passwordField.setText("");

        if (!authenticated) {
            loginFeedback.setText("Incorrect username or password.");
            passwordField.requestFocusInWindow();
            return;
        }

        usernameField.setText("");
        loginFeedback.setText(" ");
        adminPanel.refreshAll();
        showCard(ADMIN);
    }

    private void showMemberPortal() {
        memberPanel.resetSession();
        showCard(MEMBER);
    }

    private void logout() {
        memberPanel.resetSession();
        usernameField.setText("");
        passwordField.setText("");
        loginFeedback.setText(" ");
        showAccessPage();
    }

    private void showAccessPage() {
        showCard(ACCESS);
        usernameField.requestFocusInWindow();
    }

    private void showCard(String card) {
        visibleCard = card;
        cardLayout.show(cards, card);
    }

    private static JPanel accessCard() {
        JPanel card = new JPanel();
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.BORDER),
                BorderFactory.createEmptyBorder(28, 28, 28, 28)
        ));
        return card;
    }

    private static void addLoginField(
            JPanel panel,
            GridBagConstraints constraints,
            int row,
            String labelText,
            JTextField field
    ) {
        constraints.gridy = row;
        panel.add(new JLabel(labelText), constraints);
        constraints.gridy = row + 1;
        panel.add(field, constraints);
    }
}
