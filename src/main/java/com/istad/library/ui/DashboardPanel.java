package com.istad.library.ui;

import com.istad.library.model.Loan;
import com.istad.library.service.LibraryService;
import com.istad.library.service.LibraryStatistics;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

@SuppressWarnings("serial")
final class DashboardPanel extends JPanel {
    private final LibraryService service;
    private final Consumer<String> navigate;
    private final Runnable openSelfService;
    private final JLabel usersValue = metricValue("metric.users");
    private final JLabel titlesValue = metricValue("metric.titles");
    private final JLabel availableValue = metricValue("metric.available");
    private final JLabel activeLoansValue = metricValue("metric.activeLoans");
    private final JLabel overdueValue = metricValue("metric.overdue");
    private final DefaultTableModel loanModel = new DefaultTableModel(
            new Object[]{"Loan ID", "Member", "Book", "Due Date", "Status", "Fine"},
            0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 3 -> LocalDate.class;
                case 5 -> Double.class;
                default -> String.class;
            };
        }
    };
    private final JTable table = new JTable(loanModel);

    DashboardPanel(LibraryService service) {
        this(service, page -> { }, () -> { });
    }

    DashboardPanel(LibraryService service, Consumer<String> navigate) {
        this(service, navigate, () -> { });
    }

    DashboardPanel(LibraryService service, Consumer<String> navigate, Runnable openSelfService) {
        this.service = service;
        this.navigate = navigate;
        this.openSelfService = openSelfService;
        setName("page.overview");
        setLayout(new BorderLayout(0, 18));
        setBorder(BorderFactory.createEmptyBorder(24, 26, 24, 26));
        setBackground(AppTheme.BACKGROUND);

        JPanel top = new JPanel(new BorderLayout(0, 18));
        top.setOpaque(false);
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JPanel headingText = new JPanel(new GridLayout(2, 1, 0, 2));
        headingText.setOpaque(false);
        headingText.add(AppTheme.pageTitle("Library Overview"));
        headingText.add(AppTheme.mutedLabel("Today’s collection and circulation summary"));
        heading.add(headingText, BorderLayout.WEST);
        heading.add(buildQuickActions(), BorderLayout.EAST);
        top.add(heading, BorderLayout.NORTH);

        JPanel metrics = new JPanel(new GridLayout(1, 5, 12, 0));
        metrics.setOpaque(false);
        metrics.add(metricPanel("Registered Members", usersValue));
        metrics.add(metricPanel("Book Titles", titlesValue));
        metrics.add(metricPanel("Available Copies", availableValue));
        metrics.add(metricPanel("Active Loans", activeLoansValue));
        metrics.add(metricPanel("Overdue", overdueValue));
        top.add(metrics, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        AppTheme.styleTable(table);
        AppTheme.setCurrencyRenderer(table, 5);
        table.setName("dashboard.recentLoans");
        table.setAutoCreateRowSorter(true);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(AppTheme.sectionBorder("Recent borrowing activity"));
        scrollPane.getViewport().setBackground(Color.WHITE);
        add(scrollPane, BorderLayout.CENTER);
        refreshData();
    }

    void refreshData() {
        LibraryStatistics statistics = service.getStatistics();
        usersValue.setText(Integer.toString(statistics.registeredUsers()));
        titlesValue.setText(Integer.toString(statistics.bookTitles()));
        availableValue.setText(Integer.toString(statistics.availableCopies()));
        activeLoansValue.setText(Long.toString(statistics.activeLoans()));
        overdueValue.setText(Long.toString(statistics.overdueLoans()));
        overdueValue.setForeground(statistics.overdueLoans() > 0 ? AppTheme.RED : AppTheme.BLUE);

        loanModel.setRowCount(0);
        List<Loan> recentLoans = service.getLoans().stream().limit(12).toList();
        LocalDate today = service.getToday();
        for (Loan loan : recentLoans) {
            double fine = loan.isActive() ? loan.calculateFine(today) : loan.getFineAmount();
            String member = service.findUserById(loan.getUserId())
                    .map(user -> user.getId() + " — " + user.getName())
                    .orElse(loan.getUserId());
            String book = service.findBookById(loan.getBookId())
                    .map(item -> item.getId() + " — " + item.getTitle())
                    .orElse(loan.getBookId());
            loanModel.addRow(new Object[]{
                    loan.getId(), member, book, loan.getDueDate(), statusText(loan, today), fine
            });
        }
    }

    private JPanel buildQuickActions() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        panel.setOpaque(false);
        JButton member = AppTheme.secondaryButton("+ Member");
        member.setToolTipText("Open member management");
        member.addActionListener(event -> navigate.accept(LibraryApplicationPanel.MEMBERS));
        JButton book = AppTheme.secondaryButton("+ Book");
        book.setToolTipText("Open book management");
        book.addActionListener(event -> navigate.accept(LibraryApplicationPanel.BOOKS));
        JButton borrow = AppTheme.primaryButton("Member Self-Service");
        borrow.setName("dashboard.selfService");
        borrow.setToolTipText("Open the member registration and borrowing window");
        borrow.addActionListener(event -> openSelfService.run());
        panel.add(member);
        panel.add(book);
        panel.add(borrow);
        return panel;
    }

    private static JPanel metricPanel(String labelText, JLabel value) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(Color.WHITE);
        panel.setBorder(AppTheme.cardBorder());
        JLabel label = new JLabel(labelText, SwingConstants.CENTER);
        label.setForeground(AppTheme.MUTED);
        panel.add(value, BorderLayout.CENTER);
        panel.add(label, BorderLayout.SOUTH);
        return panel;
    }

    private static JLabel metricValue(String name) {
        JLabel label = new JLabel("0", SwingConstants.CENTER);
        label.setName(name);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 28f));
        label.setForeground(AppTheme.BLUE);
        return label;
    }

    private static String statusText(Loan loan, LocalDate today) {
        if (!loan.isActive()) {
            return "Returned";
        }
        return loan.isOverdue(today) ? "Overdue" : "Active";
    }
}
