package com.bw.jPdfTool;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Describes the standards a document claims to follow: PDF version, PDF/A, PDF/X, PDF/UA and e-invoice.<br>
 * This only reads what the document declares in its metadata, it doesn't validate anything.
 */
public final class PdfStandard {

    private static final Pattern PDFA_PART = Pattern.compile("pdfaid:part(?:>|=\")\\s*(\\d)");
    private static final Pattern PDFA_CONFORMANCE = Pattern.compile("pdfaid:conformance(?:>|=\")\\s*([A-Za-z])");
    private static final Pattern PDFX = Pattern.compile("pdfxid:GTS_PDFXVersion(?:>|=\")\\s*([^<\"]+)");
    private static final Pattern PDFUA = Pattern.compile("pdfuaid:part(?:>|=\")\\s*(\\d)");

    private PdfStandard() {
    }

    /**
     * Gets a short description, e.g. "PDF 1.7 · PDF/A-3u · Factur-X".
     *
     * @param document The document.
     * @return The description, never null.
     */
    public static String describe(PDDocument document) {
        List<String> parts = new ArrayList<>();
        parts.add(String.format(Locale.ROOT, "PDF %.1f", document.getVersion()));

        String xmp = xmp(document);
        Matcher m = PDFA_PART.matcher(xmp);
        if (m.find()) {
            String level = m.group(1);
            Matcher c = PDFA_CONFORMANCE.matcher(xmp);
            parts.add("PDF/A-" + level + (c.find() ? c.group(1).toLowerCase(Locale.ROOT) : ""));
        }
        m = PDFX.matcher(xmp);
        if (m.find()) {
            String x = m.group(1).trim();
            parts.add(x.startsWith("PDF/X") ? x : "PDF/X-" + x);
        }
        m = PDFUA.matcher(xmp);
        if (m.find())
            parts.add("PDF/UA-" + m.group(1));

        if (EInvoice.hasInvoice(document))
            parts.add("Factur-X");
        return String.join(" · ", parts);
    }

    /**
     * Gets the PDF/A part the document declares in its metadata (1, 2, 3 or 4).
     *
     * @return The part or 0 if the document declares no PDF/A conformance.
     */
    public static int pdfaPart(PDDocument document) {
        Matcher m = PDFA_PART.matcher(xmp(document));
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /**
     * Checks if the document has an output intent with an RGB ICC profile.<br>
     * PDF/A allows device RGB colors (e.g. images rendered by this tool) only with such an output intent.
     */
    public static boolean hasRgbOutputIntent(PDDocument document) {
        try {
            for (var intent : document.getDocumentCatalog().getOutputIntents()) {
                var profile = intent.getDestOutputIntent();
                if (profile == null)
                    continue;
                try (var is = profile.createInputStream()) {
                    byte[] header = is.readNBytes(20);
                    // ICC header: bytes 16-19 are the colour space of the data.
                    if (header.length == 20 && "RGB ".equals(new String(header, 16, 4, StandardCharsets.US_ASCII)))
                        return true;
                }
            }
        } catch (Exception e) {
            Log.debug("Failed to read output intents: %s", e.getMessage());
        }
        return false;
    }

    /**
     * Name of the sRGB profile in the Mustang library. It is the profile Mustang adds to e-invoices,
     * using it here keeps all documents written by this tool consistent.
     */
    private static final String MUSTANG_SRGB_PROFILE = "sRGB.icc";

    /**
     * Adds an sRGB output intent if the document has no output intent at all.<br>
     * PDF/A needs an RGB output intent for device RGB colors (e.g. images rendered by this tool). A document with
     * another output intent (e.g. CMYK) is not changed: PDF/A allows only one output profile, and replacing it
     * would change the meaning of device colors.
     *
     * @return True if the output intent was added.
     */
    public static boolean addSrgbOutputIntent(PDDocument document) throws java.io.IOException {
        if (!document.getDocumentCatalog().getOutputIntents().isEmpty())
            return false;
        try (java.io.InputStream icc = openSrgbProfile()) {
            PDOutputIntent intent = new PDOutputIntent(document, icc);
            intent.setInfo("sRGB IEC61966-2.1");
            intent.setOutputCondition("sRGB IEC61966-2.1");
            intent.setOutputConditionIdentifier("sRGB IEC61966-2.1");
            intent.setRegistryName("http://www.color.org");
            document.getDocumentCatalog().addOutputIntent(intent);
        }
        return true;
    }

    /**
     * The sRGB profile of Mustang, or the one of Java if Mustang doesn't contain it (it is no API of Mustang).
     */
    static java.io.InputStream openSrgbProfile() {
        java.io.InputStream is = org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA3.class.getClassLoader()
                .getResourceAsStream(MUSTANG_SRGB_PROFILE);
        if (is != null)
            return is;
        Log.debug("Mustang sRGB profile not found, using the one of Java.");
        return new java.io.ByteArrayInputStream(
                java.awt.color.ICC_Profile.getInstance(java.awt.color.ColorSpace.CS_sRGB).getData());
    }

    private static String xmp(PDDocument document) {
        try {
            PDMetadata metadata = document.getDocumentCatalog().getMetadata();
            return metadata == null ? "" : new String(metadata.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.debug("Failed to read metadata: %s", e.getMessage());
            return "";
        }
    }
}
