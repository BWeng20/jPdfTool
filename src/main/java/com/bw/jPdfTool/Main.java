package com.bw.jPdfTool;

import com.formdev.flatlaf.util.SystemInfo;

import javax.swing.*;

public class Main {

    public static void main(String[] args) {
        Main main = new Main();
        SwingUtilities.invokeLater(main::createUI);
    }


    protected void createUI() {

        com.formdev.flatlaf.FlatDarkLaf.setup();

        if (SystemInfo.isLinux) {
            JFrame.setDefaultLookAndFeelDecorated(true);
            JDialog.setDefaultLookAndFeelDecorated(true);
        }

        JFrame frame = new JFrame("PDF Passwords & Rights");
        frame.setIconImage(UI.readFromResources("/icon.png"));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        UI ui = new UI();
        frame.setLocationByPlatform(true);
        frame.setContentPane(ui);
        frame.setSize(1500, 600);

        frame.setVisible(true);
    }

}
