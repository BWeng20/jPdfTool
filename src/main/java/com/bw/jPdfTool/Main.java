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
        // com.formdev.flatlaf.FlatDarkLaf.setup();
        com.formdev.flatlaf.FlatLightLaf.setup();

        if (SystemInfo.isLinux) {
            JFrame.setDefaultLookAndFeelDecorated(true);
            JDialog.setDefaultLookAndFeelDecorated(true);
        }

        JFrame frame = new JFrame("PDF Passwords & Rights");

        try {
            ShapeMultiResolutionImage icon = new ShapeMultiResolutionImage(
                    SVGConverter.convert(Main.class.getResourceAsStream("/icon.svg"))
            );
            frame.setIconImage(icon);
        } catch (Exception ignored) {
        }
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        UI ui = new UI();
        frame.setLocationByPlatform(true);
        frame.setContentPane(ui);
        frame.setSize(1500, 600);

        frame.setVisible(true);
    }

}
