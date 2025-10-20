package com.bw.jPdfTool;

import com.bw.jPdfTool.model.DocumentProxy;
import com.bw.jPdfTool.model.MergeOptions;
import com.bw.jPdfTool.model.Page;
import com.bw.jPdfTool.toast.Toast;
import com.bw.jPdfTool.toast.ToastType;
import com.bw.jPdfTool.toast.Toaster;
import com.bw.jtools.svg.SVGConverter;
import com.bw.jtools.ui.ShapeIcon;
import com.formdev.flatlaf.FlatLaf;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
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
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

/**
 * Main panel to show the pdf tool. Wrap this panel into a frame to create an app.
 * See {@link Main#main(String[])}.
 */
public class UI extends JSplitPane {

    private static final Map<String, Icon> icons = new HashMap<>();

    private int encryptionKeyLength = 256;

    private final Toaster toaster = new Toaster();

    protected static JFileChooser savePdfChooser;
    protected static JFileChooser pdfChooser;
    protected static JFileChooser imageChooser;

    protected static FileNameExtensionFilter pdfFilter = new FileNameExtensionFilter("PDF-Files (*.pdf)", "pdf");
    protected static FileNameExtensionFilter imageFilter = new FileNameExtensionFilter("Picture (*.jpg, *.png)", "jpeg", "jpg", "png");

    private final JTextField ownerPasswordField = new JTextField();
    private final JTextField userPasswordField = new JTextField();

    private final JButton saveButton = new JButton("Write to File");
    private final JCheckBox compression = new JCheckBox("Compression");

    private final PageWidgetContainer pages = new PageWidgetContainer();
    protected DocumentProxy documentProxy;
    private PageWidget selectedPage;

    private final JTextField pageCount = new JTextField(4);
    private final JCheckBox useDocumentInformation = new JCheckBox("Use Information from first loaded Document");
    private final JButton clearInformation = new JButton("Clear Information");
    private final JTextField title = new JTextField(15);
    private final JTextField author = new JTextField(15);
    private final JTextField creator = new JTextField(15);
    private final JTextField producer = new JTextField(15);
    private final JTextField subject = new JTextField(15);

    private final JTextField keywords = new JTextField(15);
    private final JLabel statusMessage = new JLabel();


    /////////////////////////////////////////////
    // Permissions
    /// //////////////////////////////////////////

    private final JCheckBox allowPrinting = new JCheckBox("Printing");
    private final JCheckBox allowExtraction = new JCheckBox("Extraction");
    private final JCheckBox allowModification = new JCheckBox("Modify");
    private final JCheckBox allowFillIn = new JCheckBox("Fill Form");
    private final JCheckBox allowAssembly = new JCheckBox("Assembly");
    private final JCheckBox allowAnnotations = new JCheckBox("Annotations");

    /////////////////////////////////////////////
    // Page manipulation
    /// //////////////////////////////////////////

    private final JButton deleteButton = new JButton(getIcon("delete"));
    private final JButton rotateClockwiseButton = new JButton(getIcon("rotateClockwise"));
    private final JButton moveLeft = new JButton(getIcon("moveLeft"));
    private final JButton moveRight = new JButton(getIcon("moveRight"));
    private final JButton exportImages = new JButton("<html>Export<br>Images</html>");
    private final JTextField rotation = new JTextField(4);
    private final JLabel pageNb = new JLabel();
    private final JLabel quality = new JLabel();
    private final JButton browseButton = new JButton(getIcon("openPdf"));
    private final JButton browseAppendButton = new JButton(getIcon("addPdf"));

    private final String HELP_ICON_NAME = "OptionPane.questionIcon";

    private final JButton help = new JButton(UIManager.getIcon(HELP_ICON_NAME));

    private Font normalFont;
    private Font hintFont;

    /////////////////////////////////////////////
    // Splitting
    /// //////////////////////////////////////////
    private final JSpinner pagesPerDocument = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
    private final JButton splitDocument = new JButton("Split Document");

    protected final RenderQueue renderQueue = new RenderQueue();

    public UI() {
        super(JSplitPane.HORIZONTAL_SPLIT);

        normalFont = UIManager.getFont("Panel.font");
        hintFont = normalFont.deriveFont(Font.ITALIC);
        final FontMetrics fm = getFontMetrics(normalFont);
        final int charWith = fm.charWidth('W');

        new DropTarget(this, new DropTargetAdapter() {

            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
                    if (dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor) instanceof java.util.List<?> droppedFiles) {
                        boolean append = dtde.getDropAction() == DnDConstants.ACTION_COPY;
                        for (Object item : droppedFiles) {
                            if (item instanceof File file) {
                                if (append && documentProxy != null) {
                                    appendPdf(file, new MergeOptions());
                                } else {
                                    selectPdf(file);
                                    append = true;
                                }
                            }
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace(System.err);
                }
            }
        });

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));

        JButton generateOwner = new JButton("Generate Owner Password");

        help.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "<html><font size='+2'><b>How to set PDF Permissions</b></font><br>" +
                        "<font size='+1'>" +
                        "To prevent unauthorized access to a PDF, you only need to set a user password.<br>" +
                        "If you also want to define specific permissions - such as restricting printing or<br>" +
                        "editing - you must set both an owner password and a user password.<br>" +
                        "The owner password always grants full control over the document, while the user<br>" +
                        "password enforces only the permissions you've selected.<br>" +
                        "Most tools use a hidden, automatically generated owner password to prevent<br>" +
                        "users from gaining full access.<br><br>" +
                        "Remint, that permission <i>Annotations</i> covers forms, comments and signing.</font></html>"));

        compression.setSelected(true);
        allowPrinting.setSelected(true);
        saveButton.setEnabled(false);
        saveButton.setFont(normalFont.deriveFont(Font.BOLD, normalFont.getSize2D() * 1.5f));
        browseAppendButton.setEnabled(false);
        splitDocument.setEnabled(false);
        rotation.setEditable(false);


        JLabel ownerPasswordLabel = new JLabel("Owner Password");
        ownerPasswordLabel.setLabelFor(ownerPasswordField);

        JLabel userPasswordLabel = new JLabel("User Password");
        userPasswordLabel.setLabelFor(userPasswordField);

        GridBagConstraints gcLabel = new GridBagConstraints();
        gcLabel.anchor = GridBagConstraints.NORTHWEST;
        gcLabel.fill = GridBagConstraints.NONE;
        gcLabel.insets = new Insets(0, 0, 5, 5);

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(0, 0, 5, 5);
        gc.gridx = 0;
        gc.gridy = 0;
        gc.gridwidth = 2;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(createDocumentPanel(), gc);

        pages.addSelectionListener(e -> setSelectedPage(e.getFirstIndex() < 0 ? null : pages.getSelectedPage()));

        deleteButton.setOpaque(false);
        deleteButton.setToolTipText("Deletes the current page.");
        deleteButton.addActionListener(e -> doDeletePage());

        rotateClockwiseButton.setOpaque(false);
        rotateClockwiseButton.setToolTipText("Rotates the current page clockwise.");
        rotateClockwiseButton.addActionListener(e -> doRotateClockwise());

        moveLeft.setOpaque(false);
        moveLeft.setToolTipText("Moves the current page up.");
        moveLeft.addActionListener(e -> doMoveLeft());

        moveRight.setOpaque(false);
        moveRight.setToolTipText("Moves the current page down.");
        moveRight.addActionListener(e -> doMoveRight());

        exportImages.setToolTipText("Exports embedded images of the current page.");
        exportImages.addActionListener(e -> {
            PageWidget pw = pages.getSelectedPage();
            if (pw != null) {
                showImageExtractor(pw.getPage());
            }
        });

        JPanel manipulations = new JPanel(new GridBagLayout());
        GridBagConstraints pmGc = new GridBagConstraints();
        pmGc.anchor = GridBagConstraints.NORTHWEST;
        pmGc.gridx = 0;
        pmGc.gridy = 0;
        pmGc.weightx = 0.5;
        pmGc.fill = GridBagConstraints.BOTH;
        manipulations.add(createPageManipulationPanel(), pmGc);
        pmGc.gridx++;
        manipulations.add(createSplitterPanel(), pmGc);

        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.weightx = 1;
        gc.weighty = 0;
        gc.gridwidth = 3;
        gc.gridx = 0;
        gc.gridy = 2;
        panel.add(manipulations, gc);

        gcLabel.gridx = 0;
        gcLabel.gridy = 3;
        panel.add(ownerPasswordLabel, gcLabel);

        gc.gridx = 0;
        gc.gridy = 4;
        gc.gridwidth = 1;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(ownerPasswordField, gc);
        gc.gridx = 1;
        gc.weightx = 0;
        panel.add(generateOwner, gc);
        gcLabel.gridy += 2;
        panel.add(userPasswordLabel, gcLabel);
        gc.weightx = 1;
        gc.gridwidth = 2;
        gc.gridx = 0;
        gc.gridy += 2;
        panel.add(userPasswordField, gc);

        JPanel permissions = new JPanel(new GridLayout(4, 2));

        permissions.add(compression);
        permissions.add(allowPrinting);
        permissions.add(allowModification);
        permissions.add(allowAnnotations);
        permissions.add(allowExtraction);
        permissions.add(allowFillIn);
        permissions.add(allowAssembly);

        allowAnnotations.addItemListener(e -> SwingUtilities.invokeLater(this::updatePermissions));

        gc.weightx = 1;
        gc.gridx = 0;
        gc.gridy++;
        gc.gridwidth = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(permissions, gc);

        gc.gridx = 1;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        panel.add(help, gc);

        gc.gridx = 0;
        gc.gridy++;
        gc.gridwidth = 2;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.NONE;
        gc.insets.top = 10;
        gcLabel.anchor = GridBagConstraints.NORTHWEST;
        panel.add(saveButton, gc);
        gc.insets.bottom = 5;

        gcLabel.gridy = gc.gridy + 1;
        gcLabel.anchor = GridBagConstraints.SOUTHWEST;
        gcLabel.weighty = 0.5;
        gcLabel.gridwidth = 1;
        gcLabel.fill = GridBagConstraints.BOTH;
        panel.add(Box.createGlue(), gcLabel);


        String ownerPassword = null;

        generateOwner.addActionListener(e -> ownerPasswordField.setText(UUID.randomUUID().toString()));
        if (Preferences.getInstance().getBoolean(Preferences.USER_PREF_STORE_OWNER_PASSWORD, false)) {
            ownerPassword = Preferences.getInstance().getString(Preferences.USER_PREF_OWNER_PASSWORD, null);
        }
        if (ownerPassword == null)
            ownerPassword = UUID.randomUUID().toString();
        ownerPasswordField.setText(ownerPassword);

        DocumentListener passwordChecker = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updatePermissions();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updatePermissions();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updatePermissions();
            }
        };

        ownerPasswordField.getDocument().addDocumentListener(passwordChecker);
        userPasswordField.getDocument().addDocumentListener(passwordChecker);

        browseButton.addActionListener(e -> doBrowsePdf());
        browseAppendButton.addActionListener(e -> {
            if (documentProxy == null)
                doBrowsePdf();
            else
                doBrowseAdditionalPdf();
        });

        saveButton.addActionListener(e -> doSave());
        splitDocument.addActionListener(e -> doSplit());

        setLeftComponent(panel);
        JScrollPane scrollPane = new JScrollPane(pages);
        scrollPane.getViewport().setPreferredSize(new Dimension(charWith * 50, fm.getHeight() * 40));
        setRightComponent(scrollPane);

        setDividerLocation(charWith * 65);
        updatePermissions();
        setSelectedPage(null);

        pages.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (selectedPage != null) {
                    quality.setText(String.format("%d dpi (x %.2f)", selectedPage.getPage().dpi, selectedPage.getScale()));
                }
            }
        });
        renderQueue.start();

        SwingUtilities.invokeLater(() ->
                toaster.attachFrame((JFrame) SwingUtilities.getWindowAncestor(this))
        );
    }

    protected JPanel createDocumentPanel() {
        JPanel documentPanel = new JPanel(new GridBagLayout());
        documentPanel.setBorder(BorderFactory.createTitledBorder("Effective Document"));

        JPanel browseButtons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 5, 0));

        browseButton.setToolTipText("<html>Loads new document.<br>Resets the content to this file.</html>");
        browseAppendButton.setToolTipText("<html>Adds another document.</html>");
        browseButton.setOpaque(false);
        browseAppendButton.setOpaque(false);

        browseButtons.add(browseButton);
        browseButtons.add(browseAppendButton);

        pageCount.setEditable(false);
        pageCount.setText("0");

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(0, 5, 5, 5);
        gc.gridx = 0;
        gc.gridy = 0;
        gc.gridwidth = 1;
        gc.gridheight = 1;
        gc.fill = GridBagConstraints.NONE;
        gc.anchor = GridBagConstraints.WEST;

        JLabel pageCountLabel = new JLabel("Pages");
        pageCountLabel.setLabelFor(pageCount);
        documentPanel.add(pageCountLabel, gc);

        gc.gridx = 1;
        gc.weightx = 1;
        documentPanel.add(pageCount, gc);

        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 2;
        gc.weightx = 1;
        statusMessage.setFont(hintFont);
        documentPanel.add(statusMessage, gc);

        gc.gridy = 0;
        gc.gridx = 2;
        gc.anchor = GridBagConstraints.NORTHEAST;
        gc.gridwidth = 1;
        gc.weighty = 0;
        gc.weightx = 0;
        gc.gridheight = 2;
        documentPanel.add(browseButtons, gc);

        gc.gridheight = 1;
        gc.anchor = GridBagConstraints.WEST;
        gc.gridx = 0;
        gc.gridy = 2;
        gc.weightx = 0;
        gc.gridwidth = 3;

        JPanel docButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        docButtons.add(useDocumentInformation);
        docButtons.add(clearInformation);
        documentPanel.add(docButtons, gc);

        useDocumentInformation.setSelected(true);
        initDocumentInformation();
        useDocumentInformation.addItemListener(e -> initDocumentInformation());

        clearInformation.addActionListener(e -> {
            useDocumentInformation.setSelected(false);

            SwingUtilities.invokeLater(() -> {
                // Shall work also without invokeLater.
                // Only for the case we get some issue with checkbox change handling.
                title.setText("");
                author.setText("");
                creator.setText("");
                producer.setText("");
                subject.setText("");
                keywords.setText("");
            });
        });

        gc.gridy++;
        gc.gridwidth = 1;

        JLabel titleLabel = new JLabel("Title");
        titleLabel.setLabelFor(title);
        documentPanel.add(titleLabel, gc);
        gc.gridwidth = 2;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        gc.gridx = 1;
        documentPanel.add(title, gc);

        gc.gridwidth = 1;
        gc.weightx = 0;
        gc.gridx = 0;
        gc.gridy++;
        JLabel authorLabel = new JLabel("Author");
        authorLabel.setLabelFor(author);
        documentPanel.add(authorLabel, gc);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        gc.gridx = 1;
        gc.gridwidth = 2;
        documentPanel.add(author, gc);

        gc.gridy++;
        JLabel creatorLabel = new JLabel("Creator");
        creatorLabel.setLabelFor(creator);
        gc.weightx = 0;
        gc.gridx = 0;
        gc.gridy++;
        gc.gridwidth = 1;
        documentPanel.add(creatorLabel, gc);
        gc.weightx = 1;
        gc.gridx = 1;
        gc.gridwidth = 2;
        documentPanel.add(creator, gc);

        gc.gridy++;
        JLabel producerLabel = new JLabel("Producer");
        producerLabel.setLabelFor(producer);
        gc.weightx = 0;
        gc.gridx = 0;
        gc.gridy++;
        gc.gridwidth = 1;
        documentPanel.add(producerLabel, gc);
        gc.weightx = 1;
        gc.gridx = 1;
        gc.gridwidth = 2;
        documentPanel.add(producer, gc);


        gc.gridy++;
        JLabel subjectLabel = new JLabel("Subject");
        subjectLabel.setLabelFor(subject);
        gc.weightx = 0;
        gc.gridx = 0;
        gc.gridwidth = 1;
        documentPanel.add(subjectLabel, gc);
        gc.weightx = 1;
        gc.gridx = 1;
        gc.gridwidth = 2;
        documentPanel.add(subject, gc);

        gc.gridy++;
        JLabel keywordsLabel = new JLabel("Keywords");
        keywordsLabel.setLabelFor(keywords);
        gc.weightx = 0;
        gc.gridx = 0;
        gc.gridwidth = 1;
        documentPanel.add(keywordsLabel, gc);
        gc.weightx = 1;
        gc.gridx = 1;
        gc.gridwidth = 2;
        documentPanel.add(keywords, gc);


        return documentPanel;
    }

    protected JPanel createSplitterPanel() {
        JPanel splitter = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.insets = new Insets(0, 5, 5, 5);
        gc.gridx = 0;
        gc.gridy = 0;
        gc.gridwidth = 1;
        gc.weightx = 0;
        gc.weighty = 0;

        JLabel pagesLabel = new JLabel("Number of Pages");
        pagesLabel.setLabelFor(pagesPerDocument);
        splitter.add(pagesLabel, gc);
        gc.gridx++;
        splitter.add(pagesPerDocument, gc);

        gc.fill = GridBagConstraints.BOTH;
        gc.weightx = 1;
        gc.gridx = 0;
        gc.gridy++;
        gc.gridwidth = 2;
        splitter.add(Box.createGlue(), gc);

        gc.fill = GridBagConstraints.NONE;
        gc.weightx = 0;
        gc.gridy++;
        splitter.add(splitDocument, gc);

        splitter.setBorder(BorderFactory.createTitledBorder("Splitting"));
        return splitter;
    }

    protected JPanel createPageManipulationPanel() {
        JPanel pageManipulation = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.WEST;
        gc.insets = new Insets(0, 5, 5, 5);
        gc.gridx = 0;
        gc.gridy = 0;
        gc.gridwidth = 2;
        gc.weightx = 0;
        pageManipulation.add(pageNb, gc);
        gc.gridwidth = 1;
        gc.gridy++;
        JLabel rotationLabel = new JLabel("Rotation");
        rotationLabel.setLabelFor(rotation);
        pageManipulation.add(rotationLabel, gc);
        gc.gridx = 1;
        pageManipulation.add(rotation, gc);

        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 2;
        pageManipulation.add(quality, gc);

        gc.gridwidth = 1;
        gc.gridx = 2;
        gc.gridy = 0;
        gc.fill = GridBagConstraints.NONE;
        pageManipulation.add(deleteButton, gc);
        gc.gridy++;
        pageManipulation.add(rotateClockwiseButton, gc);
        gc.gridy++;
        pageManipulation.add(moveLeft, gc);
        gc.gridx++;
        pageManipulation.add(moveRight, gc);

        gc.gridy = 0;
        gc.gridheight = 2;
        gc.anchor = GridBagConstraints.NORTH;
        pageManipulation.add(exportImages, gc);

        gc.gridy++;
        gc.weightx = 0.1;
        gc.gridheight = 1;
        pageManipulation.add(Box.createHorizontalGlue(), gc);
        pageManipulation.setBorder(BorderFactory.createTitledBorder("Page Manipulation"));
        return pageManipulation;
    }

    private final JRadioButtonMenuItem encryptionKeyLength40 = new JRadioButtonMenuItem("40-Bit");
    private final JRadioButtonMenuItem encryptionKeyLength128 = new JRadioButtonMenuItem("128-Bit");
    private final JRadioButtonMenuItem encryptionKeyLength256 = new JRadioButtonMenuItem("256-Bit");


    private final JRadioButtonMenuItem lafLight = new JRadioButtonMenuItem("Light");
    private final JRadioButtonMenuItem lafDark = new JRadioButtonMenuItem("Dark");
    private final JRadioButtonMenuItem lafSystem = new JRadioButtonMenuItem("OS Default");
    private final JRadioButtonMenuItem lafCross = new JRadioButtonMenuItem("Cross Platform");

    private final JCheckBox storeOwnerPassword = new JCheckBox("Remember Owner Password");

    private final JPrefCheckBoxMenuItem viewQualityAA
            = new JPrefCheckBoxMenuItem("Antialiasing", Preferences.USER_PREF_VIEWER_ANTIALIASING,
            Preferences.USER_PREF_VIEWER_ANTIALIASING_DEFAULT);

    private final JRadioButtonMenuItem viewQualityInterpolBiCubic = new JPrefRadioButtonMenuItem(
            "Interpolation bi-cubic", Preferences.USER_PREF_VIEWER_INTERPOLATE_BI_CUBIC, false, true);
    private final JRadioButtonMenuItem viewQualityInterpolBiLinear = new JPrefRadioButtonMenuItem(
            "Interpolation bi-linear", Preferences.USER_PREF_VIEWER_INTERPOLATE_BI_CUBIC, true, false);

    private final JRadioButtonMenuItem viewQualityRenderFast = new JPrefRadioButtonMenuItem(
            "Rendering for speed", Preferences.USER_PREF_VIEWER_RENDER_QUALITY, true, false);
    private final JRadioButtonMenuItem viewQualityRenderQuality = new JPrefRadioButtonMenuItem(
            "Rendering for quality", Preferences.USER_PREF_VIEWER_RENDER_QUALITY, false, true);

    private JMenuBar menuBar;

    public static boolean isFlatLaf() {
        return UIManager.getLookAndFeel() instanceof FlatLaf;
    }

    protected void setLaf(String laf) {
        try {
            if (!Objects.equals(UIManager.getLookAndFeel().getClass().getName(), laf)) {
                UIManager.setLookAndFeel(laf);
                FlatLaf.updateUILater();
                if (pdfChooser != null)
                    SwingUtilities.updateComponentTreeUI(pdfChooser);
                if (imageChooser != null)
                    SwingUtilities.updateComponentTreeUI(imageChooser);
                if (savePdfChooser != null)
                    SwingUtilities.updateComponentTreeUI(savePdfChooser);
                SwingUtilities.updateComponentTreeUI(mergeOptions);
                Preferences.getInstance().set(Preferences.USER_PREF_LAF, laf);
                help.setOpaque(false);
                help.setIcon(UIManager.getIcon(HELP_ICON_NAME));
                normalFont = UIManager.getFont("Panel.font");
                hintFont = normalFont.deriveFont(Font.ITALIC);
                saveButton.setFont(normalFont.deriveFont(Font.BOLD, normalFont.getSize2D() * 1.5f));
                statusMessage.setFont(hintFont);
                setSelectedPage(selectedPage);
                SwingUtilities.getWindowAncestor(this).pack();
                Log.info("Switched to %s", laf);
            }
        } catch (Exception e) {
            System.err.println("Failed to switch LAF to " + laf);
            e.printStackTrace(System.err);
        }
    }

    public static String getPref(String key, String defaultValue) {
        return Preferences.getInstance().getString(key, defaultValue);
    }


    public void setEncryptionKeyLength(int bits) {
        if (bits != 40 && bits != 128 && bits != 256)
            throw new IllegalArgumentException("Encryption Key Length must be 40, 128 or 256");
        if (this.encryptionKeyLength != bits) {
            this.encryptionKeyLength = bits;
            Preferences.getInstance().set(Preferences.USER_ENCRYPTION_KEY_LENGTH, bits);
        }
    }

    public synchronized JMenuBar getMenu() {
        if (menuBar == null) {

            encryptionKeyLength40.addActionListener(e -> setEncryptionKeyLength(40));
            encryptionKeyLength128.addActionListener(e -> setEncryptionKeyLength(128));
            encryptionKeyLength256.addActionListener(e -> setEncryptionKeyLength(256));

            lafDark.addActionListener(e -> setLaf(Preferences.LAF_DARK_CLASSNAME));
            lafLight.addActionListener(e -> setLaf(Preferences.LAF_LIGHT_CLASSNAME));
            lafSystem.addActionListener(e -> setLaf(UIManager.getSystemLookAndFeelClassName()));
            lafCross.addActionListener(e -> setLaf(UIManager.getCrossPlatformLookAndFeelClassName()));

            menuBar = new JMenuBar();

            JMenu encryptionKeyLengthMenu = new JMenu("Encryption Key Length");
            encryptionKeyLengthMenu.add(encryptionKeyLength40);
            encryptionKeyLengthMenu.add(encryptionKeyLength128);
            encryptionKeyLengthMenu.add(encryptionKeyLength256);
            ButtonGroup encryptionKeyLengthGroup = new ButtonGroup();
            encryptionKeyLengthGroup.add(encryptionKeyLength40);
            encryptionKeyLengthGroup.add(encryptionKeyLength128);
            encryptionKeyLengthGroup.add(encryptionKeyLength256);

            JMenu laf = new JMenu("Look And Feel");
            laf.add(lafLight);
            laf.add(lafDark);
            laf.add(lafCross);
            laf.add(lafSystem);
            ButtonGroup lafGroup = new ButtonGroup();
            lafGroup.add(lafLight);
            lafGroup.add(lafDark);
            lafGroup.add(lafCross);
            lafGroup.add(lafSystem);

            storeOwnerPassword.setToolTipText(
                    "<html>Stores the owner password in user preferences.<br>" +
                            "Otherwise the owner password is generated randomly on each start." +
                            "</html>");
            storeOwnerPassword.addActionListener(e -> {
                boolean sop = storeOwnerPassword.isSelected();
                var prefs = Preferences.getInstance();
                if (sop != prefs.getBoolean(Preferences.USER_PREF_STORE_OWNER_PASSWORD, false)) {
                    prefs.set(Preferences.USER_PREF_STORE_OWNER_PASSWORD, sop);
                    if (!sop)
                        prefs.remove(Preferences.USER_PREF_OWNER_PASSWORD);
                }
            });

            JMenu renderQuality = new JMenu("Render Quality");

            int[] dpis = new int[]{150, 300, 600, 1200};

            ButtonGroup dpiGroup = new ButtonGroup();

            int dpiSetting = Preferences.getInstance().getInt(Preferences.USER_PREF_DPI, 300);

            for (int dpi : dpis) {
                JRadioButtonMenuItem dpiItem = new JRadioButtonMenuItem(String.format("%d dpi", dpi));
                dpiItem.setSelected(dpi == dpiSetting);
                dpiGroup.add(dpiItem);
                renderQuality.add(dpiItem);
                final int dpiFinal = dpi;
                dpiItem.addActionListener(e -> setDpi(dpiFinal));
            }

            this.encryptionKeyLength = Preferences.getInstance().getInt(Preferences.USER_ENCRYPTION_KEY_LENGTH, 256);
            switch (this.encryptionKeyLength) {
                case 40 -> encryptionKeyLength40.setSelected(true);
                case 128 -> encryptionKeyLength128.setSelected(true);
                case 256 -> encryptionKeyLength256.setSelected(true);
                default -> {
                    this.encryptionKeyLength = 256;
                    encryptionKeyLength256.setSelected(true);
                }
            }

            JMenu options = new JMenu("Options");
            options.add(storeOwnerPassword);
            options.add(encryptionKeyLengthMenu);
            options.add(laf);
            options.add(renderQuality);

            if (Log.DEBUG) {
                JMenu viewerQuality = new JMenu("Viewer Quality (debug mode)");

                viewerQuality.add(viewQualityAA);
                viewerQuality.addSeparator();

                ButtonGroup interpolateGroup = new ButtonGroup();
                interpolateGroup.add(viewQualityInterpolBiLinear);
                viewerQuality.add(viewQualityInterpolBiLinear);
                interpolateGroup.add(viewQualityInterpolBiCubic);
                viewerQuality.add(viewQualityInterpolBiCubic);

                viewerQuality.addSeparator();

                ButtonGroup qualityRenderGroup = new ButtonGroup();
                qualityRenderGroup.add(viewQualityRenderFast);
                viewerQuality.add(viewQualityRenderFast);
                qualityRenderGroup.add(viewQualityRenderQuality);
                viewerQuality.add(viewQualityRenderQuality);

                options.add(viewerQuality);
            }
            menuBar.add(options);
        }

        LookAndFeel currentLaf = UIManager.getLookAndFeel();
        String currentLafClassName = currentLaf == null ? null : currentLaf.getClass().getName();

        if (Preferences.LAF_DARK_CLASSNAME.equals(currentLafClassName))
            lafDark.setSelected(true);
        else if (Preferences.LAF_LIGHT_CLASSNAME.equals(currentLafClassName))
            lafLight.setSelected(true);
        else if (UIManager.getCrossPlatformLookAndFeelClassName().equals(currentLafClassName))
            lafCross.setSelected(true);
        else if (UIManager.getSystemLookAndFeelClassName().equals(currentLafClassName))
            lafSystem.setSelected(true);

        storeOwnerPassword.setSelected(Preferences.getInstance().getBoolean(
                Preferences.USER_PREF_STORE_OWNER_PASSWORD, false));
        return menuBar;
    }

    protected void setDpi(int dpi) {
        Preferences.getInstance().set(Preferences.USER_PREF_DPI, dpi);
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
            MergeOptions mo = new MergeOptions();
            if (selectedFiles.length > 0) {
                selectPdf(selectedFiles[0]);
                for (int i = 1; i < selectedFiles.length; ++i)
                    appendPdf(selectedFiles[i], mo);
            }
        }
    }

    private final MergeOptionPanel mergeOptions = new MergeOptionPanel();

    /**
     * Opens file browser to load another pdf.
     */
    protected void doBrowseAdditionalPdf() {
        JFileChooser chooser = getPdfChooser();
        chooser.setDialogTitle("Select PDF(s) to Append...");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(true);
        try {
            mergeOptions.setOriginalPageCount(documentProxy.getPageCount());
            mergeOptions.install(chooser);
            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File[] selectedFiles = chooser.getSelectedFiles();
                MergeOptions mo = mergeOptions.getMergeOptions();
                for (File selectedFile : selectedFiles) appendPdf(selectedFile, mo);
            }
        } finally {
            mergeOptions.uninstall(chooser);
        }
    }

    /**
     * Moves the selected page.
     */
    protected void doMoveRight() {
        PageWidget pw = pages.getSelectedPage();
        if (pw != null) {
            int pageCount = documentProxy.getPageCount();
            int pageNb = pw.getPageNumber() + 1;
            if (pageNb > pageCount)
                pageNb = pageCount;
            pw.getPage().movePage(1);
            pages.setSelectedPage(pages.getPageWidget(pageNb));
        }
    }

    /**
     * Moves the selected page.
     */
    protected void doMoveLeft() {
        PageWidget pw = pages.getSelectedPage();
        if (pw != null) {
            int pageNb = pw.getPageNumber() - 1;
            if (pageNb <= 0)
                pageNb = 1;
            pw.getPage().movePage(-1);
            pages.setSelectedPage(pages.getPageWidget(pageNb));
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
     * Requests a file template and creates multiple documents.
     */
    protected void doSplit() {
        if (pagesPerDocument.getValue() instanceof Number number) {
            int pagePerDocument = number.intValue();
            if (pagePerDocument > 0 && documentProxy != null) {
                JFileChooser chooser = getSavePdfChooser();
                chooser.setMultiSelectionEnabled(false);
                chooser.setDialogTitle("Choose a base file name for split…");
                JLabel info = new JLabel("<html>Choose a base file name,<br>the index of the file will be put<br><b>behind</b> the name</html>");
                info.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
                info.setVerticalAlignment(JLabel.TOP);
                chooser.setAccessory(info);
                int result = chooser.showSaveDialog(this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = chooser.getSelectedFile();

                    String fprefix;
                    String fpostfix;
                    String fname = selectedFile.getName();
                    int idx = fname.lastIndexOf('.');
                    if (idx < 0) {
                        fprefix = selectedFile.getAbsolutePath();
                        fpostfix = ".pdf";
                    } else {
                        fprefix = new File(selectedFile.getParent(), fname.substring(0, idx)).getAbsolutePath();
                        fpostfix = fname.substring(idx);
                    }

                    int fileCount = 0;
                    final PDDocument source = documentProxy.getDocument();
                    Splitter splitter = new Splitter();
                    splitter.setSplitAtPage(pagePerDocument);
                    try {
                        List<PDDocument> splittedDocs = splitter.split(source);
                        for (PDDocument doc : splittedDocs) {
                            setDocumentInformation(doc);
                            String docFile = String.format("%s%03d%s", fprefix, ++fileCount, fpostfix);
                            doc.save(docFile);
                            Log.info("Stored file '%s'", docFile);
                            doc.close();
                        }
                        toaster.toast(ToastType.SUCCESS, 5000, "<html>Spitted to <br><font size='+1'>%s<i> [ 001 .. %03d ]%s</i></font></html>",
                                fname, fileCount, fpostfix);
                    } catch (IOException e) {
                        toaster.toast(ToastType.ERROR, 20000,
                                "<html><font size='+2'>PDF Error</font><br><font size='+1'><i>%s</i></font></html>", e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Requests a file name and saves the document.
     */
    protected void doSave() {
        if (documentProxy != null && documentProxy.getDocument() != null) {

            try {
                JFileChooser chooser = getSavePdfChooser();
                int result = chooser.showSaveDialog(this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    statusMessage.setText("");

                    File selectedFile = chooser.getSelectedFile();
                    Path selectedFilePath = selectedFile.toPath();

                    String fname = selectedFilePath.getFileName().toString();
                    int idx = fname.lastIndexOf('.');
                    if (idx < 0) {
                        selectedFilePath = selectedFilePath.getParent().resolve(fname + ".pdf");
                    }

                    if (Files.exists(selectedFilePath) && !askOverwrite(this, selectedFilePath)) {
                        return;
                    }

                    String ownerPwd = ownerPasswordField.getText().trim();
                    String userPwd = userPasswordField.getText().trim();

                    PDDocument document = documentProxy.getCopy();

                    AccessPermission ap = new AccessPermission();
                    ap.setCanPrint(allowPrinting.isSelected());
                    ap.setCanModify(allowModification.isSelected());
                    ap.setCanExtractContent(allowExtraction.isSelected());
                    ap.setCanExtractForAccessibility(allowExtraction.isSelected());
                    ap.setCanFillInForm(allowFillIn.isSelected());
                    ap.setCanAssembleDocument(allowAssembly.isSelected());
                    ap.setCanModifyAnnotations(allowAnnotations.isSelected());

                    if (ownerPwd.isEmpty() && userPwd.isEmpty()) {
                        document.setAllSecurityToBeRemoved(true);
                    } else {
                        StandardProtectionPolicy spp = new StandardProtectionPolicy(ownerPwd, userPwd, ap);
                        Log.info("Encryption-key-length %d bits", this.encryptionKeyLength);
                        spp.setEncryptionKeyLength(this.encryptionKeyLength);
                        document.protect(spp);
                    }

                    setDocumentInformation(document);
                    document.save(selectedFilePath.toFile());

                    var prefs = Preferences.getInstance();
                    if (prefs.getBoolean(Preferences.USER_PREF_STORE_OWNER_PASSWORD, false)) {
                        prefs.set(Preferences.USER_PREF_OWNER_PASSWORD, ownerPwd);
                    }

                    List<Object> options = new ArrayList<>(2);
                    options.add("OK");

                    // If supported, give buttons to browse/view the result
                    // in the system default application (vie Desktop-support)
                    if (Desktop.isDesktopSupported()) {
                        try {
                            final URI finalFile = selectedFilePath.toUri();
                            JButton open = new JButton("Open External");
                            open.setToolTipText("<html>Tries to open the saved file with the<br>system's default application.</html>");
                            open.addActionListener(e -> {
                                try {
                                    Desktop.getDesktop().browse(finalFile);
                                } catch (Exception ex) {
                                    JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                                }
                            });
                            options.add(open);
                            final URI finalParent = selectedFilePath.getParent().toUri();
                            JButton openDir = new JButton("Open Directory");
                            openDir.setToolTipText("<html>Tries to open the directory with the system's<br>default application for browsing files.</html>");
                            openDir.addActionListener(e -> {
                                try {
                                    Desktop.getDesktop().browse(finalParent);
                                } catch (Exception ex) {
                                    JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                                }
                            });
                            options.add(openDir);
                        } catch (Exception ex) {
                            // Will possibly never happen
                            Log.error("Error in preparation of save-info buttons: %s", ex.getMessage());
                        }
                    }

                    // toaster.toast("<html>Stored as<br><font size='+1'><i>%s</i></font></html>", selectedFilePath.getFileName());
                    JOptionPane.showOptionDialog(this,
                            "<html><font size='+1'>Stored PDF as<p><b>" + selectedFilePath + "</b></font><p></html>", "Stored",
                            JOptionPane.DEFAULT_OPTION,
                            JOptionPane.INFORMATION_MESSAGE, null, options.toArray(), null);


                }
            } catch (Exception ex) {
                toaster.toast(ToastType.ERROR, 20000, "<html><font size='+2'>Error</font><br><font size='+1'><i>%s</i></font></html>", ex.getMessage());
                // JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
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

    /**
     * Gets a svg icon by name. The svg file must exist in root folder of resources.
     *
     * @param name The name (filename without extension)
     * @return the icon, null if not found.
     */
    public static Icon getIcon(String name) {
        synchronized (icons) {
            Icon i = icons.get(name);
            if (i == null) {
                try (InputStream is = UI.class.getResourceAsStream("/" + name + ".svg")) {
                    ShapeIcon si = new ShapeIcon(SVGConverter.convert(is));
                    icons.put(name, si);
                    i = si;
                } catch (Exception e) {
                    Log.error("Error in getting icon '%s': %s", name, e.getMessage());
                }
            }
            return i;
        }
    }

    private void setSelectedPage(PageWidget page) {
        selectedPage = page;
        boolean enabled = page != null;
        deleteButton.setEnabled(enabled);
        rotateClockwiseButton.setEnabled(enabled);
        moveLeft.setEnabled(enabled);
        moveRight.setEnabled(enabled);
        exportImages.setEnabled(enabled);
        if (enabled) {
            pageNb.setText("Page " + page.getPageNumber());
            pageNb.setFont(normalFont);
            quality.setText(String.format("%d dpi (x %.2f)", page.getPage().dpi, page.getScale()));
            rotation.setText(page.getPage().getRotation() + " °");
        } else {
            rotation.setText("");
            pageNb.setText("Select a Page");
            pageNb.setFont(hintFont);
            quality.setText("");
        }
    }

    protected void appendPdf(File file, MergeOptions mo) {
        if (documentProxy == null)
            selectPdf(file);
        else {
            String parent = file.getParent();
            if (parent != null)
                Preferences.getInstance().set(Preferences.USER_PREF_LAST_PDF_DIR, parent);

            documentProxy.setOwnerPassword(ownerPasswordField.getText());
            final String fileName = file.getName();
            Toast toast = toaster.toast("<html><b>Appending…</b><br>%s</html>", fileName);
            long start = System.currentTimeMillis();
            documentProxy.load(file.toPath(), mo, document -> {
                if (toast != null)
                    toast.setMessage(String.format("<html><b>Appended (%d ms):</b><br>%s</html>",
                            System.currentTimeMillis() - start,
                            fileName));
            });
        }
    }

    private boolean busy;
    private JPanel glassPane;

    public void setBusy(boolean busy) {
        if (this.busy != busy) {
            this.busy = busy;
            Main.mainWindow.setCursor(Cursor.getPredefinedCursor(busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
            if (glassPane == null) {
                glassPane = new JPanel();
                glassPane.setOpaque(false);
                glassPane.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                glassPane.addMouseListener(new MouseAdapter() {
                }); // Block mouse events
                Main.mainWindow.setGlassPane(glassPane);

                glassPane.setVisible(busy);
            }
        }
    }

    /**
     * Select a pdf file for preview and processing.
     */
    protected void selectPdf(File selectedFile) {
        if (selectedFile != null) {
            selectedFile = selectedFile.getAbsoluteFile();
            String parent = selectedFile.getParent();
            if (parent != null)
                Preferences.getInstance().set(Preferences.USER_PREF_LAST_PDF_DIR, parent);
        }
        if (documentProxy != null) {
            documentProxy.close();
        }
        documentProxy = null;
        pages.clear();

        if (selectedFile != null) {
            documentProxy = new DocumentProxy(renderQueue);
            documentProxy.setOwnerPassword(ownerPasswordField.getText());

            documentProxy.addDocumentConsumer(new DocumentProxy.DocumentConsumer() {

                @Override
                public void documentLoaded(PDDocument document) {
                    // This method is called each time some file wasloaded.
                    // "document" is always the main effective document.

                    saveButton.setEnabled(true);
                    splitDocument.setEnabled(true);
                    browseAppendButton.setEnabled(true);

                    initDocumentInformation();

                    statusMessage.setText("");
                }

                @Override
                public void failed(String error) {
                    if (documentProxy == null)
                        // Force initialisation for case where loading the main file failed.
                        selectPdf(null);
                    statusMessage.setText(error == null ? "" : error);
                }
            });
            pages.setDocument(documentProxy);
            try {
                final String fileName = selectedFile.getName();
                Toast toast = toaster.toast("<html><b>Loading…</b><br><font size='+1'>%s</font></html>", fileName);
                long start = System.currentTimeMillis();
                documentProxy.load(selectedFile.toPath(), new MergeOptions(), document -> {
                    if (toast != null)
                        toast.setMessage(String.format("<html><b>Loaded (%d ms):</b><br>%s</html>",
                                System.currentTimeMillis() - start, fileName));
                });
            } catch (Exception e) {
                statusMessage.setText(e.getMessage());
                selectPdf(null);
            }
        } else {
            initDocumentInformation();

            saveButton.setEnabled(false);
            splitDocument.setEnabled(false);
            browseAppendButton.setEnabled(false);
        }
    }

    protected void setDocumentInformation(PDDocument pdDocument) {
        PDDocumentInformation info = new PDDocumentInformation();

        info.setCreationDate(Calendar.getInstance());
        info.setModificationDate(Calendar.getInstance());

        // TODO: Possibly outdated for PDF Version 2
        info.setTitle(title.getText());
        info.setAuthor(author.getText());
        info.setCreator(creator.getText());
        info.setProducer(producer.getText());
        info.setSubject(subject.getText());
        info.setKeywords(keywords.getText());

        pdDocument.setDocumentInformation(info);
        pdDocument.getDocumentCatalog().setMetadata(null);
    }

    protected void initDocumentInformation() {

        final boolean useDoc = useDocumentInformation.isSelected();
        final PDDocument document = documentProxy == null ? null : documentProxy.getDocument();
        if (document != null) {
            pageCount.setText(String.format("%d", documentProxy.getPageCount()));

            if (useDoc) {
                PDDocumentInformation info = document.getDocumentInformation();

                String a = info.getTitle();
                title.setText(a == null ? "" : a);
                a = info.getAuthor();
                author.setText(a == null ? "" : a);
                a = info.getCreator();
                creator.setText(a == null ? "" : a);
                a = info.getProducer();
                producer.setText(a == null ? "" : a);
                a = info.getSubject();
                subject.setText(a == null ? "" : a);
                a = info.getKeywords();
                keywords.setText(a == null ? "" : a);
            }
        } else {
            pageCount.setText("");
            if (useDoc) {
                title.setText("");
                author.setText("");
                creator.setText("");
                producer.setText("");
                subject.setText("");
                keywords.setText("");
            }
        }
        if (title.isEditable() == useDoc) {
            title.setEditable(!useDoc);
            author.setEditable(!useDoc);
            creator.setEditable(!useDoc);
            producer.setEditable(!useDoc);
            subject.setEditable(!useDoc);
            keywords.setEditable(!useDoc);
        }

    }


    protected void updatePermissions() {
        boolean rightsPossible =
                !(ownerPasswordField.getText().trim().isEmpty() || userPasswordField.getText().trim().isEmpty());
        allowPrinting.setEnabled(rightsPossible);
        allowExtraction.setEnabled(rightsPossible);
        allowModification.setEnabled(rightsPossible);
        allowFillIn.setEnabled(rightsPossible && !allowAnnotations.isSelected());
        allowAssembly.setEnabled(rightsPossible);
        allowAnnotations.setEnabled(rightsPossible);
    }

    public static JFileChooser getImageChooser() {
        if (imageChooser == null) {
            imageChooser = new JFileChooser();
        }
        imageChooser.setFileFilter(imageFilter);
        String lastDir = getPref(Preferences.USER_PREF_LAST_IMAGE_DIR, null);
        if (lastDir != null) {
            imageChooser.setCurrentDirectory(new File(lastDir));
        }
        imageChooser.setMultiSelectionEnabled(false);
        return imageChooser;
    }

    public static JFileChooser getPdfChooser() {
        if (pdfChooser == null) {
            pdfChooser = new JFileChooser();
        }
        pdfChooser.setFileFilter(pdfFilter);
        String lastDir = getPref(Preferences.USER_PREF_LAST_PDF_DIR, null);
        if (lastDir != null) {
            Log.debug("Last PDF Directory '%s'", lastDir);
            pdfChooser.setCurrentDirectory(new File(lastDir));
        }
        return pdfChooser;
    }

    public static JFileChooser getSavePdfChooser() {
        if (savePdfChooser == null) {
            savePdfChooser = new JFileChooser();
        }
        String lastDir = getPref(Preferences.USER_PREF_LAST_PDF_DIR, null);
        if (lastDir != null) {
            Log.debug("Last PDF Directory '%s'", lastDir);
            savePdfChooser.setCurrentDirectory(new File(lastDir));
        }
        savePdfChooser.setDialogTitle("Select PDF to Save…");
        savePdfChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        savePdfChooser.setMultiSelectionEnabled(false);
        savePdfChooser.setFileFilter(pdfFilter);
        savePdfChooser.setAccessory(null);
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
