package com.bw.jPdfTool;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Simple proxy for lazy asynchronous handling of the pdf documents.
 */
public class DocumentProxy {

    public Path file;
    private PDDocument document;
    public boolean stopProcessing = false;
    public String owerPassword4Load;

    public DocumentProxy(Path file) {
        this.file = file;
    }

    public synchronized PDDocument getDocument() throws IOException {
        if (document == null)
            document = owerPassword4Load != null
                    ? Loader.loadPDF(file.toFile(), owerPassword4Load)
                    : Loader.loadPDF(file.toFile());
        return document;
    }


    public void close() {
        stopProcessing = true;
        if (document != null) {
            try {
                document.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            document = null;
        }
    }
}
