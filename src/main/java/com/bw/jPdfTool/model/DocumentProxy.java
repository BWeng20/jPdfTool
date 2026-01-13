package com.bw.jPdfTool.model;

import com.bw.jPdfTool.Log;
import com.bw.jPdfTool.Preferences;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

/**
 * Simple proxy for lazy asynchronous handling of the pdf documents.
 */
public class DocumentProxy {

    public final List<Page> pages = new ArrayList<>();
    private final List<DocumentConsumer> docConsumerList = new ArrayList<>();
    private boolean closed = false;
    private PDDocument document;
    private final RenderQueue renderQueue;
    private final List<DocumentProxy.PageConsumer> pageConsumerList = new ArrayList<>();

    public final Deque<PdfLoadWorker> loadWorker = new ArrayDeque<>();
    public PdfLoadWorker currentLoader;

    public String error;

    public DocumentProxy(RenderQueue renderQueue) {
        this.renderQueue = renderQueue;
    }

    /**
     * Starts the next background loader task.
     * Document for document and if all documents are loaded, renders all pages.<br>
     * This is done parallel to the UI that shows the known pages.
     */
    public synchronized void startNextLoader() {
        if (currentLoader == null || currentLoader.isFinished()) {
            do {
                currentLoader = loadWorker.pollFirst();
            } while (currentLoader != null && currentLoader.isFinished());
            if (currentLoader != null) {
                currentLoader.execute();
            }
        }
        if (currentLoader == null && DocumentProxy.this.document != null) {
            int pageCount = DocumentProxy.this.document.getNumberOfPages();
            while (pages.size() < pageCount) {
                Page page = new Page(DocumentProxy.this, pages.size() + 1, pageCount);
                pages.add(page);
            }
            renderQueue.addDocument(this);
        }
    }

    /**
     * Get the effective document
     *
     * @return Null if no document was loaded yet.
     */
    public PDDocument getDocument() {
        return document;
    }

    /**
     * Set the effective document
     */
    public void setDocument(PDDocument document) {
        this.document = document;
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

    private boolean notificationTriggered = false;
    private final List<Page> renderedPages = new LinkedList<>();

    /**
     * Called by renderer if a page was finished.<br>
     * Called from worker threads.
     */
    public void firePageRendered(Page page) {
        if (closed || page == null)
            return;
        synchronized (renderedPages) {
            renderedPages.add(page);
            if (!notificationTriggered) {
                notificationTriggered = true;
                SwingUtilities.invokeLater(() -> {
                    List<Page> pages;
                    synchronized (renderedPages) {
                        notificationTriggered = false;
                        pages = new ArrayList<>(renderedPages);
                        renderedPages.clear();
                    }
                    List<PageConsumer> l;
                    synchronized (pageConsumerList) {
                        l = new ArrayList<>(pageConsumerList);
                    }
                    Log.debug("%d pages finished", pages.size());
                    for (Page p : pages) {
                        for (var pc : l) {
                            pc.pageRendered(p);
                        }
                    }
                });
            }
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

    public void fireDocumentLoaded() {
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
        List<Page> toFire;
        synchronized (this) {
            toFire = new ArrayList<>(pages);
        }
        for (Page p : toFire) {
            if (p.image != null)
                firePageRendered(p);
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
                Log.error("Error closing document: %s", e.getMessage());
            }
            document = null;
        }
        loadWorker.clear();
        if (currentLoader != null) {
            var cl = currentLoader;
            currentLoader = null;
            if (!cl.isFinished())
                cl.cancel();
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
        final int pageCount = pages.size();
        if (pageNb < 0 || pageNb > pageCount) {
            throw new IllegalArgumentException("Page " + pageNb + " is out of range");
        }

        PDPageTree tree = document.getDocumentCatalog().getPages();
        final int pageIndex = pageNb - 1;
        final int targetPrevPageIndex = (offset < 0) ? (pageIndex + offset - 1) : (pageIndex + offset);

        if (targetPrevPageIndex != pageIndex) {
            if (targetPrevPageIndex >= 0) {
                if (targetPrevPageIndex < pageCount) {
                    PDPage pdPage = tree.get(pageIndex);
                    PDPage prevPage = tree.get(targetPrevPageIndex);
                    tree.remove(pageIndex);
                    tree.insertAfter(pdPage, prevPage);
                } else
                    return;
            } else if (pageIndex > 0) {
                PDPage pdPage = tree.get(pageIndex);
                PDPage nextPage = tree.get(0);
                tree.remove(pageIndex);
                tree.insertBefore(pdPage, nextPage);
            } else
                return;

            synchronized (this) {
                pages.clear();
                for (int i = 0; i < pageCount; ++i) {
                    Page page = new Page(DocumentProxy.this, i + 1, pageCount);
                    pages.add(page);
                }
            }
            refirePages();
        }
    }

    /**
     * Get the rotation of a page.
     *
     * @param pageNb The 1-based page number.
     * @return The rotation in degree 0,90,180 or 270.
     */
    public int getPageRotation(int pageNb) {
        ensuredDocument();

        if (document.getNumberOfPages() >= pageNb) {
            int pageIndex = pageNb - 1;
            PDPage pd = document.getPage(pageIndex);
            return pd.getRotation();
        } else
            return 0;
    }

    public void rotatePage(int pageNb, int degree) {
        ensuredDocument();

        if (document.getNumberOfPages() >= pageNb) {
            int pageIndex = pageNb - 1;

            Page p = pages.get(pageIndex);
            p.image = null;
            p.scale = 0;
            PDPage pd = document.getPage(pageIndex);
            pd.setRotation(pd.getRotation() + degree);
            refirePages();
        }
    }

    public void renderPageToImage(int pageNb) {
        ensuredDocument();
        if (document.getNumberOfPages() >= pageNb) {

            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int pageIndex = pageNb - 1;

            try {

                final PDPage oldPage = document.getPage(pageIndex);

                int rotation = oldPage.getRotation();
                oldPage.setRotation(0);

                PDRectangle mediaBox = oldPage.getMediaBox();
                PDPage newPage = new PDPage(mediaBox);

                int dpi = Preferences.getInstance().getDpi();

                BufferedImage bim = pdfRenderer.renderImageWithDPI(pageIndex, dpi);
                PDImageXObject pdImage = LosslessFactory.createFromImage(document, bim);

                try (PDPageContentStream contentStream = new PDPageContentStream(document, newPage)) {
                    contentStream.drawImage(pdImage, 0, 0, mediaBox.getWidth(), mediaBox.getHeight());
                }
                newPage.setRotation(rotation);

                var pageTree = document.getPages();
                pageTree.insertAfter(newPage, oldPage);
                pageTree.remove(oldPage);

                // should visibly the same, but to ensure WYSIWYG, force to re-render the page
                pages.get(pageIndex).image = null;

                refirePages();
            } catch (Exception e) {

            }
        }
    }

    public void deletePage(int pageNb) {

        ensuredDocument();

        if (document.getNumberOfPages() >= pageNb) {

            int pageIndex = pageNb - 1;

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
    }

    private void refirePages() {
        fireDocumentLoaded();
        boolean imageMissing = false;
        List<Page> pagesToFire;
        synchronized (this) {
            pagesToFire = new ArrayList<>(pages);
        }
        for (Page p : pagesToFire) {
            if (p.image == null) {
                imageMissing = true;
                break;
            }
        }
        if (imageMissing) {
            renderQueue.addDocument(this);
        }
    }

    public void ensuredNotClosed() {
        if (closed)
            throw new IllegalStateException("Document is closed");
    }

    protected void ensuredDocument() {
        ensuredNotClosed();
        if (document == null)
            throw new IllegalStateException("No Document loaded");
    }

    public synchronized Page getPage(int pageNb) {
        ensuredDocument();
        if (pageNb <= pages.size())
            return pages.get(pageNb - 1);
        else
            return null;
    }


    public boolean isClosed() {
        return closed;
    }

    public int getPageCount() {
        return pages.size();
    }

    public synchronized boolean needsRendering() {
        // Closed documents don't have pages to render!
        if (!closed) {
            for (Page p : pages) {
                if (p.image == null && p.error == null)
                    return true;
            }
        }
        return false;
    }

    public synchronized void loaderFinished(PdfLoadWorker loadWorker, PDDocument document, MergeOptions mo) {
        boolean fireLoaded = false;
        int oldPageCount = -1;
        if (!isClosed()) {
            {
                if (document != null) {
                    int pageCount;
                    if (this.getDocument() == null) {
                        this.setDocument(document);
                        this.pages.clear();
                        pageCount = this.getDocument().getNumberOfPages();
                    } else {
                        // Append pages
                        try {
                            oldPageCount = this.getDocument().getNumberOfPages();
                            PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();
                            pdfMergerUtility.appendDocument(this.getDocument(), document);
                        } catch (Exception e) {
                            Log.error("Failed to merge. %s", e.getMessage());
                            oldPageCount = -1;
                        } finally {
                            IOUtils.closeQuietly(document);
                        }
                        pageCount = this.pages.size() + document.getNumberOfPages();
                    }
                    // zipper merge if requested
                    if (mo.startPageNb > 0 && mo.startPageNb < oldPageCount) {
                        int targetPageIndex = mo.startPageNb - 1;
                        int segmentCount = 0;
                        int pageToMoveIndex = oldPageCount;
                        PDPageTree tree = this.getDocument().getDocumentCatalog().getPages();

                        while (pageToMoveIndex < pageCount) {

                            PDPage pdPage = tree.get(pageToMoveIndex);
                            if (targetPageIndex > 0) {
                                PDPage prevPage = tree.get(targetPageIndex - 1);
                                tree.remove(pageToMoveIndex);
                                tree.insertAfter(pdPage, prevPage);
                            } else {
                                PDPage nextPage = tree.get(targetPageIndex);
                                tree.remove(pageToMoveIndex);
                                tree.insertBefore(pdPage, nextPage);
                            }

                            ++pageToMoveIndex;
                            ++segmentCount;

                            if (segmentCount >= mo.segmentLength && mo.gapLength > 0) {
                                targetPageIndex += mo.gapLength;
                            }
                            ++targetPageIndex;

                        }
                        this.pages.clear();
                    }

                    while (this.pages.size() < pageCount) {
                        Page page = new Page(this, this.pages.size() + 1, pageCount);
                        this.pages.add(page);
                    }

                    // Tell anyone, that a new document is loaded.
                    fireLoaded = true;
                } else if (this.error != null) {
                    fireLoaded = true;
                }
            }
        }
        if (fireLoaded) {
            fireDocumentLoaded();
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
         * @param document The effective main document, never null
         */
        void documentLoaded(PDDocument document);

        void failed(String error);
    }

    /**
     * Brute force copy. Writes to byte array and load it.
     *
     * @throws IOException If some write operation failed.
     */
    public PDDocument getCopy() throws IOException {
        if (document != null) {
            document.setAllSecurityToBeRemoved(true);
            ByteArrayOutputStream os = new ByteArrayOutputStream(5 * 1024 * 1024);
            document.save(os, CompressParameters.NO_COMPRESSION);
            return Loader.loadPDF(os.toByteArray());
        } else
            return null;
    }

}
