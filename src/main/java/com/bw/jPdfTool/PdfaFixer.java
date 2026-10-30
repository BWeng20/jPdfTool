package com.bw.jPdfTool;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Walks all objects of a document once, removes entries PDF/A forbids and collects the content streams,
 * see {@link PdfStandard#fixPdfaIssues}.
 */
final class PdfaFixer {
    private static final Set<String> FORBIDDEN_ACTIONS = Set.of("Launch", "Sound", "Movie", "ResetForm",
            "ImportData", "JavaScript", "Hide", "SetOCGState", "Rendition", "Trans", "GoTo3DView");
    private static final Set<String> RENDERING_INTENTS = Set.of("AbsoluteColorimetric", "RelativeColorimetric",
            "Saturation", "Perceptual");
    private static final COSName OPI = COSName.getPDFName("OPI");
    private static final COSName ALTERNATES = COSName.getPDFName("Alternates");
    private static final COSName REF = COSName.getPDFName("Ref");
    private static final COSName TR2 = COSName.getPDFName("TR2");
    private static final COSName HTP = COSName.getPDFName("HTP");
    private static final COSName JAVASCRIPT = COSName.getPDFName("JavaScript");
    private static final COSName OPEN_ACTION = COSName.getPDFName("OpenAction");
    private static final int FLAG_HIDDEN = 2, FLAG_PRINT = 4, FLAG_NOVIEW = 32;

    private final Set<COSBase> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private final List<COSStream> contents = new ArrayList<>();
    /**
     * What was changed and how often, in the order of the first change.
     */
    private final Map<String, Integer> changes = new LinkedHashMap<>();

    private void changed(String what, int times) {
        changes.merge(what, times, Integer::sum);
    }

    private boolean remove(COSDictionary d, COSName key) {
        if (!d.containsKey(key))
            return false;
        d.removeItem(key);
        changed("Removed forbidden entry /" + key.getName(), 1);
        return true;
    }

    private void fixIntent(COSDictionary d, COSName key) {
        COSName ri = d.getCOSName(key);
        if (ri != null && !RENDERING_INTENTS.contains(ri.getName())) {
            d.setItem(key, COSName.getPDFName("RelativeColorimetric"));
            changed("Replaced unknown rendering intent", 1);
        }
    }

    private void fixAction(COSDictionary d, COSName key) {
        if (d.getDictionaryObject(key) instanceof COSDictionary action) {
            COSName type = action.getCOSName(COSName.S);
            if (type != null && FORBIDDEN_ACTIONS.contains(type.getName())) {
                d.removeItem(key);
                changed("Removed forbidden action " + type.getName(), 1);
            }
        }
    }

    private void fixGraphicsState(COSDictionary state) {
        remove(state, COSName.TR);
        remove(state, HTP);
        // Only /Default is allowed for TR2.
        COSBase tr2 = state.getDictionaryObject(TR2);
        if (tr2 != null && !(tr2 instanceof COSName n && n.getName().equals("Default")))
            remove(state, TR2);
        fixIntent(state, COSName.getPDFName("RI"));
    }

    private void fixDictionary(COSDictionary d) {
        COSName type = d.getCOSName(COSName.TYPE);
        COSName subtype = d.getCOSName(COSName.SUBTYPE);
        boolean stream = d instanceof COSStream;

        if (stream) {
            remove(d, COSName.F);
            remove(d, COSName.F_FILTER);
            remove(d, COSName.F_DECODE_PARMS);
        }
        if (COSName.IMAGE.equals(subtype)) {
            if (d.getBoolean(COSName.INTERPOLATE, false)) {
                d.setBoolean(COSName.INTERPOLATE, false);
                changed("Disabled image interpolation", 1);
            }
            remove(d, ALTERNATES);
            remove(d, OPI);
            fixIntent(d, COSName.INTENT);
        } else if (COSName.FORM.equals(subtype)) {
            remove(d, OPI);
            remove(d, REF);
        }
        if (d.getDictionaryObject(COSName.EXT_G_STATE) instanceof COSDictionary states)
            for (COSName name : states.keySet())
                if (states.getDictionaryObject(name) instanceof COSDictionary state)
                    fixGraphicsState(state);
        if (COSName.CATALOG.equals(type)) {
            remove(d, COSName.AA);
            fixAction(d, OPEN_ACTION);
            if (d.getDictionaryObject(COSName.NAMES) instanceof COSDictionary names)
                remove(names, JAVASCRIPT);
        }
        if (COSName.WIDGET.equals(subtype) || d.containsKey(COSName.FT))
            remove(d, COSName.AA);
        fixAction(d, COSName.A);
        if (COSName.PAGE.equals(type) && d.getDictionaryObject(COSName.ANNOTS) instanceof COSArray annots)
            for (int i = 0; i < annots.size(); ++i)
                if (annots.getObject(i) instanceof COSDictionary annot
                        && !COSName.POPUP.equals(annot.getCOSName(COSName.SUBTYPE))) {
                    int f = annot.getInt(COSName.F, 0);
                    int fixedFlags = (f | FLAG_PRINT) & ~(FLAG_HIDDEN | FLAG_NOVIEW);
                    if (fixedFlags != f) {
                        annot.setInt(COSName.F, fixedFlags);
                        changed("Fixed annotation flags (printable, visible)", 1);
                    }
                }
    }

    void visit(COSBase base) {
        if (base instanceof COSObject o)
            base = o.getObject();
        if (base == null || !visited.add(base))
            return;
        if (base instanceof COSDictionary d) {
            fixDictionary(d);
            if (d instanceof COSStream s) {
                if (COSName.FORM.equals(d.getCOSName(COSName.SUBTYPE)) || d.getInt(COSName.PATTERN_TYPE) == 1)
                    contents.add(s);
            }
            if (COSName.PAGE.equals(d.getCOSName(COSName.TYPE))) {
                COSBase c = d.getDictionaryObject(COSName.CONTENTS);
                if (c instanceof COSStream s)
                    contents.add(s);
                else if (c instanceof COSArray a)
                    for (int i = 0; i < a.size(); ++i)
                        if (a.getObject(i) instanceof COSStream s)
                            contents.add(s);
            }
            if (COSName.TYPE3.equals(d.getCOSName(COSName.SUBTYPE)) && d.getDictionaryObject(COSName.CHAR_PROCS) instanceof COSDictionary procs)
                for (COSBase p : procs.getValues())
                    if (p instanceof COSObject o && o.getObject() instanceof COSStream s)
                        contents.add(s);
                    else if (p instanceof COSStream s)
                        contents.add(s);
            for (COSBase v : d.getValues())
                visit(v);
        } else if (base instanceof COSArray a) {
            for (int i = 0; i < a.size(); ++i)
                visit(a.get(i));
        }
    }

    /**
     * Fixes the inline images of the collected content streams.
     */
    void fixContentStreams() throws IOException {
        for (COSStream s : new java.util.LinkedHashSet<>(contents)) {
            byte[] data;
            try (InputStream is = s.createInputStream()) {
                data = is.readAllBytes();
            }
            // Cheap check before parsing: inline images start with the BI operator.
            if (!new String(data, StandardCharsets.ISO_8859_1).contains("BI"))
                continue;
            List<Object> tokens = new PDFStreamParser(data).parse();
            int n = 0;
            for (Object t : tokens) {
                if (t instanceof Operator op && "BI".equals(op.getName()) && op.getImageParameters() != null) {
                    COSDictionary p = op.getImageParameters();
                    if (p.getBoolean(COSName.I, false) || p.getBoolean(COSName.INTERPOLATE, false)) {
                        p.removeItem(COSName.INTERPOLATE);
                        p.setBoolean(COSName.I, false);
                        ++n;
                    }
                }
            }
            if (n == 0)
                continue;
            try (OutputStream os = s.createOutputStream(COSName.FLATE_DECODE)) {
                new ContentStreamWriter(os).writeTokens(tokens);
            }
            changed("Disabled inline image interpolation", n);
        }
    }

    /**
     * @return What was changed and how often, empty if nothing.
     */
    static Map<String, Integer> fix(PDDocument document) throws IOException {
        PdfaFixer fixer = new PdfaFixer();
        fixer.visit(document.getDocument().getTrailer());
        fixer.fixContentStreams();
        return fixer.changes;
    }
}
