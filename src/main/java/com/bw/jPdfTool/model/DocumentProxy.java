package com.bw.jPdfTool.model;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Simple proxy for lazy asynchronous handling of the pdf documents.
 */
public class DocumentProxy {

    public final List<Page> pages = new ArrayList<>();
    private final Path file;
    private final List<PageConsumer> pageConsumerList = new ArrayList<>();
    private final List<DocumentConsumer> docConsumerList = new ArrayList<>();
    public int pageCount = -1;
    public boolean closed = false;
    public String owerPassword4Load;
    protected JPasswordField passwordField = new JPasswordField();
    private PDDocument document;
    private PdfLoadWorker loadWorker;
    private PdfRenderWorker renderWorker;
    private String error;

    public DocumentProxy(Path file) {
        this.file = file;
    }

    /**
     * Triggers loading of the document in background
     *
     * @return The future with the document. Completed, if document was already. loaded
     */
    public synchronized Future<PDDocument> load() {
        ensuredNotClosed();
        synchronized (this) {
            if (document == null && error == null) {
                if (loadWorker == null) {
                    loadWorker = new PdfLoadWorker();
                    loadWorker.execute();
                }
                return loadWorker;
            }
            return CompletableFuture.completedFuture(document);
        }
    }

    /**
     * Returns the error description if the document couldn't be loaded.
     *
     * @return null if no error occurred.
     */
    public String getError() {
        return error;
    }

    public Path getPath() {
        return file;
    }

    /**
     * Get the loaded docunent
     *
     * @return Null if document was not loaded.
     */
    public PDDocument getLoadedDocument() {
        return document;
    }

    /**
     * Helper to request the owner password from user.
     */
    protected String requestOwnerPassword() {
        Object[] message = {"Enter Owner Password:", passwordField};
        int option = JOptionPane.showConfirmDialog(
                null, message, "Owner Password needed",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        );

        if (option == JOptionPane.OK_OPTION) {
            char[] password = passwordField.getPassword();
            return new String(password);
        } else {
            return null;
        }
    }

    /**
     * Adds a Page consumer.<br>
     * The method calls the consumer directly from this inside call
     * for already available pages.
     *
     * @param consumer The consumer.
     */
    public void addPageConsumer(PageConsumer consumer) {
        ensuredNotClosed();
        synchronized (pageConsumerList) {
            pageConsumerList.remove(consumer);
            pageConsumerList.add(consumer);
        }
        synchronized (this) {
            for (Page p : pages)
                if (p.image != null)
                    consumer.pageRendered(p);
        }
    }

    public void removePageConsumer(PageConsumer consumer) {
        synchronized (pageConsumerList) {
            pageConsumerList.remove(consumer);
        }
    }

    protected void firePageRendered(Page page) {
        if (closed)
            return;
        List<PageConsumer> l;
        synchronized (pageConsumerList) {
            l = new ArrayList<>(pageConsumerList);
        }
        for (var pc : l) {
            pc.pageRendered(page);
        }
    }

    /**
     * Adds a consumer. Calls the consumer directly from this call if the document was loaded.
     *
     * @param consumer The consumer.
     */
    public void addDocumentConsumer(DocumentConsumer consumer) {
        ensuredNotClosed();
        synchronized (docConsumerList) {
            docConsumerList.remove(consumer);
            docConsumerList.add(consumer);
        }
        synchronized (this) {
            if (document != null) {
                consumer.documentLoaded(document);
            } else if (error != null) {
                consumer.failed(error);
            }
        }
    }

    public void removeDocumentConsumer(DocumentConsumer consumer) {
        synchronized (docConsumerList) {
            docConsumerList.remove(consumer);
        }
    }

    protected void fireDocumentLoaded() {
        if (closed)
            return;
        List<DocumentConsumer> l;
        synchronized (docConsumerList) {
            l = new ArrayList<>(docConsumerList);
        }
        for (var dc : l) {
            if (error == null) {
                if (document != null)
                    dc.documentLoaded(document);
            } else
                dc.failed(error);
        }
    }

    /**
     * CLose all resources and stops any background activity.<br>
     * The proxy is not usable after this call and will throw IllegalStateExceptions if you try.
     */
    public synchronized void close() {
        closed = true;
        docConsumerList.clear();
        pageConsumerList.clear();
        if (document != null) {
            try {
                document.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            document = null;
        }
        if (loadWorker != null) {
            loadWorker.cancel(true);
            loadWorker = null;
        }
        if (renderWorker != null) {
            renderWorker.cancel(true);
            renderWorker = null;
        }
    }

    /**
     * Moves a page by offset. The ensure no cache issues, the complete document is re-rendered.
     *
     * @param pageNb The 1-based page number.
     * @param offset The offset
     */
    public void movePage(int pageNb, int offset) {
        ensuredDocument();
        if (pageNb < 0 || pageNb > pageCount) {
            throw new IllegalArgumentException("Page " + pageNb + " is out of range");
        }

        PDPageTree tree = document.getDocumentCatalog().getPages();
        int pageIndex = pageNb - 1;
        PDPage pdPage = tree.get(pageIndex);
        PDPage prevPage = tree.get(pageIndex + offset);
        tree.remove(pageIndex);
        tree.insertAfter(pdPage, prevPage);

        synchronized (pages) {
            pages.clear();
            for (int i = 0; i < pageCount; ++i) {
                Page page = new Page(DocumentProxy.this, i + 1, pageCount);
                pages.add(page);
            }
        }
        refirePages();
    }

    /**
     * Get the rotation of a page.
     *
     * @param pageNb The 1-based page number.
     * @return The rotation in degree 0,90,180 or 270.
     */
    public int getPageRotation(int pageNb) {
        ensuredDocument();

        int pageIndex = pageNb - 1;
        PDPage pd = document.getPage(pageIndex);
        return pd.getRotation();
    }

    public void rotatePage(int pageNb, int degree) {
        ensuredDocument();

        int pageIndex = pageNb - 1;

        if (renderWorker != null) {
            renderWorker.cancel(true);
            renderWorker = null;
        }
        Page p = pages.get(pageIndex);
        p.image = null;
        p.scale = 0;
        PDPage pd = document.getPage(pageIndex);
        pd.setRotation(pd.getRotation() + degree);
        refirePages();
    }

    public void deletePage(int pageNb) {
        ensuredDocument();

        int pageIndex = pageNb - 1;

        if (renderWorker != null) {
            renderWorker.cancel(true);
            renderWorker = null;
        }

        // 0-based index!
        document.removePage(pageIndex);
        pageCount = document.getNumberOfPages();

        pages.remove(pageIndex);
        for (int i = 0; i < pageCount; ++i) {
            Page p = pages.get(i);
            p.pageNb = i + 1;
            p.pageCount = pageCount;
        }
        refirePages();
    }

    private void refirePages() {
        fireDocumentLoaded();
        boolean imageMissing = false;
        for (Page p : pages) {
            if (p.image == null)
                imageMissing = true;
            else
                firePageRendered(p);
        }
        if (imageMissing) {
            renderWorker = new PdfRenderWorker();
            renderWorker.execute();
        }

    }

    protected void ensuredNotClosed() {
        if (closed)
            throw new IllegalStateException("Document is closed");
    }

    protected void ensuredDocument() {
        ensuredNotClosed();
        if (document == null)
            throw new IllegalStateException("No Document loaded");
    }

    public PDPage getPDPage(int pageNb) {
        ensuredDocument();
        return document.getPage(pageNb - 1);
    }

    /**
     * Interface to notify about rendered pages.
     */
    public interface PageConsumer {
        void pageRendered(Page page);
    }

    /**
     * Interface to notify if the document was loaded.
     */
    public interface DocumentConsumer {

        /**
         * Load operation was successful .
         *
         * @param document The document, never null
         */
        void documentLoaded(PDDocument document);

        void failed(String error);
    }

    protected class PdfLoadWorker extends SwingWorker<PDDocument, Void> {

        boolean passwordNeeded = false;

        @Override
        protected PDDocument doInBackground() {
            try {
                PDDocument document = owerPassword4Load != null
                        ? Loader.loadPDF(file.toFile(), owerPassword4Load)
                        : Loader.loadPDF(file.toFile());
                pageCount = document.getNumberOfPages();
                error = null;
                return document;
            } catch (InvalidPasswordException ep) {
                error = "File is encrypted and owner password\ndoesn't match";
                passwordNeeded = true;
                return null;
            } catch (Exception e) {
                error = e.getMessage();
                return null;
            }
        }

        @Override
        protected void done() {
            synchronized (DocumentProxy.this) {
                try {
                    document = get();
                    if (loadWorker == PdfLoadWorker.this) {
                        loadWorker = null;
                        if (document == null && passwordNeeded) {
                            String password = requestOwnerPassword();
                            if (password != null) {
                                owerPassword4Load = password;
                                // retry
                                loadWorker = new PdfLoadWorker();
                                loadWorker.execute();
                            } else {
                                error = "File is protected.\nPassword needed.";
                                fireDocumentLoaded();
                            }
                        }
                    }
                    if (document != null) {
                        synchronized (pages) {
                            pages.clear();
                            for (int i = 0; i < pageCount; ++i) {
                                Page page = new Page(DocumentProxy.this, i + 1, pageCount);
                                pages.add(page);
                            }
                        }
                        fireDocumentLoaded();
                        PdfRenderWorker render = new PdfRenderWorker();
                        render.execute();
                    }
                } catch (Exception e) {
                    error = e.getMessage();
                    fireDocumentLoaded();
                }
            }
        }
    }

    protected class PdfRenderWorker extends SwingWorker<Void, Page> {

        @Override
        protected void done() {
        }

        @Override
        protected Void doInBackground() {
            try {
                PDFRenderer renderer = new PDFRenderer(document);

                RenderingHints renderingHints = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                renderer.setRenderingHints(renderingHints);

                for (int i = 0; i < pageCount; i++) {
                    if (closed)
                        break;
                    Page page;
                    synchronized (pages) {
                        page = pages.get(i);
                    }
                    if (page.image == null) {
                        page.image = renderer.renderImageWithDPI(i, 300);
                        publish(page);
                    }
                }
                error = null;
            } catch (InvalidPasswordException ep) {
                error = "File is encrypted and owner password\ndoesn't match";
            } catch (Exception e) {
                error = e.getMessage();
            }
            return null;
        }

        @Override
        protected void process(java.util.List<Page> pages) {
            for (Page p : pages)
                firePageRendered(p);
        }
    }
}
