package com.bw.jPdfTool;

import javax.swing.*;

public class Main {

    public static void main(String[] args) {
        Main main = new Main();
        SwingUtilities.invokeLater(main::createUI);
    }


    protected void createUI() {
        JFrame frame = new JFrame("PDF Passwords & Rights");
        frame.setIconImage(UI.readFromResources("/icon.png"));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        UI ui = new UI();
        frame.setContentPane(ui);
        frame.setSize(800, 600);
        frame.setLocationByPlatform(true);

        frame.setVisible(true);
    }

}
