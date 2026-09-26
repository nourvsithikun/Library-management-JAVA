package com.istad.library.ui;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.awt.GraphicsEnvironment;

final class UiMessages {
    private UiMessages() {
    }

    static void showError(Component parent, String title, String message) {
        if (!GraphicsEnvironment.isHeadless()) {
            JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE);
        }
    }

    static void showInfo(Component parent, String title, String message) {
        if (!GraphicsEnvironment.isHeadless()) {
            JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
        }
    }

    static boolean confirm(Component parent, String title, String message) {
        return GraphicsEnvironment.isHeadless()
                || JOptionPane.showConfirmDialog(
                parent,
                message,
                title,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        ) == JOptionPane.YES_OPTION;
    }
}
