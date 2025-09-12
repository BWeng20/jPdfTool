package com.bw.jPdfTool;

import org.apache.pdfbox.pdmodel.PDDocument;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import java.awt.*;

public class PageContainer extends JComponent {

    public PageContainer() {
        setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
    }

    private DocumentProxy document;

    private DocumentProxy.PageConsumer pageConsumer = page -> {
        PageWidget pw = getPage(page.pageNb);
        if (pw != null) {
            pw.setPage(page);
        }
    };

    private DocumentProxy.DocumentConsumer docConsumer = new DocumentProxy.DocumentConsumer() {
        @Override
        public void documentLoaded(PDDocument document) {
            removeAll();
            orgPageCount = -1;
            ensurePage(document.getNumberOfPages());
        }

        @Override
        public void failed(String error) {
        }
    };

    public PageWidget getPage(int pageNb) {
        ensurePage(pageNb);
        return (PageWidget) getComponent(pageNb - 1);
    }

    private void ensurePage(int pageNb) {
        int cc = getComponentCount();
        while (cc < pageNb) {
            addPage(new PageWidget(++cc));
        }
    }

    public void clear() {
        removeAll();
        if (document != null) {
            document.removePageConsumer(pageConsumer);
            document.removeDocumentConsumer(docConsumer);
            document = null;
        }
        orgPageCount = -1;
        revalidate();

    }

    public void setDocument(DocumentProxy file) {
        clear();
        document = file;
        document.addDocumentConsumer(docConsumer);
        document.addPageConsumer(pageConsumer);
    }

    int orgPageCount = -1;
    Dimension orgVS;
    Dimension orgSize;

    @Override
    public Dimension getPreferredSize() {

        int cc = getComponentCount();
        Dimension vs = getScrollPane().getViewport().getSize();
        if (orgSize == null ||
                cc != orgPageCount ||
                !vs.equals(orgVS)) {

            orgPageCount = cc;
            orgVS = vs;
            int x = 5;
            int y = 5;
            int w = 5;
            int h = 5;
            for (int i = 0; i < cc; ++i) {

                Component c = getComponent(i);

                if ((x + c.getWidth()) >= vs.width && x > 10) {
                    y = h;
                    x = 5;
                }
                c.setLocation(x, y);
                x += c.getWidth() + 5;
                if (x > w)
                    w = x;

                int ym = y + c.getHeight() + 5;
                if (ym > h)
                    h = ym;

            }
            orgSize = new Dimension(w, h);
        }
        return orgSize;
    }

    public void addPage(PageWidget page) {
        add(page);
        revalidate();
    }

    protected JScrollPane getScrollPane() {
        return ((JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this));
    }


}
