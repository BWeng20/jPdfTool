package com.bw.jPdfTool.cli;

import com.bw.jPdfTool.SignatureTool;
import com.bw.jPdfTool.model.DocumentProxy;
import com.bw.jPdfTool.model.MergeOptions;
import com.bw.jPdfTool.model.RenderQueue;
import org.apache.pdfbox.Loader;
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
    public void loadDocuments(String password2Load) {

        if (documentProxy != null)
            documentProxy.close();

        // Currently we simply need an instance bit don't actually render .
        RenderQueue renderQueue = new RenderQueue();
        documentProxy = new DocumentProxy(renderQueue);

        // TODO: We could use multiple threads here, but for now keep it simple.
        // TODO: Support setting MergeOptions from arguments.
        for (Path p : files) {
            CliPdfLoader lw = new CliPdfLoader(documentProxy, p, new MergeOptions(), password2Load);
            lw.execute();
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
            try {
                final boolean doSign = signatureKeyPath != null;

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
                        sign(document, signatureKeyPath, signatureKeyPwd);
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
    }

    public void sign(PDDocument document, Path signatureKeyPath, char[] pw) throws Exception {
        SignatureTool createSignature = new SignatureTool();

        try (InputStream is = Files.newInputStream(signatureKeyPath)) {
            String alias = createSignature.addKey(is, pw);
            createSignature.addSignature(document, alias, pw);
        }
    }

    public void close() {
        if ( documentProxy != null) {
            documentProxy.close();
        }
    }
}
