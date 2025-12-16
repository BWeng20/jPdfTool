package com.bw.jPdfTool.cli;

import com.bw.jPdfTool.Log;
import com.bw.jPdfTool.model.DocumentProxy;
import com.bw.jPdfTool.model.MergeOptions;
import com.bw.jPdfTool.model.PdfLoadWorker;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * PDF load worker for CLI (not concurrently).
 */
public class CliPdfLoader implements PdfLoadWorker {

    private final DocumentProxy documentProxy;
    protected final Path file;
    protected final MergeOptions mo;
    protected Consumer<PDDocument> consumer;
    private final String ownerPassword;

    public CliPdfLoader(DocumentProxy proxy, Path file, MergeOptions mo, String ownerPassword) {
        this.documentProxy = proxy;
        this.file = file;
        this.mo = mo;
        this.ownerPassword = ownerPassword;
    }

    @Override
    public void execute() {
        PDDocument document = null;
        try {
            byte[] data = Files.readAllBytes(file);
            document = ownerPassword != null
                    ? Loader.loadPDF(data, ownerPassword)
                    : Loader.loadPDF(data);
            documentProxy.error = null;
        } catch (InvalidPasswordException ep) {
            documentProxy.error = "File is encrypted and owner password\ndoesn't match";
        } catch (NoSuchFileException fe) {
            documentProxy.error = String.format("File '%s' does not exist", file.getFileName());
        } catch (Exception e) {
            documentProxy.error = e.getMessage();
        }

        try {
            if (!documentProxy.isClosed()) {
                if (document != null) {
                    if (consumer != null)
                        consumer.accept(document);
                }
                Log.debug("DONE (%s)", file);
                documentProxy.loaderFinished(this, document, mo);
            }
        } catch (Exception e) {
            documentProxy.error = e.getMessage();
            documentProxy.fireDocumentLoaded();
        }
    }

    @Override
    public void cancel() {
        // Not asynchronous, nothing to stop.
    }
}
