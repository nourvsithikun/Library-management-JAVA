package com.istad.library.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Insets;
import java.util.Locale;

public final class AppTheme {
    static final Color NAVY = new Color(24, 38, 78);
    static final Color BLUE = new Color(47, 78, 184);
    static final Color BLUE_HOVER = new Color(38, 64, 154);
    static final Color RED = new Color(196, 48, 59);
    static final Color BACKGROUND = new Color(245, 247, 251);
    static final Color SURFACE = Color.WHITE;
    static final Color BORDER = new Color(218, 223, 233);
    static final Color TEXT = new Color(31, 38, 51);
    static final Color MUTED = new Color(101, 110, 128);
    static final Color SUCCESS = new Color(21, 128, 82);
    static final Color WARNING = new Color(181, 101, 16);
    static final Color SELECTION = new Color(226, 232, 251);

    private AppTheme() {
    }

    public static void apply() {
        Font baseFont = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
        UIManager.put("Button.font", baseFont.deriveFont(Font.BOLD));
        UIManager.put("Label.font", baseFont);
        UIManager.put("TextField.font", baseFont);
        UIManager.put("FormattedTextField.font", baseFont);
        UIManager.put("ComboBox.font", baseFont);
        UIManager.put("Spinner.font", baseFont);
        UIManager.put("CheckBox.font", baseFont);
        UIManager.put("Table.font", baseFont);
        UIManager.put("TableHeader.font", baseFont.deriveFont(Font.BOLD));
        UIManager.put("OptionPane.messageFont", baseFont);
        UIManager.put("OptionPane.buttonFont", baseFont.deriveFont(Font.BOLD));
        UIManager.put("Table.rowHeight", 32);
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Table.gridColor", new Color(235, 238, 244));
        UIManager.put("Table.selectionBackground", SELECTION);
        UIManager.put("Table.selectionForeground", TEXT);
    }

    static JButton primaryButton(String text) {
        return button(text, BLUE, Color.WHITE, BorderFactory.createEmptyBorder(10, 17, 10, 17));
    }

    static JButton secondaryButton(String text) {
        return button(
                text,
                SURFACE,
                BLUE,
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BORDER),
                        BorderFactory.createEmptyBorder(9, 16, 9, 16)
                )
        );
    }

    static JButton dangerButton(String text) {
        return button(text, RED, Color.WHITE, BorderFactory.createEmptyBorder(10, 17, 10, 17));
    }

    static JButton navigationButton(String text) {
        JButton button = button(text, NAVY, new Color(220, 226, 242), BorderFactory.createEmptyBorder(12, 18, 12, 18));
        button.setHorizontalAlignment(JButton.LEFT);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 14f));
        return button;
    }

    static void setNavigationSelected(JButton button, boolean selected) {
        button.setBackground(selected ? BLUE : NAVY);
        button.setForeground(Color.WHITE);
    }

    static Border sectionBorder(String title) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createTitledBorder(
                        BorderFactory.createEmptyBorder(6, 8, 8, 8),
                        title
                )
        );
    }

    static Border cardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)
        );
    }

    static void applyInputStyle(JComponent component) {
        component.setBackground(Color.WHITE);
        component.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(7, 9, 7, 9)
        ));
    }

    static void styleTable(JTable table) {
        table.setFillsViewportHeight(true);
        table.setRowHeight(32);
        table.setShowVerticalLines(false);
        table.setSelectionBackground(SELECTION);
        table.setSelectionForeground(TEXT);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setBackground(new Color(238, 241, 247));
        table.getTableHeader().setForeground(TEXT);
    }

    static void setCurrencyRenderer(JTable table, int column) {
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                if (value instanceof Number amount) {
                    setText(String.format(Locale.US, "$%.2f", amount.doubleValue()));
                } else {
                    setText("");
                }
            }
        };
        renderer.setHorizontalAlignment(SwingConstants.RIGHT);
        table.getColumnModel().getColumn(column).setCellRenderer(renderer);
    }

    static JLabel pageTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 25f));
        label.setForeground(NAVY);
        return label;
    }

    static JLabel mutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        return label;
    }

    private static JButton button(String text, Color background, Color foreground, Border border) {
        JButton button = new JButton(text);
        button.setBackground(background);
        button.setForeground(foreground);
        button.setOpaque(true);
        button.setFocusPainted(true);
        button.setBorder(border);
        button.setMargin(new Insets(8, 14, 8, 14));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }
}
