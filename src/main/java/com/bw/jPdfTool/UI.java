package com.bw.jPdfTool;

import com.bw.jPdfTool.model.DocumentProxy;
import com.bw.jPdfTool.model.MergeOptions;
import com.bw.jPdfTool.model.Page;
import com.bw.jtools.svg.SVGConverter;
import com.bw.jtools.ui.ShapeIcon;
import com.formdev.flatlaf.FlatLaf;
import org.apache.pdfbox.multipdf.Splitter;
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
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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

    protected static JFileChooser savePdfChooser;
    protected static JFileChooser pdfChooser;
    protected static JFileChooser imageChooser;

    protected static FileNameExtensionFilter pdfFilter = new FileNameExtensionFilter("PDF-Files (*.pdf)", "pdf");
    protected static FileNameExtensionFilter imageFilter = new FileNameExtensionFilter("Picture (*.jpg, *.png)", "jpeg", "jpg", "png");

    private final JTextField ownerPasswordField = new JTextField();
    private final JTextField userPasswordField = new JTextField();

    private final JButton saveButton = new JButton("Write To File");
    private final JCheckBox compression = new JCheckBox("Compression");

    private final PageWidgetContainer pages = new PageWidgetContainer();
    protected DocumentProxy documentProxy;
    private PageWidget selectedPage;

    private final JTextField pageCount = new JTextField(4);
    private final JLabel statusMessage = new JLabel();


    /////////////////////////////////////////////
    // Permissions
    /// //////////////////////////////////////////

    private final JCheckBox allowPrinting = new JCheckBox("Printing");
    private final JCheckBox allowExtraction = new JCheckBox("Extraction");
    private final JCheckBox allowModification = new JCheckBox("Modify");
    private final JCheckBox allowFillIn = new JCheckBox("Fill Form");
    private final JCheckBox allowAssembly = new JCheckBox("Assembly");

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
    private final JButton help = new JButton(UIManager.getIcon("OptionPane.informationIcon"));

    /////////////////////////////////////////////
    // Splitting
    /// //////////////////////////////////////////
    private final JSpinner pagesPerDocument = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
    private final JButton splitDocument = new JButton("Split Document");

    protected final RenderQueue renderQueue = new RenderQueue();

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
                            appendPdf(droppedFiles.get(0), new MergeOptions());
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
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));

        JButton generateOwner = new JButton("Generate Owner Password");

        help.addActionListener(e -> {
            JOptionPane.showMessageDialog(this,
                    "<html><font size='+1'>To prevent unauthorized access to a PDF, you only need to set a user password.<br>" +
                            "If you also want to define specific permissions - such as restricting printing or " +
                            "editing - you must set both an owner password and a user password.<br>" +
                            "The owner password always grants full control over the document, while the user password " +
                            "enforces only the permissions you've selected.<br>" +
                            "Most tools use a random owner password.</font></html>");
        });

        compression.setSelected(true);
        allowPrinting.setSelected(true);
        saveButton.setEnabled(false);
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
        gcLabel.insets = new Insets(0, 0, 0, 5);

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

        JPanel permissions = new JPanel(new GridLayout(3, 2));

        permissions.add(compression);
        permissions.add(allowPrinting);
        permissions.add(allowModification);
        permissions.add(allowExtraction);
        permissions.add(allowFillIn);
        permissions.add(allowAssembly);

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
        gc.gridwidth = 3;
        gc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(saveButton, gc);

        gcLabel.gridy = gc.gridy + 1;
        gcLabel.anchor = GridBagConstraints.SOUTHWEST;
        gcLabel.weighty = 0.5;
        gcLabel.gridwidth = 3;
        gcLabel.fill = GridBagConstraints.BOTH;
        gcLabel.insets = new Insets(15, 5, 5, 5);
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
        checkPassword();
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
    }

    protected JPanel createDocumentPanel() {
        JPanel documentPanel = new JPanel(new GridBagLayout());
        documentPanel.setBorder(BorderFactory.createTitledBorder("Effective Document"));

        JPanel browseButtons = new JPanel(new GridLayout(2, 1, 5, 5));

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

        gc.gridy = 0;
        gc.gridx = 2;
        gc.gridheight = 3;
        gc.anchor = GridBagConstraints.EAST;
        documentPanel.add(browseButtons, gc);

        gc.gridy = 1;
        gc.gridx = 0;
        gc.gridheight = 2;
        gc.gridwidth = 2;
        gc.anchor = GridBagConstraints.WEST;
        documentPanel.add(statusMessage, gc);

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

    protected void setLaf(String laf) {
        try {
            if (!Objects.equals(UIManager.getLookAndFeel().getClass().getName(), laf)) {
                UIManager.setLookAndFeel(laf);
                FlatLaf.updateUILater();
                if (pdfChooser != null)
                    SwingUtilities.updateComponentTreeUI(pdfChooser);
                if (imageChooser != null)
                    SwingUtilities.updateComponentTreeUI(imageChooser);
                SwingUtilities.updateComponentTreeUI(mergeOptions);
                Preferences.getInstance().set(Preferences.USER_PREF_LAF, laf);
                help.setOpaque(false);
                help.setIcon(UIManager.getIcon("OptionPane.informationIcon"));
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

    public synchronized JMenuBar getMenu() {
        if (menuBar == null) {

            lafDark.addActionListener(e -> setLaf(Preferences.LAF_DARK_CLASSNAME));
            lafLight.addActionListener(e -> setLaf(Preferences.LAF_LIGHT_CLASSNAME));
            lafSystem.addActionListener(e -> setLaf(UIManager.getSystemLookAndFeelClassName()));
            lafCross.addActionListener(e -> setLaf(UIManager.getCrossPlatformLookAndFeelClassName()));

            menuBar = new JMenuBar();

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

            JMenu options = new JMenu("Options");
            options.add(storeOwnerPassword);
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
                            String docFile = String.format("%s%03d%s", fprefix, ++fileCount, fpostfix);
                            doc.save(docFile);
                            Log.info("Stored file '%s'", docFile);
                            doc.close();
                        }
                    } catch (IOException e) {
                        JOptionPane.showMessageDialog(this, e.getMessage(),
                                "PDF Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }
    }

    /**
     * Requests a file name and saves the document.
     */
    protected void doSave() {
        String ownerPwd = ownerPasswordField.getText().trim();
        String userPwd = userPasswordField.getText().trim();

        if (documentProxy != null) {
            PDDocument document = documentProxy.getDocument();
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
                        var prefs = Preferences.getInstance();
                        if (prefs.getBoolean(Preferences.USER_PREF_STORE_OWNER_PASSWORD, false)) {
                            prefs.set(Preferences.USER_PREF_OWNER_PASSWORD, ownerPwd);
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
                    ShapeIcon si = new ShapeIcon(SVGConverter.convert(is));
                    icons.put(name, si);
                    i = si;
                } catch (Exception e) {
                    e.printStackTrace();
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
            quality.setText(String.format("%d dpi (x %.2f)", page.getPage().dpi, page.getScale()));
            rotation.setText(page.getPage().getRotation() + " °");
        } else {
            rotation.setText("");
            pageNb.setText("<html><i>Select a Page</i></hml>");
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
            documentProxy.load(file.toPath(), mo);
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
        selectedFile = selectedFile.getAbsoluteFile();
        String parent = selectedFile.getParent();
        if (parent != null)
            Preferences.getInstance().set(Preferences.USER_PREF_LAST_PDF_DIR, parent);
        if (documentProxy != null) {
            documentProxy.close();
        }
        pages.clear();

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
                pageCount.setText(String.format("%d", documentProxy.getPageCount()));
                statusMessage.setText("");
            }

            @Override
            public void failed(String error) {
                saveButton.setEnabled(false);
                splitDocument.setEnabled(false);
                browseAppendButton.setEnabled(false);
                documentProxy = null;
                statusMessage.setText(error == null ? "" : error);
            }
        });
        pages.setDocument(documentProxy);
        Path p;
        try {
            documentProxy.load(selectedFile.toPath(), new MergeOptions());
        } catch (Exception e) {
            statusMessage.setText(e.getMessage());
        }
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
