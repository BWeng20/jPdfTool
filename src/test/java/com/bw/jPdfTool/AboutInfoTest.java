package com.bw.jPdfTool;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AboutInfoTest {

    @Test
    void text() {
        String t = AboutInfo.asText();
        Assertions.assertTrue(t.startsWith("jPdfTool " + BuildVersion.VERSION), t);
        Assertions.assertTrue(t.contains("Build date: " + BuildVersion.BUILD_DATE), t);
        Assertions.assertTrue(t.contains("org.apache.pdfbox:pdfbox "), t);
    }

    @Test
    void licenseIsPartOfTheBuild() {
        Assertions.assertTrue(AboutInfo.licenseText().contains("unencumbered software"), "LICENSE resource");
    }
}
