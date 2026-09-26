package com.istad.library.ui;

import com.istad.library.service.LibraryService;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** A kiosk-style window that exposes no administrative screens. */
@SuppressWarnings("serial")
public final class LibrarySelfServiceFrame extends JFrame {
    private final SelfServicePanel selfServicePanel;

    public LibrarySelfServiceFrame(LibraryService service) {
        this(service, true);
    }

    public LibrarySelfServiceFrame(LibraryService service, boolean exitApplicationOnClose) {
        super("Library Member Self-Service");
        selfServicePanel = new SelfServicePanel(service, () -> { }, this::dispose);
        setDefaultCloseOperation(exitApplicationOnClose ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(820, 680));
        setSize(960, 740);
        setLocationRelativeTo(null);
        setContentPane(buildContent());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent event) {
                selfServicePanel.refreshData();
            }

            @Override
            public void windowIconified(WindowEvent event) {
                selfServicePanel.resetSession();
            }

            @Override
            public void windowClosing(WindowEvent event) {
                selfServicePanel.resetSession();
            }
        });
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(AppTheme.BACKGROUND);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppTheme.NAVY);
        header.setBorder(BorderFactory.createEmptyBorder(15, 22, 15, 22));
        JLabel title = new JLabel("ISTAD LIBRARY");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 21f));
        header.add(title, BorderLayout.WEST);
        JLabel mode = new JLabel("MEMBER SELF-SERVICE");
        mode.setForeground(new Color(200, 210, 235));
        mode.setFont(mode.getFont().deriveFont(Font.BOLD, 12f));
        header.add(mode, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        root.add(selfServicePanel, BorderLayout.CENTER);
        JLabel footer = AppTheme.mutedLabel("Registration and borrowing changes are saved automatically.");
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppTheme.BORDER),
                BorderFactory.createEmptyBorder(7, 13, 7, 13)
        ));
        root.add(footer, BorderLayout.SOUTH);
        return root;
    }
}
