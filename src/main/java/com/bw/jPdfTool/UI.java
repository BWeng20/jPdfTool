package com.bw.jPdfTool;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.prefs.Preferences;

public class UI extends JSplitPane {

    public static BufferedImage readFromResources(String name) {
        try {
            URL imageUrl = UI.class.getResource(name);
            return ImageIO.read(imageUrl);
        } catch (Exception e) {
            return null;
        }
    }

    public UI() {
        super(JSplitPane.HORIZONTAL_SPLIT);
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JTextField filePathField = new JTextField();
        filePathField.setEditable(false);
        JTextField ownerPasswordField = new JTextField();
        JTextField userPasswordField = new JTextField();

        JCheckBox allowPrinting = new JCheckBox("Printing");
        allowPrinting.setSelected(true);
        JCheckBox allowExtraction = new JCheckBox("Extraction");

        JCheckBox allowModification = new JCheckBox("Modify");
        JCheckBox allowFillIn = new JCheckBox("Fill Form");
        JCheckBox allowAssembly = new JCheckBox("Assembly");


        JButton protectButton = new JButton("Create Protected Copy");
        protectButton.setEnabled(false);

        JButton browseButton = new JButton("...");

        JLabel filePathLabel = new JLabel("Path to source:");
        filePathLabel.setLabelFor(filePathField);

        JLabel ownerPasswordLabel = new JLabel("Owner Password:");
        ownerPasswordLabel.setLabelFor(ownerPasswordField);

        JLabel userPasswordLabel = new JLabel("User Password:");
        userPasswordLabel.setLabelFor(userPasswordField);

        PreviewPane preview = new PreviewPane();

        GridBagConstraints gcLabel = new GridBagConstraints();
        gcLabel.anchor = GridBagConstraints.NORTHWEST;
        gcLabel.fill = GridBagConstraints.NONE;

        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        gcLabel.gridx = 0;
        gcLabel.gridy = 0;
        gc.gridx = 1;
        gc.gridy = 0;
        gc.weightx = 1;
        gc.gridwidth = 1;

        panel.add(filePathLabel, gcLabel);
        panel.add(filePathField, gc);
        gc.gridx++;
        gc.weightx = 0;

        panel.add(browseButton, gc);
        gc.weightx = 1;
        gc.gridx--;
        gc.gridwidth = 2;

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
            if (chooser == null)
                chooser = new JFileChooser();
            String lastDir = prefs.get("lastDir", null);
            if (lastDir != null) {
                chooser.setCurrentDirectory(new File(lastDir));
            }
            chooser.setFileFilter(pdfFilter);
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = chooser.getSelectedFile();
                filePathField.setText(selectedFile.getAbsolutePath());
                prefs.put("lastDir", selectedFile.getParent());
                if (documentProxy != null)
                    documentProxy.close();
                documentProxy = new DocumentProxy(selectedFile.toPath());
                documentProxy.owerPassword4Load = ownerPasswordField.getText().trim();
                if (documentProxy.owerPassword4Load.isEmpty())
                    documentProxy.owerPassword4Load = null;

                while (true) {
                    try {
                        documentProxy.getDocument();
                        break;
                    } catch (InvalidPasswordException ie) {
                        preview.setErrorText("File is protected.\nPassword needed.");
                        String password = getPassword();
                        if (password != null) {
                            documentProxy.owerPassword4Load = password;
                        } else {
                            filePathField.setText("");
                            documentProxy = null;
                            break;
                        }

                    } catch (Exception ex) {
                        ex.printStackTrace();
                        preview.setErrorText(ex.getMessage());
                        documentProxy = null;
                        break;
                    }
                }
                if (documentProxy != null)
                    preview.load(documentProxy);


                protectButton.setEnabled(true);
            }
        });


        protectButton.addActionListener(e -> {
            String path = filePathField.getText();
            String ownerPwd = ownerPasswordField.getText().trim();
            String userPwd = userPasswordField.getText().trim();

            if (documentProxy != null) {
                try {
                    PDDocument document = documentProxy.getDocument();

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
                    if (Files.exists(Paths.get(outputPath))) {
                        int result = JOptionPane.showConfirmDialog(
                                panel,
                                "The File \"" + outputPath + "\" exists.\nShall this file be overwritten?",
                                "Overwrite File?",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.WARNING_MESSAGE);

                        if (result == JOptionPane.YES_OPTION) {
                            document.save(outputPath);
                            JOptionPane.showMessageDialog(this, "PDF stored:\n" + outputPath);
                        }
                    } else {
                        document.save(outputPath);
                        JOptionPane.showMessageDialog(this, "PDF stored:\n" + outputPath, "Stored", JOptionPane.INFORMATION_MESSAGE);
                    }
                    document.close();

                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        setLeftComponent(panel);
        JScrollPane scrollPane = new JScrollPane(preview);
        setRightComponent(scrollPane);
    }

    protected String getPassword() {
        Object[] message = {"Enter Password:", passwordField};
        int option = JOptionPane.showConfirmDialog(
                null, message, "Password needed",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        );

        if (option == JOptionPane.OK_OPTION) {
            char[] password = passwordField.getPassword();
            return new String(password);
        } else {
            return null;
        }
    }


    protected DocumentProxy documentProxy;
    protected JPasswordField passwordField = new JPasswordField();

    protected static final Preferences prefs = Preferences.userRoot().node("jPdfTool");
    protected static JFileChooser chooser;
    protected static FileNameExtensionFilter pdfFilter = new FileNameExtensionFilter("PDF-Files (*.pdf)", "pdf");

}
