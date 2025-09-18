package com.bw.jPdfTool;

import com.bw.jPdfTool.model.Page;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.form.PDTransparencyGroup;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDInlineImage;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.pdmodel.graphics.state.PDSoftMask;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stream Engine to extract images.
 */
public class ImageExtractor extends PDFGraphicsStreamEngine {

    public final List<Page.ImageResource> images = new ArrayList<>();
    public boolean noColorConvert = false;

    private final Set<COSStream> processed = new HashSet<>();
    private int imageCounter = 1;

    public ImageExtractor(PDPage page) {
        super(page);
    }

    protected void addImage(String name, BufferedImage image) {
        Page.ImageResource i = new Page.ImageResource();
        i.image = image;
        i.name = name;


        var mat = getGraphicsState().getCurrentTransformationMatrix();
        Point2D p = new Point2D.Double(0, 0);

        AffineTransform imageTransform = new AffineTransform(mat.createAffineTransform());
        int rotationAngle = getPage().getRotation();
        if (rotationAngle != 0) {
            imageTransform.preConcatenate(AffineTransform.getRotateInstance(Math.toRadians(rotationAngle)));
        }

        imageTransform.transform(p, p);
        i.x = p.getX();
        i.y = p.getY();

        images.add(i);
    }

    public void run() throws IOException {
        PDPage page = getPage();
        processPage(page);
        PDResources res = page.getResources();
        if (res == null) {
            return;
        }
        // Code below is copied from examples, I don't know what this does...
        // Need to check "processSoftMask"
        for (COSName name : res.getExtGStateNames()) {
            PDExtendedGraphicsState extGState = res.getExtGState(name);
            if (extGState == null) {
                continue;
            }
            PDSoftMask softMask = extGState.getSoftMask();
            if (softMask != null) {
                PDTransparencyGroup group = softMask.getGroup();
                if (group != null) {
                    res.getExtGState(name).copyIntoGraphicsState(getGraphicsState());
                    processSoftMask(group);
                }
            }
        }
    }

    @Override
    public void drawImage(PDImage pdImage) throws IOException {
        if (pdImage instanceof PDImageXObject) {
            PDImageXObject xobject = (PDImageXObject) pdImage;
            COSStream cos = xobject.getCOSObject();
            if (processed.contains(cos)) {
                return;
            }
            processed.add(cos);
        } else if (pdImage instanceof PDInlineImage) {
            // Skipping inline image
            return;
        }

        // TODO: Any unique naming available?
        String name = "image-" + (imageCounter++);

        BufferedImage image = null;
        if (noColorConvert) {
            image = pdImage.getRawImage();
        }
        if (image == null)
            image = pdImage.getImage();
        if (image != null) {
            addImage(name, image);
        }
    }

    @Override
    public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
    }

    @Override
    public void clip(int windingRule) {
    }

    @Override
    public void moveTo(float x, float y) {
    }

    @Override
    public void lineTo(float x, float y) {
    }

    @Override
    public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
    }

    @Override
    public Point2D getCurrentPoint() {
        return new Point2D.Float(0, 0);
    }

    @Override
    public void closePath() {
    }

    @Override
    public void endPath() {
    }

    @Override
    protected void showGlyph(Matrix textRenderingMatrix,
                             PDFont font,
                             int code,
                             Vector displacement) {
    }

    @Override
    public void strokePath() throws IOException {
    }

    @Override
    public void fillPath(int windingRule) throws IOException {
    }

    @Override
    public void fillAndStrokePath(int windingRule) throws IOException {
    }

    @Override
    public void shadingFill(COSName shadingName) throws IOException {
    }

}