package com.bw.jPdfTool;

import com.bw.jtools.svg.SVGConverter;
import com.bw.jtools.ui.ShapeMultiResolutionImage;
import com.formdev.flatlaf.fonts.roboto.FlatRobotoFont;
import com.formdev.flatlaf.util.SystemInfo;

import javax.swing.*;

public class Main {

    public static void main(String[] args) {
        Main main = new Main();
        SwingUtilities.invokeLater(main::createUI);
    }


    protected void createUI() {

        FlatRobotoFont.installLazy();
        if (SystemInfo.isLinux) {
            JFrame.setDefaultLookAndFeelDecorated(true);
            JDialog.setDefaultLookAndFeelDecorated(true);
        }

        String lafClassName = UI.getPref(UI.USER_PREF_LAF, UI.DEFAULT_LAF);
        try {
            UIManager.setLookAndFeel(lafClassName);
        } catch (Exception e) {
            System.err.println("Failed to set LAF " + lafClassName);
        }

        UI ui = new UI();

        JFrame frame = new JFrame("PDF Passwords & Rights");

        try {
            ShapeMultiResolutionImage icon = new ShapeMultiResolutionImage(
                    SVGConverter.convert(Main.class.getResourceAsStream("/icon.svg"))
            );
            frame.setIconImage(icon);
        } catch (Exception ignored) {
        }
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.setJMenuBar(ui.getMenu());
        frame.setLocationByPlatform(true);
        frame.setContentPane(ui);
        frame.pack();

        frame.setVisible(true);
    }

}
