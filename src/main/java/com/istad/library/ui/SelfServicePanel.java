package com.istad.library.ui;

import com.istad.library.model.Book;
import com.istad.library.model.Loan;
import com.istad.library.model.User;
import com.istad.library.service.LibraryService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.IOException;

@SuppressWarnings("serial")
final class SelfServicePanel extends JPanel {
    private static final int SESSION_TIMEOUT_MILLIS = 120_000;

    private final LibraryService service;
    private final Runnable onDataChanged;
    private final Runnable onSessionFinished;
    private final JTabbedPane tabs = new JTabbedPane();

    private final JTextField memberIdField = new JTextField(20);
    private final JPasswordField memberPhoneField = new JPasswordField(20);
    private final JComboBox<Book> bookBox = new JComboBox<>();
    private final JTextField borrowDateField = new JTextField(20);
    private final JTextField dueDateField = new JTextField(20);
    private final JCheckBox confirmationBox = new JCheckBox(
            "I confirm that this loan will be recorded on my membership."
    );
    private final JButton borrowButton = AppTheme.primaryButton("Confirm Borrowing");
    private final JLabel borrowFeedback = new JLabel("Enter your membership details to borrow a book.");

    private final JTextField registerNameField = new JTextField(22);
    private final JTextField registerEmailField = new JTextField(22);
    private final JTextField registerPhoneField = new JTextField(22);
    private final JLabel registrationResult = new JLabel(" ");
    private final Timer inactivityTimer = new Timer(SESSION_TIMEOUT_MILLIS, event -> resetSession());
    private boolean resettingSession;

    SelfServicePanel(LibraryService service, Runnable onDataChanged) {
        this(service, onDataChanged, () -> { });
    }

    SelfServicePanel(LibraryService service, Runnable onDataChanged, Runnable onSessionFinished) {
        this.service = service;
        this.onDataChanged = onDataChanged;
        this.onSessionFinished = onSessionFinished;
        setName("page.selfService");
        setLayout(new BorderLayout(0, 18));
        setBorder(BorderFactory.createEmptyBorder(24, 26, 22, 26));
        setBackground(AppTheme.BACKGROUND);

        add(buildHeader(), BorderLayout.NORTH);
        tabs.setName("self.tabs");
        tabs.addTab("Borrow a Book", buildBorrowPanel());
        tabs.addTab("Register Membership", buildRegistrationPanel());
        add(tabs, BorderLayout.CENTER);
        inactivityTimer.setRepeats(false);
        refreshData();
        installActivityTracking();
    }

    void refreshData() {
        try {
            service.refresh();
        } catch (IOException exception) {
            setBorrowFeedback("Could not refresh library data: " + exception.getMessage(), AppTheme.RED);
            return;
        }
        String selectedBookId = selectedBookId();
        bookBox.removeAllItems();
        for (Book book : service.getAvailableBooks()) {
            bookBox.addItem(book);
            if (selectedBookId != null && book.getId().equalsIgnoreCase(selectedBookId)) {
                bookBox.setSelectedItem(book);
            }
        }
        borrowDateField.setText(service.getToday().toString());
        dueDateField.setText(service.getToday().plusDays(LibraryService.DEFAULT_LOAN_DAYS).toString());
        updateBorrowButton();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel text = new JPanel(new GridLayout(2, 1, 0, 3));
        text.setOpaque(false);
        text.add(AppTheme.pageTitle("Member Self-Service"));
        text.add(AppTheme.mutedLabel("Borrow with your membership, or register in a minute"));
        header.add(text, BorderLayout.WEST);
        JButton finishButton = AppTheme.secondaryButton("Finish & Return");
        finishButton.setName("self.finish");
        finishButton.addActionListener(event -> {
            resetSession();
            onSessionFinished.run();
        });
        header.add(finishButton, BorderLayout.EAST);
        return header;
    }

    private JPanel buildBorrowPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 14));
        outer.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        outer.setBackground(Color.WHITE);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(AppTheme.sectionBorder("Your borrowing information"));
        GridBagConstraints constraints = constraints();

        memberIdField.setName("self.memberId");
        memberPhoneField.setName("self.memberPhone");
        bookBox.setName("self.book");
        borrowDateField.setName("self.borrowDate");
        dueDateField.setName("self.dueDate");
        borrowDateField.setEditable(false);
        dueDateField.setEditable(false);
        borrowDateField.setBackground(new Color(244, 246, 250));
        dueDateField.setBackground(new Color(244, 246, 250));

        addField(form, constraints, 0, "Membership ID *", memberIdField);
        addField(form, constraints, 1, "Registered phone *", memberPhoneField);
        addField(form, constraints, 2, "Available book *", bookBox);
        addField(form, constraints, 3, "Borrow date", borrowDateField);
        addField(form, constraints, 4, "Due date", dueDateField);

        confirmationBox.setName("self.confirm");
        confirmationBox.setOpaque(false);
        confirmationBox.addActionListener(event -> {
            noteActivity();
            updateBorrowButton();
        });
        constraints.gridx = 1;
        constraints.gridy = 5;
        constraints.gridwidth = 1;
        constraints.weightx = 1;
        form.add(confirmationBox, constraints);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actionPanel.setOpaque(false);
        borrowButton.setName("self.borrow");
        borrowButton.setMnemonic('L');
        borrowButton.addActionListener(event -> borrowBook());
        JButton clearButton = AppTheme.secondaryButton("Clear");
        clearButton.setName("self.borrow.clear");
        clearButton.addActionListener(event -> clearBorrowForm());
        actionPanel.add(borrowButton);
        actionPanel.add(clearButton);
        constraints.gridx = 0;
        constraints.gridy = 6;
        constraints.gridwidth = 2;
        constraints.weighty = 1;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        form.add(actionPanel, constraints);
        outer.add(form, BorderLayout.CENTER);

        JPanel footer = new JPanel(new GridLayout(2, 1, 0, 4));
        footer.setOpaque(false);
        borrowFeedback.setName("self.borrowFeedback");
        borrowFeedback.setForeground(AppTheme.MUTED);
        footer.add(borrowFeedback);
        footer.add(AppTheme.mutedLabel(
                "Phone verification is masked. Ask library staff if your membership has no registered phone."
        ));
        outer.add(footer, BorderLayout.SOUTH);
        return outer;
    }

    private JPanel buildRegistrationPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 14));
        outer.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        outer.setBackground(Color.WHITE);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(AppTheme.sectionBorder("Create your free membership"));
        GridBagConstraints constraints = constraints();

        registerNameField.setName("self.register.name");
        registerEmailField.setName("self.register.email");
        registerPhoneField.setName("self.register.phone");
        addField(form, constraints, 0, "Full name *", registerNameField);
        addField(form, constraints, 1, "Email address *", registerEmailField);
        addField(form, constraints, 2, "Phone number *", registerPhoneField);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actions.setOpaque(false);
        JButton registerButton = AppTheme.primaryButton("Create Membership");
        registerButton.setName("self.register.submit");
        registerButton.setMnemonic('G');
        registerButton.addActionListener(event -> registerMember());
        actions.add(registerButton);
        constraints.gridx = 0;
        constraints.gridy = 3;
        constraints.gridwidth = 2;
        constraints.weighty = 1;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        form.add(actions, constraints);
        outer.add(form, BorderLayout.CENTER);

        registrationResult.setName("self.register.result");
        registrationResult.setForeground(AppTheme.MUTED);
        registrationResult.setFont(registrationResult.getFont().deriveFont(Font.BOLD, 15f));
        outer.add(registrationResult, BorderLayout.SOUTH);
        return outer;
    }

    private void borrowBook() {
        noteActivity();
        refreshData();
        if (!confirmationBox.isSelected()) {
            showBorrowError(new IllegalArgumentException("Confirm the borrowing agreement before continuing."));
            return;
        }
        Book book = (Book) bookBox.getSelectedItem();
        if (book == null) {
            showBorrowError(new IllegalArgumentException("No book is currently available."));
            return;
        }
        try {
            Loan loan = service.borrowBookAsMember(
                    memberIdField.getText(), new String(memberPhoneField.getPassword()), book.getId()
            );
            onDataChanged.run();
            refreshData();
            clearSensitiveBorrowFields();
            registrationResult.setText(" ");
            registrationResult.setForeground(AppTheme.MUTED);
            setBorrowFeedback(
                    "Success! Loan " + loan.getId() + " is due on " + loan.getDueDate() + ".",
                    AppTheme.SUCCESS
            );
        } catch (IllegalArgumentException | IOException exception) {
            memberPhoneField.setText("");
            confirmationBox.setSelected(false);
            updateBorrowButton();
            showBorrowError(exception);
        }
    }

    private void registerMember() {
        noteActivity();
        try {
            User member = service.registerMember(
                    registerNameField.getText(), registerEmailField.getText(), registerPhoneField.getText()
            );
            onDataChanged.run();
            refreshData();
            memberIdField.setText(member.getId());
            memberPhoneField.setText("");
            registerNameField.setText("");
            registerEmailField.setText("");
            registerPhoneField.setText("");
            registrationResult.setText("Membership created. Your ID is " + member.getId() + ".");
            registrationResult.setForeground(AppTheme.SUCCESS);
            setBorrowFeedback(
                    "Welcome, " + member.getName() + "! Your membership ID is " + member.getId()
                            + ". Re-enter your phone, select a book, and confirm the loan.",
                    AppTheme.SUCCESS
            );
            tabs.setSelectedIndex(0);
            bookBox.requestFocusInWindow();
        } catch (IllegalArgumentException | IOException exception) {
            registrationResult.setText(exception.getMessage());
            registrationResult.setForeground(AppTheme.RED);
            UiMessages.showError(this, "Cannot create membership", exception.getMessage());
        }
    }

    private void clearBorrowForm() {
        clearSensitiveBorrowFields();
        setBorrowFeedback("Enter your membership details to borrow a book.", AppTheme.MUTED);
        memberIdField.requestFocusInWindow();
    }

    void resetSession() {
        inactivityTimer.stop();
        resettingSession = true;
        try {
            clearSensitiveBorrowFields();
            registerNameField.setText("");
            registerEmailField.setText("");
            registerPhoneField.setText("");
            registrationResult.setText(" ");
            registrationResult.setForeground(AppTheme.MUTED);
            setBorrowFeedback("Enter your membership details to borrow a book.", AppTheme.MUTED);
            tabs.setSelectedIndex(0);
            refreshData();
        } finally {
            resettingSession = false;
        }
        memberIdField.requestFocusInWindow();
    }

    private void clearSensitiveBorrowFields() {
        memberIdField.setText("");
        memberPhoneField.setText("");
        confirmationBox.setSelected(false);
        updateBorrowButton();
    }

    private void installActivityTracking() {
        DocumentListener activity = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                noteActivity();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                noteActivity();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                noteActivity();
            }
        };
        memberIdField.getDocument().addDocumentListener(activity);
        memberPhoneField.getDocument().addDocumentListener(activity);
        registerNameField.getDocument().addDocumentListener(activity);
        registerEmailField.getDocument().addDocumentListener(activity);
        registerPhoneField.getDocument().addDocumentListener(activity);
        bookBox.addActionListener(event -> noteActivity());
        tabs.addChangeListener(event -> noteActivity());
    }

    private void noteActivity() {
        if (!resettingSession) {
            inactivityTimer.restart();
        }
    }

    private void updateBorrowButton() {
        borrowButton.setEnabled(bookBox.getItemCount() > 0 && confirmationBox.isSelected());
    }

    private String selectedBookId() {
        Book selected = (Book) bookBox.getSelectedItem();
        return selected == null ? null : selected.getId();
    }

    private void showBorrowError(Exception exception) {
        setBorrowFeedback(exception.getMessage(), AppTheme.RED);
        UiMessages.showError(this, "Cannot borrow book", exception.getMessage());
    }

    private void setBorrowFeedback(String message, Color color) {
        borrowFeedback.setText(message);
        borrowFeedback.setForeground(color);
    }

    private static GridBagConstraints constraints() {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(8, 10, 8, 10);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        return constraints;
    }

    private static void addField(
            JPanel panel,
            GridBagConstraints constraints,
            int row,
            String text,
            JComponent component
    ) {
        JLabel label = new JLabel(text);
        label.setLabelFor(component);
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 1;
        constraints.weightx = 0;
        panel.add(label, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        AppTheme.applyInputStyle(component);
        panel.add(component, constraints);
    }
}
