package com.bw.jPdfTool.cli;

import com.bw.jPdfTool.Log;
import com.bw.jPdfTool.SignatureTool;
import com.bw.jPdfTool.model.DocumentProxy;
import com.bw.jPdfTool.model.MergeOptions;
import com.bw.jPdfTool.model.RenderQueue;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Tool to execute CLI commands.
 */
public class CommandExecuter {

    protected final List<Path> files;
    protected DocumentProxy documentProxy;

    public CommandExecuter(List<Path> files) {
        this.files = new ArrayList<>(files);
    }

    /**
     * Loads all files.
     */
    public void loadDocuments(String password2Load, MergeOptions mo) {

        if (documentProxy != null)
            documentProxy.close();

        // Currently we simply need an instance bit don't actually render .
        RenderQueue renderQueue = new RenderQueue();
        documentProxy = new DocumentProxy(renderQueue);

        if (mo == null)
            mo = new MergeOptions();

        // TODO: We could use multiple threads here, but for now keep it simple.
        // TODO: Support setting MergeOptions from arguments.
        for (Path p : files) {
            CliPdfLoader lw = new CliPdfLoader(documentProxy, p, mo, password2Load);
            lw.execute();
        }
    }

    /**
     * Split the effective document.
     * The document is closed during the call.
     */
    public void split(int pagePerDocument,
                      String ownerPwd, String userPwd,
                      AccessPermission ap, int encryptionKeyLength,
                      Path baseFileName,
                      Path signatureKeyPath, char[] signatureKeyPwd) throws Exception {

        if (documentProxy != null && documentProxy.getDocument() != null) {
            PDDocument document = documentProxy.getCopy();
            final boolean doSign = signatureKeyPath != null;
            try {
                String fprefix;
                String fpostfix;
                String fname = baseFileName.getFileName().toString();
                int idx = fname.lastIndexOf('.');
                if (idx < 0) {
                    fprefix = baseFileName.toAbsolutePath().toString();
                    fpostfix = ".pdf";
                } else {
                    fprefix = baseFileName.toAbsolutePath().getParent().resolve(fname.substring(0, idx)).toString();
                    fpostfix = fname.substring(idx);
                }

                int fileCount = 0;
                String alias = null;
                SignatureTool createSignature = null;

                if (doSign) {
                    createSignature = new SignatureTool();
                    try (InputStream is = Files.newInputStream(signatureKeyPath)) {
                        alias = createSignature.addKey(is, signatureKeyPwd);
                    }
                }

                final PDDocument source = documentProxy.getCopy();
                Splitter splitter = new Splitter();
                splitter.setSplitAtPage(pagePerDocument);
                List<PDDocument> splittedDocs = splitter.split(source);
                for (PDDocument doc : splittedDocs) {
                    String docFile = String.format("%s%03d%s", fprefix, ++fileCount, fpostfix);
                    if (doSign) {
                        saveDocument(doc, ownerPwd, userPwd, ap, encryptionKeyLength, Paths.get(docFile), createSignature, alias, signatureKeyPwd);
                    } else {
                        saveDocument(doc, ownerPwd, userPwd, ap, encryptionKeyLength, Paths.get(docFile), null, null, null);
                    }
                    Log.info("Stored file '%s'", docFile);
                }
                source.close();
            } finally {
                if (document != null)
                    document.close();
            }
        }
    }

    /**
     * Save the effective document.
     * The document is closed during the call.
     */
    public void save(String ownerPwd, String userPwd,
                     AccessPermission ap, int encryptionKeyLength,
                     Path file,
                     Path signatureKeyPath, char[] signatureKeyPwd
    ) throws Exception {
        if (documentProxy != null && documentProxy.getDocument() != null) {
            PDDocument document = documentProxy.getCopy();
            final boolean doSign = signatureKeyPath != null;
            if (doSign) {
                SignatureTool createSignature = new SignatureTool();

                String alias;
                try (InputStream is = Files.newInputStream(signatureKeyPath)) {
                    alias = createSignature.addKey(is, signatureKeyPwd);
                }
                saveDocument(document, ownerPwd, userPwd, ap, encryptionKeyLength, file, createSignature, alias, signatureKeyPwd);

            } else {
                saveDocument(document, ownerPwd, userPwd, ap, encryptionKeyLength, file, null, null, null);
            }
        }
    }


    protected void saveDocument(PDDocument document, String ownerPwd, String userPwd,
                                AccessPermission ap, int encryptionKeyLength,
                                Path file,
                                SignatureTool createSignature, String keyAlias, char[] signatureKeyPwd) throws Exception {

        try {
            final boolean doSign = createSignature != null;

            if ((!ownerPwd.isEmpty()) || (!userPwd.isEmpty())) {
                StandardProtectionPolicy spp = new StandardProtectionPolicy(ownerPwd, userPwd, ap);
                spp.setEncryptionKeyLength(encryptionKeyLength);
                document.protect(spp);

                if (doSign) {
                    // For some reason the encryption breaks if we sign fresh protected document.
                    // If we reload it (in protected state) it works.
                    ByteArrayOutputStream os = new ByteArrayOutputStream(5 * 1024 * 1024);
                    document.save(os, CompressParameters.NO_COMPRESSION);
                    document.close();
                    document = Loader.loadPDF(os.toByteArray(), ownerPwd.isEmpty() ? userPwd : ownerPwd);
                }
            }

            try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(file))) {
                if (doSign) {
                    createSignature.addSignature(document, keyAlias, signatureKeyPwd);
                    document.saveIncremental(os);
                } else {
                    document.save(os);
                }
            }
        } finally {
            if (document != null)
                document.close();
        }
    }


    public void close() {
        if (documentProxy != null) {
            documentProxy.close();
        }
    }
}
