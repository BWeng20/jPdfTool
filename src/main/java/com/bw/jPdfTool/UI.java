package com.bw.jPdfTool;

import com.bw.jPdfTool.model.DocumentProxy;
import com.bw.jPdfTool.model.Page;
import com.bw.jtools.svg.SVGConverter;
import com.bw.jtools.ui.ShapeIcon;
import com.formdev.flatlaf.FlatLaf;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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

    /**
     * User preferences key for the last Look And Feel selected.
     */
    public final static String USER_PREF_LAF = "laf";

    /**
     * User preferences key for the owner password option.
     */
    public final static String USER_PREF_STORE_OWNER_PASSWORD = "storeOwnerPassword";

    /**
     * User preferences key for the owner password (if option is enabled).
     */
    public final static String USER_PREF_OWNER_PASSWORD = "ownerPassword";

    public final static String LAF_DARK_CLASSNAME = "com.formdev.flatlaf.FlatDarkLaf";
    public final static String LAF_LIGHT_CLASSNAME = "com.formdev.flatlaf.FlatLightLaf";

    public final static String DEFAULT_LAF = LAF_LIGHT_CLASSNAME;

    protected static final Preferences prefs = Preferences.userRoot().node("jPdfTool");
    private static final Map<String, Icon> icons = new HashMap<>();
    protected static JFileChooser savePdfChooser;
    protected static JFileChooser pdfChooser;
    protected static JFileChooser imageChooser;
    protected static FileNameExtensionFilter pdfFilter = new FileNameExtensionFilter("PDF-Files (*.pdf)", "pdf");
    protected static FileNameExtensionFilter imageFilter = new FileNameExtensionFilter("Picture (*.jpg, *.png)", "jpeg", "jpg", "png");

    private final JTextField ownerPasswordField = new JTextField();
    private final JTextField userPasswordField = new JTextField();
    private final JCheckBox allowPrinting = new JCheckBox("Printing");
    private final JCheckBox allowExtraction = new JCheckBox("Extraction");
    private final DefaultListModel<String> filePathsModel = new DefaultListModel<>();
    private final JList<String> filePaths = new JList<>(filePathsModel);
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

        Font f = UIManager.getFont("Panel.font");
        final FontMetrics fm = getFontMetrics(f);
        final int charWith = fm.charWidth('W');

        new DropTarget(this, new DropTargetAdapter() {

            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
                    java.util.List<File> droppedFiles = (java.util.List<File>) dtde.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    if (!droppedFiles.isEmpty()) {
                        if (dtde.getDropAction() == DnDConstants.ACTION_COPY) {
                            appendPdf(droppedFiles.get(0));
                        } else {
                            selectPdf(droppedFiles.get(0));
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace(System.err);
                }
            }
        });

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JButton generateOwner = new JButton("Random");

        compression.setSelected(true);
        allowPrinting.setSelected(true);
        saveButton.setEnabled(false);
        rotation.setEditable(false);

        JButton browseButton = new JButton("...");
        JButton browseAppendButton = new JButton("+");

        JLabel filePathLabel = new JLabel("Loaded files");

        JScrollPane filePathScroller = new JScrollPane(filePaths);
        filePathLabel.setLabelFor(filePathScroller);

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
        filePathScroller.setMinimumSize(new Dimension(0, fm.getHeight() * 5));
        panel.add(filePathScroller, gc);
        gc.gridx++;
        gc.weightx = 0;

        JPanel browseButtons = new JPanel(new GridLayout(2, 1));
        browseButtons.add(browseButton);
        browseButtons.add(browseAppendButton);

        gc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(browseButtons, gc);

        gcLabel.gridy++;
        panel.add(ownerPasswordLabel, gcLabel);
        gc.weightx = 1;
        gc.gridx--;
        gc.gridy++;
        gc.gridwidth = 1;
        panel.add(ownerPasswordField, gc);
        gc.gridx++;
        gc.weightx = 0;
        panel.add(generateOwner, gc);
        gcLabel.gridy++;
        panel.add(userPasswordLabel, gcLabel);
        gc.weightx = 1;
        gc.gridx--;
        gc.gridwidth = 2;
        gc.gridy++;
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
        deleteButton.addActionListener(e -> doDeletePage());

        rotateClockwiseButton = new JButton(getIcon("rotateClockwise"));
        rotateClockwiseButton.setToolTipText("Rotates the current page clockwise.");
        rotateClockwiseButton.addActionListener(e -> doRotateClockwise());

        moveLeft = new JButton(getIcon("moveLeft"));
        moveLeft.setToolTipText("Moves the current page up.");
        moveLeft.addActionListener(e -> doMoveLeft());

        moveRight = new JButton(getIcon("moveRight"));
        moveRight.setToolTipText("Moves the current page down.");
        moveRight.addActionListener(e -> doMoveRight());

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
                "enforces only the permissions you've selected.<br>" +
                "Most tools use a random owner password.</font></html>");


        panel.add(info, gcLabel);

        String ownerPassword = null;
        generateOwner.addActionListener(e -> ownerPasswordField.setText(UUID.randomUUID().toString()));
        if (prefs.getBoolean(USER_PREF_STORE_OWNER_PASSWORD, false)) {
            ownerPassword = prefs.get(USER_PREF_OWNER_PASSWORD, null);
        }
        if (ownerPassword == null)
            ownerPassword = UUID.randomUUID().toString();
        ownerPasswordField.setText(ownerPassword);

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

        browseButton.addActionListener(e -> doBrowsePdf());
        browseAppendButton.addActionListener(e -> doBrowseAdditionalPdf());

        saveButton.addActionListener(e -> doSave());


        setLeftComponent(panel);
        JScrollPane scrollPane = new JScrollPane(pages);
        scrollPane.getViewport().setPreferredSize(new Dimension(charWith * 30, fm.getHeight() * 40));
        setRightComponent(scrollPane);

        setDividerLocation(charWith * 65);
        checkPassword();
        setSelectedPage(null);
    }

    private final JRadioButtonMenuItem lafLight = new JRadioButtonMenuItem("Light");
    private final JRadioButtonMenuItem lafDark = new JRadioButtonMenuItem("Dark");
    private final JCheckBox storeOwnerPassword = new JCheckBox("Remember Owner Password");

    private JMenuBar menuBar;

    protected void setLaf(String laf) {
        try {
            if (!Objects.equals(UIManager.getLookAndFeel().getClass().getName(), laf)) {
                UIManager.setLookAndFeel(laf);
                FlatLaf.updateUILater();
                if (pdfChooser != null)
                    SwingUtilities.updateComponentTreeUI(pdfChooser);
                if (imageChooser != null)
                    SwingUtilities.updateComponentTreeUI(imageChooser);
                prefs.put(USER_PREF_LAF, laf);
                System.out.println("Switched to " + laf);
            }
        } catch (Exception e) {
            System.err.println("Failed to switch LAF to " + laf);
            e.printStackTrace(System.err);
        }
    }

    public static String getPref(String key, String defaultValue) {
        return prefs.get(key, defaultValue);
    }

    public synchronized JMenuBar getMenu() {
        if (menuBar == null) {

            lafDark.addActionListener(e -> setLaf(LAF_DARK_CLASSNAME));
            lafLight.addActionListener(e -> setLaf(LAF_LIGHT_CLASSNAME));

            menuBar = new JMenuBar();

            JMenu laf = new JMenu("Look And Feel");
            laf.add(lafLight);
            laf.add(lafDark);
            ButtonGroup lafGroup = new ButtonGroup();
            lafGroup.add(lafLight);
            lafGroup.add(lafDark);

            storeOwnerPassword.setToolTipText(
                    "<html>Stores the owner password in user preferences.<br>" +
                            "Otherwise the owner password is generated randomly on each start." +
                            "</html>");
            storeOwnerPassword.addActionListener(e -> {
                boolean sop = storeOwnerPassword.isSelected();
                if (sop != prefs.getBoolean(USER_PREF_STORE_OWNER_PASSWORD, false)) {
                    prefs.putBoolean(USER_PREF_STORE_OWNER_PASSWORD, sop);
                    if (!sop)
                        prefs.remove(USER_PREF_OWNER_PASSWORD);
                }
            });

            JMenu options = new JMenu("Options");
            options.add(storeOwnerPassword);
            options.add(laf);

            menuBar.add(options);
        }

        LookAndFeel currentLaf = UIManager.getLookAndFeel();
        String currentLafClassName = currentLaf == null ? null : currentLaf.getClass().getName();

        if (LAF_DARK_CLASSNAME.equals(currentLafClassName))
            lafDark.setSelected(true);
        if (LAF_LIGHT_CLASSNAME.equals(currentLafClassName))
            lafLight.setSelected(true);
        storeOwnerPassword.setSelected(prefs.getBoolean(USER_PREF_STORE_OWNER_PASSWORD, false));

        return menuBar;
    }

    /**
     * Opens file browser to load another pdf.
     */
    protected void doBrowsePdf() {
        JFileChooser chooser = getPdfChooser();
        chooser.setDialogTitle("Select PDF(s) to Load...");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(true);
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = chooser.getSelectedFiles();
            if (selectedFiles.length > 0) {
                selectPdf(selectedFiles[0]);
                for (int i = 1; i < selectedFiles.length; ++i)
                    appendPdf(selectedFiles[i]);
            }
        }
    }

    /**
     * Opens file browser to load another pdf.
     */
    protected void doBrowseAdditionalPdf() {
        JFileChooser chooser = getPdfChooser();
        chooser.setDialogTitle("Select PDF(s) to Append...");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(true);
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = chooser.getSelectedFiles();
            if (selectedFiles.length > 0)
                for (File selectedFile : selectedFiles) appendPdf(selectedFile);
        }
    }


    /**
     * Moves the selected page.
     */
    protected void doMoveRight() {
        PageWidget pw = pages.getSelectedPage();
        if (pw != null) {
            pw.getPage().movePage(1);
        }
    }

    /**
     * Moves the selected page.
     */
    protected void doMoveLeft() {
        PageWidget pw = pages.getSelectedPage();
        if (pw != null) {
            pw.getPage().movePage(-1);
        }
    }

    /**
     * Rotates the selected page.
     */
    protected void doRotateClockwise() {
        PageWidget pw = pages.getSelectedPage();
        if (pw != null) {
            Page page = pw.getPage();
            page.rotatePage(90);
        }
    }

    /**
     * Deletes the selected page.
     */
    protected void doDeletePage() {
        PageWidget pw = pages.getSelectedPage();
        if (pw != null) {
            Page page = pw.getPage();
            page.document.deletePage(page.pageNb);
        }
    }

    /**
     * Requests a file name and saves the document.
     */
    protected void doSave() {
        String ownerPwd = ownerPasswordField.getText().trim();
        String userPwd = userPasswordField.getText().trim();

        if (documentProxy != null) {
            PDDocument document = documentProxy.getLoadedDocument();
            if (document != null) {
                try {
                    AccessPermission ap = new AccessPermission();
                    ap.setCanPrint(allowPrinting.isSelected());
                    ap.setCanModify(allowModification.isSelected());
                    ap.setCanExtractContent(allowExtraction.isSelected());
                    ap.setCanFillInForm(allowFillIn.isSelected());
                    ap.setCanAssembleDocument(allowAssembly.isSelected());

                    StandardProtectionPolicy spp = new StandardProtectionPolicy(ownerPwd, userPwd, ap);
                    spp.setEncryptionKeyLength(128);
                    document.protect(spp);

                    JFileChooser chooser = getSavePdfChooser();
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

                                JOptionPane.showMessageDialog(this,
                                        "<html><font size='+1'>Stored PDF as<br><b>" + selectedFile + "</b></font></html>", "Stored", JOptionPane.INFORMATION_MESSAGE);
                                selectPdf(selectedFile);
                            }
                        } else {
                            document.save(selectedFile);
                            JOptionPane.showMessageDialog(this,
                                    "<html><font size='+1'>Stored PDF as<br><b>" + selectedFile + "</b></font></html>", "Stored", JOptionPane.INFORMATION_MESSAGE);
                        }
                        if (prefs.getBoolean(USER_PREF_STORE_OWNER_PASSWORD, false)) {
                            prefs.put(USER_PREF_OWNER_PASSWORD, ownerPwd);
                        }
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
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

    protected void appendPdf(File file) {
        if (documentProxy == null)
            selectPdf(file);
        else {
            String parent = file.getParent();
            if (parent != null)
                prefs.put(USER_PREF_LAST_PDF_DIR, parent);
            documentProxy.setOwnerPassword(ownerPasswordField.getText());
            documentProxy.load(file.toPath());
        }
    }

    /**
     * Select a pdf file for preview and processing.
     */
    protected void selectPdf(File selectedFile) {
        selectedFile = selectedFile.getAbsoluteFile();
        filePathsModel.clear();
        String parent = selectedFile.getParent();
        if (parent != null)
            prefs.put(USER_PREF_LAST_PDF_DIR, parent);
        if (documentProxy != null) {
            documentProxy.close();
        }
        pages.clear();

        documentProxy = new DocumentProxy();
        documentProxy.setOwnerPassword(ownerPasswordField.getText());

        documentProxy.addDocumentConsumer(new DocumentProxy.DocumentConsumer() {
            @Override
            public void documentLoaded(PDDocument document, Path file) {
                saveButton.setEnabled(document != null);
                if (file != null)
                    filePathsModel.addElement(file.toString());
            }

            @Override
            public void failed(String error) {
                saveButton.setEnabled(false);
                filePathsModel.addElement(error);
                documentProxy = null;
            }
        });
        pages.setDocument(documentProxy);
        documentProxy.load(selectedFile.toPath());
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
        String lastDir = getPref(USER_PREF_LAST_IMAGE_DIR, null);
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
        String lastDir = getPref(USER_PREF_LAST_PDF_DIR, null);
        if (lastDir != null) {
            Log.debug("Last PDF Directory '%s'", lastDir);
            pdfChooser.setCurrentDirectory(new File(lastDir));
        }
        return pdfChooser;
    }

    public static JFileChooser getSavePdfChooser() {
        if (savePdfChooser == null) {
            savePdfChooser = new JFileChooser();
            savePdfChooser.setDialogTitle("Select PDF to Save...");
            savePdfChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            savePdfChooser.setMultiSelectionEnabled(false);
            savePdfChooser.setFileFilter(pdfFilter);
        }
        String lastDir = getPref(USER_PREF_LAST_PDF_DIR, null);
        if (lastDir != null) {
            Log.debug("Last PDF Directory '%s'", lastDir);
            savePdfChooser.setCurrentDirectory(new File(lastDir));
        }

        return savePdfChooser;
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
