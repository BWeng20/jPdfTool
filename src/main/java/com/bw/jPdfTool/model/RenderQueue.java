package com.bw.jPdfTool.model;

import com.bw.jPdfTool.Log;
import com.bw.jPdfTool.Preferences;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.RenderingHints;
import java.beans.PropertyChangeListener;
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

                    try {
                        proxy = documentQueue.take();
                    } catch (InterruptedException ignored) {
                        continue;
                    }
                    if (proxy.isClosed())
                        continue;
                    PDDocument document = proxy.getDocument();
                    if (document != null) {
                        Log.debug("Render document started");
                        PDFRenderer renderer = new PDFRenderer(document);

                        RenderingHints renderingHints = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        renderingHints.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        renderingHints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                        renderer.setRenderingHints(renderingHints);

                        // Check in loop for cases where the document was manipulated meanwhile.
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
                                            if (page.image == null) {
                                                page.error = "Page not rendered (unknown error)";

                                            }
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
                } catch (Exception e) {
                    Log.error("Render document failed: %s", e.getMessage());
                    if (Log.DEBUG) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private void pageRendered(Page page) {
            page.document.firePageRendered(page);
        }
    }
}
