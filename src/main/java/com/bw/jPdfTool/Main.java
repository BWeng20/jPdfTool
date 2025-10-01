package com.bw.jPdfTool;

import com.bw.jPdfTool.model.MergeOptions;
import com.bw.jtools.svg.SVGConverter;
import com.bw.jtools.ui.ShapeMultiResolutionImage;
import com.formdev.flatlaf.fonts.roboto.FlatRobotoFont;
import com.formdev.flatlaf.util.SystemInfo;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        Main main = new Main();

        main.files = new ArrayList<>(args.length);
        for (String a : args) {
            Path p = Paths.get(a);
            if (Files.exists(p))
                main.files.add(p);
            else {
                Log.error("File '%s' doesn't exists.", a);
            }
        }

        SwingUtilities.invokeLater(main::createUI);
    }

    private List<Path> files;

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

        if (!files.isEmpty()) {
            MergeOptions mo = new MergeOptions();
            ui.selectPdf(files.get(0).toFile());
            for (int i = 1; i < files.size(); ++i)
                ui.appendPdf(files.get(i).toFile(), mo);
        }
    }

    public static JFrame mainWindow;

}
