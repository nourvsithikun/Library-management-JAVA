package com.istad.library.ui;

import com.istad.library.service.LibraryService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** The complete application content, separated from JFrame so it can be tested headlessly. */
@SuppressWarnings("serial")
public final class LibraryApplicationPanel extends JPanel {
    static final String OVERVIEW = "overview";
    static final String MEMBERS = "members";
    static final String BOOKS = "books";
    static final String CIRCULATION = "circulation";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final Map<String, JButton> navigationButtons = new LinkedHashMap<>();
    private final LibraryService service;
    private final DashboardPanel dashboardPanel;
    private final UsersPanel usersPanel;
    private final BooksPanel booksPanel;
    private final LoansPanel loansPanel;
    private final Runnable openMemberPortal;
    private final Runnable logout;

    public LibraryApplicationPanel(LibraryService service, Path dataDirectory) {
        this(service, dataDirectory, () -> { }, () -> { });
    }

    LibraryApplicationPanel(
            LibraryService service,
            Path dataDirectory,
            Runnable openMemberPortal,
            Runnable logout
    ) {
        this.service = service;
        this.openMemberPortal = openMemberPortal;
        this.logout = logout;
        setLayout(new BorderLayout());
        setBackground(AppTheme.BACKGROUND);

        dashboardPanel = new DashboardPanel(service, this::showPage, openMemberPortal);
        usersPanel = new UsersPanel(service, this::refreshAll);
        booksPanel = new BooksPanel(service, this::refreshAll);
        loansPanel = new LoansPanel(service, this::refreshAll);

        cards.add(dashboardPanel, OVERVIEW);
        cards.add(usersPanel, MEMBERS);
        cards.add(booksPanel, BOOKS);
        cards.add(loansPanel, CIRCULATION);

        add(buildNavigation(), BorderLayout.WEST);
        add(cards, BorderLayout.CENTER);
        add(buildStatusBar(dataDirectory), BorderLayout.SOUTH);
        showPage(OVERVIEW);
    }

    void refreshAll() {
        try {
            service.refresh();
        } catch (java.io.IOException exception) {
            UiMessages.showError(this, "Cannot refresh library data", exception.getMessage());
        }
        dashboardPanel.refreshData();
        usersPanel.refreshData();
        booksPanel.refreshData();
        loansPanel.refreshData();
    }

    void showPage(String page) {
        cardLayout.show(cards, page);
        navigationButtons.forEach((name, button) -> AppTheme.setNavigationSelected(button, name.equals(page)));
        refreshAll();
    }

    private JPanel buildNavigation() {
        JPanel navigation = new JPanel();
        navigation.setLayout(new BoxLayout(navigation, BoxLayout.Y_AXIS));
        navigation.setBackground(AppTheme.NAVY);
        navigation.setPreferredSize(new Dimension(210, 0));
        navigation.setBorder(BorderFactory.createEmptyBorder(22, 14, 18, 14));

        JLabel brand = new JLabel("ISTAD LIBRARY");
        brand.setForeground(Color.WHITE);
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 20f));
        brand.setAlignmentX(LEFT_ALIGNMENT);
        navigation.add(brand);
        JLabel subtitle = new JLabel("Management System");
        subtitle.setForeground(new Color(180, 191, 220));
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        navigation.add(subtitle);
        navigation.add(Box.createVerticalStrut(30));

        addNavigationButton(navigation, OVERVIEW, "Overview", 'O');
        addNavigationButton(navigation, MEMBERS, "Members", 'M');
        addNavigationButton(navigation, BOOKS, "Books", 'B');
        addNavigationButton(navigation, CIRCULATION, "Circulation", 'C');
        navigation.add(Box.createVerticalStrut(12));
        addActionButton(navigation, "nav.selfService", "Member Portal", openMemberPortal);
        navigation.add(Box.createVerticalGlue());

        addActionButton(navigation, "nav.logout", "Log Out", logout);
        navigation.add(Box.createVerticalStrut(18));

        JLabel storage = new JLabel("CSV STORAGE");
        storage.setForeground(new Color(147, 162, 200));
        storage.setFont(storage.getFont().deriveFont(Font.BOLD, 11f));
        storage.setAlignmentX(LEFT_ALIGNMENT);
        navigation.add(storage);
        JLabel autosave = new JLabel("Changes save automatically");
        autosave.setForeground(new Color(190, 200, 224));
        autosave.setFont(autosave.getFont().deriveFont(11f));
        autosave.setAlignmentX(LEFT_ALIGNMENT);
        navigation.add(autosave);
        return navigation;
    }

    private void addNavigationButton(JPanel navigation, String page, String label, char mnemonic) {
        JButton button = AppTheme.navigationButton(label);
        button.setName("nav." + page);
        button.setMnemonic(mnemonic);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.addActionListener(event -> showPage(page));
        navigationButtons.put(page, button);
        navigation.add(button);
        navigation.add(Box.createVerticalStrut(7));
    }

    private void addActionButton(JPanel navigation, String name, String label, Runnable action) {
        JButton button = AppTheme.navigationButton(label);
        button.setName(name);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.addActionListener(event -> action.run());
        navigation.add(button);
        navigation.add(Box.createVerticalStrut(7));
    }

    private JPanel buildStatusBar(Path dataDirectory) {
        JPanel status = new JPanel(new BorderLayout());
        status.setBackground(Color.WHITE);
        status.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppTheme.BORDER),
                BorderFactory.createEmptyBorder(7, 13, 7, 13)
        ));
        JLabel ready = new JLabel("●  Ready — all changes are saved automatically");
        ready.setForeground(AppTheme.SUCCESS);
        status.add(ready, BorderLayout.WEST);
        JLabel path = new JLabel(dataDirectory.toAbsolutePath().normalize().toString());
        path.setToolTipText("CSV data folder");
        path.setForeground(AppTheme.MUTED);
        path.setFont(path.getFont().deriveFont(11f));
        status.add(path, BorderLayout.EAST);
        return status;
    }
}
