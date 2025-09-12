package com.bw.jPdfTool;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public class PageWidget extends JComponent {

    JButton deleteButton;
    JButton rotateClockwiseButton;


    private int buttonY = 5;

    JButton createButton(String icon, ActionListener actionListener) {
        JButton b = new JButton();
        Icon ic = UI.getIcon(icon);

        b.addActionListener(actionListener);
        b.setLocation(5, buttonY);
        b.setIcon(ic);
        b.setSize(ic.getIconWidth() + 8, ic.getIconHeight() + 8);
        b.setVisible(false);

        buttonY += b.getHeight() + 5;

        return b;
    }

    /**
     * Page Number used only until page is rendered.
     */
    private int pageNr;
    private Page page;
    private boolean hovering = false;

    public PageWidget(int pageNr) {
        this.pageNr = pageNr;
        setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));

        deleteButton = createButton("delete", e -> {
            if (page != null) {
                page.document.deletePage(page.pageNb);
            }
        });
        add(deleteButton);

        rotateClockwiseButton = createButton("rotateClockwise", e -> {
            if (page != null) {
                page.rotatePage(90);
            }
        });
        add(rotateClockwiseButton);
        updateUI();


        Timer hideTimer = new Timer(200, e -> {
            hovering = false;
            setButtonsVisible(false);
        });
        hideTimer.setRepeats(false);

        MouseListener mouseListener = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hideTimer.stop();
                setButtonsVisible(page != null);
                hovering = true;
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hideTimer.restart();
            }
        };

        addMouseListener(mouseListener);
        deleteButton.addMouseListener(mouseListener);
        rotateClockwiseButton.addMouseListener(mouseListener);

    }

    private void setButtonsVisible(boolean v) {
        deleteButton.setVisible(v);
        rotateClockwiseButton.setVisible(v);
    }

    public void setPage(Page page) {
        if (page != this.page) {
            if (page != null)
                pageNr = page.pageNb;
            this.page = page;
            if (hovering)
                setButtonsVisible(true);
            repaint();
        }
    }

    @Override
    public void updateUI() {
        Font f = UIManager.getFont("Panel.font");
        setFont(f);
        FontMetrics fm = getFontMetrics(f);
        Dimension d = new Dimension(fm.charWidth('W') * 10, fm.getHeight() * 6);
        setPreferredSize(d);
        setSize(d);
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

            double scale = Math.min(w / (double) imgWidth, h / (double) imgHeight);

            int drawWidth = (int) (imgWidth * scale);
            int drawHeight = (int) (imgHeight * scale);

            int x = i.left + (w - drawWidth) / 2;
            int y = i.top + (h - drawHeight) / 2;

            if (g2d.hitClip(x, y, drawWidth, drawHeight))
                g2d.drawImage(page.image, x, y, drawWidth, drawHeight, null);
        }
    }


}
