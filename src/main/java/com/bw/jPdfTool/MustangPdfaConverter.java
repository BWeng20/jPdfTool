package com.bw.jPdfTool;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.mustangproject.ZUGFeRD.PDFAConformanceLevel;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA3;
import org.apache.xmpbox.XMPMetadata;

import java.io.IOException;

/**
 * Uses the PDF/A-3 preparation of Mustang (XMP identification, document information, sRGB output intent,
 * CIDSet removal, ...) on an open document, without attaching an invoice.<br>
 * Mustang offers this only as part of its export, so the protected parts are used.
 */
final class MustangPdfaConverter extends ZUGFeRDExporterFromA3 {

    private MustangPdfaConverter(PDDocument document) {
        this.doc = document;
        // Keep what the document already says (producer, creator, dates, title), only fill the gaps.
        setOverwrite(false);
        setAttachZUGFeRDHeaders(false);
        setConformanceLevel(PDFAConformanceLevel.BASIC);
        var info = document.getDocumentInformation();
        if (info.getProducer() != null && !info.getProducer().isBlank())
            setProducer(info.getProducer());
        else
            setProducer("jPdfTool");
        if (info.getCreator() != null && !info.getCreator().isBlank())
            setCreator(info.getCreator());
        else
            setCreator("jPdfTool");
        setCreatorTool("jPdfTool");
    }

    /**
     * Without an invoice no extension schema is needed. Mustang would write an empty one then, which veraPDF
     * rejects (ISO 19005-3 6.6.2.3.3).
     */
    @Override
    protected void addXMP(XMPMetadata metadata) {
    }

    /**
     * Declares PDF/A-3b and makes the metadata consistent. The document is changed in place.
     */
    static void convert(PDDocument document) throws IOException {
        // Mustang fails with a NullPointerException on pages without any resources (even inherited ones).
        for (PDPage page : document.getPages())
            if (page.getResources() == null)
                page.setResources(new PDResources());
        try {
            new MustangPdfaConverter(document).prepareDocument();
        } catch (IOException e) {
            // Unreadable XMP: replace it.
            Log.debug("Unreadable XMP metadata is replaced: %s", e.getMessage());
            document.getDocumentCatalog().setMetadata(null);
            new MustangPdfaConverter(document).prepareDocument();
        }
    }
}
