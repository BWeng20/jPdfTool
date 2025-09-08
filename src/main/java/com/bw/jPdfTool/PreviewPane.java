package com.bw.jPdfTool;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class PreviewPane extends JPanel {

    public PreviewPane() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                super.componentResized(e);
                updateScales();
            }
        });
    }

    protected class PdfLoaderWorker extends SwingWorker<Void, Page> {

        DocumentProxy documentProxy;
        boolean stop = false;


        public PdfLoaderWorker(DocumentProxy document) {
            this.documentProxy = document;
        }

        @Override
        protected Void doInBackground() {
            try {
                PDDocument document = this.documentProxy.getDocument();
                if (document != null) {
                    PDFRenderer renderer = new PDFRenderer(document);

                    RenderingHints renderingHints = new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    renderer.setRenderingHints(renderingHints);

                    for (int i = 0; i < 2 && i < this.documentProxy.pageCount; i++) {
                        if (stop || documentProxy.stopProcessing)
                            break;
                        BufferedImage image = renderer.renderImageWithDPI(i, 300);
                        Page page = new Page();
                        page.nr = i + 1;
                        page.image = image;
                        page.scale = 0;
                        page.pageCount = this.documentProxy.pageCount;
                        publish(page);
                    }
                }
                synchronized (PreviewPane.this) {
                    if (PreviewPane.this.worker == PdfLoaderWorker.this)
                        PreviewPane.this.worker = null;
                }
                error = null;
            } catch (InvalidPasswordException ep) {
                error = "File is encrypted and owner password\ndoesn't match";
                SwingUtilities.invokeLater(() -> {
                    refresh();
                });

            } catch (Exception e) {
                error = e.getMessage();
                SwingUtilities.invokeLater(() -> {
                    refresh();
                });
            }
            return null;

        }

        @Override
        protected void process(java.util.List<Page> pages) {
            synchronized (PreviewPane.this.pages) {
                PreviewPane.this.pages.addAll(pages);
                updateScales();
            }
            SwingUtilities.invokeLater(PreviewPane.this::refresh);
        }
    }

    protected void refresh() {
        var sp = PreviewPane.this.getScrollPane();
        sp.revalidate();
        sp.repaint();

    }

    protected static class Page {
        BufferedImage image;
        int nr;
        int pageCount;
        double scale;
    }

    private PdfLoaderWorker worker;
    private final List<Page> pages = new ArrayList<>();
    private int space = 5;
    private String error;

    public void load(DocumentProxy file) {

        error = null;
        synchronized (pages) {
            pages.clear();
        }
        synchronized (this) {
            if (worker != null)
                worker.stop = true;
            worker = new PdfLoaderWorker(file);
            worker.execute();
        }
    }

    protected JScrollPane getScrollPane() {
        return ((JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this));
    }

    protected int getTargetWidth() {
        var vp = getScrollPane().getViewport();
        return vp.getWidth();
    }

    protected void updateScales() {

        List<Page> tmp;
        synchronized (PreviewPane.this.pages) {
            tmp = new ArrayList<>(PreviewPane.this.pages);
        }
        int drawWidth = getTargetWidth() - (2 * space);
        for (Page i : tmp) {
            i.scale = ((double) drawWidth) / i.image.getWidth();
        }
    }

    public synchronized void setErrorText(String message) {
        error = message;
        if (worker != null) {
            worker.stop = true;
        }
        synchronized (pages) {
            pages.clear();
        }
        refresh();
    }

    @Override
    public Dimension getPreferredSize() {

        List<Page> tmp;
        synchronized (PreviewPane.this.pages) {
            tmp = new ArrayList<>(PreviewPane.this.pages);
        }
        int drawWidth = getTargetWidth();
        int drawHeight = space;

        for (Page i : tmp) {
            drawHeight += (int) ((i.image.getHeight() * i.scale) + space);
        }

        return new Dimension(drawWidth, drawHeight);

    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int panelWidth = getTargetWidth();
        FontMetrics fm = g2d.getFontMetrics();

        if (error != null) {
            int y = space + fm.getAscent();
            for (String line : error.split("\n")) {
                g2d.drawString(line, space, y);
                y += fm.getHeight();
            }
            return;
        }

        List<Page> tmp;
        synchronized (PreviewPane.this.pages) {
            tmp = new ArrayList<>(PreviewPane.this.pages);
        }

        int pageCount = -1;

        int y = space;
        for (Page i : tmp) {
            if (i.scale > 0) {
                pageCount = i.pageCount;

                int imgWidth = i.image.getWidth();
                int imgHeight = i.image.getHeight();

                int drawWidth = (int) (imgWidth * i.scale);
                int drawHeight = (int) (imgHeight * i.scale);

                int x = (panelWidth - drawWidth) / 2;
                g2d.drawImage(i.image, x, y, drawWidth, drawHeight, null);
                y += drawHeight + space;
            }
        }

        if (pageCount > tmp.size()) {
            y = space + fm.getAscent();
            g2d.setPaint(Color.BLACK);
            g2d.drawString("Showing first " + tmp.size() + " of " + pageCount + " Pages", space, y);
        }
    }
}
