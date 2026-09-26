package com.istad.library.document;

import com.istad.library.model.Book;
import com.istad.library.model.Loan;
import com.istad.library.model.User;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Creates a polished, dependency-free PDF invoice for a returned loan with an overdue fine. */
public final class FineInvoicePdfGenerator {
    private static final int IMAGE_WIDTH = 1240;
    private static final int IMAGE_HEIGHT = 1754;
    private static final Color NAVY = new Color(24, 38, 78);
    private static final Color BLUE = new Color(47, 78, 184);
    private static final Color TEXT = new Color(31, 38, 51);
    private static final Color MUTED = new Color(101, 110, 128);
    private static final Color BORDER = new Color(218, 223, 233);
    private static final Color BACKGROUND = new Color(245, 247, 251);
    private static final Color WARNING_BACKGROUND = new Color(255, 247, 230);
    private static final Color WARNING = new Color(160, 82, 10);

    private FineInvoicePdfGenerator() {
    }

    public static Path generate(Path invoiceDirectory, Loan loan, User member, Book book) throws IOException {
        Objects.requireNonNull(invoiceDirectory, "Invoice directory is required.");
        Objects.requireNonNull(loan, "Loan is required.");
        Objects.requireNonNull(member, "Member is required.");
        Objects.requireNonNull(book, "Book is required.");
        if (loan.isActive() || loan.getReturnDate() == null) {
            throw new IllegalArgumentException("An invoice can only be created after the book is returned.");
        }
        if (loan.getFineAmount() <= 0) {
            throw new IllegalArgumentException("This loan has no fine to invoice.");
        }

        Files.createDirectories(invoiceDirectory);
        String safeLoanId = loan.getId().replaceAll("[^A-Za-z0-9_-]", "_");
        Path target = invoiceDirectory.resolve("Fine-Invoice-" + safeLoanId + ".pdf");
        Path temporary = Files.createTempFile(invoiceDirectory, ".invoice-", ".tmp");
        try {
            BufferedImage invoiceImage = renderInvoice(loan, member, book);
            byte[] jpeg = encodeJpeg(invoiceImage);
            byte[] pdf = createPdf(jpeg, loan.getId());
            Files.write(temporary, pdf);
            moveIntoPlace(temporary, target);
            return target.toAbsolutePath().normalize();
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static BufferedImage renderInvoice(Loan loan, User member, Book book) {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

            drawHeader(graphics, loan);
            drawSummary(graphics, loan);
            drawMemberSection(graphics, member);
            drawLoanSection(graphics, loan, book);
            drawFineSection(graphics, loan);
            drawFooter(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void drawHeader(Graphics2D graphics, Loan loan) {
        graphics.setColor(NAVY);
        graphics.fillRect(0, 0, IMAGE_WIDTH, 215);
        graphics.setColor(Color.WHITE);
        graphics.setFont(font(Font.BOLD, 38));
        graphics.drawString("ISTAD LIBRARY", 86, 90);
        graphics.setFont(font(Font.PLAIN, 21));
        graphics.setColor(new Color(211, 220, 242));
        graphics.drawString("Library Management System", 88, 130);

        graphics.setFont(font(Font.BOLD, 42));
        graphics.setColor(Color.WHITE);
        drawRightAligned(graphics, "FINE INVOICE", IMAGE_WIDTH - 86, 90);
        graphics.setFont(font(Font.PLAIN, 21));
        graphics.setColor(new Color(211, 220, 242));
        drawRightAligned(graphics, "Invoice INV-" + safeText(loan.getId()), IMAGE_WIDTH - 86, 130);
    }

    private static void drawSummary(Graphics2D graphics, Loan loan) {
        graphics.setColor(BACKGROUND);
        graphics.fillRoundRect(82, 265, 1076, 238, 18, 18);

        graphics.setColor(MUTED);
        graphics.setFont(font(Font.BOLD, 18));
        graphics.drawString("INVOICE DATE", 118, 320);
        graphics.drawString("PAYMENT STATUS", 470, 320);
        graphics.drawString("TOTAL AMOUNT DUE", 810, 320);

        graphics.setColor(TEXT);
        graphics.setFont(font(Font.BOLD, 26));
        graphics.drawString(loan.getReturnDate().toString(), 118, 365);
        graphics.setColor(WARNING);
        graphics.drawString("UNPAID", 470, 365);
        graphics.setColor(BLUE);
        graphics.setFont(font(Font.BOLD, 48));
        drawRightAligned(graphics, money(loan.getFineAmount()), 1120, 374);

        graphics.setColor(MUTED);
        graphics.setFont(font(Font.PLAIN, 19));
        graphics.drawString("Please pay this overdue fine at the library service desk.", 118, 448);
    }

    private static void drawMemberSection(Graphics2D graphics, User member) {
        drawSectionTitle(graphics, "MEMBER INFORMATION", 570);
        drawRow(graphics, 630, "Member ID", member.getId());
        drawRow(graphics, 690, "Full name", member.getName());
        drawRow(graphics, 750, "Email", member.getEmail().isBlank() ? "Not provided" : member.getEmail());
        drawRow(graphics, 810, "Phone", member.getPhone().isBlank() ? "Not provided" : member.getPhone());
    }

    private static void drawLoanSection(Graphics2D graphics, Loan loan, Book book) {
        drawSectionTitle(graphics, "LOAN DETAILS", 900);
        drawRow(graphics, 960, "Loan ID", loan.getId());
        drawRow(graphics, 1020, "Book", book.getId() + " - " + book.getTitle());
        drawRow(graphics, 1080, "Author", book.getAuthor().isBlank() ? "Not provided" : book.getAuthor());
        drawDateRow(graphics, 1150, loan);
    }

    private static void drawFineSection(Graphics2D graphics, Loan loan) {
        long overdueDays = Math.max(0, ChronoUnit.DAYS.between(loan.getDueDate(), loan.getReturnDate()));
        drawSectionTitle(graphics, "FINE CALCULATION", 1260);

        graphics.setColor(BACKGROUND);
        graphics.fillRoundRect(82, 1305, 1076, 218, 16, 16);
        graphics.setColor(MUTED);
        graphics.setFont(font(Font.BOLD, 18));
        graphics.drawString("OVERDUE DAYS", 118, 1360);
        graphics.drawString("DAILY RATE", 470, 1360);
        graphics.drawString("TOTAL", 850, 1360);

        graphics.setFont(font(Font.BOLD, 30));
        graphics.setColor(TEXT);
        graphics.drawString(Long.toString(overdueDays), 118, 1412);
        graphics.drawString(money(Loan.DAILY_FINE), 470, 1412);
        graphics.setColor(BLUE);
        graphics.setFont(font(Font.BOLD, 42));
        drawRightAligned(graphics, money(loan.getFineAmount()), 1120, 1420);

        graphics.setColor(MUTED);
        graphics.setFont(font(Font.PLAIN, 18));
        graphics.drawString(overdueDays + " day(s) x " + money(Loan.DAILY_FINE) + " per day", 118, 1480);
    }

    private static void drawFooter(Graphics2D graphics) {
        graphics.setStroke(new BasicStroke(2f));
        graphics.setColor(BORDER);
        graphics.drawLine(82, 1600, 1158, 1600);
        graphics.setColor(TEXT);
        graphics.setFont(font(Font.BOLD, 20));
        graphics.drawString("Thank you for using ISTAD Library.", 82, 1650);
        graphics.setColor(MUTED);
        graphics.setFont(font(Font.PLAIN, 17));
        graphics.drawString("This invoice was generated automatically from the library loan record.", 82, 1687);
        drawRightAligned(graphics, "Page 1 of 1", 1158, 1687);
    }

    private static void drawSectionTitle(Graphics2D graphics, String title, int y) {
        graphics.setColor(NAVY);
        graphics.setFont(font(Font.BOLD, 24));
        graphics.drawString(title, 82, y);
        graphics.setColor(BLUE);
        graphics.fillRect(82, y + 16, 1076, 4);
    }

    private static void drawRow(Graphics2D graphics, int y, String label, String value) {
        graphics.setColor(MUTED);
        graphics.setFont(font(Font.BOLD, 18));
        graphics.drawString(label.toUpperCase(Locale.ROOT), 92, y);
        graphics.setColor(TEXT);
        graphics.setFont(font(Font.PLAIN, 22));
        graphics.drawString(fitText(graphics, safeText(value), 720), 390, y);
        graphics.setColor(BORDER);
        graphics.drawLine(82, y + 22, 1158, y + 22);
    }

    private static void drawDateRow(Graphics2D graphics, int y, Loan loan) {
        String[] labels = {"BORROWED", "DUE DATE", "RETURNED"};
        String[] values = {
                loan.getBorrowDate().toString(), loan.getDueDate().toString(), loan.getReturnDate().toString()
        };
        int[] xPositions = {92, 455, 818};
        for (int index = 0; index < labels.length; index++) {
            graphics.setColor(MUTED);
            graphics.setFont(font(Font.BOLD, 17));
            graphics.drawString(labels[index], xPositions[index], y);
            graphics.setColor(TEXT);
            graphics.setFont(font(Font.BOLD, 22));
            graphics.drawString(values[index], xPositions[index], y + 38);
        }
    }

    private static String fitText(Graphics2D graphics, String text, int maximumWidth) {
        FontMetrics metrics = graphics.getFontMetrics();
        if (metrics.stringWidth(text) <= maximumWidth) {
            return text;
        }
        String suffix = "...";
        int suffixWidth = metrics.stringWidth(suffix);
        StringBuilder fitted = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            String next = new String(Character.toChars(codePoint));
            if (metrics.stringWidth(fitted + next) + suffixWidth > maximumWidth) {
                break;
            }
            fitted.append(next);
            offset += Character.charCount(codePoint);
        }
        return fitted + suffix;
    }

    private static void drawRightAligned(Graphics2D graphics, String text, int rightEdge, int baseline) {
        graphics.drawString(text, rightEdge - graphics.getFontMetrics().stringWidth(text), baseline);
    }

    private static Font font(int style, int size) {
        return new Font(Font.SANS_SERIF, style, size);
    }

    private static String safeText(String text) {
        return text == null ? "" : text.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String money(double amount) {
        return String.format(Locale.US, "$%.2f", amount);
    }

    private static byte[] encodeJpeg(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG writer is available for PDF invoice generation.");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(imageOutput);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            if (parameters.canWriteCompressed()) {
                parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parameters.setCompressionQuality(0.92f);
            }
            writer.write(null, new IIOImage(image, null, null), parameters);
            imageOutput.flush();
            return bytes.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private static byte[] createPdf(byte[] jpeg, String loanId) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(jpeg.length + 2048);
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        write(output, "%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n");

        writeObject(output, offsets, 1, "<< /Type /Catalog /Pages 2 0 R >>");
        writeObject(output, offsets, 2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        writeObject(
                output,
                offsets,
                3,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                        + "/Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>"
        );

        offsets.add(output.size());
        write(output, "4 0 obj\n<< /Type /XObject /Subtype /Image /Width " + IMAGE_WIDTH
                + " /Height " + IMAGE_HEIGHT
                + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length "
                + jpeg.length + " >>\nstream\n");
        output.write(jpeg);
        write(output, "\nendstream\nendobj\n");

        byte[] content = "q\n595 0 0 842 0 0 cm\n/Im0 Do\nQ\n".getBytes(StandardCharsets.US_ASCII);
        offsets.add(output.size());
        write(output, "5 0 obj\n<< /Length " + content.length + " >>\nstream\n");
        output.write(content);
        write(output, "endstream\nendobj\n");

        writeObject(
                output,
                offsets,
                6,
                "<< /Title (Fine Invoice INV-" + pdfLiteral(loanId) + ") "
                        + "/Author (ISTAD Library) /Subject (Overdue library fine) >>"
        );

        int crossReferenceOffset = output.size();
        write(output, "xref\n0 7\n0000000000 65535 f \n");
        for (int object = 1; object <= 6; object++) {
            write(output, String.format(Locale.ROOT, "%010d 00000 n \n", offsets.get(object)));
        }
        write(output, "trailer\n<< /Size 7 /Root 1 0 R /Info 6 0 R >>\nstartxref\n"
                + crossReferenceOffset + "\n%%EOF\n");
        return output.toByteArray();
    }

    private static void writeObject(
            ByteArrayOutputStream output,
            List<Integer> offsets,
            int objectNumber,
            String body
    ) throws IOException {
        offsets.add(output.size());
        write(output, objectNumber + " 0 obj\n" + body + "\nendobj\n");
    }

    private static void write(ByteArrayOutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String pdfLiteral(String value) {
        return safeText(value).replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
