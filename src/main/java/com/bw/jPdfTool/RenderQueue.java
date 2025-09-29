package com.bw.jPdfTool;

import com.bw.jPdfTool.model.DocumentProxy;
import com.bw.jPdfTool.model.Page;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Worker-Queue with background thread to render pages.
 */
public class RenderQueue {

    private final LinkedBlockingDeque<DocumentProxy> documentQueue = new LinkedBlockingDeque<>();
    private boolean running = false;
    private int dpi = 300;

    /**
     * Starts the queue worker.
     * The thread will be marked as daemon.
     */
    public void start() {
        if (!running) {
            var prefs = Preferences.getInstance();
            dpi = prefs.getInt(Preferences.USER_PREF_DPI, dpi);
            prefs.addPropertyChangeListener(propertyChangeListener, Preferences.USER_PREF_DPI);
            running = true;
            PdfRenderWorker worker = new PdfRenderWorker();
            worker.setDaemon(true);
            worker.setName("PdfRenderer");
            worker.start();
        }
    }

    private final PropertyChangeListener propertyChangeListener =
            evt -> dpi = Preferences.getInstance().getInt(Preferences.USER_PREF_DPI, dpi);

    public void stop() {
        Preferences.getInstance().removePropertyChangeListener(propertyChangeListener, Preferences.USER_PREF_DPI);
        if (running) {
            running = false;
        }
    }

    public void addDocument(DocumentProxy document) {
        if (document != null)
            documentQueue.offer(document);
    }


    protected class PdfRenderWorker extends Thread {

        @Override
        public void run() {
            while (running) {
                DocumentProxy proxy;

                try {
                    proxy = documentQueue.take();
                } catch (InterruptedException ignored) {
                    continue;
                }
                if (proxy.isClosed())
                    continue;
                PDDocument document = proxy.getLoadedDocument();
                if (document != null) {
                    Log.debug("Render document od #%d started", document.getDocumentId());
                    PDFRenderer renderer = new PDFRenderer(document);

                    RenderingHints renderingHints = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    renderingHints.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    renderingHints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    renderer.setRenderingHints(renderingHints);

                    // CHeck in loop for cases where the document was manipulated meanwhile.
                    while (proxy.needsRendering()) {
                        int pdPageCount = document.getNumberOfPages();
                        for (int i = 0; i < proxy.getPageCount(); i++) {
                            if (proxy.isClosed())
                                break;
                            Page page = proxy.getPage(i + 1);
                            if (page != null && page.image == null && page.error == null) {
                                if (pdPageCount > i) {
                                    Log.debug("Render page #%d", i);
                                    try {
                                        page.image = renderer.renderImageWithDPI(i, dpi);
                                        page.dpi = dpi;
                                        page.error = null;
                                    } catch (Exception e) {
                                        page.error = e.getMessage();
                                    }
                                } else {
                                    page.error = "Page Index of range (internal error)";
                                }
                                pageRendered(page);
                            }
                        }
                    }
                    Log.debug("Render document finished");
                }
            }
        }

        private boolean notificationTriggered = false;
        private final List<Page> renderedPages = new LinkedList<>();

        private void pageRendered(Page page) {
            synchronized (renderedPages) {
                renderedPages.add(page);
                if (!notificationTriggered) {
                    SwingUtilities.invokeLater(() -> {
                        List<Page> pages;
                        synchronized (renderedPages) {
                            notificationTriggered = false;
                            pages = new ArrayList<>(renderedPages);
                            renderedPages.clear();
                        }
                        Log.debug("%d pages finished", pages.size());
                        for (Page p : pages) {
                            page.document.firePageRendered(p);
                        }
                    });
                }
            }
        }
    }
}
