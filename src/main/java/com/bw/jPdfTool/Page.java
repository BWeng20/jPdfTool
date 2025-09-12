package com.bw.jPdfTool;

import java.awt.image.BufferedImage;

public class Page {

    public final DocumentProxy document;
    public int pageNb;
    public int pageCount;

    public double scale = 0;
    public BufferedImage image;

    public Page(DocumentProxy document, int pageNb, int pageCount) {
        this.pageNb = pageNb;
        this.document = document;
        this.pageCount = pageCount;
    }

    public void rotatePage(int degree) {
        document.rotatePage(pageNb, degree);
    }
}
