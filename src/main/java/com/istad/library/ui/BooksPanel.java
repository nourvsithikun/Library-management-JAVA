package com.istad.library.ui;

import com.istad.library.model.Book;
import com.istad.library.service.LibraryService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.List;

@SuppressWarnings("serial")
final class BooksPanel extends JPanel {
    private final LibraryService service;
    private final Runnable onDataChanged;
    private final JTextField searchField = new JTextField(25);
    private final JTextField idField = new JTextField(20);
    private final JTextField titleField = new JTextField(20);
    private final JTextField authorField = new JTextField(20);
    private final JTextField categoryField = new JTextField(20);
    private final JSpinner quantitySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100000, 1));
    private final JLabel availabilityLabel = AppTheme.mutedLabel("Available: —");
    private final JLabel feedbackLabel = new JLabel("Ready");
    private final JLabel countLabel = AppTheme.mutedLabel("0 titles");
    private final JButton addButton = AppTheme.primaryButton("Add Book");
    private final JButton updateButton = AppTheme.secondaryButton("Save Changes");
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"Book ID", "Title", "Author", "Category", "Copies", "Available"},
            0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex >= 4 ? Integer.class : String.class;
        }
    };
    private final JTable table = new JTable(tableModel);

    BooksPanel(LibraryService service, Runnable onDataChanged) {
        this.service = service;
        this.onDataChanged = onDataChanged;
        setName("page.books");
        setLayout(new BorderLayout(0, 16));
        setBorder(BorderFactory.createEmptyBorder(24, 26, 20, 26));
        setBackground(AppTheme.BACKGROUND);
        add(buildHeader(), BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildFormPanel(), buildTablePanel());
        splitPane.setResizeWeight(0.30);
        splitPane.setDividerLocation(350);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);
        add(splitPane, BorderLayout.CENTER);

        feedbackLabel.setForeground(AppTheme.MUTED);
        feedbackLabel.setBorder(BorderFactory.createEmptyBorder(2, 3, 0, 0));
        add(feedbackLabel, BorderLayout.SOUTH);
        searchField.getDocument().addDocumentListener(documentListener(this::refreshData));
        clearForm();
        refreshData();
    }

    void refreshData() {
        List<Book> matches = service.searchBooks(searchField.getText());
        showBooks(matches);
        countLabel.setText(matches.size() + (matches.size() == 1 ? " title" : " titles"));
    }

    private JPanel buildHeader() {
        JPanel container = new JPanel(new BorderLayout(0, 16));
        container.setOpaque(false);
        JPanel heading = new JPanel(new GridLayout(2, 1, 0, 2));
        heading.setOpaque(false);
        heading.add(AppTheme.pageTitle("Books"));
        heading.add(AppTheme.mutedLabel("Maintain the catalogue and inventory copy counts"));
        container.add(heading, BorderLayout.NORTH);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        toolbar.setOpaque(false);
        toolbar.add(new JLabel("Search"));
        searchField.setName("books.search");
        searchField.setToolTipText("Search by ID, title, author, or category");
        AppTheme.applyInputStyle(searchField);
        toolbar.add(searchField);
        JButton clearSearch = AppTheme.secondaryButton("Clear Search");
        clearSearch.addActionListener(event -> searchField.setText(""));
        toolbar.add(clearSearch);
        container.add(toolbar, BorderLayout.SOUTH);
        return container;
    }

    private JPanel buildFormPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(AppTheme.sectionBorder("Book details"));
        GridBagConstraints constraints = baseConstraints();

        idField.setName("books.id");
        titleField.setName("books.title");
        authorField.setName("books.author");
        categoryField.setName("books.category");
        quantitySpinner.setName("books.quantity");
        addField(panel, constraints, 0, "Book ID *", idField);
        addField(panel, constraints, 1, "Title *", titleField);
        addField(panel, constraints, 2, "Author", authorField);
        addField(panel, constraints, 3, "Category", categoryField);
        addField(panel, constraints, 4, "Total copies *", quantitySpinner);

        availabilityLabel.setName("books.availability");
        constraints.gridx = 1;
        constraints.gridy = 5;
        constraints.weightx = 1;
        panel.add(availabilityLabel, constraints);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actions.setOpaque(false);
        addButton.setName("books.add");
        addButton.setMnemonic('A');
        addButton.addActionListener(event -> addBook());
        updateButton.setName("books.update");
        updateButton.setMnemonic('S');
        updateButton.addActionListener(event -> updateBook());
        JButton clearButton = AppTheme.secondaryButton("New / Clear");
        clearButton.setName("books.clear");
        clearButton.addActionListener(event -> clearForm());
        actions.add(addButton);
        actions.add(updateButton);
        actions.add(clearButton);

        constraints.gridx = 0;
        constraints.gridy = 6;
        constraints.gridwidth = 2;
        constraints.weighty = 1;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        panel.add(actions, constraints);
        return panel;
    }

    private JPanel buildTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        table.setName("books.table");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        AppTheme.styleTable(table);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                populateFromSelection();
            }
        });
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(AppTheme.sectionBorder("Book catalogue"));
        scrollPane.getViewport().setBackground(Color.WHITE);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(countLabel, BorderLayout.SOUTH);
        return panel;
    }

    private void addBook() {
        try {
            Book book = service.addBook(
                    idField.getText(),
                    titleField.getText(),
                    authorField.getText(),
                    categoryField.getText(),
                    (Integer) quantitySpinner.getValue()
            );
            onDataChanged.run();
            searchField.setText(book.getId());
            populateForm(book);
            setFeedback("Book " + book.getId() + " was added to the catalogue.", AppTheme.SUCCESS);
        } catch (IllegalArgumentException | IOException exception) {
            showError(exception);
        }
    }

    private void updateBook() {
        try {
            Book book = service.updateBook(
                    idField.getText(),
                    titleField.getText(),
                    authorField.getText(),
                    categoryField.getText(),
                    (Integer) quantitySpinner.getValue()
            );
            onDataChanged.run();
            populateForm(book);
            setFeedback("Book " + book.getId() + " was updated.", AppTheme.SUCCESS);
        } catch (IllegalArgumentException | IOException exception) {
            showError(exception);
        }
    }

    private void showBooks(List<Book> books) {
        tableModel.setRowCount(0);
        for (Book book : books) {
            tableModel.addRow(new Object[]{
                    book.getId(), book.getTitle(), book.getAuthor(), book.getCategory(),
                    book.getQuantity(), book.getAvailable()
            });
        }
    }

    private void populateFromSelection() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        String id = tableModel.getValueAt(modelRow, 0).toString();
        service.findBookById(id).ifPresent(this::populateForm);
    }

    private void populateForm(Book book) {
        idField.setText(book.getId());
        titleField.setText(book.getTitle());
        authorField.setText(book.getAuthor());
        categoryField.setText(book.getCategory());
        quantitySpinner.setValue(book.getQuantity());
        availabilityLabel.setText("Available: " + book.getAvailable() + " of " + book.getQuantity());
        idField.setEditable(false);
        addButton.setEnabled(false);
        updateButton.setEnabled(true);
    }

    private void clearForm() {
        table.clearSelection();
        idField.setText("");
        titleField.setText("");
        authorField.setText("");
        categoryField.setText("");
        quantitySpinner.setValue(1);
        availabilityLabel.setText("Available: —");
        idField.setEditable(true);
        addButton.setEnabled(true);
        updateButton.setEnabled(false);
        idField.requestFocusInWindow();
        setFeedback("Enter details to add a new book.", AppTheme.MUTED);
    }

    private void showError(Exception exception) {
        setFeedback(exception.getMessage(), AppTheme.RED);
        UiMessages.showError(this, "Cannot save book", exception.getMessage());
    }

    private void setFeedback(String message, Color color) {
        feedbackLabel.setText(message);
        feedbackLabel.setForeground(color);
    }

    private static GridBagConstraints baseConstraints() {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(7, 8, 7, 8);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        return constraints;
    }

    private static void addField(
            JPanel panel,
            GridBagConstraints constraints,
            int row,
            String label,
            java.awt.Component component
    ) {
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 1;
        constraints.weightx = 0;
        panel.add(new JLabel(label), constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        if (component instanceof javax.swing.JComponent swingComponent) {
            AppTheme.applyInputStyle(swingComponent);
        }
        panel.add(component, constraints);
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
