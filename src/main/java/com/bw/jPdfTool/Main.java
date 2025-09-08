package com.bw.jPdfTool;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.Loader;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.prefs.Preferences;

public class Main {

    public static void main(String[] args) {
        Main main = new Main();
        SwingUtilities.invokeLater(main::createUI);
    }

    protected void createUI() {
        JFrame frame = new JFrame("PDF Passwords & Rights");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);
        frame.setLocationByPlatform(true);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));


        JTextField filePathField = new JTextField();
        JTextField ownerPasswordField = new JTextField();
        JTextField userPasswordField = new JTextField();

        JCheckBox allowPrinting = new JCheckBox("Printing");
        JCheckBox allowExtraction = new JCheckBox("Extraction");

        JCheckBox allowModification = new JCheckBox("Modify");
        JCheckBox allowFillIn = new JCheckBox("Fill Form");
        JCheckBox allowAssembly = new JCheckBox("Assembly");


        JButton protectButton = new JButton("Create Protected Copy");

        JButton browseButton = new JButton("Select PDF...");

        JLabel filePathLabel = new JLabel("Path to source:");
        filePathLabel.setLabelFor(filePathField);

        JLabel ownerPasswordLabel = new JLabel("Owner Password:");
        ownerPasswordLabel.setLabelFor(ownerPasswordField);

        JLabel userPasswordLabel = new JLabel("User Password:");
        userPasswordLabel.setLabelFor(userPasswordField);

        GridBagConstraints gcLabel = new GridBagConstraints();
        gcLabel.anchor = GridBagConstraints.NORTHWEST;
        gcLabel.fill = GridBagConstraints.NONE;

        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        gcLabel.gridx = 0;
        gcLabel.gridy = 0;
        gc.gridx = 1;
        gc.gridy = 0;
        gc.weightx = 1;

        panel.add( filePathLabel,  gcLabel );
        panel.add( filePathField, gc );
        gc.gridx++;
        panel.add(  browseButton, gc );
        gc.gridx--;


        gc.gridy++;
        gcLabel.gridy++;

        panel.add(ownerPasswordLabel, gcLabel);
        panel.add(ownerPasswordField, gc);

        gc.gridy++;
        gcLabel.gridy++;

        panel.add(userPasswordLabel, gcLabel);
        panel.add(userPasswordField, gc);

        gc.gridy++;
        gcLabel.gridy++;

        panel.add(allowPrinting, gc);
        gc.gridy++;
        gcLabel.gridy++;
        panel.add(allowModification, gc);
        gc.gridy++;
        gcLabel.gridy++;
        panel.add(allowExtraction, gc);
        gc.gridy++;
        gcLabel.gridy++;
        panel.add(allowFillIn, gc);
        gc.gridy++;
        gcLabel.gridy++;
        panel.add(allowAssembly, gc);

        gc.gridy++;
        gc.weighty = 1;
        gc.fill = GridBagConstraints.NONE;
        gc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(protectButton, gc);

        browseButton.addActionListener(e -> {
            if ( chooser == null )
                chooser = new JFileChooser();
            String lastDir = prefs.get("lastDir", null);
            if (lastDir != null) {
                chooser.setCurrentDirectory(new File(lastDir));
            }
            chooser.setFileFilter(pdfFilter);
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int result = chooser.showOpenDialog(frame);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = chooser.getSelectedFile();
                filePathField.setText(selectedFile.getAbsolutePath());
                prefs.put("lastDir", selectedFile.getParent());
            }
        });

        protectButton.addActionListener(e -> {
            String path = filePathField.getText();
            String ownerPwd = ownerPasswordField.getText();
            String userPwd = userPasswordField.getText();

            try {
                PDDocument document = Loader.loadPDF(new File(path));
                AccessPermission ap = new AccessPermission();
                ap.setCanPrint(allowPrinting.isSelected());
                ap.setCanModify(allowModification.isSelected());
                ap.setCanExtractContent(allowExtraction.isSelected());
                ap.setCanFillInForm(allowFillIn.isSelected());
                ap.setCanAssembleDocument(allowAssembly.isSelected());

                StandardProtectionPolicy spp = new StandardProtectionPolicy(ownerPwd, userPwd, ap);
                spp.setEncryptionKeyLength(128);
                document.protect(spp);

                String outputPath = path.replace(".pdf", "_protected.pdf");
                document.save(outputPath);
                document.close();

                JOptionPane.showMessageDialog(frame, "PDF erfolgreich geschützt:\n" + outputPath);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Fehler: " + ex.getMessage());
            }
        });

        frame.setContentPane(panel);
        frame.setVisible(true);
    }

    protected static final Preferences prefs = Preferences.userRoot().node("jPdfTool");
    protected static JFileChooser chooser;
    protected static FileNameExtensionFilter pdfFilter = new FileNameExtensionFilter("PDF-Files (*.pdf)", "pdf");
}
