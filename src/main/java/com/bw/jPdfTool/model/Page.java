package com.bw.jPdfTool.model;

import com.bw.jPdfTool.ImageExtractor;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    public int getRotation() {
        return document.getPageRotation(pageNb);
    }

    public void movePage(int i) {
        document.movePage(pageNb, i);
    }

    public static class ImageResource {
        public String name;
        public BufferedImage image;
        public String error;

        public double x;
        public double y;
    }

    public List<ImageResource> getImages() {
        List<ImageResource> images = new ArrayList<>();

        ImageExtractor ie = new ImageExtractor(document.getPDPage(pageNb));
        try {
            ie.run();
            images.addAll(ie.images);

            ImageResource r = new ImageResource();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return images;
    }
}
