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

        String lafClassName = UI.getPref(Preferences.USER_PREF_LAF, Preferences.DEFAULT_LAF);
        try {
            UIManager.setLookAndFeel(lafClassName);
        } catch (Exception e) {
            System.err.println("Failed to set LAF " + lafClassName);
        }

        UI ui = new UI();

        mainWindow = new JFrame("PDF Passwords & Rights");

        try {
            ShapeMultiResolutionImage icon = new ShapeMultiResolutionImage(
                    SVGConverter.convert(Main.class.getResourceAsStream("/icon.svg"))
            );
            mainWindow.setIconImage(icon);
        } catch (Exception ignored) {
        }
        mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        mainWindow.setJMenuBar(ui.getMenu());
        mainWindow.setLocationByPlatform(true);
        mainWindow.setContentPane(ui);
        mainWindow.pack();

        mainWindow.setVisible(true);
    }

    public static JFrame mainWindow;

}
