package com.bw.jPdfTool;

public final class Log {

    public static final boolean INFO = true;
    public static final boolean DEBUG = true;

    public static void info(String format, Object... arguments) {
        if (INFO)
            System.out.printf((format) + "%n", arguments);
    }

    public static void debug(String format, Object... arguments) {
        if (DEBUG)
            System.out.printf((format) + "%n", arguments);
    }

    public static void error(String format, Object... arguments) {
        System.err.printf((format) + "%n", arguments);
    }
}
