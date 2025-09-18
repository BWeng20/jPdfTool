package com.bw.jPdfTool;

import com.bw.jPdfTool.model.Page;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Panel to show the images inside a page.
 */
public class PageImageViewer extends JPanel {

    java.awt.List resourceList;
    Map<String, Page.ImageResource> imageMap = new LinkedHashMap<>();
    ImageWidget image = new ImageWidget();
    boolean showCombined = false;
    Page page;
    BufferedImage combinedImage;
    JCheckBox combined;

    public PageImageViewer() {
        super(new BorderLayout());
        resourceList = new java.awt.List();

        combined = new JCheckBox("Combined");

        JPanel left = new JPanel(new BorderLayout());
        left.add(resourceList, BorderLayout.CENTER);
        left.add(combined, BorderLayout.NORTH);

        combined.addItemListener(e -> {
            setShowCombined(combined.isSelected());
        });

        JSplitPane splitter = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitter.setLeftComponent(left);
        splitter.setRightComponent(image);


        image.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                BufferedImage i = image.getImage();
                if (i != null) {
                    double scalex = ((double) image.getWidth() / i.getWidth());
                    double scaley = ((double) image.getHeight() / i.getHeight());
                    image.setScale(Math.min(scalex, scaley));
                }
            }
        });
        image.setPreferredSize(new Dimension(400, 500));

        add(splitter, BorderLayout.CENTER);

        resourceList.addItemListener(e -> {
            if (!combined.isSelected()) {
                showImageByName(resourceList.getSelectedItem());
            }
        });
    }

    protected void setImages(List<Page.ImageResource> images) {
        resourceList.removeAll();
        imageMap.clear();
        for (var image : images) {
            imageMap.put(image.name, image);
        }
        for (var name : imageMap.keySet()) {
            resourceList.add(name);
        }
        showImageByName(null);
        combinedImage = null;
        setShowCombined(false);
    }

    /**
     * Shows an image by its resource name.
     *
     * @param imageName The name. Can be null.
     */
    protected void showImageByName(String imageName) {
        if (imageName != null) {
            image.setAlternativeText(imageName);
            Page.ImageResource i = imageMap.get(imageName);
            if (i != null) {
                if (i.image != null) {
                    showImage(i.image);
                } else if (i.error != null) {
                    image.setAlternativeText(i.error);
                    image.setImage(null);
                } else {
                    image.setAlternativeText("Unsupported format");
                    image.setImage(null);
                }
                return;
            }
        }
        showImage(null);
    }

    protected void showImage(BufferedImage bimage) {
        if (bimage == null) {
            image.setAlternativeText(imageMap.isEmpty() ? "No images" : "Select an image");
        } else {
            double scalex = ((double) image.getWidth() / bimage.getWidth());
            double scaley = ((double) image.getHeight() / bimage.getHeight());
            image.setScale(Math.min(scalex, scaley));
        }
        image.setImage(bimage);

    }

    protected void setShowCombined(boolean showCombined) {
        if (showCombined != this.showCombined) {
            this.showCombined = showCombined;
            this.combined.setSelected(showCombined);
            resourceList.setEnabled(!showCombined);
            if (showCombined) {
                if (combinedImage == null) {
                    combinedImage = getCombined();
                }
                if (combinedImage == null)
                    image.setAlternativeText("Operation Failed");
                showImage(combinedImage);
            } else {
                showImageByName(resourceList.getSelectedItem());
            }
        }
    }

    public BufferedImage getCombined() {

        BufferedImage combined;
        Graphics2D combinedG;

        double minX = Integer.MAX_VALUE;
        double minY = Integer.MAX_VALUE;
        double maxX = Integer.MIN_VALUE;
        double maxY = Integer.MIN_VALUE;

        for (var v : imageMap.values()) {
            if (v.x < minX)
                minX = v.x;
            if (v.y < minY)
                minY = v.y;
            double v2 = v.x + v.image.getWidth();
            if (v2 > maxX)
                maxX = v2;
            v2 = v.y + v.image.getHeight();
            if (v2 > maxY)
                maxY = v2;
        }

        double w = maxX - minX;
        double h = maxY - minY;

        combined = new BufferedImage((int) w, (int) h, BufferedImage.TYPE_INT_ARGB);
        combinedG = combined.createGraphics();

        for (var v : imageMap.values()) {
            combinedG.drawImage(v.image, (int) (v.x - minX), (int) (v.y - minY), null);
        }
        combinedG.dispose();

        return combined;
    }

    /**
     * Sets the page to show images from.
     *
     * @param page The page. Must not be null.
     */
    public void setPage(Page page) {
        this.page = page;

        setImages(page.getImages());
    }
}
