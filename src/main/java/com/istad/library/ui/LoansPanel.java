package com.istad.library.ui;

import com.istad.library.model.Book;
import com.istad.library.model.Loan;
import com.istad.library.model.User;
import com.istad.library.model.AccountStatus;
import com.istad.library.service.LibraryService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("serial")
final class LoansPanel extends JPanel {
    private final LibraryService service;
    private final Runnable onDataChanged;
    private final JComboBox<User> userBox = new JComboBox<>();
    private final JComboBox<Book> bookBox = new JComboBox<>();
    private final JTextField dueDateField = new JTextField(12);
    private final JTextField searchField = new JTextField(20);
    private final JCheckBox activeOnlyBox = new JCheckBox("Active loans only", true);
    private final JLabel feedbackLabel = new JLabel("Ready");
    private final JLabel countLabel = AppTheme.mutedLabel("0 loans");
    private final JButton borrowButton = AppTheme.primaryButton("Borrow Book");
    private final JButton returnButton = AppTheme.dangerButton("Return Selected");
    private final JButton invoiceButton = AppTheme.secondaryButton("Open Fine Invoice");
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"Loan ID", "Member", "Book", "Borrowed", "Due", "Returned", "Status", "Fine"},
            0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 3, 4, 5 -> LocalDate.class;
                case 7 -> Double.class;
                default -> String.class;
            };
        }
    };
    private final JTable table = new JTable(tableModel);

    LoansPanel(LibraryService service, Runnable onDataChanged) {
        this.service = service;
        this.onDataChanged = onDataChanged;
        setName("page.circulation");
        setLayout(new BorderLayout(0, 16));
        setBorder(BorderFactory.createEmptyBorder(24, 26, 20, 26));
        setBackground(AppTheme.BACKGROUND);
        dueDateField.setText(service.getToday().plusDays(14).toString());

        add(buildHeaderAndTransactionPanel(), BorderLayout.NORTH);
        add(buildTablePanel(), BorderLayout.CENTER);
        feedbackLabel.setForeground(AppTheme.MUTED);
        feedbackLabel.setBorder(BorderFactory.createEmptyBorder(2, 3, 0, 0));
        add(feedbackLabel, BorderLayout.SOUTH);

        activeOnlyBox.addActionListener(event -> refreshData());
        searchField.getDocument().addDocumentListener(documentListener(this::refreshData));
        returnButton.setEnabled(false);
        invoiceButton.setEnabled(false);
        refreshData();
    }

    void refreshData() {
        refreshChoices();
        tableModel.setRowCount(0);
        LocalDate today = service.getToday();
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        int count = 0;
        for (Loan loan : service.getLoans()) {
            if (activeOnlyBox.isSelected() && !loan.isActive()) {
                continue;
            }
            String member = service.findUserById(loan.getUserId())
                    .map(user -> user.getId() + " — " + user.getName())
                    .orElse(loan.getUserId());
            String book = service.findBookById(loan.getBookId())
                    .map(item -> item.getId() + " — " + item.getTitle())
                    .orElse(loan.getBookId());
            String status = statusText(loan, today);
            if (!query.isEmpty() && !(loan.getId() + " " + member + " " + book + " " + status)
                    .toLowerCase(Locale.ROOT)
                    .contains(query)) {
                continue;
            }
            double fine = loan.isActive() ? loan.calculateFine(today) : loan.getFineAmount();
            tableModel.addRow(new Object[]{
                    loan.getId(),
                    member,
                    book,
                    loan.getBorrowDate(),
                    loan.getDueDate(),
                    loan.getReturnDate(),
                    status,
                    fine
            });
            count++;
        }
        countLabel.setText(count + (count == 1 ? " loan" : " loans"));
        updateLoanActionButtons();
    }

    private JPanel buildHeaderAndTransactionPanel() {
        JPanel container = new JPanel(new BorderLayout(0, 16));
        container.setOpaque(false);
        JPanel heading = new JPanel(new GridLayout(2, 1, 0, 2));
        heading.setOpaque(false);
        heading.add(AppTheme.pageTitle("Circulation"));
        heading.add(AppTheme.mutedLabel("Borrow, return, and review loan history"));
        container.add(heading, BorderLayout.NORTH);
        container.add(buildTransactionPanel(), BorderLayout.CENTER);
        return container;
    }

    private JPanel buildTransactionPanel() {
        JPanel container = new JPanel(new BorderLayout(12, 0));
        container.setBackground(Color.WHITE);
        container.setBorder(AppTheme.sectionBorder("Create a loan"));

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(7, 8, 7, 8);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        userBox.setName("loans.user");
        bookBox.setName("loans.book");
        dueDateField.setName("loans.dueDate");
        addField(fields, constraints, 0, "Member", userBox);
        addField(fields, constraints, 1, "Book", bookBox);
        addField(fields, constraints, 2, "Due date (YYYY-MM-DD)", dueDateField);
        container.add(fields, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 22));
        actions.setOpaque(false);
        borrowButton.setName("loans.borrow");
        borrowButton.setMnemonic('L');
        borrowButton.addActionListener(event -> borrowBook());
        returnButton.setName("loans.return");
        returnButton.setMnemonic('R');
        returnButton.addActionListener(event -> returnSelectedLoan());
        invoiceButton.setName("loans.invoice");
        invoiceButton.setToolTipText("Create or reopen the PDF invoice for a returned overdue loan");
        invoiceButton.addActionListener(event -> openSelectedFineInvoice());
        actions.add(borrowButton);
        actions.add(returnButton);
        actions.add(invoiceButton);
        container.add(actions, BorderLayout.EAST);
        return container;
    }

    private JPanel buildTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        toolbar.setOpaque(false);
        toolbar.add(new JLabel("Search"));
        searchField.setName("loans.search");
        searchField.setToolTipText("Search by loan, member, book, or status");
        AppTheme.applyInputStyle(searchField);
        toolbar.add(searchField);
        activeOnlyBox.setName("loans.activeOnly");
        activeOnlyBox.setOpaque(false);
        toolbar.add(activeOnlyBox);
        JButton refreshButton = AppTheme.secondaryButton("Refresh");
        refreshButton.addActionListener(event -> refreshData());
        toolbar.add(refreshButton);
        panel.add(toolbar, BorderLayout.NORTH);

        table.setName("loans.table");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        AppTheme.styleTable(table);
        AppTheme.setCurrencyRenderer(table, 7);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateLoanActionButtons();
            }
        });
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(AppTheme.sectionBorder("Borrowing records"));
        scrollPane.getViewport().setBackground(Color.WHITE);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(countLabel, BorderLayout.SOUTH);
        return panel;
    }

    private void refreshChoices() {
        String selectedUser = selectedUserId();
        String selectedBook = selectedBookId();

        userBox.removeAllItems();
        for (User user : service.getUsers()) {
            if (user.getStatus() != AccountStatus.ACTIVE) {
                continue;
            }
            userBox.addItem(user);
            if (selectedUser != null && user.getId().equalsIgnoreCase(selectedUser)) {
                userBox.setSelectedItem(user);
            }
        }
        bookBox.removeAllItems();
        for (Book book : service.getBooks()) {
            if (book.getAvailable() <= 0) {
                continue;
            }
            bookBox.addItem(book);
            if (selectedBook != null && book.getId().equalsIgnoreCase(selectedBook)) {
                bookBox.setSelectedItem(book);
            }
        }
        borrowButton.setEnabled(userBox.getItemCount() > 0 && bookBox.getItemCount() > 0);
    }

    private void borrowBook() {
        User user = (User) userBox.getSelectedItem();
        Book book = (Book) bookBox.getSelectedItem();
        if (user == null || book == null) {
            showError(new IllegalArgumentException("An active member and an available book are required."),
                    "Cannot borrow book");
            return;
        }
        try {
            LocalDate dueDate = LocalDate.parse(dueDateField.getText().trim());
            Loan loan = service.borrowBook(user.getId(), book.getId(), dueDate);
            onDataChanged.run();
            setFeedback("Loan " + loan.getId() + " was created for " + user.getName() + ".", AppTheme.SUCCESS);
        } catch (DateTimeParseException exception) {
            showError(new IllegalArgumentException("Due date must use the YYYY-MM-DD format."), "Cannot borrow book");
        } catch (IllegalArgumentException | IOException exception) {
            showError(exception, "Cannot borrow book");
        }
    }

    private void returnSelectedLoan() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            setFeedback("Select an active loan before returning a book.", AppTheme.WARNING);
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        String loanId = tableModel.getValueAt(modelRow, 0).toString();
        Loan selected = service.findLoanById(loanId).orElse(null);
        if (selected == null || !selected.isActive()) {
            setFeedback("The selected loan is no longer active.", AppTheme.WARNING);
            refreshData();
            return;
        }

        double estimatedFine = selected.calculateFine(service.getToday());
        String confirmation = "Return loan " + selected.getId() + "?\n"
                + "Member: " + tableModel.getValueAt(modelRow, 1) + "\n"
                + "Book: " + tableModel.getValueAt(modelRow, 2) + "\n"
                + String.format("Fine due: $%.2f", estimatedFine);
        if (!UiMessages.confirm(this, "Confirm return", confirmation)) {
            return;
        }

        try {
            Loan returned = service.returnLoan(loanId, service.getToday());
            Path invoice = null;
            Exception invoiceFailure = null;
            if (returned.getFineAmount() > 0) {
                try {
                    invoice = service.generateFineInvoice(returned.getId());
                } catch (IllegalArgumentException | IOException exception) {
                    invoiceFailure = exception;
                }
            }
            onDataChanged.run();
            if (invoiceFailure != null) {
                setFeedback(
                        "Loan " + returned.getId() + " was returned, but its PDF invoice could not be created.",
                        AppTheme.WARNING
                );
                UiMessages.showError(this, "Book returned - invoice failed", invoiceFailure.getMessage());
            } else if (invoice != null) {
                showInvoice(invoice, returned);
            } else {
                setFeedback("Loan " + returned.getId() + " returned with no fine due.", AppTheme.SUCCESS);
            }
        } catch (IllegalArgumentException | IOException exception) {
            showError(exception, "Cannot return book");
        }
    }

    private void openSelectedFineInvoice() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            setFeedback("Select a returned loan with a fine first.", AppTheme.WARNING);
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        String loanId = tableModel.getValueAt(modelRow, 0).toString();
        try {
            Loan loan = service.findLoanById(loanId).orElseThrow(() -> new IllegalArgumentException(
                    "The selected loan no longer exists."
            ));
            Path invoice = service.generateFineInvoice(loanId);
            showInvoice(invoice, loan);
        } catch (IllegalArgumentException | IOException exception) {
            showError(exception, "Cannot create fine invoice");
        }
    }

    private void showInvoice(Path invoice, Loan loan) {
        setFeedback(
                "Fine invoice for " + loan.getId() + " saved to " + invoice,
                AppTheme.WARNING
        );
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            UiMessages.showInfo(this, "Fine invoice created", "Invoice saved to:\n" + invoice);
            return;
        }
        try {
            Desktop.getDesktop().open(invoice.toFile());
        } catch (IOException exception) {
            UiMessages.showInfo(
                    this,
                    "Fine invoice created",
                    "Invoice saved to:\n" + invoice + "\n\nIt could not be opened automatically."
            );
        }
    }

    private void updateLoanActionButtons() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            returnButton.setEnabled(false);
            invoiceButton.setEnabled(false);
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        String loanId = tableModel.getValueAt(modelRow, 0).toString();
        Loan loan = service.findLoanById(loanId).orElse(null);
        returnButton.setEnabled(loan != null && loan.isActive());
        invoiceButton.setEnabled(loan != null && !loan.isActive() && loan.getFineAmount() > 0);
    }

    private String selectedUserId() {
        User selected = (User) userBox.getSelectedItem();
        return selected == null ? null : selected.getId();
    }

    private String selectedBookId() {
        Book selected = (Book) bookBox.getSelectedItem();
        return selected == null ? null : selected.getId();
    }

    private void showError(Exception exception, String title) {
        setFeedback(exception.getMessage(), AppTheme.RED);
        UiMessages.showError(this, title, exception.getMessage());
    }

    private void setFeedback(String message, Color color) {
        feedbackLabel.setText(message);
        feedbackLabel.setForeground(color);
    }

    private static String statusText(Loan loan, LocalDate today) {
        if (!loan.isActive()) {
            return "Returned";
        }
        return loan.isOverdue(today) ? "Overdue" : "Active";
    }

    private static void addField(
            JPanel panel,
            GridBagConstraints constraints,
            int row,
            String label,
            javax.swing.JComponent field
    ) {
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 0;
        panel.add(new JLabel(label), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        AppTheme.applyInputStyle(field);
        panel.add(field, constraints);
    }

    private static DocumentListener documentListener(Runnable action) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                action.run();
            }
        };
    }
}
