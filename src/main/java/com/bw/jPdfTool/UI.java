package com.bw.jPdfTool;

import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
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

    private final JTextField ownerPasswordField = new JTextField();
    private final JTextField userPasswordField = new JTextField();

    private final JCheckBox allowPrinting = new JCheckBox("Printing");
    private final JCheckBox allowExtraction = new JCheckBox("Extraction");
    private final JTextField filePathField = new JTextField();
    private final JButton saveButton = new JButton("Write Copy");

    private final PreviewPane preview = new PreviewPane();

    private final JCheckBox allowModification = new JCheckBox("Modify");
    private final JCheckBox allowFillIn = new JCheckBox("Fill Form");
    private final JCheckBox allowAssembly = new JCheckBox("Assembly");
    private final JCheckBox compression = new JCheckBox("Compression");


    public UI() {
        super(JSplitPane.HORIZONTAL_SPLIT);
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        filePathField.setEditable(false);
        JButton generateOwner = new JButton("Random");

        compression.setSelected(true);
        allowPrinting.setSelected(true);
        saveButton.setEnabled(false);

        JButton browseButton = new JButton("...");

        JLabel filePathLabel = new JLabel("Path to source");
        filePathLabel.setLabelFor(filePathField);

        JLabel ownerPasswordLabel = new JLabel("Owner Password");
        ownerPasswordLabel.setLabelFor(ownerPasswordField);

        JLabel userPasswordLabel = new JLabel("User Password");
        userPasswordLabel.setLabelFor(userPasswordField);


        GridBagConstraints gcLabel = new GridBagConstraints();
        gcLabel.anchor = GridBagConstraints.NORTHWEST;
        gcLabel.fill = GridBagConstraints.NONE;
        gcLabel.insets = new Insets(0, 0, 0, 5);

        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(0, 0, 5, 0);

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
        gc.gridwidth = 1;
        panel.add(ownerPasswordLabel, gcLabel);
        panel.add(ownerPasswordField, gc);
        gc.gridx++;
        gc.weightx = 0;
        panel.add(generateOwner, gc);
        gc.weightx = 1;
        gc.gridx--;
        gc.gridwidth = 2;

        gc.gridy++;
        gcLabel.gridy++;

        panel.add(userPasswordLabel, gcLabel);
        panel.add(userPasswordField, gc);

        gc.gridy++;
        gcLabel.gridy++;
        panel.add(compression, gc);
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
        gcLabel.gridy++;
        gc.weighty = 1;
        gc.fill = GridBagConstraints.NONE;
        gc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(saveButton, gc);

        gcLabel.anchor = GridBagConstraints.SOUTHWEST;
        gcLabel.gridy++;
        gcLabel.gridwidth = 3;
        gcLabel.fill = GridBagConstraints.BOTH;

        JLabel info = new JLabel();
        info.setVerticalAlignment(SwingConstants.TOP);
        info.setText("<html>To prevent unauthorized access to a PDF, you only need to set a user password.<br>" +
                "If you also want to define specific permissions - such as restricting printing or " +
                "editing - you must set both an owner password and a user password.<br>" +
                "The owner password always grants full control over the document, while the user password " +
                "enforces only the permissions you've selected.</html>");
        panel.add(info, gcLabel);

        generateOwner.addActionListener(e -> ownerPasswordField.setText(UUID.randomUUID().toString()));

        DocumentListener passwordChecker = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                checkPassword();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                checkPassword();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                checkPassword();
            }
        };

        ownerPasswordField.getDocument().addDocumentListener(passwordChecker);
        userPasswordField.getDocument().addDocumentListener(passwordChecker);

        browseButton.addActionListener(e -> {
            JFileChooser chooser = getChooser();
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = chooser.getSelectedFile();

                selectPdf(selectedFile);
            }
        });


        saveButton.addActionListener(e -> {
            String ownerPwd = ownerPasswordField.getText().trim();
            String userPwd = userPasswordField.getText().trim();

            if (documentProxy != null) {
                try {
                    final Path orgFile = documentProxy.file;
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

                    JFileChooser chooser = getChooser();
                    chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                    int result = chooser.showSaveDialog(this);
                    if (result == JFileChooser.APPROVE_OPTION) {
                        File selectedFile = chooser.getSelectedFile();

                        Path selectedFilePath = selectedFile.toPath();
                        if (Files.exists(selectedFilePath)) {
                            int overwrite = JOptionPane.showConfirmDialog(
                                    panel,
                                    "The File \"" + selectedFile + "\" exists.\nShall this file be overwritten?",
                                    "Overwrite File?",
                                    JOptionPane.YES_NO_OPTION,
                                    JOptionPane.WARNING_MESSAGE);

                            if (overwrite == JOptionPane.YES_OPTION) {
                                // PdfBox can't write to the same file. Write into buffer, close original,
                                // then re-open it.
                                ByteArrayOutputStream os = new ByteArrayOutputStream(5 * 1024 * 1024);
                                document.save(os, compression.isSelected()
                                        ? CompressParameters.DEFAULT_COMPRESSION : CompressParameters.NO_COMPRESSION);

                                documentProxy.close();
                                documentProxy = null;

                                Files.write(selectedFile.toPath(), os.toByteArray());

                                JOptionPane.showMessageDialog(this, "PDF stored:\n" + selectedFile, "Stored", JOptionPane.INFORMATION_MESSAGE);
                                selectPdf(orgFile.toFile());
                            }
                        } else {
                            document.save(selectedFile);
                            JOptionPane.showMessageDialog(this, "PDF stored:\n" + selectedFile);
                        }
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        setLeftComponent(panel);
        JScrollPane scrollPane = new JScrollPane(preview);
        setRightComponent(scrollPane);

        setDividerLocation(0.5);

        checkPassword();
    }

    /**
     * Select a pdf file for preview and processing.
     */
    protected void selectPdf(File selectedFile) {
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
                String password = requestOwnerPassword();
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
        saveButton.setEnabled(documentProxy != null);
    }

    protected void checkPassword() {
        boolean rightsPossible =
                !(ownerPasswordField.getText().trim().isEmpty() || userPasswordField.getText().trim().isEmpty());

        allowPrinting.setEnabled(rightsPossible);
        allowExtraction.setEnabled(rightsPossible);
        allowModification.setEnabled(rightsPossible);
        allowFillIn.setEnabled(rightsPossible);
        allowAssembly.setEnabled(rightsPossible);
    }

    protected String requestOwnerPassword() {
        Object[] message = {"Enter Owner Password:", passwordField};
        int option = JOptionPane.showConfirmDialog(
                null, message, "Owner Password needed",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        );

        if (option == JOptionPane.OK_OPTION) {
            char[] password = passwordField.getPassword();
            return new String(password);
        } else {
            return null;
        }
    }

    protected JFileChooser getChooser() {
        if (chooser == null) {
            chooser = new JFileChooser();
            chooser.setFileFilter(pdfFilter);
        }
        String lastDir = prefs.get("lastDir", null);
        if (lastDir != null) {
            chooser.setCurrentDirectory(new File(lastDir));
        }
        return chooser;
    }


    protected DocumentProxy documentProxy;
    protected JPasswordField passwordField = new JPasswordField();

    protected static final Preferences prefs = Preferences.userRoot().node("jPdfTool");
    protected static JFileChooser chooser;
    protected static FileNameExtensionFilter pdfFilter = new FileNameExtensionFilter("PDF-Files (*.pdf)", "pdf");

}
