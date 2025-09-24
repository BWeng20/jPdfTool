package com.bw.jPdfTool.model;

import com.bw.jPdfTool.Log;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Simple proxy for lazy asynchronous handling of the pdf documents.
 */
public class DocumentProxy {

    private final List<Page> pages = new ArrayList<>();
    private final List<Path> files = new ArrayList<>();
    private final List<PageConsumer> pageConsumerList = new ArrayList<>();
    private final List<DocumentConsumer> docConsumerList = new ArrayList<>();
    private boolean closed = false;
    private String owerPassword4Load;
    private final JPasswordField passwordField = new JPasswordField();
    private PDDocument document;

    private final Deque<PdfLoadWorker> loadWorker = new ArrayDeque<>();
    private PdfLoadWorker currentLoader;

    private PdfRenderWorker renderWorker;
    private String error;

    public DocumentProxy() {
    }

    /**
     * Triggers loading of the document in background
     *
     * @return The future with the document.
     */
    public synchronized Future<PDDocument> load(Path file) {
        ensuredNotClosed();
        synchronized (this) {
            PdfLoadWorker lw = new PdfLoadWorker(file);
            loadWorker.addLast(lw);
            startNextLoader();
            return lw;
        }
    }

    protected synchronized void startNextLoader() {
        if (currentLoader == null) {
            currentLoader = loadWorker.pollFirst();
            if (currentLoader != null)
                currentLoader.execute();
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

    public List<Path> getPaths() {
        return files;
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
    protected void requestOwnerPassword(Path file) {
        SwingUtilities.invokeLater(() -> {
            Object[] message = {"Enter Owner Password:", passwordField};
            int option = JOptionPane.showConfirmDialog(
                    null, message, "Owner Password needed",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
            );

            if (option == JOptionPane.OK_OPTION) {
                owerPassword4Load = new String(passwordField.getPassword());
                // retry
                loadWorker.add(new PdfLoadWorker(file));
                startNextLoader();
            } else {
                error = "File is protected.\nPassword needed.";
                fireDocumentLoaded(null);
            }
        });
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
                consumer.documentLoaded(document, files.isEmpty() ? null : files.get(0));
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

    protected void fireDocumentLoaded(Path file) {
        if (closed)
            return;
        List<DocumentConsumer> l;
        synchronized (docConsumerList) {
            l = new ArrayList<>(docConsumerList);
        }
        for (var dc : l) {
            if (error == null) {
                if (document != null)
                    dc.documentLoaded(document, file);
            } else
                dc.failed(error);
        }
        synchronized (pages) {
            for (Page p : pages) {
                if (p.image != null)
                    firePageRendered(p);
            }
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
        loadWorker.clear();
        if (currentLoader != null) {
            var cl = currentLoader;
            currentLoader = null;
            cl.cancel(true);
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
        int pageCount = pages.size();
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
        pages.remove(pageIndex);
        int pageCount = pages.size();
        for (int i = 0; i < pageCount; ++i) {
            Page p = pages.get(i);
            p.pageNb = i + 1;
            p.pageCount = pageCount;
        }
        refirePages();
    }

    private void refirePages() {
        fireDocumentLoaded(null);
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

    public void setOwnerPassword(String ownerPassword) {
        if (ownerPassword != null) {
            owerPassword4Load = ownerPassword.trim();
            if (owerPassword4Load.isEmpty())
                owerPassword4Load = null;
        } else {
            owerPassword4Load = null;
        }
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
        void documentLoaded(PDDocument document, Path file);

        void failed(String error);
    }

    protected class PdfLoadWorker extends SwingWorker<PDDocument, Void> {

        protected boolean passwordNeeded = false;
        protected final Path file;

        public PdfLoadWorker(Path file) {
            this.file = file;
        }

        @Override
        protected PDDocument doInBackground() {
            try {
                PDDocument document = owerPassword4Load != null
                        ? Loader.loadPDF(file.toFile(), owerPassword4Load)
                        : Loader.loadPDF(file.toFile());
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
                    Log.debug("DONE (%s)", file);
                    currentLoader = null;
                    PDDocument document = get();
                    if (!DocumentProxy.this.closed) {
                        if (passwordNeeded) {
                            requestOwnerPassword(file);
                        } else {
                            if (document != null) {
                                if (DocumentProxy.this.document == null) {
                                    DocumentProxy.this.document = document;
                                    pages.clear();
                                } else {
                                    // Append pages
                                    PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();
                                    pdfMergerUtility.appendDocument(DocumentProxy.this.document, document);
                                    document.close();
                                }
                                int pageCount = DocumentProxy.this.document.getNumberOfPages();
                                synchronized (pages) {
                                    while (pages.size() < pageCount) {
                                        Page page = new Page(DocumentProxy.this, pages.size() + 1, pageCount);
                                        pages.add(page);
                                    }
                                }
                                fireDocumentLoaded(file);
                                Log.debug("Start renderer (%s)", file);

                                PdfRenderWorker render = new PdfRenderWorker();
                                render.execute();
                            }
                        }
                    }
                } catch (Exception e) {
                    error = e.getMessage();
                    fireDocumentLoaded(null);
                }
                startNextLoader();
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

                int pageCount = pages.size();
                for (int i = 0; i < pageCount; i++) {
                    if (closed)
                        break;
                    Page page;
                    synchronized (pages) {
                        page = pages.get(i);
                    }
                    if (page.image == null) {
                        Log.debug("Render page #%d", i);
                        page.image = renderer.renderImageWithDPI(i, 300);
                        publish(page);
                    }
                }
                Log.debug("Render finished");
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
