package com.bw.jPdfTool;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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

    /**
     * A library or font the tool contains, with its license.
     *
     * @param name       Name, for libraries "group:artifact".
     * @param version    Version, may be empty.
     * @param license    Name of the license.
     * @param licenseUrl Address of the license text.
     */
    public record Component(String name, String version, String license, String licenseUrl) {
    }

    private static final String APACHE = "Apache License 2.0";
    private static final String APACHE_URL = "https://www.apache.org/licenses/LICENSE-2.0";
    private static final String BOUNCY = "Bouncy Castle Licence (MIT)";
    private static final String BOUNCY_URL = "https://www.bouncycastle.org/licence.html";
    private static final String UNKNOWN = "unknown";

    /**
     * The license of each direct dependency as {license name, url}. Keep it in sync with build.gradle,
     * a test fails for libraries which are missing here.
     */
    private static final Map<String, String[]> LIBRARY_LICENSES = Map.ofEntries(
            Map.entry("com.formdev:flatlaf", new String[]{APACHE, APACHE_URL}),
            Map.entry("com.formdev:flatlaf-fonts-roboto", new String[]{APACHE, APACHE_URL}),
            Map.entry("io.github.bweng20:jSVGIcon", new String[]{"The Unlicense (public domain)", LICENSE_URL}),
            Map.entry("org.bouncycastle:bcprov-jdk18on", new String[]{BOUNCY, BOUNCY_URL}),
            Map.entry("org.bouncycastle:bcpkix-jdk18on", new String[]{BOUNCY, BOUNCY_URL}),
            Map.entry("org.apache.pdfbox:pdfbox", new String[]{APACHE, APACHE_URL}),
            Map.entry("org.apache.pdfbox:fontbox", new String[]{APACHE, APACHE_URL}),
            Map.entry("commons-logging:commons-logging", new String[]{APACHE, APACHE_URL}),
            Map.entry("org.mustangproject:library", new String[]{APACHE, APACHE_URL}),
            Map.entry("org.mustangproject:validator", new String[]{APACHE, APACHE_URL}),
            Map.entry("org.slf4j:slf4j-nop", new String[]{"MIT License", "https://www.slf4j.org/license.html"}),
            Map.entry("com.fasterxml.jackson.core:jackson-databind", new String[]{APACHE, APACHE_URL}),
            Map.entry("info.picocli:picocli", new String[]{APACHE, APACHE_URL}));

    /**
     * The fonts which are embedded into PDF/A documents instead of missing ones, see PdfFontEmbedder.
     */
    private static final List<Component> FONTS = List.of(
            new Component("Arimo (font)", "1.23.0", APACHE, APACHE_URL),
            new Component("Tinos (font)", "1.23.0", APACHE, APACHE_URL),
            new Component("Cousine (font)", "1.23.0", APACHE, APACHE_URL));

    private AboutInfo() {
    }

    /**
     * The libraries and fonts the tool contains, with their licenses.
     */
    public static List<Component> components() {
        List<Component> result = new ArrayList<>();
        for (String l : BuildVersion.LIBRARIES) {
            int i = l.lastIndexOf(':');
            String name = i < 0 ? l : l.substring(0, i);
            String[] license = LIBRARY_LICENSES.getOrDefault(name, new String[]{UNKNOWN, null});
            result.add(new Component(name, i < 0 ? "" : l.substring(i + 1), license[0], license[1]));
        }
        result.addAll(FONTS);
        return result;
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
        sb.append("Libraries and fonts:\n");
        components().forEach(c -> sb.append("  ").append(c.name()).append(c.version().isEmpty() ? "" : " " + c.version())
                .append(" - ").append(c.license()).append(c.licenseUrl() == null ? "" : " (" + c.licenseUrl() + ")")
                .append('\n'));
        return sb.toString();
    }
}
