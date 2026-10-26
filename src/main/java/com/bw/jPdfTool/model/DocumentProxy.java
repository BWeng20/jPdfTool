package com.bw.jPdfTool.model;

import com.bw.jPdfTool.invoice.EInvoice;
import com.bw.jPdfTool.Log;
import com.bw.jPdfTool.Preferences;
import com.bw.jPdfTool.Redactor;
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

    /**
     * Optional check for documents that shall be appended to the effective document.
     * Returns an error message to refuse the document, null to accept it.
     */
    public java.util.function.Function<PDDocument, String> appendCheck;

    /**
     * Checks a loaded document before it is appended, see {@link #appendCheck}.
     *
     * @return An error message if the document is refused, null if it can be used.
     * The first document is always accepted, it is not appended.
     */
    public String checkAppend(PDDocument candidate) {
        if (candidate == null || document == null || appendCheck == null)
            return null;
        return appendCheck.apply(candidate);
    }

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
        if (pageNb < 1 || pageNb > pageCount) {
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

    public void rotatePages(int degree) {
        ensuredDocument();

        for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); ++pageIndex) {
            Page p = pages.get(pageIndex);
            p.image = null;
            p.scale = 0;
            PDPage pd = document.getPage(pageIndex);
            pd.setRotation(pd.getRotation() + degree);
        }
        refirePages();
    }

    private void renderPageToImage(PDFRenderer pdfRenderer, final int pageIndex) throws IOException {

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
    }

    public void renderPageToImage(int pageNb) {
        ensuredDocument();
        if (document.getNumberOfPages() >= pageNb) {

            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int pageIndex = pageNb - 1;

            try {
                renderPageToImage(pdfRenderer, pageIndex);
                refirePages();
            } catch (Exception e) {
                Log.exception(e);
            }
        }
    }

    public void renderAllPagesToImage() {
        ensuredDocument();
        try {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); ++pageIndex) {
                renderPageToImage(pdfRenderer, pageIndex);
            }
        } catch (Exception e) {
            Log.exception(e);
        }
        refirePages();

    }

    /**
     * Permanently removes the regions, see {@link Redactor}. The affected pages are replaced by images.
     *
     * @param regions Regions in unrotated page coordinates.
     */
    public void redact(List<Redactor.Region> regions) throws IOException {
        ensuredDocument();
        Preferences prefs = Preferences.getInstance();
        Redactor.redact(document, regions, prefs.getDpi(), prefs.getRedactionColor());
        synchronized (this) {
            for (Redactor.Region r : regions) {
                Page p = pages.get(r.pageNb - 1);
                p.image = null;
                p.scale = 0;
            }
        }
        refirePages();
    }

    /**
     * Attaches an invoice XML (ZUGFeRD/Factur-X) to the effective document, see {@link EInvoice#attachXml}.
     * An existing invoice is replaced. The visible pages are not changed.
     *
     * @throws EInvoice.NotConformException If the result would be no valid invoice. The document is not changed then.
     */
    public void attachInvoice(byte[] xml) throws IOException {
        ensuredDocument();
        PDDocument newDocument = Loader.loadPDF(EInvoice.attachXmlStrict(getBytes(), xml));
        PDDocument oldDocument;
        synchronized (this) {
            oldDocument = document;
            document = newDocument;
        }
        IOUtils.closeQuietly(oldDocument);
        fireDocumentLoaded();
    }

    /**
     * Removes the invoice XML from the effective document.
     *
     * @return True if there was an invoice.
     */
    public boolean removeInvoice() throws IOException {
        ensuredDocument();
        boolean removed = EInvoice.removeXml(document);
        if (removed)
            // Let the UI update what depends on the invoice (e.g. the standards label).
            fireDocumentLoaded();
        return removed;
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

    public synchronized void loaderFinished(PDDocument document, MergeOptions mo) {
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
                        oldPageCount = this.getDocument().getNumberOfPages();
                        try {
                            if (EInvoice.hasInvoice(this.getDocument()) && EInvoice.hasInvoice(document)) {
                                // Both would use the same attachment name, one invoice must go.
                                Log.error("Both documents contain an invoice. Only the invoice of the first document is kept.");
                                EInvoice.removeXml(document);
                            }
                            PDFMergerUtility pdfMergerUtility = new PDFMergerUtility();
                            pdfMergerUtility.appendDocument(this.getDocument(), document);
                        } catch (Exception e) {
                            Log.error("Failed to merge. %s", e.getMessage());
                            this.error = "Failed to merge: " + e.getMessage();
                            oldPageCount = -1;
                        } finally {
                            IOUtils.closeQuietly(document);
                        }
                        // Count what was really appended, the merge may have failed.
                        pageCount = this.getDocument().getNumberOfPages();
                    }
                    // zipper merge if requested (start is 1-based "insert before page", so the last page is allowed)
                    if (oldPageCount > 0 && mo.startPageNb > 0 && mo.startPageNb <= oldPageCount) {
                        zipperMerge(oldPageCount, pageCount, mo);
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
     * Moves the appended pages between the original pages.<br>
     * The appended pages start at index oldPageCount. Starting before page {@link MergeOptions#startPageNb},
     * {@link MergeOptions#segmentLength} new pages are inserted, then {@link MergeOptions#gapLength}
     * original pages are skipped, and so on. New pages left over when the original pages run out stay at the end.
     * A segment length &lt;= 0 inserts all new pages in one block.
     *
     * @param oldPageCount Number of pages before the merge.
     * @param pageCount    Number of pages after the merge.
     */
    private void zipperMerge(int oldPageCount, int pageCount, MergeOptions mo) {
        PDPageTree tree = this.getDocument().getDocumentCatalog().getPages();
        int targetPageIndex = mo.startPageNb - 1;
        int segmentCount = 0;

        // Moving a page in front of the remaining new pages doesn't shift them,
        // so the next new page to move is always at the next index.
        for (int pageToMoveIndex = oldPageCount;
             pageToMoveIndex < pageCount && targetPageIndex < pageToMoveIndex;
             ++pageToMoveIndex) {

            PDPage pdPage = tree.get(pageToMoveIndex);
            tree.remove(pdPage);
            if (targetPageIndex > 0)
                tree.insertAfter(pdPage, tree.get(targetPageIndex - 1));
            else
                tree.insertBefore(pdPage, tree.get(0));
            ++targetPageIndex;

            if (mo.segmentLength > 0 && ++segmentCount >= mo.segmentLength) {
                segmentCount = 0;
                if (mo.gapLength > 0)
                    targetPageIndex += mo.gapLength;
            }
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
        byte[] bytes = getBytes();
        return bytes == null ? null : Loader.loadPDF(bytes);
    }

    /**
     * Writes the effective document (without any security) to memory.
     *
     * @return The PDF or null if no document is loaded.
     * @throws IOException If some write operation failed.
     */
    public byte[] getBytes() throws IOException {
        if (document != null) {
            document.setAllSecurityToBeRemoved(true);
            ByteArrayOutputStream os = new ByteArrayOutputStream(5 * 1024 * 1024);
            document.save(os, CompressParameters.NO_COMPRESSION);
            return os.toByteArray();
        } else
            return null;
    }

}
