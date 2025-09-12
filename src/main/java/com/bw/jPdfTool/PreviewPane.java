package com.bw.jPdfTool;

import org.apache.pdfbox.pdmodel.PDDocument;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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

    protected void refresh() {
        var sp = PreviewPane.this.getScrollPane();
        sp.revalidate();
        sp.repaint();

    }

    private DocumentProxy document;
    private final List<Page> pages = new ArrayList<>();
    private int space = 5;

    private final DocumentProxy.PageConsumer pageConsumer = page -> {
        if (page.pageNb > pages.size())
            pages.add(page);
        else
            pages.add(page.pageNb - 1, page);
        updateScales();
        refresh();
    };

    private final DocumentProxy.DocumentConsumer docConsumer = new DocumentProxy.DocumentConsumer() {
        @Override
        public void documentLoaded(PDDocument document) {
            removeAll();
            synchronized (pages) {
                pages.clear();
            }
            refresh();
        }

        @Override
        public void failed(String error) {

        }
    };

    public void setDocument(DocumentProxy file) {
        if (document != null) {
            document.removePageConsumer(pageConsumer);
            document.removeDocumentConsumer(docConsumer);
        }
        document = file;
        removeAll();
        synchronized (pages) {
            pages.clear();
        }
        // To detect if pages needs to be reloaded
        document.addDocumentConsumer(docConsumer);
        document.addPageConsumer(pageConsumer);
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

    private String error;

    public synchronized void setErrorText(String message) {
        error = message;
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

                if (g2d.hitClip(x, y, drawWidth, drawHeight))
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
