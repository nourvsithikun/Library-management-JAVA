package com.istad.library.test;

import java.util.Objects;

public final class TestSupport {
    private TestSupport() {
    }

    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " Expected: " + expected + ", actual: " + actual);
        }
    }

    public static void assertEquals(double expected, double actual, double tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + " Expected: " + expected + ", actual: " + actual);
        }
    }

    public static <T extends Throwable> T assertThrows(
            Class<T> expectedType,
            ThrowingRunnable operation,
            String message
    ) throws Exception {
        try {
            operation.run();
        } catch (Throwable thrown) {
            if (expectedType.isInstance(thrown)) {
                return expectedType.cast(thrown);
            }
            throw new AssertionError(
                    message + " Expected " + expectedType.getSimpleName() + " but caught "
                            + thrown.getClass().getSimpleName() + ".",
                    thrown
            );
        }
        throw new AssertionError(message + " Expected " + expectedType.getSimpleName() + ".");
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
