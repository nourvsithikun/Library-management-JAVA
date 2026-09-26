package com.istad.library.ui;

import com.istad.library.service.LibraryService;

import javax.swing.JFrame;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;

@SuppressWarnings("serial")
public final class LibraryManagementFrame extends JFrame {
    private final LibraryPortalPanel portalPanel;

    public LibraryManagementFrame(LibraryService service, Path dataDirectory) {
        super("Library Management System");
        portalPanel = new LibraryPortalPanel(service, dataDirectory);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1080, 700));
        setSize(1280, 790);
        setLocationRelativeTo(null);
        setContentPane(portalPanel);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent event) {
                portalPanel.refreshVisiblePage();
            }
        });
    }
}
