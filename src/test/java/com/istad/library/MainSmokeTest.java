package com.istad.library;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.istad.library.test.TestSupport.assertTrue;

public final class MainSmokeTest {
    private MainSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Path dataDirectory = Files.createTempDirectory("library-main-smoke-");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            Main.main(new String[]{"--smoke-test", "--data-dir=" + dataDirectory});
        } finally {
            System.setOut(original);
        }

        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(
                text.contains("Library system ready: 0 users, 0 books, 0 active loans."),
                "Smoke mode should report a ready empty library. Output: " + text
        );
        assertTrue(Files.exists(dataDirectory.resolve("users.csv")), "Smoke mode should initialize CSV files.");

        output.reset();
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            Main.main(new String[]{"--help"});
        } finally {
            System.setOut(original);
        }
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("--self-service"),
                "Help output should document member self-service mode.");
        System.out.println("MainSmokeTest passed.");
    }
}
