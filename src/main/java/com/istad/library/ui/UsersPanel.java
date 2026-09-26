package com.istad.library.ui;

import com.istad.library.model.AccountStatus;
import com.istad.library.model.User;
import com.istad.library.service.LibraryService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
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
final class UsersPanel extends JPanel {
    private static final String ALL_STATUSES = "All statuses";

    private final LibraryService service;
    private final Runnable onDataChanged;
    private final JTextField searchField = new JTextField(22);
    private final JComboBox<String> statusFilter = new JComboBox<>(
            new String[]{ALL_STATUSES, AccountStatus.ACTIVE.name(), AccountStatus.INACTIVE.name()}
    );
    private final JTextField idField = new JTextField(20);
    private final JTextField nameField = new JTextField(20);
    private final JTextField emailField = new JTextField(20);
    private final JTextField phoneField = new JTextField(20);
    private final JComboBox<AccountStatus> statusBox = new JComboBox<>(AccountStatus.values());
    private final JLabel feedbackLabel = new JLabel("Ready");
    private final JLabel countLabel = AppTheme.mutedLabel("0 members");
    private final JButton addButton = AppTheme.primaryButton("Add Member");
    private final JButton updateButton = AppTheme.secondaryButton("Save Changes");
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"Member ID", "Name", "Email", "Phone", "Status"},
            0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 4 ? AccountStatus.class : String.class;
        }
    };
    private final JTable table = new JTable(tableModel);

    UsersPanel(LibraryService service, Runnable onDataChanged) {
        this.service = service;
        this.onDataChanged = onDataChanged;
        setName("page.members");
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
        statusFilter.addActionListener(event -> refreshData());
        clearForm();
        refreshData();
    }

    void refreshData() {
        String selectedStatus = (String) statusFilter.getSelectedItem();
        String query = searchField.getText().trim();
        List<User> searchResults = query.isEmpty()
                ? service.getUsers()
                : service.findUserById(query).map(List::of).orElseGet(() -> service.searchUsers(query));
        List<User> matches = searchResults.stream()
                .filter(user -> ALL_STATUSES.equals(selectedStatus) || user.getStatus().name().equals(selectedStatus))
                .toList();
        showUsers(matches);
        countLabel.setText(matches.size() + (matches.size() == 1 ? " member" : " members"));
    }

    private JPanel buildHeader() {
        JPanel container = new JPanel(new BorderLayout(0, 16));
        container.setOpaque(false);

        JPanel heading = new JPanel(new GridLayout(2, 1, 0, 2));
        heading.setOpaque(false);
        heading.add(AppTheme.pageTitle("Members"));
        heading.add(AppTheme.mutedLabel("Register members and manage borrowing eligibility"));
        container.add(heading, BorderLayout.NORTH);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        toolbar.setOpaque(false);
        toolbar.add(new JLabel("Search"));
        searchField.setName("members.search");
        searchField.setToolTipText("Search by member ID, name, email, or phone");
        AppTheme.applyInputStyle(searchField);
        toolbar.add(searchField);
        statusFilter.setName("members.statusFilter");
        AppTheme.applyInputStyle(statusFilter);
        toolbar.add(statusFilter);
        JButton clearSearch = AppTheme.secondaryButton("Clear Filters");
        clearSearch.addActionListener(event -> {
            searchField.setText("");
            statusFilter.setSelectedItem(ALL_STATUSES);
        });
        toolbar.add(clearSearch);
        container.add(toolbar, BorderLayout.SOUTH);
        return container;
    }

    private JPanel buildFormPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(AppTheme.sectionBorder("Member details"));
        GridBagConstraints constraints = baseConstraints();

        idField.setName("members.id");
        nameField.setName("members.name");
        emailField.setName("members.email");
        phoneField.setName("members.phone");
        statusBox.setName("members.status");
        addField(panel, constraints, 0, "Member ID *", idField);
        addField(panel, constraints, 1, "Full name *", nameField);
        addField(panel, constraints, 2, "Email", emailField);
        addField(panel, constraints, 3, "Phone", phoneField);
        addField(panel, constraints, 4, "Account status", statusBox);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actions.setOpaque(false);
        addButton.setName("members.add");
        addButton.setMnemonic('A');
        addButton.addActionListener(event -> addUser());
        updateButton.setName("members.update");
        updateButton.setMnemonic('S');
        updateButton.addActionListener(event -> updateUser());
        JButton clearButton = AppTheme.secondaryButton("New / Clear");
        clearButton.setName("members.clear");
        clearButton.addActionListener(event -> clearForm());
        actions.add(addButton);
        actions.add(updateButton);
        actions.add(clearButton);

        constraints.gridx = 0;
        constraints.gridy = 5;
        constraints.gridwidth = 2;
        constraints.weighty = 1;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        panel.add(actions, constraints);
        return panel;
    }

    private JPanel buildTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        table.setName("members.table");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        AppTheme.styleTable(table);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                populateFromSelection();
            }
        });
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(AppTheme.sectionBorder("Registered members"));
        scrollPane.getViewport().setBackground(Color.WHITE);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(countLabel, BorderLayout.SOUTH);
        return panel;
    }

    private void addUser() {
        try {
            User user = service.addUser(
                    idField.getText(),
                    nameField.getText(),
                    emailField.getText(),
                    phoneField.getText(),
                    (AccountStatus) statusBox.getSelectedItem()
            );
            onDataChanged.run();
            searchField.setText(user.getId());
            populateForm(user);
            setFeedback("Member " + user.getId() + " was created and saved.", AppTheme.SUCCESS);
        } catch (IllegalArgumentException | IOException exception) {
            showError(exception);
        }
    }

    private void updateUser() {
        try {
            User user = service.updateUser(
                    idField.getText(),
                    nameField.getText(),
                    emailField.getText(),
                    phoneField.getText(),
                    (AccountStatus) statusBox.getSelectedItem()
            );
            onDataChanged.run();
            populateForm(user);
            setFeedback("Member " + user.getId() + " was updated.", AppTheme.SUCCESS);
        } catch (IllegalArgumentException | IOException exception) {
            showError(exception);
        }
    }

    private void showUsers(List<User> users) {
        tableModel.setRowCount(0);
        for (User user : users) {
            tableModel.addRow(new Object[]{
                    user.getId(), user.getName(), user.getEmail(), user.getPhone(), user.getStatus()
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
        service.findUserById(id).ifPresent(this::populateForm);
    }

    private void populateForm(User user) {
        idField.setText(user.getId());
        nameField.setText(user.getName());
        emailField.setText(user.getEmail());
        phoneField.setText(user.getPhone());
        statusBox.setSelectedItem(user.getStatus());
        idField.setEditable(false);
        addButton.setEnabled(false);
        updateButton.setEnabled(true);
    }

    private void clearForm() {
        table.clearSelection();
        idField.setText("");
        nameField.setText("");
        emailField.setText("");
        phoneField.setText("");
        statusBox.setSelectedItem(AccountStatus.ACTIVE);
        idField.setEditable(true);
        addButton.setEnabled(true);
        updateButton.setEnabled(false);
        idField.requestFocusInWindow();
        setFeedback("Enter details to register a new member.", AppTheme.MUTED);
    }

    private void showError(Exception exception) {
        setFeedback(exception.getMessage(), AppTheme.RED);
        UiMessages.showError(this, "Cannot save member", exception.getMessage());
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
