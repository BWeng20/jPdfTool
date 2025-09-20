package com.bw.jPdfTool;

import com.bw.jPdfTool.model.DocumentProxy;
import com.bw.jPdfTool.model.Page;
import com.bw.jtools.svg.SVGConverter;
import com.bw.jtools.ui.ShapeIcon;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.prefs.Preferences;

/**
 * Main panel to show the pdf tool. Wrap this panel into a frame to create an app.
 * See {@link Main#main(String[])}.
 */
public class UI extends JSplitPane {

    /**
     * User preferences key for the last PDF directory the user selected.
     */
    public final static String USER_PREF_LAST_PDF_DIR = "lastDir";

    /**
     * User preferences key for the last image directory the user selected.
     */
    public final static String USER_PREF_LAST_IMAGE_DIR = "lastImageDir";


    protected static final Preferences prefs = Preferences.userRoot().node("jPdfTool");
    private static final Map<String, Icon> icons = new HashMap<>();
    protected static JFileChooser pdfChooser;
    protected static JFileChooser imageChooser;
    protected static FileNameExtensionFilter pdfFilter = new FileNameExtensionFilter("PDF-Files (*.pdf)", "pdf");
    protected static FileNameExtensionFilter imageFilter = new FileNameExtensionFilter("Picture (*.jpg, *.png)", "jpeg", "jpg", "png");

    private final JTextField ownerPasswordField = new JTextField();
    private final JTextField userPasswordField = new JTextField();
    private final JCheckBox allowPrinting = new JCheckBox("Printing");
    private final JCheckBox allowExtraction = new JCheckBox("Extraction");
    private final JTextField filePathField = new JTextField();
    private final JButton saveButton = new JButton("Write To File");
    private final JCheckBox allowModification = new JCheckBox("Modify");
    private final JCheckBox allowFillIn = new JCheckBox("Fill Form");
    private final JCheckBox allowAssembly = new JCheckBox("Assembly");
    private final JCheckBox compression = new JCheckBox("Compression");
    private final JButton deleteButton;
    private final JButton rotateClockwiseButton;
    private final JButton moveLeft;
    private final JButton moveRight;
    private final JButton images;
    private final JTextField rotation = new JTextField();
    private final JLabel pageNb = new JLabel();
    private final PageWidgetContainer pages = new PageWidgetContainer();
    protected DocumentProxy documentProxy;

    public UI() {
        super(JSplitPane.HORIZONTAL_SPLIT);
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        filePathField.setEditable(false);
        JButton generateOwner = new JButton("Random");

        compression.setSelected(true);
        allowPrinting.setSelected(true);
        saveButton.setEnabled(false);
        rotation.setEditable(false);

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

        pages.addSelectionListener(e -> setSelectedPage(e.getFirstIndex() < 0 ? null : pages.getSelectedPage()));

        deleteButton = new JButton(getIcon("delete"));
        deleteButton.setToolTipText("Deletes the current page.");
        deleteButton.addActionListener(e -> {
            PageWidget pw = pages.getSelectedPage();
            if (pw != null) {
                Page page = pw.getPage();
                page.document.deletePage(page.pageNb);
            }
        });

        rotateClockwiseButton = new JButton(getIcon("rotateClockwise"));
        rotateClockwiseButton.setToolTipText("Rotates the current page clockwise.");
        rotateClockwiseButton.addActionListener(e -> {
            PageWidget pw = pages.getSelectedPage();
            if (pw != null) {
                Page page = pw.getPage();
                page.rotatePage(90);
            }
        });

        moveLeft = new JButton(getIcon("moveLeft"));
        moveLeft.setToolTipText("Moves the current page up.");
        moveLeft.addActionListener(e -> {
                    PageWidget pw = pages.getSelectedPage();
                    if (pw != null) {
                        pw.getPage().movePage(-1);
                    }
                }
        );

        moveRight = new JButton(getIcon("moveRight"));
        moveRight.setToolTipText("Moves the current page down.");
        moveRight.addActionListener(e -> {
            PageWidget pw = pages.getSelectedPage();
            if (pw != null) {
                pw.getPage().movePage(1);
            }
        });

        images = new JButton("Images");
        images.setToolTipText("Shows embedded images of the current page.");
        images.addActionListener(e -> {
            PageWidget pw = pages.getSelectedPage();
            if (pw != null) {
                showImageExtractor(pw.getPage());
            }
        });

        JPanel pageManipulation = new JPanel(new GridBagLayout());
        GridBagConstraints pmGc = new GridBagConstraints();
        pmGc.anchor = GridBagConstraints.NORTHWEST;
        pmGc.insets = new Insets(0, 5, 5, 5);
        pmGc.gridx = 0;
        pmGc.gridy = 0;
        pmGc.gridwidth = 2;
        pmGc.weightx = 0;
        pageManipulation.add(pageNb, pmGc);
        pmGc.gridwidth = 1;
        pmGc.gridy++;
        JLabel rotationLabel = new JLabel("Rotation");
        rotationLabel.setLabelFor(rotation);
        pageManipulation.add(rotationLabel, pmGc);
        pmGc.gridx = 1;
        pageManipulation.add(rotation, pmGc);

        pmGc.gridx = 2;
        pmGc.gridy = 0;
        pmGc.fill = GridBagConstraints.NONE;
        pageManipulation.add(deleteButton, pmGc);
        pmGc.gridy++;
        pageManipulation.add(rotateClockwiseButton, pmGc);
        pmGc.gridy++;
        pageManipulation.add(moveLeft, pmGc);
        pmGc.gridx++;
        pageManipulation.add(moveRight, pmGc);

        pmGc.gridx++;
        pmGc.gridy = 0;
        pageManipulation.add(images, pmGc);

        pmGc.gridy++;
        pmGc.weightx = 1;
        pageManipulation.add(Box.createHorizontalGlue(), pmGc);

        pageManipulation.setBorder(BorderFactory.createTitledBorder("Page Manipulation"));

        gcLabel.gridy++;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.weightx = 1;
        gc.weighty = 0;
        gc.gridwidth = 3;
        gc.gridx = 0;
        gc.gridy++;
        panel.add(pageManipulation, gc);


        gcLabel.gridy++;
        gc.gridy++;
        panel.add(saveButton, gc);

        gcLabel.anchor = GridBagConstraints.SOUTHWEST;
        gcLabel.gridy++;
        gcLabel.weighty = 0.5;
        gcLabel.gridwidth = 3;
        gcLabel.fill = GridBagConstraints.BOTH;
        gcLabel.insets = new Insets(15, 5, 5, 5);

        JLabel info = new JLabel();
        info.setVerticalAlignment(SwingConstants.BOTTOM);
        info.setText("<html><font size='+1'>To prevent unauthorized access to a PDF, you only need to set a user password.<br>" +
                "If you also want to define specific permissions - such as restricting printing or " +
                "editing - you must set both an owner password and a user password.<br>" +
                "The owner password always grants full control over the document, while the user password " +
                "enforces only the permissions you've selected.</font></html>");

        Font f = UIManager.getFont("Panel.font");
        FontMetrics fm = getFontMetrics(f);

        setPreferredSize(new Dimension(fm.charWidth('W') * 20, fm.getHeight() * 30));

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
            JFileChooser chooser = getPdfChooser();
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
                PDDocument document = documentProxy.getLoadedDocument();
                if (document != null) {
                    try {
                        final Path orgFile = documentProxy.getPath();

                        AccessPermission ap = new AccessPermission();
                        ap.setCanPrint(allowPrinting.isSelected());
                        ap.setCanModify(allowModification.isSelected());
                        ap.setCanExtractContent(allowExtraction.isSelected());
                        ap.setCanFillInForm(allowFillIn.isSelected());
                        ap.setCanAssembleDocument(allowAssembly.isSelected());

                        StandardProtectionPolicy spp = new StandardProtectionPolicy(ownerPwd, userPwd, ap);
                        spp.setEncryptionKeyLength(128);
                        document.protect(spp);

                        JFileChooser chooser = getPdfChooser();
                        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                        int result = chooser.showSaveDialog(this);
                        if (result == JFileChooser.APPROVE_OPTION) {
                            File selectedFile = chooser.getSelectedFile();

                            Path selectedFilePath = selectedFile.toPath();
                            if (Files.exists(selectedFilePath)) {
                                if (askOverwrite(this, selectedFilePath)) {
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
            }
        });

        setLeftComponent(panel);
        JScrollPane scrollPane = new JScrollPane(pages);
        setRightComponent(scrollPane);

        setDividerLocation(fm.charWidth('W') * 65);
        checkPassword();
        setSelectedPage(null);
    }

    public static boolean askOverwrite(Component comp, Path file) {
        int overwrite = JOptionPane.showConfirmDialog(
                comp,
                "The File \"" + file + "\" exists.\nShall this file be overwritten?",
                "Overwrite File?",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        return (overwrite == JOptionPane.YES_OPTION);
    }


    public static Icon getIcon(String name) {
        synchronized (icons) {
            Icon i = icons.get(name);
            if (i == null) {
                try (InputStream is = UI.class.getResourceAsStream("/" + name + ".svg")) {
                    i = new ShapeIcon(SVGConverter.convert(is));
                    icons.put(name, i);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return i;
        }
    }

    private void setSelectedPage(PageWidget page) {
        boolean enabled = page != null;
        deleteButton.setEnabled(enabled);
        rotateClockwiseButton.setEnabled(enabled);
        moveLeft.setEnabled(enabled);
        moveRight.setEnabled(enabled);
        images.setEnabled(enabled);
        if (enabled) {
            pageNb.setText("Page " + page.getPageNumber());
            rotation.setText(page.getPage().getRotation() + " °");
        } else {
            rotation.setText("");
            pageNb.setText("<html><i>Select a Page</i></hml>");
        }
    }

    /**
     * Select a pdf file for preview and processing.
     */
    protected void selectPdf(File selectedFile) {
        filePathField.setText(selectedFile.getAbsolutePath());
        prefs.put(USER_PREF_LAST_PDF_DIR, selectedFile.getParent());
        if (documentProxy != null) {
            documentProxy.close();
        }
        pages.clear();

        documentProxy = new DocumentProxy(selectedFile.toPath());
        documentProxy.owerPassword4Load = ownerPasswordField.getText().trim();
        if (documentProxy.owerPassword4Load.isEmpty())
            documentProxy.owerPassword4Load = null;

        documentProxy.addDocumentConsumer(new DocumentProxy.DocumentConsumer() {
            @Override
            public void documentLoaded(PDDocument document) {
                saveButton.setEnabled(document != null);
            }

            @Override
            public void failed(String error) {
                saveButton.setEnabled(false);
                filePathField.setText("");
                documentProxy = null;
            }
        });
        pages.setDocument(documentProxy);
        documentProxy.load();
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

    public static JFileChooser getImageChooser() {
        if (imageChooser == null) {
            imageChooser = new JFileChooser();
        }
        imageChooser.setFileFilter(imageFilter);
        String lastDir = prefs.get(USER_PREF_LAST_IMAGE_DIR, null);
        if (lastDir != null) {
            imageChooser.setCurrentDirectory(new File(lastDir));
        }
        imageChooser.setMultiSelectionEnabled(false);
        return imageChooser;
    }

    public static JFileChooser getPdfChooser() {
        if (pdfChooser == null) {
            pdfChooser = new JFileChooser();
            pdfChooser.setFileFilter(pdfFilter);
        }
        String lastDir = prefs.get(USER_PREF_LAST_PDF_DIR, null);
        if (lastDir != null) {
            pdfChooser.setCurrentDirectory(new File(lastDir));
        }
        return pdfChooser;
    }

    PageImageViewer pageImageViewer;
    JDialog imageExtractorDialog;

    protected void showImageExtractor(Page page) {
        if (pageImageViewer == null) {
            pageImageViewer = new PageImageViewer();
            imageExtractorDialog = new JDialog(SwingUtilities.getWindowAncestor(this));

            JPanel main = new JPanel(new BorderLayout(0, 5));
            JPanel buttons = new JPanel(new FlowLayout());
            buttons.add(new JButton("close"));

            main.add(BorderLayout.CENTER, pageImageViewer);
            main.add(BorderLayout.SOUTH, buttons);

            imageExtractorDialog.setContentPane(pageImageViewer);
            imageExtractorDialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
            imageExtractorDialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
            imageExtractorDialog.setLocationRelativeTo(this);

            imageExtractorDialog.pack();
        }
        pageImageViewer.setPage(page);
        imageExtractorDialog.setVisible(true);
    }

}
