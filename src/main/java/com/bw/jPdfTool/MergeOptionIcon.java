package com.bw.jPdfTool;

import com.bw.jPdfTool.model.MergeOptions;

import javax.swing.*;
import java.awt.*;

public final class MergeOptionIcon implements Icon {

    private final int w;
    private final int h;
    private MergeOptions options;

    private final int pageWidth;
    private final int pageHeight;
    private final int gap = 2;

    public MergeOptionIcon(Component c) {
        FontMetrics fm = c.getFontMetrics(c.getFont());
        pageWidth = 4 + fm.stringWidth("…00");
        pageHeight = fm.getHeight() + 4;
        w = (pageWidth + gap) * 7 - gap;
        h = pageHeight * 2 + gap;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {

        Graphics2D g2 = (Graphics2D) g;
        g2.setPaint(c.getBackground());
        g2.fillRect(x, y, w, h);
        g2.setFont(c.getFont());

        if (options != null) {

            int y2 = y + pageHeight + gap;

            int pw = pageWidth + gap;
            Color color = c.getForeground();
            Color color2 = UIManager.getColor("List.selectionForeground");
            Color color2Bk = UIManager.getColor("List.selectionBackground");

            int pgNb = 1;
            // Draw prefix (original pages before first inserted page, possibly empty)
            if (options.startPageNb > 1) {
                drawPage(g2, x, y, color, null, "1");
                x += pw;
                if (options.startPageNb > 2) {
                    drawPage(g2, x, y, color, null, String.format(options.startPageNb > 3 ? "…%d" : "%d", options.startPageNb - 1));
                    pgNb = options.startPageNb;
                    x += pw;
                } else
                    ++pgNb;
            }
            if (options.segmentLength > 0) {
                // Draw inserted segment (1 to n)
                drawPage(g2, x, y2, color2, color2Bk, String.format("%d", pgNb));
                x += pw;
                if (options.segmentLength > 1) {
                    pgNb += options.segmentLength - 1;
                    drawPage(g2, x, y2, color2, color2Bk, String.format(options.segmentLength > 2 ? "…%d" : "%d", pgNb++));
                    x += pw;
                } else
                    pgNb++;
            }

            if (options.gapLength > 0) {
                // Draw gab before next segment (1 to n)
                drawPage(g2, x, y, color, null, String.format("%d", pgNb++));
                x += pw;
                if (options.gapLength > 1) {
                    pgNb += options.gapLength - 1;
                    drawPage(g2, x, y, color, null, String.format(options.gapLength > 2 ? "…%d" : "%d", pgNb++));
                    x += pw;
                }
            }

            // Draw start of next inserted segment
            drawPage(g2, x, y2, color2, color2Bk, String.format("%d…", pgNb));

        }
    }

    private void drawPage(Graphics2D g2, int x, int y, Color fg, Color bk, String txt) {
        FontMetrics fm = g2.getFontMetrics();
        int textY = y + fm.getAscent() + (pageHeight - fm.getHeight()) / 2;
        if (bk != null) {
            g2.setPaint(bk);
            g2.fillRect(x, y, pageWidth, pageHeight);
            g2.setPaint(fg);
        } else {
            g2.setPaint(fg);
            g2.drawRect(x, y, pageWidth, pageHeight);
        }
        g2.drawString(txt, x + (pageWidth - fm.stringWidth(txt)) / 2, textY);
    }

    public void setMergeOptions(MergeOptions options) {
        this.options = options;
    }

    @Override
    public int getIconWidth() {
        return w;
    }

    @Override
    public int getIconHeight() {
        return h;
    }
}
