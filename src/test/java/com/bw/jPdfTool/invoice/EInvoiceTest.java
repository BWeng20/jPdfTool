package com.bw.jPdfTool.invoice;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.PDResources;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class EInvoiceTest {

    public static byte[] sampleXml() throws IOException {
        try (InputStream is = EInvoiceTest.class.getResourceAsStream("/factur-x-minimum.xml")) {
            return is.readAllBytes();
        }
    }

    public static byte[] plainPdf(int pages) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            // Pages without resources on purpose: Mustang needs a workaround for them, see EInvoice.addMissingResources.
            for (int i = 0; i < pages; ++i)
                doc.addPage(new PDPage());
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            doc.save(os);
            return os.toByteArray();
        }
    }

    /**
     * A PDF with a not embedded font: it can't be PDF/A.
     */
    public static byte[] nonConformPdf() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(72, 700);
                cs.showText("Not embedded font");
                cs.endText();
            }
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            doc.save(os);
            return os.toByteArray();
        }
    }

    public static byte[] invoicePdf(int pages) throws IOException {
        return EInvoice.attachXml(plainPdf(pages), sampleXml());
    }

    @Test
    void test_attachAndExtract() throws Exception {
        try (PDDocument plain = Loader.loadPDF(plainPdf(1))) {
            Assertions.assertFalse(EInvoice.hasInvoice(plain));
            Assertions.assertTrue(EInvoice.extractXml(plain).isEmpty());
        }
        try (PDDocument doc = Loader.loadPDF(invoicePdf(1))) {
            Assertions.assertTrue(EInvoice.hasInvoice(doc));
            byte[] xml = EInvoice.extractXml(doc).orElseThrow();
            Assertions.assertTrue(new String(xml, "UTF-8").contains("INV-2024-001"));
        }
    }

    @Test
    void test_attachmentIsNamedFacturX() throws Exception {
        try (PDDocument doc = Loader.loadPDF(invoicePdf(1))) {
            var files = doc.getDocumentCatalog().getNames().getEmbeddedFiles().getNames();
            Assertions.assertEquals(java.util.Set.of("factur-x.xml"), files.keySet());
            Assertions.assertEquals("factur-x.xml", files.get("factur-x.xml").getFilename());
        }
    }

    @Test
    void test_attachStrictFailsIfNotConform() throws Exception {
        Assertions.assertThrows(EInvoice.NotConformException.class,
                () -> EInvoice.attachXmlStrict(nonConformPdf(), sampleXml()));
        Assertions.assertDoesNotThrow(() -> EInvoice.attachXmlStrict(plainPdf(1), sampleXml()));
    }

    @Test
    void test_randomEn16931Invoices() throws Exception {
        // Random but consistent invoices, the seed is part of the message to reproduce a failure.
        for (int i = 0; i < 5; ++i) {
            InvoiceXmlGenerator gen = new InvoiceXmlGenerator(System.nanoTime() + i);
            String xml = gen.create();
            try {
                byte[] pdf = EInvoice.attachXmlStrict(plainPdf(1), xml.getBytes(StandardCharsets.UTF_8));
                try (PDDocument doc = Loader.loadPDF(pdf)) {
                    Assertions.assertArrayEquals(xml.getBytes(StandardCharsets.UTF_8), EInvoice.extractXml(doc).orElseThrow());
                }
                Assertions.assertNotNull(EInvoice.summarize(xml.getBytes(StandardCharsets.UTF_8)).id());
            } catch (Exception | AssertionError e) {
                throw new AssertionError("Seed " + gen.seed() + ": " + e.getMessage() + "\n" + xml, e);
            }
        }
    }

    @Test
    void test_randomInvoiceWithWrongScheme() throws Exception {
        // 9930 is not an allowed scheme for the legal organization
        InvoiceXmlGenerator gen = new InvoiceXmlGenerator(System.nanoTime());
        String broken = gen.create("9930");
        EInvoice.NotConformException e = Assertions.assertThrows(EInvoice.NotConformException.class,
                () -> EInvoice.attachXmlStrict(plainPdf(1), broken.getBytes(StandardCharsets.UTF_8)), "Seed " + gen.seed());
        Assertions.assertTrue(e.getMessage().contains("schemeID"), e.getMessage());
        Assertions.assertFalse(e.getMessage().contains("warning"), e.getMessage());
        Assertions.assertTrue(e.getMessage().length() < 400, e.getMessage());

        // an invoice without the legal organization (otherwise generated the same way) is fine
        Assertions.assertDoesNotThrow(() -> EInvoice.attachXmlStrict(plainPdf(1), new InvoiceXmlGenerator(gen.seed()).create().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void test_supportsInvoiceDependsOnPdfVersion() throws Exception {
        for (float version : new float[]{1.3f, 1.4f, 1.7f, 2.0f}) {
            try (PDDocument doc = Loader.loadPDF(plainPdf(1))) {
                doc.getDocument().setVersion(version);
                Assertions.assertEquals(version <= 1.7f, EInvoice.supportsInvoice(doc), "version " + version);
            }
        }
    }

    @Test
    void test_attachFailsForPdf20() throws Exception {
        byte[] pdf20;
        try (PDDocument doc = Loader.loadPDF(plainPdf(1))) {
            doc.getDocument().setVersion(2.0f);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            doc.save(os);
            pdf20 = os.toByteArray();
        }
        Assertions.assertThrows(EInvoice.NotConformException.class, () -> EInvoice.attachXmlStrict(pdf20, sampleXml()));
    }

    @Test
    void test_summarize() throws Exception {
        EInvoice.Summary s = EInvoice.summarize(sampleXml());
        Assertions.assertEquals("INV-2024-001", s.id());
        Assertions.assertEquals("Seller GmbH", s.seller());
        Assertions.assertEquals("Buyer AG", s.buyer());
        Assertions.assertEquals("EUR", s.currency());
        Assertions.assertTrue(s.amount().startsWith("119"), s.amount());
        Assertions.assertEquals("380", s.documentCode());
        Assertions.assertEquals("20241001", s.issueDate());
        Assertions.assertEquals("04011000-12345-34", s.reference());
        Assertions.assertEquals("100.00", s.netAmount());
        Assertions.assertEquals("19.00", s.taxAmount());
        Assertions.assertEquals("MINIMUM", s.profile());
        // Not part of the MINIMUM profile: missing values are null, not empty or an error.
        Assertions.assertNull(s.dueDate());
        Assertions.assertNull(s.iban());
    }

    @Test
    void test_removeXml() throws Exception {
        try (PDDocument doc = Loader.loadPDF(invoicePdf(1))) {
            Assertions.assertTrue(EInvoice.removeXml(doc));
            Assertions.assertFalse(EInvoice.hasInvoice(doc));
            Assertions.assertFalse(EInvoice.removeXml(doc));

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            doc.save(os);
            try (PDDocument reloaded = Loader.loadPDF(os.toByteArray())) {
                Assertions.assertFalse(EInvoice.hasInvoice(reloaded));
            }
        }
    }

    @Test
    void test_validate() throws Exception {
        EInvoice.Validation ok = EInvoice.validate(invoicePdf(1), "ok.pdf");
        Assertions.assertTrue(ok.valid(), ok.report());
        Assertions.assertTrue(ok.messages().isEmpty(), ok.messages().toString());

        EInvoice.Validation missing = EInvoice.validate(plainPdf(1), "plain.pdf");
        Assertions.assertFalse(missing.valid(), missing.report());
        Assertions.assertNotNull(missing.report());
    }

    /**
     * A ZUGFeRD 1.0 document must be kept as it is: no "repair" to Factur-X metadata on save.
     */
    @Test
    void test_zugferd1_isNotRepaired() throws Exception {
        java.io.ByteArrayOutputStream os = new java.io.ByteArrayOutputStream();
        try (org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA3 exporter = new org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA3()
                .ignorePDFAErrors().setZUGFeRDVersion(1).load(EInvoice.addMissingResources(plainPdf(1)))) {
            exporter.setXML(sampleXml());
            exporter.export(os);
        }
        try (PDDocument doc = Loader.loadPDF(os.toByteArray())) {
            Assertions.assertTrue(EInvoice.hasInvoice(doc));
            Assertions.assertTrue(EInvoice.hasInvoiceMetadata(doc));
            Assertions.assertSame(doc, EInvoice.repair(doc));
            Assertions.assertEquals(java.util.Set.of("ZUGFeRD-invoice.xml"),
                    doc.getDocumentCatalog().getNames().getEmbeddedFiles().getNames().keySet());
        }
    }

    @Test
    void test_zugferd1_removeCleansMetadata() throws Exception {
        java.io.ByteArrayOutputStream os = new java.io.ByteArrayOutputStream();
        try (org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA3 exporter = new org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA3()
                .ignorePDFAErrors().setZUGFeRDVersion(1).load(EInvoice.addMissingResources(plainPdf(1)))) {
            exporter.setXML(sampleXml());
            exporter.export(os);
        }
        try (PDDocument doc = Loader.loadPDF(os.toByteArray())) {
            Assertions.assertTrue(EInvoice.removeXml(doc));
            Assertions.assertFalse(EInvoice.hasInvoice(doc));
            String xmp = new String(doc.getDocumentCatalog().getMetadata().toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            Assertions.assertFalse(xmp.contains("urn:ferd:pdfa:CrossIndustryDocument:invoice:1p0#\""),
                    "ZUGFeRD 1.0 schema must be removed");
        }
    }

    private static byte[] save(PDDocument doc) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        doc.save(os);
        return os.toByteArray();
    }

    @Test
    void test_pageWithoutResources_attachIsValid() throws Exception {
        byte[] pdf = plainPdf(1);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            Assertions.assertNull(doc.getPage(0).getResources(), "test needs a page without resources");
        }
        byte[] result = EInvoice.attachXmlStrict(pdf, sampleXml());
        try (PDDocument doc = Loader.loadPDF(result)) {
            Assertions.assertTrue(EInvoice.hasInvoice(doc));
            Assertions.assertNotNull(doc.getPage(0).getResources());
        }
    }

    @Test
    void test_inheritedResources_areKept() throws Exception {
        byte[] pdf;
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.getPages().getCOSObject().setItem(org.apache.pdfbox.cos.COSName.RESOURCES, new PDResources());
            pdf = save(doc);
        }
        Assertions.assertSame(pdf, EInvoice.addMissingResources(pdf), "nothing to fix, must be unchanged");
        try (PDDocument doc = Loader.loadPDF(EInvoice.attachXml(pdf, sampleXml()))) {
            Assertions.assertTrue(EInvoice.hasInvoice(doc));
            Assertions.assertFalse(doc.getPage(0).getCOSObject().containsKey(org.apache.pdfbox.cos.COSName.RESOURCES),
                    "the page must still inherit its resources");
        }
    }

    @Test
    void test_mixedPages_onlyMissingAreFixed() throws Exception {
        byte[] pdf;
        try (PDDocument doc = new PDDocument()) {
            PDPage withResources = new PDPage();
            PDResources res = new PDResources();
            res.getCOSObject().setString(org.apache.pdfbox.cos.COSName.getPDFName("Marker"), "kept");
            withResources.setResources(res);
            doc.addPage(withResources);
            doc.addPage(new PDPage());
            pdf = save(doc);
        }
        try (PDDocument doc = Loader.loadPDF(EInvoice.addMissingResources(pdf))) {
            Assertions.assertEquals("kept", doc.getPage(0).getResources().getCOSObject()
                    .getString(org.apache.pdfbox.cos.COSName.getPDFName("Marker")));
            Assertions.assertNotNull(doc.getPage(1).getResources());
        }
        try (PDDocument doc = Loader.loadPDF(EInvoice.attachXml(pdf, sampleXml()))) {
            Assertions.assertTrue(EInvoice.hasInvoice(doc));
            Assertions.assertEquals(2, doc.getNumberOfPages());
        }
    }

    @Test
    void test_xrechnungAttachment_isDetected() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            var file = new org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile(doc,
                    new java.io.ByteArrayInputStream(sampleXml()));
            var spec = new org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification();
            spec.setFile("xrechnung.xml");
            spec.setEmbeddedFile(file);
            var tree = new org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode();
            tree.setNames(java.util.Map.of("xrechnung.xml", spec));
            var names = new org.apache.pdfbox.pdmodel.PDDocumentNameDictionary(doc.getDocumentCatalog());
            names.setEmbeddedFiles(tree);
            doc.getDocumentCatalog().setNames(names);

            Assertions.assertTrue(EInvoice.hasInvoice(doc));
            Assertions.assertTrue(EInvoice.extractXml(doc).isPresent());
            Assertions.assertTrue(EInvoice.removeXml(doc));
            Assertions.assertFalse(EInvoice.hasInvoice(doc));
        }
    }
}
