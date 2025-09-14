package com.bw.jPdfTool;

import com.bw.jPdfTool.model.Page;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class PageWidget extends JComponent {

    private final Border selectedBorder;
    private final Border notSelectedBorder;
    /**
     * Page Number used only until page is rendered.
     */
    private int pageNr;
    private Page page;
    private boolean selected = false;

    public PageWidget(int pageNr) {
        this.pageNr = pageNr;
        Color c = UIManager.getColor("Button.foreground");
        selectedBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(c, 2), BorderFactory.createEmptyBorder(2, 2, 2, 2));
        notSelectedBorder = BorderFactory.createEmptyBorder(4, 4, 4, 4);
        setBorder(notSelectedBorder);
        updateUI();
    }

    public Page getPage() {
        return page;
    }

    public void setPage(Page page) {
        if (page != this.page) {
            if (page != null)
                pageNr = page.pageNb;
            this.page = page;
        }
    }

    public int getPageNumber() {
        return pageNr;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        if (this.selected != selected) {
            this.selected = selected;
            setBorder(selected ? selectedBorder : notSelectedBorder);
        }

    }

    @Override
    public void updateUI() {
        Font f = UIManager.getFont("Panel.font");
        setFont(f);
        FontMetrics fm = getFontMetrics(f);
        Dimension d = new Dimension(fm.charWidth('W') * 10, fm.getHeight() * 6);
        setMinimumSize(d);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setPaint(getBackground());

        Insets i = getBorder().getBorderInsets(this);

        int w = getWidth() - i.left - i.right;
        int h = getHeight() - i.top - i.bottom;
        g.fillRect(0, 0, w, h);


        if (page == null) {
            FontMetrics fm = g2d.getFontMetrics();
            String txt = "# " + pageNr;
            var bounds = fm.getStringBounds(txt, g2d);
            g2d.setPaint(getForeground());
            g2d.drawString(txt, (int) (0.5 + (w - bounds.getWidth()) / 2), (h - fm.getAscent()) / 2);

        } else {
            int imgWidth = page.image.getWidth();
            int imgHeight = page.image.getHeight();

            int drawWidth = (int) (imgWidth * page.scale);
            int drawHeight = (int) (imgHeight * page.scale);

            int x = i.left + (w - drawWidth) / 2;
            int y = i.top + (h - drawHeight) / 2;

            if (g2d.hitClip(x, y, drawWidth, drawHeight))
                g2d.drawImage(page.image, x, y, drawWidth, drawHeight, null);
        }
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Page ").append(pageNr).append(" selected:").append(this.selected);
        if (page != null && page.image != null)
            stringBuilder.append(" image ").append(page.image.getWidth()).append(" x ").append(page.image.getHeight());
        return stringBuilder.toString();
    }

}
