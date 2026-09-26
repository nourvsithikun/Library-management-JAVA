package com.istad.library;

import com.istad.library.service.LibraryService;
import com.istad.library.service.LibraryStatistics;
import com.istad.library.ui.AppTheme;
import com.istad.library.ui.LibraryManagementFrame;
import com.istad.library.ui.LibrarySelfServiceFrame;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        if (hasArgument(args, "--help")) {
            printHelp();
            return;
        }

        boolean smokeTest = hasArgument(args, "--smoke-test");
        boolean selfServiceMode = hasArgument(args, "--self-service");
        try {
            Path dataDirectory = resolveDataDirectory(args);
            LibraryService service = new LibraryService(dataDirectory);
            if (smokeTest) {
                LibraryStatistics statistics = service.getStatistics();
                System.out.printf(
                        "Library system ready: %d users, %d books, %d active loans.%n",
                        statistics.registeredUsers(),
                        statistics.bookTitles(),
                        statistics.activeLoans()
                );
                return;
            }

            if (GraphicsEnvironment.isHeadless()) {
                throw new IllegalStateException("A graphical desktop is required. Use --smoke-test for a CLI check.");
            }
            setSystemLookAndFeel();
            AppTheme.apply();
            SwingUtilities.invokeLater(() -> {
                if (selfServiceMode) {
                    new LibrarySelfServiceFrame(service).setVisible(true);
                } else {
                    new LibraryManagementFrame(service, dataDirectory).setVisible(true);
                }
            });
        } catch (Exception exception) {
            exception.printStackTrace(System.err);
            if (!smokeTest && !GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(
                        null,
                        "The application could not start:\n" + exception.getMessage(),
                        "Startup Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
            System.exit(1);
        }
    }

    private static Path resolveDataDirectory(String[] args) {
        for (String argument : args) {
            if (argument.startsWith("--data-dir=")) {
                String value = argument.substring("--data-dir=".length()).trim();
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("--data-dir requires a path.");
                }
                return Path.of(value).toAbsolutePath().normalize();
            }
        }
        return Path.of("data").toAbsolutePath().normalize();
    }

    private static boolean hasArgument(String[] args, String expected) {
        for (String argument : args) {
            if (argument.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    private static void setSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Swing's cross-platform look and feel remains available.
        }
    }

    private static void printHelp() {
        System.out.println("Library Management System");
        System.out.println("  --data-dir=<path>  Read and write CSV data in this folder");
        System.out.println("  --self-service     Open the member-only registration and borrowing window");
        System.out.println("  --smoke-test       Initialize data and verify startup without opening Swing");
        System.out.println("  --help             Show this help text");
    }
}
