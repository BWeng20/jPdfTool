package com.bw.jPdfTool;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * The data of the About dialog: version, build, license and the environment the tool runs in.
 */
public final class AboutInfo {

    public static final String NAME = "jPdfTool";
    public static final String DESCRIPTION = "Merge, split, rotate, redact, encrypt and sign PDF files, "
            + "and create e-invoices (ZUGFeRD / Factur-X).";
    public static final String HOME_PAGE = "https://github.com/BWeng20/jPdfTool";
    public static final String LICENSE_NAME = "The Unlicense (public domain)";
    public static final String LICENSE_URL = "https://unlicense.org";

    private AboutInfo() {
    }

    /**
     * The libraries the tool uses directly, e.g. "org.apache.pdfbox:pdfbox 3.0.8", sorted.
     */
    public static List<String> libraries() {
        return Arrays.stream(BuildVersion.LIBRARIES).map(l -> {
            int i = l.lastIndexOf(':');
            return i < 0 ? l : l.substring(0, i) + " " + l.substring(i + 1);
        }).toList();
    }

    /**
     * The full text of the license, empty if it is not part of the build.
     */
    public static String licenseText() {
        try (InputStream is = AboutInfo.class.getResourceAsStream("/LICENSE")) {
            return is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.debug("Failed to read the license: %s", e.getMessage());
            return "";
        }
    }

    /**
     * Java runtime, e.g. "17.0.9 (Eclipse Adoptium)".
     */
    public static String java() {
        return System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")";
    }

    /**
     * Operating system, e.g. "Windows 11 10.0 (amd64)".
     */
    public static String os() {
        return System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")";
    }

    /**
     * Everything as plain text, e.g. to attach it to a bug report.
     */
    public static String asText() {
        StringBuilder sb = new StringBuilder();
        sb.append(NAME).append(' ').append(BuildVersion.VERSION).append('\n');
        sb.append("Build date: ").append(BuildVersion.BUILD_DATE).append('\n');
        sb.append("Java: ").append(java()).append('\n');
        sb.append("System: ").append(os()).append('\n');
        sb.append("License: ").append(LICENSE_NAME).append('\n');
        sb.append("Home page: ").append(HOME_PAGE).append('\n');
        sb.append("Libraries:\n");
        libraries().forEach(l -> sb.append("  ").append(l).append('\n'));
        return sb.toString();
    }
}
