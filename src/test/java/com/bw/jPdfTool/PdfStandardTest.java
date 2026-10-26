package com.bw.jPdfTool;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PdfStandardTest {

    private static String describe(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return PdfStandard.describe(doc);
        }
    }

    @Test
    void test_plainPdf() throws Exception {
        String d = describe(EInvoiceTest.plainPdf(1));
        Assertions.assertTrue(d.matches("PDF \\d\\.\\d"), d);
    }

    @Test
    void test_pdfa() throws Exception {
        Assertions.assertTrue(describe(PdfaVersionsTest.pdfa(1)).endsWith("PDF/A-1b"));
        Assertions.assertTrue(describe(PdfaVersionsTest.pdfa(2)).endsWith("PDF/A-2b"));
        Assertions.assertTrue(describe(PdfaVersionsTest.pdfa(3)).endsWith("PDF/A-3b"));
    }

    @Test
    void test_invoice() throws Exception {
        String d = describe(EInvoice.attachXml(PdfaVersionsTest.pdfa(3), EInvoiceTest.sampleXml()));
        Assertions.assertTrue(d.contains("PDF/A-3u"), d);
        Assertions.assertTrue(d.endsWith("Factur-X"), d);
    }

    private static int part(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return PdfStandard.pdfaPart(doc);
        }
    }

    @Test
    void test_pdfaPart() throws Exception {
        Assertions.assertEquals(0, part(EInvoiceTest.plainPdf(1)));
        for (int p = 1; p <= 3; ++p)
            Assertions.assertEquals(p, part(PdfaVersionsTest.pdfa(p)));
        Assertions.assertEquals(3, part(EInvoiceTest.invoicePdf(1)));
    }

    @Test
    void test_rgbOutputIntent() throws Exception {
        try (PDDocument doc = Loader.loadPDF(PdfaVersionsTest.pdfa(3))) {
            Assertions.assertTrue(PdfStandard.hasRgbOutputIntent(doc), "sRGB output intent");
        }
        try (PDDocument doc = Loader.loadPDF(EInvoiceTest.plainPdf(1))) {
            Assertions.assertFalse(PdfStandard.hasRgbOutputIntent(doc), "no output intent");
        }
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            java.awt.color.ICC_Profile gray = java.awt.color.ICC_Profile.getInstance(java.awt.color.ColorSpace.CS_GRAY);
            var intent = new org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent(doc,
                    new java.io.ByteArrayInputStream(gray.getData()));
            intent.setOutputConditionIdentifier("Gray");
            doc.getDocumentCatalog().addOutputIntent(intent);
            Assertions.assertFalse(PdfStandard.hasRgbOutputIntent(doc), "gray output intent");
        }
    }

    @Test
    void test_addSrgbOutputIntent() throws Exception {
        try (PDDocument doc = Loader.loadPDF(EInvoiceTest.plainPdf(1))) {
            Assertions.assertTrue(PdfStandard.addSrgbOutputIntent(doc));
            Assertions.assertTrue(PdfStandard.hasRgbOutputIntent(doc));
            Assertions.assertEquals(1, doc.getDocumentCatalog().getOutputIntents().size());
            // Only once.
            Assertions.assertFalse(PdfStandard.addSrgbOutputIntent(doc));
            Assertions.assertEquals(1, doc.getDocumentCatalog().getOutputIntents().size());

            // The profile of Mustang is used.
            byte[] mustang;
            try (var is = org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA3.class.getClassLoader().getResourceAsStream("sRGB.icc")) {
                Assertions.assertNotNull(is, "Mustang contains no sRGB.icc any more, the fallback of Java is used");
                mustang = is.readAllBytes();
            }
            try (var is = doc.getDocumentCatalog().getOutputIntents().get(0).getDestOutputIntent().createInputStream()) {
                Assertions.assertArrayEquals(mustang, is.readAllBytes());
            }
        }
    }

    @Test
    void test_addSrgbOutputIntent_keepsOtherIntent() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            java.awt.color.ICC_Profile gray = java.awt.color.ICC_Profile.getInstance(java.awt.color.ColorSpace.CS_GRAY);
            var intent = new org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent(doc,
                    new java.io.ByteArrayInputStream(gray.getData()));
            doc.getDocumentCatalog().addOutputIntent(intent);
            Assertions.assertFalse(PdfStandard.addSrgbOutputIntent(doc));
            Assertions.assertEquals(1, doc.getDocumentCatalog().getOutputIntents().size());
            Assertions.assertFalse(PdfStandard.hasRgbOutputIntent(doc));
        }
    }

    @Test
    void test_addSrgbOutputIntent_staysValidInvoice() throws Exception {
        byte[] pdf;
        try (PDDocument doc = Loader.loadPDF(EInvoiceTest.invoicePdf(1))) {
            doc.getDocumentCatalog().setOutputIntents(new java.util.ArrayList<>());
            Assertions.assertTrue(PdfStandard.addSrgbOutputIntent(doc));
            java.io.ByteArrayOutputStream os = new java.io.ByteArrayOutputStream();
            doc.save(os);
            pdf = os.toByteArray();
        }
        EInvoice.Validation v = EInvoice.validate(pdf, "srgb");
        Assertions.assertTrue(v.valid(), String.join(System.lineSeparator(), v.messages()));
    }
}
