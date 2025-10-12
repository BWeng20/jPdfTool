package com.bw.jPdfTool.toast;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;

public class Toaster {

    private JLayeredPane layeredPane;
    private final List<Toast> bread = new ArrayList<>();

    public static int HORIZONTAL_GAP = 5;
    public static int VERTICAL_GAP = 5;

    public static int closeDelayMS = 5000;


    public Toaster() {
    }

    public void attachFrame(JFrame frame) {
        layeredPane = frame.getLayeredPane();
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                repositionPanels();
            }
        });

    }

    public void toast(String message, Object... arguments) {
        if (layeredPane != null) {
            final Toast[] toasts = new Toast[1];

            if (arguments.length > 0)
                message = String.format(message, arguments);

            toasts[0] = new Toast(message, ToastType.INFO, () -> removeNotification(toasts[0]), 300);
            layeredPane.add(toasts[0], JLayeredPane.POPUP_LAYER);
            bread.add(0, toasts[0]);
            SwingUtilities.invokeLater(() -> {
                animateFadeIn(toasts[0]);
                layeredPane.repaint();
            });
        }
    }


    private void animateFadeIn(Toast panel) {
        panel.setLocation(layeredPane.getWidth(), layeredPane.getHeight());
        repositionPanels();
    }

    private void removeNotification(Toast toast) {
        layeredPane.remove(toast);
        bread.remove(toast);
        toast.cleanUp();
        repositionPanels();
        layeredPane.repaint();
    }


    private void animateMove(Toast toast, int x, int y) {
        Point c = toast.getLocation();
        if (c.x == x && c.y == y)
            return;

        toast.animateTo(500, x, y);
    }

    private void repositionPanels() {
        int w = layeredPane.getWidth();
        int y = layeredPane.getHeight();
        for (Toast panel : bread) {
            y -= panel.getHeight() + VERTICAL_GAP;
            animateMove(panel, w - HORIZONTAL_GAP - panel.getWidth(), y);
        }
    }
}